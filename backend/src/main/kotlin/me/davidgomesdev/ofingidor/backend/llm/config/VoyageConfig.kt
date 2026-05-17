package me.davidgomesdev.ofingidor.backend.llm.config

import io.smallrye.config.ConfigMapping
import java.time.Duration

@ConfigMapping(prefix = "model.voyage")
interface VoyageConfig {
    fun apiKey(): String
    fun timeout(): Duration
    fun embeddingModel(): EmbeddingChatModelConfig

    @Suppress("kotlin:S6517")
    interface EmbeddingChatModelConfig {
        fun modelId(): String
    }
}
