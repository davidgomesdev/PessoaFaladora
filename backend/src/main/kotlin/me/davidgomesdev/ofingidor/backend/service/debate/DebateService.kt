package me.davidgomesdev.ofingidor.backend.service.debate

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.ContentMetadata
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.UserMessage
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.quarkus.arc.Arc
import io.smallrye.mutiny.Multi
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

    fun query(
        input: String,
        conversationId: UUID,
        personaA: Persona,
        personaB: Persona,
        callerSpan: Span,
    ): Multi<DebateEvent> {
        val speakerPlan = listOf(personaA, personaB, personaA, personaB)

        return Multi.createFrom().emitter { emitter ->
            val callerScope = callerSpan.makeCurrent()

            fun finishSuccessfully() {
                emitter.emit(DebateEvent.Done)
                emitter.complete()
                callerScope.close()
                callerSpan.end()
            }

            fun failStream(error: Throwable) {
                callerSpan.recordException(error)
                callerSpan.setStatus(StatusCode.ERROR)
                callerScope.close()
                callerSpan.end()
                emitter.fail(error)
            }

            fun processTurn(turnIndex: Int) {
                if (turnIndex > speakerPlan.lastIndex) {
                    finishSuccessfully()
                    return
                }

                withActiveRequestContext {
                    val speaker = speakerPlan[turnIndex]
                    val persistedTurnIndex = turnIndex + 1

                    personaContext.persona = speaker

                    val prompt = try {
                        if (persistedTurnIndex == 1) {
                            promptBuilder.openingPrompt(input, speaker)
                        } else {
                            promptBuilder.rebuttalPrompt(
                                input,
                                speaker,
                                transcriptRepository.loadTranscript(conversationId)
                            )
                        }
                    } catch (error: Throwable) {
                        failStream(error)
                        return
                    }

                    val turnSpan = tracer.spanBuilder(DebateApiConstants.SPAN_NAME_DEBATE_TURN)
                        .setSpanKind(SpanKind.INTERNAL)
                        .setAttribute("conversationId", conversationId.toString())
                        .setAttribute("speaker", speaker.codeName)
                        .setAttribute("turnIndex", persistedTurnIndex.toLong())
                        .startSpan()
                    val turnScope = turnSpan.makeCurrent()
                    val startTime = TimeSource.Monotonic.markNow()
                    val capturedSources = mutableListOf<ChatEvent.Sources.Source>()
                    val responseText = StringBuilder()
                    var emittedSources = false

                    emitter.emit(DebateEvent.TurnStart(persistedTurnIndex, speaker))

                    val stream = try {
                        debateAssistant.chat(prompt)
                    } catch (error: Throwable) {
                        turnSpan.recordException(error)
                        turnSpan.setStatus(StatusCode.ERROR)
                        turnScope.close()
                        turnSpan.end()
                        failStream(error)
                        return
                    }

                    stream
                        .onRetrieved { contents ->
                            val items = contents.map(::toSourceItem)
                            capturedSources.clear()
                            capturedSources.addAll(items)
                            emittedSources = true
                            emitter.emit(DebateEvent.Sources(persistedTurnIndex, speaker, ChatEvent.Sources(items)))
                        }
                        .onPartialResponse { partialResponse ->
                            responseText.append(partialResponse)
                            emitter.emit(DebateEvent.Token(persistedTurnIndex, speaker, partialResponse))
                        }
                        .onCompleteResponse { response ->
                            try {
                                if (!emittedSources) {
                                    emitter.emit(DebateEvent.Sources(persistedTurnIndex, speaker, ChatEvent.Sources()))
                                }

                                val finalText = response.aiMessage().text() ?: responseText.toString()
                                transcriptRepository.appendPersonaTurn(
                                    conversationId = conversationId,
                                    turnIndex = persistedTurnIndex,
                                    speaker = speaker,
                                    text = finalText,
                                    sources = capturedSources,
                                )

                                val timeTaken = startTime.elapsedNow().toString(DurationUnit.SECONDS, 2)
                                val totalTokensUsed = response.tokenUsage().totalTokenCount()

                                emitter.emit(
                                    DebateEvent.TurnDone(
                                        turnIndex = persistedTurnIndex,
                                        speaker = speaker,
                                        tokensUsed = totalTokensUsed,
                                        timeTaken = timeTaken,
                                    )
                                )

                                turnScope.close()
                                turnSpan.end()
                                processTurn(turnIndex + 1)
                            } catch (error: Throwable) {
                                turnSpan.recordException(error)
                                turnSpan.setStatus(StatusCode.ERROR)
                                turnScope.close()
                                turnSpan.end()
                                failStream(error)
                            }
                        }
                        .onError { error ->
                            log.error("Debate turn failed for ${speaker.codeName}", error)
                            turnSpan.recordException(error)
                            turnSpan.setStatus(StatusCode.ERROR)
                            turnScope.close()
                            turnSpan.end()
                            failStream(error)
                        }
                        .start()
                }
            }

            try {
                transcriptRepository.appendUserPrompt(conversationId, 0, input)
                emitter.emit(DebateEvent.Start(callerSpan.spanContext.traceId, personaA.codeName, personaB.codeName))
                processTurn(0)
            } catch (error: Throwable) {
                failStream(error)
            }
        }
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

    private inline fun withActiveRequestContext(block: () -> Unit) {
        val container = Arc.container()
        if (container == null) {
            block()
            return
        }

        val requestContext = container.requestContext()
        val activatedHere = !requestContext.isActive

        if (activatedHere) {
            requestContext.activate()
        }

        try {
            block()
        } finally {
            if (activatedHere) {
                requestContext.terminate()
            }
        }
    }
}
