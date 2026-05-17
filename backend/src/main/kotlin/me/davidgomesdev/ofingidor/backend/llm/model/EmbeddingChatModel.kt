package me.davidgomesdev.ofingidor.backend.llm.model

import dev.langchain4j.model.embedding.EmbeddingModel

@Suppress("kotlin:S6517")
interface EmbeddingChatModel {
    fun embeddingModel(): EmbeddingModel
}
