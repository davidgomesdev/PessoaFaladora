package me.davidgomesdev

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.data.document.parser.markdown.MarkdownDocumentParser
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder
import io.quarkiverse.langchain4j.ollama.OllamaEmbeddingModel
import jakarta.enterprise.context.ApplicationScoped

fun interface Assistant {
    fun chat(userMessage: String): String
}

@ApplicationScoped
class Service {

    fun runExample() {
        val documents = FileSystemDocumentLoader.loadDocument(
            "sample.md",
            MarkdownDocumentParser()
        )

        val chatModel = OllamaChatModel.builder().baseUrl("http://127.0.0.1:11434")
            .modelName("qwen3:1.7b")
            .httpClientBuilder(JaxRsHttpClientBuilder())
            .build()

        val embeddingStore = InMemoryEmbeddingStore<TextSegment>()

        val embeddingModel = OllamaEmbeddingModel.builder().baseUrl("http://127.0.0.1:11434")
            .model("embeddinggemma")
            .build()

        val contentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .build()

        EmbeddingStoreIngestor.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .build()
            .ingest(documents)

        runAiServices(chatModel, contentRetriever)
    }

    fun runAiServices(chatModel: ChatModel, contentRetriever: ContentRetriever) {
        val assistant: Assistant = AiServices.builder(Assistant::class.java)
            .chatModel(chatModel)
            .contentRetriever(contentRetriever)
            .build()

        queryAndPrint(assistant, "O que quis Deus?")
        queryAndPrint(assistant, "Dá-me uma quadra sobre vinho")
    }

    fun queryAndPrint(assistant: Assistant, input: String) {
        println("> \uD83D\uDDE3\uFE0F User")
        println(input)
        println("> \uD83D\uDDA5\uFE0F System")
        println(assistant.chat(input))
    }
}
