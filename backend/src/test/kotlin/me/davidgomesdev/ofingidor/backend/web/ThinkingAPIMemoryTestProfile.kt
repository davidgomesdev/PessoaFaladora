package me.davidgomesdev.ofingidor.backend.web

import com.google.common.util.concurrent.Futures
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.FinishReason
import dev.langchain4j.model.output.TokenUsage
import dev.langchain4j.rag.content.Content
import dev.langchain4j.service.TokenStream
import dev.langchain4j.service.tool.ToolExecution
import io.opentelemetry.api.trace.Span
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.Collections
import io.quarkus.test.Mock
import io.quarkus.test.junit.QuarkusTestProfile
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.Dependent
import jakarta.enterprise.inject.Produces
import jakarta.inject.Singleton
import me.davidgomesdev.ofingidor.backend.service.Assistant
import me.davidgomesdev.ofingidor.backend.service.debate.DebateService
import me.davidgomesdev.ofingidor.shared.dto.DebateEvent
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.function.Consumer

class ThinkingAPIMemoryTestProfile : QuarkusTestProfile {
    override fun getConfigOverrides(): Map<String, String> =
        mapOf(
            "recreate.embeddings" to "false",
            "preview-only" to "true",
            "session.jwt.secret" to "test-secret-that-is-at-least-32-chars!!",
            "session.jwt.ttl" to "PT1H",
            "session.memory.max-messages" to "20",
            "quarkus.datasource.db-kind" to "h2",
            "quarkus.datasource.jdbc.url" to "jdbc:h2:mem:thinking_test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "quarkus.datasource.username" to "sa",
            "quarkus.datasource.password" to "",
            "quarkus.flyway.migrate-at-start" to "true",
        )

    @Dependent
    class MockProducers {
        @Produces
        @Mock
        @Singleton
        fun qdrantClient(): QdrantClient {
            val mock = Mockito.mock(QdrantClient::class.java)
            Mockito.`when`(mock.listCollectionsAsync()).thenReturn(
                Futures.immediateFuture(emptyList<String>()),
            )
            Mockito
                .`when`(
                    mock.createCollectionAsync(
                        Mockito.anyString(),
                        Mockito.any(Collections.VectorParams::class.java),
                    ),
                ).thenReturn(
                    Futures.immediateFuture(Collections.CollectionOperationResponse.getDefaultInstance()),
                )
            return mock
        }

        @Produces
        @Mock
        @Singleton
        fun embeddingModel(): EmbeddingModel {
            val mock = Mockito.mock(EmbeddingModel::class.java)
            Mockito.`when`(mock.dimension()).thenReturn(384)
            return mock
        }

        @Produces
        @Mock
        @Singleton
        fun assistant(): Assistant = Assistant { _, _ -> respondingTokenStream("Sou Alberto Caeiro.") }

        @Produces
        @Mock
        @Singleton
        fun debateService(): DebateService {
            val mock = Mockito.mock(DebateService::class.java)
            whenever(mock.query(any(), any(), any(), any(), any<Span>())).thenReturn(
                Multi.createFrom().items(
                    DebateEvent.Start("trace-id", "fernando_pessoa", "alberto_caeiro"),
                    DebateEvent.Done,
                ),
            )
            return mock
        }

        private fun respondingTokenStream(text: String): TokenStream =
            object : TokenStream {
                private var onComplete: Consumer<ChatResponse>? = null
                private var onError: Consumer<Throwable>? = null

                override fun onPartialResponse(consumer: Consumer<String>): TokenStream = this

                override fun onRetrieved(consumer: Consumer<List<Content>>): TokenStream = this

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
                    val response =
                        ChatResponse
                            .builder()
                            .aiMessage(AiMessage.from(text))
                            .tokenUsage(TokenUsage(10, 5))
                            .finishReason(FinishReason.STOP)
                            .build()
                    onComplete?.accept(response)
                }
            }
    }
}
