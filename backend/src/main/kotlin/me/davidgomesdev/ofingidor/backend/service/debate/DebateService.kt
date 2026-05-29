package me.davidgomesdev.ofingidor.backend.service.debate

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.ContentMetadata
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.UserMessage
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Scope
import io.quarkus.arc.Arc
import io.quarkus.arc.ManagedContext
import io.smallrye.mutiny.Multi
import io.smallrye.mutiny.subscription.MultiEmitter
import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.ofingidor.backend.constants.DebateApiConstants
import me.davidgomesdev.ofingidor.backend.llm.PersonaContext
import me.davidgomesdev.ofingidor.backend.llm.TextAttributes
import me.davidgomesdev.ofingidor.shared.dto.ChatEvent
import me.davidgomesdev.ofingidor.shared.dto.DebateEvent
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.jboss.logging.Logger
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

fun interface DebateAssistant {
    fun chat(@UserMessage message: String): TokenStream
}

@ApplicationScoped
class DebateService(
    private val debateAssistant: DebateAssistant,
    private val personaContext: PersonaContext,
    private val promptBuilder: DebatePromptBuilder,
    private val transcriptRepository: DebateTranscriptRepository,
) {

    private val log: Logger = Logger.getLogger(this::class.java)
    private val tracer = GlobalOpenTelemetry.getTracer(this::class.java.name)

    private data class DebateContext(
        val input: String,
        val conversationId: UUID,
        val speakerPlan: List<Persona>,
        val emitter: MultiEmitter<in DebateEvent>,
        val callerSpan: Span,
        val callerScope: Scope,
    )

    fun query(
        input: String,
        conversationId: UUID,
        personaA: Persona,
        personaB: Persona,
        callerSpan: Span,
    ): Multi<DebateEvent> {
        val speakerPlan = listOf(personaA, personaB, personaA, personaB)

        return Multi.createFrom().emitter { emitter ->
            val ctx = DebateContext(input, conversationId, speakerPlan, emitter, callerSpan, callerSpan.makeCurrent())
            try {
                transcriptRepository.appendUserPrompt(conversationId, 0, input)
                emitter.emit(DebateEvent.Start(callerSpan.spanContext.traceId, personaA.codeName, personaB.codeName))
                processTurn(ctx, 0)
            } catch (error: Throwable) {
                failStream(ctx, error)
            }
        }
    }

    private fun processTurn(ctx: DebateContext, turnIndex: Int) {
        if (turnIndex > ctx.speakerPlan.lastIndex) {
            finishSuccessfully(ctx)
            return
        }

        val requestContext = Arc.container()?.requestContext() as? ManagedContext
        val activatedForThisTurn = requestContext != null && !requestContext.isActive
        if (activatedForThisTurn) requestContext!!.activate()

        val speaker = ctx.speakerPlan[turnIndex]
        val persistedTurnIndex = turnIndex + 1

        personaContext.persona = speaker

        val prompt = try {
            buildPrompt(ctx, persistedTurnIndex, speaker)
        } catch (error: Throwable) {
            if (activatedForThisTurn) requestContext?.terminate()
            failStream(ctx, error)
            return
        }

        val turnSpan = tracer.spanBuilder(DebateApiConstants.SPAN_NAME_DEBATE_TURN)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("conversationId", ctx.conversationId.toString())
            .setAttribute("speaker", speaker.codeName)
            .setAttribute("turnIndex", persistedTurnIndex.toLong())
            .startSpan()
        val turnScope = turnSpan.makeCurrent()

        ctx.emitter.emit(DebateEvent.TurnStart(persistedTurnIndex, speaker))

        val stream = try {
            debateAssistant.chat(prompt)
        } catch (error: Throwable) {
            if (activatedForThisTurn) requestContext?.terminate()
            endTurnSpanWithError(error, turnSpan, turnScope)
            failStream(ctx, error)
            return
        }

        attachStreamHandlers(stream, ctx, turnIndex, persistedTurnIndex, speaker, turnSpan, turnScope, requestContext, activatedForThisTurn)
        // Context intentionally NOT terminated here — it stays alive until the async callbacks fire
    }

    private fun buildPrompt(ctx: DebateContext, persistedTurnIndex: Int, speaker: Persona): String =
        if (persistedTurnIndex == 1) {
            val opponent = ctx.speakerPlan[1]
            promptBuilder.openingPrompt(ctx.input, speaker, opponent)
        } else {
            promptBuilder.rebuttalPrompt(
                ctx.input,
                speaker,
                transcriptRepository.loadTranscript(ctx.conversationId),
            )
        }

    private fun attachStreamHandlers(
        stream: TokenStream,
        ctx: DebateContext,
        turnIndex: Int,
        persistedTurnIndex: Int,
        speaker: Persona,
        turnSpan: Span,
        turnScope: Scope,
        requestContext: ManagedContext?,
        activatedForThisTurn: Boolean,
    ) {
        val startTime = TimeSource.Monotonic.markNow()
        val capturedSources = mutableListOf<ChatEvent.Sources.Source>()
        val responseText = StringBuilder()
        var emittedSources = false

        stream
            .onRetrieved { contents ->
                val items = contents.map(::toSourceItem)
                capturedSources.clear()
                capturedSources.addAll(items)
                emittedSources = true
                ctx.emitter.emit(DebateEvent.Sources(persistedTurnIndex, speaker, ChatEvent.Sources(items)))
            }
            .onPartialResponse { partialResponse ->
                responseText.append(partialResponse)
                ctx.emitter.emit(DebateEvent.Token(persistedTurnIndex, speaker, partialResponse))
            }
            .onCompleteResponse { response ->
                try {
                    if (!emittedSources) {
                        ctx.emitter.emit(DebateEvent.Sources(persistedTurnIndex, speaker, ChatEvent.Sources()))
                    }

                    val finalText = response.aiMessage().text() ?: responseText.toString()
                    transcriptRepository.appendPersonaTurn(ctx.conversationId, persistedTurnIndex, speaker, finalText, capturedSources)

                    val timeTaken = startTime.elapsedNow().toString(DurationUnit.SECONDS, 2)
                    val totalTokensUsed = response.tokenUsage().totalTokenCount()

                    ctx.emitter.emit(DebateEvent.TurnDone(persistedTurnIndex, speaker, totalTokensUsed, timeTaken))
                    turnScope.close()
                    turnSpan.end()
                    if (activatedForThisTurn) requestContext?.terminate()
                    processTurn(ctx, turnIndex + 1)
                } catch (error: Throwable) {
                    if (activatedForThisTurn) requestContext?.terminate()
                    endTurnSpanWithError(error, turnSpan, turnScope)
                    failStream(ctx, error)
                }
            }
            .onError { error ->
                log.error("Debate turn failed for ${speaker.codeName}", error)
                if (activatedForThisTurn) requestContext?.terminate()
                endTurnSpanWithError(error, turnSpan, turnScope)
                failStream(ctx, error)
            }
            .start()
    }

    private fun finishSuccessfully(ctx: DebateContext) {
        ctx.emitter.emit(DebateEvent.Done)
        ctx.emitter.complete()
        ctx.callerScope.close()
        ctx.callerSpan.end()
    }

    private fun failStream(ctx: DebateContext, error: Throwable) {
        ctx.callerSpan.recordException(error)
        ctx.callerSpan.setStatus(StatusCode.ERROR)
        ctx.callerScope.close()
        ctx.callerSpan.end()
        ctx.emitter.fail(error)
    }

    private fun endTurnSpanWithError(error: Throwable, span: Span, scope: Scope) {
        span.recordException(error)
        span.setStatus(StatusCode.ERROR)
        scope.close()
        span.end()
    }

    private fun toSourceItem(source: Content): ChatEvent.Sources.Source {
        val score = ((source.metadata()[ContentMetadata.SCORE] as? Double) ?: 0.0) * 100
        val metadata = source.textSegment().metadata()

        return ChatEvent.Sources.Source(
            id = metadata.getLong(TextAttributes.TEXT_ID) ?: 0,
            title = metadata.getString(TextAttributes.TITLE) ?: "",
            author = metadata.getString(TextAttributes.AUTHOR) ?: "",
            category = metadata.getString(TextAttributes.CATEGORY_NAME) ?: "",
            score = score.roundToInt(),
        )
    }
}
