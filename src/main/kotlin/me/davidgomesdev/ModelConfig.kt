package me.davidgomesdev

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.data.document.parser.markdown.MarkdownDocumentParser
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.injector.DefaultContentInjector
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.router.DefaultQueryRouter
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder
import io.quarkiverse.langchain4j.ollama.OllamaEmbeddingModel
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ModelConfig {

    @ApplicationScoped
    fun chatModel(): ChatModel =
        OllamaChatModel.builder().baseUrl("http://127.0.0.1:11434")
            .modelName("qwen3:1.7b")
            .httpClientBuilder(JaxRsHttpClientBuilder())
            .build()

    @ApplicationScoped
    fun contentRetriever(): ContentRetriever {
        val documents = FileSystemDocumentLoader.loadDocument(
            "sample.md",
            MarkdownDocumentParser()
        )

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

        return contentRetriever
    }

    @ApplicationScoped
    fun augmentor(contentRetriever: ContentRetriever): RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryRouter(DefaultQueryRouter(contentRetriever))
        .contentInjector(
            DefaultContentInjector.builder()
                .promptTemplate(PromptTemplate.from("Responde diretamente\n{{userMessage}}\n{{contents}}"))
                .build()
        )
        .build()

    @ApplicationScoped
    fun assistant(chatModel: ChatModel, retrievalAugmentor: RetrievalAugmentor): Assistant =
        AiServices.builder(Assistant::class.java)
            .chatModel(chatModel)
            .retrievalAugmentor(retrievalAugmentor)
            .build()
}
