package me.davidgomesdev.ofingidor.backend.llm.model

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger

@ApplicationScoped
class ModelsProducer(
    private val ollama: OllamaLanguageModel,
    private val anthropic: AnthropicLanguageModel,
    private val voyage: VoyageEmbeddingChatModel,
    @param:ConfigProperty(name = "model.chat-name")
    private val chatModelName: String,
    @param:ConfigProperty(name = "model.embedding-name")
    private val embeddingModelName: String,
) : LanguageModel {
    val logger: Logger = Logger.getLogger(this::class.java)

    init {
        logger.info("Using '$chatModelName' language model")
    }

    @Singleton
    override fun chatModel(): ChatModel =
        when (chatModelName) {
            "ollama" -> ollama
            "anthropic" -> anthropic
            else -> throw IllegalArgumentException("Unknown chat model '$chatModelName'")
        }.chatModel()

    @Singleton
    override fun streamingChatModel(): StreamingChatModel =
        when (chatModelName) {
            "ollama" -> ollama
            "anthropic" -> anthropic
            else -> throw IllegalArgumentException("Unknown chat model '$chatModelName'")
        }.streamingChatModel()

    @Singleton
    fun embeddingModel(): EmbeddingModel =
        when (embeddingModelName) {
            "ollama" -> ollama
            "voyage" -> voyage
            else -> throw IllegalArgumentException("Unknown embedding model '$embeddingModelName'")
        }.embeddingModel()
}
