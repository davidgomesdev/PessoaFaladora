package me.davidgomesdev.service

import dev.langchain4j.rag.content.Content
import dev.langchain4j.rag.content.ContentMetadata
import dev.langchain4j.service.SystemMessage
import dev.langchain4j.service.TokenStream
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.quarkus.runtime.Startup
import io.smallrye.mutiny.Multi
import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.observability.attributes
import org.jboss.logging.Logger
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.TimeSource

fun interface Assistant {
    @SystemMessage(
        fromResource = "system_message.txt",
    )
    fun chat(userMessage: String): TokenStream
}

@ApplicationScoped
@Startup
class ChatService(val assistant: Assistant) {

    val log: Logger = Logger.getLogger(this::class.java)

    fun query(input: String): Multi<String> {
        val chatStream = assistant.chat(input)
        val timeSource = TimeSource.Monotonic
        val startTime = timeSource.markNow()

        return Multi.createFrom().emitter { stream ->
            chatStream
                .onPartialResponse { partialResponse -> stream.emit(partialResponse); }
                .onCompleteResponse { response ->
                    val message = response.aiMessage().text()
                    val timeTaken = (startTime.elapsedNow())
                        .toString(DurationUnit.SECONDS, 2)
                    val tokensUsed = response.tokenUsage().outputTokenCount()

                    log.info(
                        "Took $timeTaken to respond (used $tokensUsed output tokens)"
                    )

                    Span.current().apply {
                        addEvent(
                            "Response complete",
                            attributes {
                                put("message", message)
                                put("model_duration.ms", timeTaken)
                                put("output_tokens_used", tokensUsed.toLong())
                            }
                        )
                    }

                    stream.complete()
                }
                .onRetrieved { contents ->
                    Span.current().apply {
                        contents.forEachIndexed { index, content ->
                            val score = (content.metadata()[ContentMetadata.SCORE] as? Double) ?: 0.0
                            val metadata = content.textSegment().metadata()

                            addEvent(
                                "Source Retrieved",
                                attributes {
                                    put("index", index.toString())
                                    put("title", metadata.getString("title"))
                                    put("category", metadata.getString("categoryName"))
                                    put("score", String.format("%.2f", score))
                                }
                            )
                        }
                    }

                    val sources = contents.joinToString(separator = "\n", transform = ::mergeSources)

                    log.info("Using sources:\n$sources")

                    stream.emit("<sources>$sources</sources>")
                }
                .onError { error ->
                    stream.fail(error)

                    Span.current().apply {
                        recordException(error)
                        setStatus(StatusCode.ERROR)
                    }

                    log.error("There was a problem with the assistant!", error)
                }
                .start()
        }
    }

    private fun mergeSources(source: Content): String {
        val score = ((source.metadata()[ContentMetadata.SCORE] as Double) * 100).roundToInt()
        val metadata = source.textSegment().metadata()

        return "- Categoria: ${metadata.getString("categoryName") ?: ""}\n" +
                "  Título: ${metadata.getString("title")}\n" +
                "  Autor ${
                    metadata.getString("author")
                } " +
                "(score: $score%)"
    }
}
