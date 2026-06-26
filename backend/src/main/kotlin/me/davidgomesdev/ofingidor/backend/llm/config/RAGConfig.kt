package me.davidgomesdev.ofingidor.backend.llm.config

import io.smallrye.config.ConfigMapping

@ConfigMapping(prefix = "rag")
interface RAGConfig {
    fun expandQuery(): Boolean

    fun expandingQueryTemplate(): String

    fun maxResults(): Int

    fun minScore(): Double

    fun ingestionChunkSize(): Int

    fun qdrant(): QdrantConfig

    fun semanticChunking(): SemanticChunkingConfig

    interface QdrantConfig {
        fun host(): String

        fun apiKey(): String

        fun collection(): CollectionConfig

        @SuppressWarnings("kotlin:S6517")
        interface CollectionConfig {
            fun name(): String
        }
    }

    interface SemanticChunkingConfig {
        fun enabled(): Boolean

        fun similarityThreshold(): Double

        fun minChunkSize(): Int

        fun maxChunkSize(): Int
    }
}
