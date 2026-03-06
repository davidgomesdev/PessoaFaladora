package me.davidgomesdev.llm

import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.TextContent
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.observability.api.listener.AiServiceErrorListener
import dev.langchain4j.observability.api.listener.AiServiceStartedListener
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.service.AiServices
import io.opentelemetry.api.trace.Span
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import me.davidgomesdev.observability.attributes
import me.davidgomesdev.service.Assistant
import org.jboss.logging.Logger

@ApplicationScoped
class ModelConfig {

    val log: Logger = Logger.getLogger(this::class.java)

    @Singleton
    @Suppress("unused")
    fun assistant(chatModel: StreamingChatModel, retrievalAugmentor: RetrievalAugmentor): Assistant {
        log.info("Creating assistant")

        return AiServices.builder(Assistant::class.java)
            .registerListeners(
                AiServiceStartedListener { event ->
                    Span.current().addEvent(
                        "LLM query",
                        attributes {
                            put(
                                "user_message",
                                event.userMessage().contents()
                                    .filterIsInstance<TextContent>()
                                    .joinToString("\n\n", transform = TextContent::text)
                            )
                            put(
                                "system_message", event.systemMessage()
                                    .map(SystemMessage::text)
                                    .orElseGet { "" })
                        })
                },
                AiServiceErrorListener { error ->
                    Span.current().recordException(error.error())
                })
            .streamingChatModel(chatModel)
            .retrievalAugmentor(retrievalAugmentor)
            .build()
    }
}
