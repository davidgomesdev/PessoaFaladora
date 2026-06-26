package me.davidgomesdev.ofingidor.backend.llm.config

import io.smallrye.config.ConfigMapping
import java.time.Duration

@ConfigMapping(prefix = "model.ollama")
interface OllamaConfig {
    fun baseUrl(): String

    fun timeout(): Duration

    fun chatModel(): ChatModelConfig

    fun embeddingModel(): EmbeddingModelConfig

    interface ChatModelConfig {
        fun modelId(): String

        fun temperature(): Double

        fun thinking(): Boolean
    }

    @SuppressWarnings("kotlin:S6517")
    interface EmbeddingModelConfig {
        fun modelId(): String
    }
}
