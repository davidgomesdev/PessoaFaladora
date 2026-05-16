package me.davidgomesdev.ofingidor.backend.debate

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.model.output.TokenUsage
import dev.langchain4j.rag.content.Content
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.tool.ToolExecution
import io.opentelemetry.api.trace.Span
import me.davidgomesdev.ofingidor.backend.llm.PersonaContext
import me.davidgomesdev.ofingidor.backend.service.debate.DebateAssistant
import me.davidgomesdev.ofingidor.backend.service.debate.DebatePromptBuilder
import me.davidgomesdev.ofingidor.backend.service.debate.DebateService
import me.davidgomesdev.ofingidor.backend.service.debate.DebateTranscriptRepository
import me.davidgomesdev.ofingidor.backend.service.debate.DebateTurnEntity
import me.davidgomesdev.ofingidor.shared.dto.DebateEvent
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.function.Consumer

class DebateServiceTest {

    private val personaContext = PersonaContext()
    private val debateAssistant = mock<DebateAssistant>()
    private val promptBuilder = DebatePromptBuilder()
    private val transcriptRepository = mock<DebateTranscriptRepository>()
    private val service = DebateService(debateAssistant, personaContext, promptBuilder, transcriptRepository)

    @Test
    fun `query emits speakers in fixed A B A B order`() {
        val conversationId = UUID.fromString("00000000-0000-0000-0000-000000000123")
        val transcript = mutableListOf<DebateTurnEntity>()

        doAnswer { invocation ->
            transcript += DebateTurnEntity().apply {
                this.conversationId = conversationId
                turnIndex = invocation.getArgument(1)
                entryType = "user_prompt"
                text = invocation.getArgument(2)
                createdAt = OffsetDateTime.now()
            }
            Unit
        }.whenever(transcriptRepository).appendUserPrompt(any(), any(), any())

        doAnswer { invocation ->
            transcript += DebateTurnEntity().apply {
                this.conversationId = conversationId
                turnIndex = invocation.getArgument(1)
                entryType = "persona_turn"
                speakerPersonaCode = invocation.getArgument<Persona>(2).codeName
                text = invocation.getArgument(3)
                createdAt = OffsetDateTime.now()
            }
            Unit
        }.whenever(transcriptRepository).appendPersonaTurn(any(), any(), any(), any(), any())

        whenever(transcriptRepository.loadTranscript(conversationId)).thenAnswer { transcript.toList() }
        whenever(debateAssistant.chat(any())).thenAnswer {
            val speaker = checkNotNull(personaContext.persona)
            respondingTokenStream(text = "Resposta de ${speaker.codeName}")
        }

        val events = service.query(
            input = "Debatam a sinceridade poética.",
            conversationId = conversationId,
            personaA = Persona.FERNANDO_PESSOA,
            personaB = Persona.ALBERTO_CAEIRO,
            callerSpan = Span.getInvalid(),
        ).collect().asList().subscribeAsCompletionStage().toCompletableFuture().get()

        val speakers = events.filterIsInstance<DebateEvent.TurnStart>().map { it.speaker }

        assertEquals(
            listOf("fernando_pessoa", "alberto_caeiro", "fernando_pessoa", "alberto_caeiro"),
            speakers,
        )
    }

    @Test
    fun `query fails fast when a later turn errors`() {
        val conversationId = UUID.fromString("00000000-0000-0000-0000-000000000124")
        val transcript = mutableListOf<DebateTurnEntity>()

        doAnswer { invocation ->
            transcript += DebateTurnEntity().apply {
                this.conversationId = conversationId
                turnIndex = invocation.getArgument(1)
                entryType = "user_prompt"
                text = invocation.getArgument(2)
                createdAt = OffsetDateTime.now()
            }
            Unit
        }.whenever(transcriptRepository).appendUserPrompt(any(), any(), any())

        doAnswer { invocation ->
            transcript += DebateTurnEntity().apply {
                this.conversationId = conversationId
                turnIndex = invocation.getArgument(1)
                entryType = "persona_turn"
                speakerPersonaCode = invocation.getArgument<Persona>(2).codeName
                text = invocation.getArgument(3)
                createdAt = OffsetDateTime.now()
            }
            Unit
        }.whenever(transcriptRepository).appendPersonaTurn(any(), any(), any(), any(), any())

        whenever(transcriptRepository.loadTranscript(conversationId)).thenAnswer { transcript.toList() }
        whenever(debateAssistant.chat(any()))
            .thenReturn(respondingTokenStream("Primeira resposta"))
            .thenReturn(failingTokenStream(IllegalStateException("turn exploded")))

        val error = assertThrows<ExecutionException> {
            service.query(
                input = "Debatam a sinceridade poética.",
                conversationId = conversationId,
                personaA = Persona.FERNANDO_PESSOA,
                personaB = Persona.ALBERTO_CAEIRO,
                callerSpan = Span.getInvalid(),
            ).collect().asList().subscribeAsCompletionStage().toCompletableFuture().get()
        }

        assertEquals("turn exploded", error.cause?.message)
        verify(debateAssistant, times(2)).chat(any())
        verify(transcriptRepository, times(1)).appendPersonaTurn(any(), any(), any(), any(), any())
    }

    private fun respondingTokenStream(text: String): TokenStream = object : TokenStream {
        private var onPartialResponse: Consumer<String>? = null
        private var onRetrieved: Consumer<List<Content>>? = null
        private var onComplete: Consumer<ChatResponse>? = null
        private var onError: Consumer<Throwable>? = null

        override fun onPartialResponse(consumer: Consumer<String>): TokenStream {
            onPartialResponse = consumer
            return this
        }

        override fun onRetrieved(consumer: Consumer<List<Content>>): TokenStream {
            onRetrieved = consumer
            return this
        }

        override fun onToolExecuted(consumer: Consumer<ToolExecution>): TokenStream = this

        override fun onCompleteResponse(consumer: Consumer<ChatResponse>): TokenStream {
            onComplete = consumer
            return this
        }

        override fun onError(consumer: Consumer<Throwable>): TokenStream {
            onError = consumer
            return this
        }

        override fun ignoreErrors(): TokenStream = this

        override fun start() {
            onRetrieved?.accept(emptyList())
            onPartialResponse?.accept(text)
            onComplete?.accept(
                ChatResponse.builder()
                    .aiMessage(AiMessage.from(text))
                    .tokenUsage(TokenUsage(12, 8))
                    .finishReason(FinishReason.STOP)
                    .build()
            )
        }
    }

    private fun failingTokenStream(error: Throwable): TokenStream = object : TokenStream {
        private var onError: Consumer<Throwable>? = null

        override fun onPartialResponse(consumer: Consumer<String>): TokenStream = this
        override fun onRetrieved(consumer: Consumer<List<Content>>): TokenStream = this
        override fun onToolExecuted(consumer: Consumer<ToolExecution>): TokenStream = this
        override fun onCompleteResponse(consumer: Consumer<ChatResponse>): TokenStream = this

        override fun onError(consumer: Consumer<Throwable>): TokenStream {
            onError = consumer
            return this
        }

        override fun ignoreErrors(): TokenStream = this

        override fun start() {
            onError?.accept(error)
        }
    }
}
