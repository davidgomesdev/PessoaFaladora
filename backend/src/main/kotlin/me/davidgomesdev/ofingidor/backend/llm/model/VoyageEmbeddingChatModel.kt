package me.davidgomesdev.ofingidor.backend.llm.model

import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.voyageai.VoyageAiEmbeddingModel
import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.ofingidor.backend.llm.config.VoyageConfig

@ApplicationScoped
class VoyageEmbeddingChatModel(val config: VoyageConfig) : EmbeddingChatModel {
    override fun embeddingModel(): EmbeddingModel =
        VoyageAiEmbeddingModel.builder()
            .apiKey(config.apiKey())
            .modelName(config.embeddingModel().modelId())
            .timeout(config.timeout())
            .build()
}
