package me.davidgomesdev.ofingidor.backend.llm.rag

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentSplitter
import dev.langchain4j.data.document.Metadata
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.qdrant.client.QdrantClient
import io.qdrant.client.grpc.Points
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import kotlinx.serialization.json.Json
import me.davidgomesdev.ofingidor.backend.dto.PessoaCategoryDto
import me.davidgomesdev.ofingidor.backend.llm.config.RAGConfig
import me.davidgomesdev.ofingidor.backend.model.PessoaCategory
import me.davidgomesdev.ofingidor.backend.observability.attributes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.File
import kotlin.time.measureTime

typealias PessoaTexts = List<PessoaText>

object TextAttributes {
    const val TEXT_ID = "textId"
    const val TITLE = "title"
    const val AUTHOR = "author"
    const val CATEGORY_NAME = "categoryName"
    const val CATEGORY_ID = "categoryId"
}

@ApplicationScoped
class RetrievalIngestor(
    @param:ConfigProperty(name = "preview-only", defaultValue = "false")
    val isPreviewOnly: Boolean,
    @param:ConfigProperty(name = "recreate.embeddings", defaultValue = "false")
    val recreateEmbeddings: Boolean,
    val config: RAGConfig,
    val embeddingModel: EmbeddingModel,
) {
    val log: Logger = Logger.getLogger(this::class.java)
    private val tracer = GlobalOpenTelemetry.getTracer(this::class.java.name)
    val splitter: DocumentSplitter =
        if (config.semanticChunking().enabled()) {
            log.info("Using SemanticDocumentSplitter with threshold=${config.semanticChunking().similarityThreshold()}")
            SemanticDocumentSplitter(
                embeddingModel = embeddingModel,
                minChunkSize = config.semanticChunking().minChunkSize(),
                maxChunkSize = config.semanticChunking().maxChunkSize(),
                similarityThreshold = config.semanticChunking().similarityThreshold(),
            )
        } else {
            log.info("Using DocumentByRegexSplitter")
            DocumentByRegexSplitter("\n\n", "\n", 900, 0, DocumentBySentenceSplitter(300, 0))
        }

    @Singleton
    @Suppress("unused")
    fun ingestor(
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel,
    ): EmbeddingStoreIngestor =
        EmbeddingStoreIngestor
            .builder()
            .documentSplitter(splitter)
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .build()

    @ApplicationScoped
    fun texts(): PessoaTexts {
        val filename =
            if (isPreviewOnly) {
                log.info("Using preview only texts")
                "assets/preview_texts.json"
            } else {
                "assets/all_texts.json"
            }

        val rootCategories = Json.decodeFromString<List<PessoaCategoryDto>>(File(filename).readText())
        val allTexts = mutableListOf<PessoaText>()

        val categoriesToBeProcessed = rootCategories.map(PessoaCategory::fromRootCategory).toMutableList()

        while (categoriesToBeProcessed.isNotEmpty()) {
            val currentCategories = categoriesToBeProcessed.toList()
            categoriesToBeProcessed.clear()

            currentCategories.forEach { category ->
                categoriesToBeProcessed.addAll(
                    category.subcategories.map {
                        PessoaCategory.from(category.rootCategoryId ?: category.id, it)
                    },
                )

                category.texts
                    .filter { it.content.isNotBlank() }
                    .forEach { text ->
                        allTexts.add(
                            PessoaText(text.title, text.author, text.content, text.id, category.id, category.title),
                        )
                    }
            }
        }

        return allTexts
    }

    fun ingestDocuments(
        qdrantClient: QdrantClient,
        collectionName: String,
        ingestor: EmbeddingStoreIngestor,
    ) {
        val span =
            tracer
                .spanBuilder("rag.ingesting")
                .setSpanKind(SpanKind.INTERNAL)
                .apply {
                    setAttribute("mode", if (isPreviewOnly) "preview" else "full")
                    setAttribute("recreate-embeddings", recreateEmbeddings)
                    setAttribute("min-score", config.minScore())
                    setAttribute("max-results", config.maxResults().toLong())
                }.startSpan()
        val ingestedDocumentIds = getIngestedDocumentIDs(qdrantClient, collectionName).toSet()

        val scope = span.makeCurrent()
        try {
            if (isPreviewOnly) {
                log.info("Running for preview ONLY")
            }

            val seenIds = mutableSetOf<Long>()
            var ingestedCount = 0

            log.info("Ingesting documents")

            val wholeTimeSpent =
                measureTime {
                    texts()
                        .map(PessoaText::toDocument)
                        .filter { doc ->
                            val textId = doc.metadata().getLong(TextAttributes.TEXT_ID) ?: return@filter true
                            textId !in ingestedDocumentIds && seenIds.add(textId)
                        }.chunked(config.ingestionChunkSize())
                        .forEach { chunk ->
                            val ingestionResult = ingestChunk(chunk, ingestor)

                            if (ingestionResult.isFailure) {
                                span.setStatus(StatusCode.ERROR)
                                span.recordException(ingestionResult.exceptionOrNull()!!)
                                return
                            } else {
                                ingestedCount += chunk.size
                            }
                        }
                }

            if (ingestedCount == 0) {
                log.info("No documents needed to ingest")
            } else {
                log.info("Documents ingested (took $wholeTimeSpent for $ingestedCount documents)")
                span.setAttribute("ingested_count", ingestedCount.toLong())
                span.setAttribute("time_spent_ms", wholeTimeSpent.inWholeMilliseconds)
            }

            span.setStatus(StatusCode.OK)
        } finally {
            scope.close()
            span.end()
        }
    }

    private fun ingestChunk(
        chunk: List<Document>,
        ingestor: EmbeddingStoreIngestor,
    ): Result<Unit> {
        if (log.isDebugEnabled) {
            val textsLink =
                chunk.joinToString(separator = "\n") {
                    "${
                        it.metadata().getString(TextAttributes.TITLE)
                    } - https://pessoa.davidgomes.blog/textReader/${
                        it.metadata().getLong(
                            TextAttributes.TEXT_ID,
                        )
                    }"
                }
            log.debug("Ingesting $textsLink")
        }

        val chunkSpan =
            tracer
                .spanBuilder("rag.ingesting.chunk")
                .setParent(Context.current().with(Span.current()))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("chunk_size", chunk.size.toLong())
                .startSpan()

        val chunkTimeSpent =
            measureTime {
                try {
                    ingestor.ingest(chunk)
                } catch (ex: Throwable) {
                    log.error("Failed to ingest", ex)
                    chunkSpan.setStatus(StatusCode.ERROR)
                    chunkSpan.recordException(ex)
                    chunkSpan.end()
                    return Result.failure(ex)
                }
            }

        log.info("Ingested chunk of ${chunk.size} (took $chunkTimeSpent)")
        chunkSpan.setAttribute("time_spent_ms", chunkTimeSpent.inWholeMilliseconds)
        chunkSpan.setAllAttributes(
            attributes {
                chunk.forEachIndexed { index, document ->
                    put(
                        "text_id_$index",
                        document.metadata().getLong(TextAttributes.TEXT_ID) ?: 0,
                    )
                }
            },
        )
        chunkSpan.setStatus(StatusCode.OK)
        chunkSpan.end()

        return Result.success(Unit)
    }

    private fun getIngestedDocumentIDs(
        qdrantClient: QdrantClient,
        collectionName: String,
    ): List<Long> {
        log.info("Getting ingested text IDs")

        val results: List<Points.RetrievedPoint> =
            qdrantClient
                .scrollAsync(
                    Points.ScrollPoints
                        .newBuilder()
                        .setCollectionName(collectionName)
                        // This is really fast and doesn't require much memory (they are only IDs)
                        .setLimit(-1)
                        .setWithPayload(
                            Points.WithPayloadSelector
                                .newBuilder()
                                .setInclude(
                                    Points.PayloadIncludeSelector
                                        .newBuilder()
                                        .addFields(TextAttributes.TEXT_ID),
                                ),
                        ).setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false))
                        .build(),
                ).get()
                .resultList

        return results.map { it.getPayloadOrThrow(TextAttributes.TEXT_ID).integerValue }.distinct()
    }
}

data class PessoaText(
    val title: String,
    val author: String,
    val content: String,
    val id: Int,
    val categoryId: Int,
    val categoryTitle: String,
) {
    fun toDocument(): Document =
        Document.document(
            content,
            Metadata.from(
                mapOf(
                    TextAttributes.TITLE to title,
                    TextAttributes.AUTHOR to author,
                    TextAttributes.TEXT_ID to id,
                    TextAttributes.CATEGORY_ID to categoryId,
                    TextAttributes.CATEGORY_NAME to categoryTitle,
                ),
            ),
        )
}
