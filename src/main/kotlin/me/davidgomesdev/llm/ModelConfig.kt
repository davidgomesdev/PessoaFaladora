package me.davidgomesdev.llm

import dev.langchain4j.data.document.loader.FileSystemDocumentLoader
import dev.langchain4j.data.document.parser.TextDocumentParser
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import me.davidgomesdev.api.Assistant
import me.davidgomesdev.db.EmbeddingRepository
import org.jboss.logging.Logger
import java.time.Duration
import kotlin.time.measureTime

@ApplicationScoped
class ModelConfig(
    val repository: EmbeddingRepository
) {

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
        log.info("Preparing content retriever")

        val documentFileName = System.getenv("DOCUMENT_FILE_NAME") ?: "alberto_caeiro.md"
        val document = FileSystemDocumentLoader.loadDocument(
            "assets/$documentFileName",
            TextDocumentParser()
        )

        val splitter = DocumentByRegexSplitter("---", "\n", 500, 50, DocumentByRegexSplitter("\n\n", "\n", 900, 50))

        val embeddingModel =
            OllamaEmbeddingModel.builder().baseUrl("http://127.0.0.1:11434")
                .timeout(Duration.ofMinutes(15))
                .model("embeddinggemma")
                .build()
        val embeddingStore: EmbeddingStore<TextSegment> = PgVectorEmbeddingStore.builder()
            .host("127.0.0.1")
            .port(15432)
            .database("pessoa_faladora")
            .user("ricardo-reis")
            .password("isThisNotAVerySecurePassword")
            .table("${documentFileName.removeSuffix(".md")}_embeddings")
            .dimension(embeddingModel.dimension())
            .build()

        log.info("Querying")

        val ingestedFiles =
            repository.listAll().map { Json.decodeFromString<EmbeddingMetadata>(it.metadata).fileName }.distinct()

        log.info("DB has '${ingestedFiles.joinToString(", ")}' file embeddings")

        log.info("Creating Embedding store")

        val contentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .build()

        if (!ingestedFiles.contains(documentFileName)) {
            log.info("Ingesting documents")
            val timeSpent = measureTime {
                EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .build()
                    .ingest(document)
            }
            log.info("Documents ingested (took $timeSpent)")
        }

        return contentRetriever
    }

    @Singleton
    fun augmentor(contentRetriever: ContentRetriever): RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryRouter(DefaultQueryRouter(contentRetriever))
        .queryTransformer {
            log.info("Querying '${it.text()}'")
            DefaultQueryTransformer().transform(it)
        }
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class EmbeddingMetadata(@SerialName("file_name") val fileName: String)
