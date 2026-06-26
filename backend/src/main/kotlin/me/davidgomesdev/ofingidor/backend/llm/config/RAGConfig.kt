package me.davidgomesdev.ofingidor.backend.llm.config

import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.smallrye.config.ConfigMapping
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton

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

@ApplicationScoped
class RAGConfigProducer(val config: RAGConfig) {
    @Singleton
    @Suppress("unused")
    fun qdrantClient(): QdrantClient =
        QdrantClient(
            QdrantGrpcClient
                .newBuilder(config.qdrant().host(), 6334, false)
                .withApiKey(config.qdrant().apiKey())
                .build(),
        )
}
