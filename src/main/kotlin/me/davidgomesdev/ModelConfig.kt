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
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer
import dev.langchain4j.service.AiServices
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import io.quarkiverse.langchain4j.jaxrsclient.JaxRsHttpClientBuilder
import io.quarkiverse.langchain4j.ollama.OllamaEmbeddingModel
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import org.jboss.logging.Logger
import java.time.Duration
import kotlin.time.measureTime


@ApplicationScoped
class ModelConfig {

    val log: Logger = Logger.getLogger(this::class.java)

    @ApplicationScoped
    fun chatModel(): ChatModel =
        OllamaChatModel.builder().baseUrl("http://127.0.0.1:11434")
            .modelName("qwen3:1.7b")
            .httpClientBuilder(JaxRsHttpClientBuilder())
            .timeout(Duration.ofMinutes(1))
            .build()

    @Singleton
    fun contentRetriever(): ContentRetriever {
        val documentFileName = System.getenv("DOCUMENT_FILE_NAME") ?: "alberto_caeiro"
        val document = FileSystemDocumentLoader.loadDocument(
            "assets/$documentFileName.md",
            MarkdownDocumentParser()
        )

        val embeddingModel =
            OllamaEmbeddingModel.builder().baseUrl("http://127.0.0.1:11434")
                .timeout(Duration.ofMinutes(15))
                .model("embeddinggemma")
                .build()

        log.info("Creating Embedding store")

        val embeddingStore: EmbeddingStore<TextSegment> = PgVectorEmbeddingStore.builder()
            .host("127.0.0.1")
            .port(15432)
            .database("pessoa_faladora")
            .user("ricardo-reis")
            .password("isThisNotAVerySecurePassword")
            .table("${documentFileName}_embeddings")
            .dimension(embeddingModel.dimension())
            .build()

        val contentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .build()

        log.info("Ingesting documents")
        val timeSpent = measureTime {
                EmbeddingStoreIngestor.builder()

                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .build()
                    .ingest(document)
            }
        log.info("Documents ingested (took $timeSpent)")

        return contentRetriever
    }

    @Singleton
    fun augmentor(contentRetriever: ContentRetriever): RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryRouter(DefaultQueryRouter(contentRetriever))
        .queryTransformer {
            log.info("Getting $it")
            DefaultQueryTransformer().transform(it)
        }
        .contentInjector(
            DefaultContentInjector.builder()
                .promptTemplate(PromptTemplate.from("Responde diretamente e em Português de Portugal.\n{{userMessage}}\n{{contents}}"))
                .build()
        )
        .build()

    @Singleton
    fun assistant(chatModel: ChatModel, retrievalAugmentor: RetrievalAugmentor): Assistant {
        log.info("Creating Assistant")
        return AiServices.builder(Assistant::class.java)
            .chatModel(chatModel)
            .retrievalAugmentor(retrievalAugmentor)
            .build()
    }
}
