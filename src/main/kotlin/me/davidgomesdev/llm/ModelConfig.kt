package me.davidgomesdev.llm

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter
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
import jakarta.transaction.Transactional
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import me.davidgomesdev.api.Assistant
import me.davidgomesdev.db.EmbeddingRepository
import me.davidgomesdev.source.PessoaCategory
import me.davidgomesdev.source.PessoaText
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.File
import java.time.Duration
import kotlin.time.measureTime

// MENSAGEM
const val PREVIEW_CATEGORY_ID = 34

@ApplicationScoped
class ModelConfig(
    val repository: EmbeddingRepository,
    @param:ConfigProperty(name = "preview-only", defaultValue = "false")
    val isPreviewOnly: Boolean,
    @param:ConfigProperty(name = "recreate.embeddings", defaultValue = "false")
    val recreateEmbeddings: Boolean,
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
    @Transactional
    fun contentRetriever(): ContentRetriever {
        log.info("Preparing content retriever")
        if (isPreviewOnly) log.info("Running for preview ONLY")

        val allTextsByCategory = getAllTextsByCategory()
        val documents = allTextsByCategory.map { category ->
            category.value.filter { it.content != "" }
                .map {
                    Document.document(
                        it.content, Metadata.from(
                            mapOf(
                                "title" to it.title,
                                "author" to it.author,
                                "textId" to it.id,
                                "categoryId" to category.key.first,
                                "categoryName" to category.key.second,
                            )
                        )
                    )
                }
        }.flatten()

        val splitter = DocumentByRegexSplitter("\n\n", "\n", 900, 0, DocumentBySentenceSplitter(300, 0))

        val embeddingModel =
            OllamaEmbeddingModel.builder().baseUrl("http://127.0.0.1:11434")
                .timeout(Duration.ofMinutes(15))
                .model("embeddinggemma")
                .build()

        val table = if (isPreviewOnly) "embeddings_preview" else "embeddings"
        val embeddingStore: EmbeddingStore<TextSegment> = PgVectorEmbeddingStore.builder()
            .host("127.0.0.1")
            .port(15432)
            .database("pessoa_faladora")
            .user("ricardo-reis")
            .password("isThisNotAVerySecurePassword")
            .table(table)
            .dimension(embeddingModel.dimension())
            .build()

        if (recreateEmbeddings) {
            log.info("Recreating embeddings, deleting")
            repository.deleteAll()
        }

        val ingestedDocuments = repository.count()

        log.info("DB has $ingestedDocuments embeddings")

        log.info("Creating Embedding store")

        val contentRetriever = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(10)
            .build()

        if (ingestedDocuments == 0L) {
            log.info("Ingesting ${documents.size} documents")
            val timeSpent = measureTime {
                EmbeddingStoreIngestor.builder()
                    .documentSplitter(splitter)
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .build()
                    .ingest(documents)
            }
            log.info("Documents ingested (took $timeSpent)")
        }

        return contentRetriever
    }

    fun getAllTextsByCategory(): Map<Pair<Int, String>, List<PessoaText>> {
        val rootCategories = Json.decodeFromString<List<PessoaCategory>>(File("assets/all_texts.json").readText())

        val allTexts = mutableMapOf<Pair<Int, String>, MutableList<PessoaText>>()

        val categoriesToBeProcessed = rootCategories.toMutableList()

        while (categoriesToBeProcessed.isNotEmpty()) {
            val currentCategories = categoriesToBeProcessed.toList()

            categoriesToBeProcessed.clear()

            currentCategories.forEach { category ->
                categoriesToBeProcessed.addAll(category.subcategories)

                val categoryTexts = allTexts.getOrPut(Pair(category.id, category.title)) { mutableListOf() }

                if (category.texts != null)
                    categoryTexts.addAll(category.texts)
            }
        }

        log.info("Total amount of texts ${allTexts.map { it.value.size }.sum()}")

        if (isPreviewOnly) {
            return allTexts.filter { it.key.first == PREVIEW_CATEGORY_ID }
        }

        return allTexts
    }

    @Singleton
    fun augmentor(contentRetriever: ContentRetriever): RetrievalAugmentor = DefaultRetrievalAugmentor.builder()
        .queryRouter(DefaultQueryRouter(contentRetriever))
        .queryTransformer {
            log.info("Querying '${it.text()}'")
            DefaultQueryTransformer().transform(it)
        }.contentInjector(
            DefaultContentInjector.builder()
                .promptTemplate(PromptTemplate.from(
                    """
                    {{userMessage}}

                    Responde tendo em conta estes textos teus:
                    {{contents}}
                    """.trimIndent()))
                .metadataKeysToInclude(mutableListOf("author", "title", "categoryName"))
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

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class EmbeddingMetadata(@SerialName("file_name") val fileName: String)
