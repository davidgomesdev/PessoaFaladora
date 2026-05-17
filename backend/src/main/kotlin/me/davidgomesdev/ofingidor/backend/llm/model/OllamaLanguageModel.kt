package me.davidgomesdev.ofingidor.backend.llm.model

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.model.ollama.OllamaEmbeddingModel
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.ofingidor.backend.llm.config.OllamaConfig
import org.jboss.logging.Logger

@ApplicationScoped
class OllamaLanguageModel(val config: OllamaConfig) : LanguageModel, EmbeddingChatModel {

    val log: Logger = Logger.getLogger(this::class.java)

    override fun chatModel(): ChatModel {
        log.info("Creating Ollama chat model: ${config.chatModel().modelId()}")
        return OllamaChatModel.builder()
            .baseUrl(config.baseUrl())
            .modelName(config.chatModel().modelId())
            .temperature(config.chatModel().temperature())
            .timeout(config.timeout())
            .build()
    }

    override fun streamingChatModel(): StreamingChatModel {
        return config.chatModel().run {
            log.info("Creating Ollama streaming chat model: ${modelId()}")
            OllamaStreamingChatModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(modelId())
                .temperature(temperature())
                .timeout(config.timeout())
                .think(thinking())
                .returnThinking(thinking())
                .build()
        }
    }

    override fun embeddingModel(): EmbeddingModel {
        log.info("Creating Ollama embedding model: ${config.embeddingModel().modelId()}")
        return OllamaEmbeddingModel.builder()
            .baseUrl(config.baseUrl())
            .modelName(config.embeddingModel().modelId())
            .timeout(config.timeout())
            .build()
    }
}
