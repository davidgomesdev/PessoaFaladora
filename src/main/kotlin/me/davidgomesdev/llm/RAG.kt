package me.davidgomesdev.llm

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.input.PromptTemplate
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.router.DefaultQueryRouter
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer
import dev.langchain4j.rag.query.transformer.QueryTransformer
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.quarkiverse.langchain4j.pgvector.PgVectorEmbeddingStore
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Named
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import kotlinx.serialization.json.Json
import me.davidgomesdev.db.EmbeddingRepository
import me.davidgomesdev.llm.config.RAGConfig
import me.davidgomesdev.observability.attributes
import me.davidgomesdev.source.PessoaCategory
import me.davidgomesdev.source.PessoaText
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.File
import kotlin.time.measureTime

// Livro do Desassossego
const val PREVIEW_CATEGORY_ID = 33

val EXPANDING_QUERY_TEMPLATE: PromptTemplate = PromptTemplate.from(
    """
    Gera {{n}} versões diferentes EM PORTUGUÊS DE PORTUGAL de uma dada pergunta do utilizador.
    Cada versão deve ser redigida de forma diferente, usando sinónimos ou estruturas de frase alternativas,
    mas todas devem manter o significado original.
    Estas versões serão usadas para recuperar documentos relevantes.
    É muito importante fornecer cada versão da query em uma linha separada,
    sem enumerações, hífens ou qualquer formatação adicional!
    Pergunta do utilizador: {{query}}"
    """.trimIndent()
)

typealias TextsByCategory = Map<Pair<Int, String>, List<PessoaText>>

@ApplicationScoped
class RAG(
    val repository: EmbeddingRepository,
    @param:ConfigProperty(name = "preview-only", defaultValue = "false")
    val isPreviewOnly: Boolean,
    @param:ConfigProperty(name = "recreate.embeddings", defaultValue = "false")
    val recreateEmbeddings: Boolean,
    val config: RAGConfig,
) {
    val log: Logger = Logger.getLogger(this::class.java)
    private val tracer = GlobalOpenTelemetry.getTracer(this::class.java.name)
    val splitter = DocumentByRegexSplitter("\n\n", "\n", 900, 0, DocumentBySentenceSplitter(300, 0))

    @Singleton
    @Suppress("unused")
    fun augmentor(
        contentRetriever: ContentRetriever,
        queryTransformer: QueryTransformer,
        contentInjector: TextsContentInjector
    ): RetrievalAugmentor =
        DefaultRetrievalAugmentor.builder()
            .queryRouter(DefaultQueryRouter(contentRetriever))
            .queryTransformer { originalQuery ->
                queryTransformer
                    .transform(originalQuery)
                    .also { transformedQuery ->
                        val transformedQueries = transformedQuery.joinToString(
                            "\n",
                            prefix = "[ ",
                            postfix = " ]"
                        ) { "'" + it.text() + "'" }

                        log.info("Transformed original query '${originalQuery.text()}' to '$transformedQueries'")

                        Span.current().addEvent(
                            "Query Transformed",
                            attributes {
                                put("original_query", originalQuery.text())
                                put("transformed_queries", transformedQueries)
                                put("transform_queries_count", transformedQuery.size.toLong())
                            }
                        )
                    }
            }
            .contentInjector(contentInjector)
            .build()

    @Singleton
    @Transactional
    @Suppress("unused")
    fun contentRetriever(
        @Named("PessoaDocuments")
        documents: List<Document>,
        embeddingModel: EmbeddingModel,
        @Named("PessoaTexts")
        embeddingStore: EmbeddingStore<TextSegment>,
    ): ContentRetriever {
        log.info("Preparing content retriever")

        val span = tracer.spanBuilder("rag.creating")
            .setSpanKind(SpanKind.INTERNAL).apply {
                setAttribute("mode", if (isPreviewOnly) "preview" else "full")
                setAttribute("recreate-embeddings", recreateEmbeddings)
                setAttribute("min-score", config.minScore())
                setAttribute("max-results", config.maxResults().toLong())
            }
            .startSpan()

        val scope = span.makeCurrent()
        try {
            if (isPreviewOnly) {
                log.info("Running for preview ONLY")
            }

            val ingestedDocumentsCount = repository.count()

            span.addEvent("Found documents", attributes {
                put("ingested_documents_count", ingestedDocumentsCount)
            })

            log.info("DB has $ingestedDocumentsCount embeddings")

            if (ingestedDocumentsCount == 0L) {
                log.info("Ingesting ${documents.size} documents")

                span.addEvent("Ingesting documents", attributes {
                    put("documents_count", documents.size.toLong())
                })

                val timeSpent = measureTime {
                    EmbeddingStoreIngestor.builder()
                        .documentSplitter(splitter)
                        .embeddingStore(embeddingStore)
                        .embeddingModel(embeddingModel)
                        .build()
                        .ingest(documents)
                }

                span.addEvent("Documents ingested", attributes {
                    put("documents_count", documents.size.toLong())
                    put("time_spent_ms", timeSpent.inWholeMilliseconds)
                })

                log.info("Documents ingested (took $timeSpent)")
            }

            span.setStatus(StatusCode.OK)
        } finally {
            scope.close()
            span.end()
        }

        return EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(config.maxResults())
            .minScore(config.minScore())
            .build()
    }

    @Singleton
    @Suppress("unused")
    fun queryTransformer(chatModel: ChatModel): QueryTransformer {
        if (isPreviewOnly) {
            log.info("Using simple query transformer for preview")
            return DefaultQueryTransformer()
        }

        return ExpandingQueryTransformer(chatModel, EXPANDING_QUERY_TEMPLATE)
    }

    @Named("PessoaTexts")
    @Singleton
    @Transactional
    @Suppress("unused")
    fun embeddingStore(embeddingModel: EmbeddingModel): EmbeddingStore<TextSegment> {
        log.info("Creating Embedding store")

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

        return embeddingStore
    }

    @Named("PessoaDocuments")
    @ApplicationScoped
    @Suppress("unused")
    fun documents(allTextsByCategory: TextsByCategory): List<Document> {
        return allTextsByCategory.map { category ->
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
    }

    @ApplicationScoped
    @Suppress("unused")
    fun allTextsByCategory(): TextsByCategory {
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
}
