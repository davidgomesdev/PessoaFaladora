package me.davidgomesdev.ofingidor.backend.llm

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentSplitter
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
import dev.langchain4j.rag.query.Query
import dev.langchain4j.rag.query.transformer.DefaultQueryTransformer
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer
import dev.langchain4j.rag.query.transformer.QueryTransformer
import dev.langchain4j.store.embedding.EmbeddingStore
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor
import dev.langchain4j.store.embedding.filter.Filter
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import io.qdrant.client.grpc.Collections.Distance
import io.qdrant.client.grpc.Collections.VectorParams
import io.qdrant.client.grpc.Points
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import kotlinx.serialization.json.Json
import me.davidgomesdev.ofingidor.backend.dto.PessoaCategoryDto
import me.davidgomesdev.ofingidor.backend.llm.config.RAGConfig
import me.davidgomesdev.ofingidor.backend.model.PessoaCategory
import me.davidgomesdev.ofingidor.backend.observability.attributes
import me.davidgomesdev.ofingidor.backend.observability.span
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.context.ManagedExecutor
import org.jboss.logging.Logger
import java.io.File
import kotlin.time.measureTime

object TextAttributes {
    const val TEXT_ID = "textId"
    const val TITLE = "title"
    const val AUTHOR = "author"
    const val CATEGORY_NAME = "categoryName"
    const val CATEGORY_ID = "categoryId"
}

@ApplicationScoped
class RAG(
    @param:ConfigProperty(name = "preview-only", defaultValue = "false")
    val isPreviewOnly: Boolean,
    @param:ConfigProperty(name = "recreate.embeddings", defaultValue = "false")
    val recreateEmbeddings: Boolean,
    val config: RAGConfig,
    val personaContext: PersonaContext,
    val embeddingModel: EmbeddingModel,
) {
    val log: Logger = Logger.getLogger(this::class.java)
    private val tracer = GlobalOpenTelemetry.getTracer(this::class.java.name)
    val splitter: DocumentSplitter = if (config.semanticChunking().enabled()) {
        log.info("Using SemanticDocumentSplitter with threshold=${config.semanticChunking().similarityThreshold()}")
        SemanticDocumentSplitter(
            embeddingModel = embeddingModel,
            minChunkSize = config.semanticChunking().minChunkSize(),
            maxChunkSize = config.semanticChunking().maxChunkSize(),
            similarityThreshold = config.semanticChunking().similarityThreshold()
        )
    } else {
        log.info("Using DocumentByRegexSplitter")
        DocumentByRegexSplitter("\n\n", "\n", 900, 0, DocumentBySentenceSplitter(300, 0))
    }

    @Singleton
    @Suppress("unused")
    fun augmentor(
        contentRetriever: ContentRetriever,
        queryTransformer: QueryTransformer,
        contentInjector: TextsContentInjector,
        managedExecutor: ManagedExecutor
    ): RetrievalAugmentor =
        DefaultRetrievalAugmentor.builder()
            .executor(managedExecutor)
            .queryRouter { _ ->
                if (personaContext.persona == Persona.O_FINGIDOR) {
                    Span.current().addEvent("Skipping RAG")
                    log.info("Skipping RAG for persona ${Persona.O_FINGIDOR.codeName}")
                    emptyList()
                } else {
                    listOf(contentRetriever)
                }
            }
            .queryTransformer { originalQuery ->
                queryTransformer
                    .transform(originalQuery)
                    .also { transformedQuery ->
                        traceQueryExpansion(transformedQuery, originalQuery)
                    }
            }
            .contentInjector(contentInjector)
            .build()

    @Singleton
    @Suppress("unused")
    fun contentRetriever(
        embeddingModel: EmbeddingModel,
        embeddingStore: EmbeddingStore<TextSegment>,
        qdrantClient: QdrantClient,
        managedExecutor: ManagedExecutor,
        ingestor: EmbeddingStoreIngestor
    ): ContentRetriever {
        log.info("Preparing content retriever")

        val span = tracer.spanBuilder("rag.initializing")
            .setSpanKind(SpanKind.INTERNAL).apply {
                setAttribute("mode", if (isPreviewOnly) "preview" else "full")
                setAttribute("recreate-embeddings", recreateEmbeddings)
                setAttribute("min-score", config.minScore())
                setAttribute("max-results", config.maxResults().toLong())
            }
            .startSpan()

        val qdrantConfig = config.qdrant()
        val baseName = qdrantConfig.collection().name()
        val collectionName = if (isPreviewOnly) "${baseName}_preview" else baseName

        if (recreateEmbeddings) {
            log.info("Recreating embeddings, deleting")
            qdrantClient.deleteCollectionAsync(collectionName).get()
            span.addEvent("Deleted collection to recreate")
        }

        val existingCollections: List<String> = qdrantClient.listCollectionsAsync().get()

        if (collectionName !in existingCollections) {
            log.info("Collection '$collectionName' not found")

            qdrantClient.createCollectionAsync(
                collectionName,
                VectorParams.newBuilder()
                    .setDistance(Distance.Cosine)
                    .setSize(embeddingModel.dimension().toLong())
                    .build()
            ).get()

            log.info("Collection '$collectionName' created successfully with dimension ${embeddingModel.dimension()}")
            span.addEvent("Created collection")
        } else {
            log.info("Collection '$collectionName' already exists")
            span.addEvent("Collection exists, proceeding")
        }

        managedExecutor.runAsync {
            ingestDocuments(
                qdrantClient,
                collectionName,
                ingestor
            )
        }

        span.setStatus(StatusCode.OK)
        span.end()

        return EmbeddingStoreContentRetriever.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(config.maxResults())
            .minScore(config.minScore())
            .dynamicFilter(::filterPersona)
            .build()
    }

    private fun filterPersona(
        @Suppress("unused_parameter")
        query: Query
    ): Filter? {
        val persona = personaContext.persona

        if (persona == null) {
            Span.current().addEvent("⚠\uFE0F Received null persona when filtering for content!")
            log.warn("Received null persona when filtering for content!")
            return null
        }

        return when (persona) {
            Persona.FERNANDO_PESSOA -> null
            // Filter out any text
            Persona.O_FINGIDOR -> metadataKey(TextAttributes.TEXT_ID).isEqualTo(-1)
            else ->
                metadataKey(TextAttributes.AUTHOR).isEqualTo(persona.displayName)
        }
    }


    fun ingestDocuments(
        qdrantClient: QdrantClient,
        collectionName: String,
        ingestor: EmbeddingStoreIngestor,
    ) {
        val span = tracer.spanBuilder("rag.ingesting")
            .setSpanKind(SpanKind.INTERNAL).apply {
                setAttribute("mode", if (isPreviewOnly) "preview" else "full")
                setAttribute("recreate-embeddings", recreateEmbeddings)
                setAttribute("min-score", config.minScore())
                setAttribute("max-results", config.maxResults().toLong())
            }
            .startSpan()
        val ingestedDocumentIds = getIngestedDocumentIDs(qdrantClient, collectionName).toSet()

        val scope = span.makeCurrent()
        try {
            if (isPreviewOnly) {
                log.info("Running for preview ONLY")
            }

            val seenIds = mutableSetOf<Long>()
            var ingestedCount = 0

            log.info("Ingesting documents")

            val wholeTimeSpent = measureTime {
                documentSequence()
                    .filter { doc ->
                        val textId = doc.metadata().getLong(TextAttributes.TEXT_ID) ?: return@filter true
                        textId !in ingestedDocumentIds && seenIds.add(textId)
                    }
                    .chunked(config.ingestionChunkSize())
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

    private fun ingestChunk(chunk: List<Document>, ingestor: EmbeddingStoreIngestor): Result<Unit> {
        if (log.isDebugEnabled) {
            val textsLink = chunk.joinToString(separator = "\n") {
                "${
                    it.metadata().getString(TextAttributes.TITLE)
                } - https://pessoa.davidgomes.blog/textReader/${
                    it.metadata().getLong(
                        TextAttributes.TEXT_ID
                    )
                }"
            }
            log.debug("Ingesting $textsLink")
        }

        val chunkSpan = tracer.spanBuilder("rag.ingesting.chunk")
            .setParent(io.opentelemetry.context.Context.current().with(Span.current()))
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("chunk_size", chunk.size.toLong())
            .startSpan()

        val chunkTimeSpent = measureTime {
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
        chunkSpan.setAllAttributes(attributes {
            chunk.forEachIndexed { index, document ->
                put(
                    "text_id_$index", document.metadata().getLong(
                        TextAttributes.TEXT_ID
                    ) ?: 0
                )
            }
        })
        chunkSpan.setStatus(StatusCode.OK)
        chunkSpan.end()

        return Result.success(Unit)
    }

    @Singleton
    @Suppress("unused")
    fun qdrantClient(): QdrantClient = QdrantClient(
        QdrantGrpcClient.newBuilder(config.qdrant().host(), 6334, false)
            .withApiKey(config.qdrant().apiKey())
            .build()
    )

    @Singleton
    @Suppress("unused")
    fun ingestor(
        embeddingStore: EmbeddingStore<TextSegment>,
        embeddingModel: EmbeddingModel
    ): EmbeddingStoreIngestor = EmbeddingStoreIngestor.builder()
        .documentSplitter(splitter)
        .embeddingStore(embeddingStore)
        .embeddingModel(embeddingModel)
        .build()

    private fun getIngestedDocumentIDs(
        qdrantClient: QdrantClient,
        collectionName: String,
    ): List<Long> {
        log.info("Getting ingested text IDs")

        val results: List<Points.RetrievedPoint> = qdrantClient.scrollAsync(
            Points.ScrollPoints.newBuilder()
                .setCollectionName(collectionName)
                // This is really fast and doesn't require much memory (they are only IDs)
                .setLimit(-1)
                .setWithPayload(
                    Points.WithPayloadSelector.newBuilder()
                        .setInclude(
                            Points.PayloadIncludeSelector.newBuilder()
                                .addFields(TextAttributes.TEXT_ID)
                        )
                )
                .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(false))
                .build()
        ).get().resultList

        return results.map { it.getPayloadOrThrow(TextAttributes.TEXT_ID).integerValue }.distinct()
    }

    @Singleton
    @Suppress("unused")
    fun queryTransformer(chatModel: ChatModel): QueryTransformer {
        if (!config.expandQuery()) {
            log.info("Using simple query transformer")
            return DefaultQueryTransformer()
        }

        return ExpandingQueryTransformer(chatModel, PromptTemplate.from(config.expandingQueryTemplate()))
    }

    @Singleton
    @Suppress("unused")
    fun embeddingStore(embeddingModel: EmbeddingModel): EmbeddingStore<TextSegment> {
        log.info("Creating Embedding store")

        val qdrantConfig = config.qdrant()
        val baseName = qdrantConfig.collection().name()
        val collectionName = if (isPreviewOnly) "${baseName}_preview" else baseName

        val embeddingStore = QdrantEmbeddingStore.builder()
            .host(qdrantConfig.host())
            .apiKey(qdrantConfig.apiKey())
            .collectionName(collectionName)
            .build()

        return embeddingStore
    }

    private fun documentSequence(): Sequence<Document> {
        val filename = if (isPreviewOnly) {
            log.info("Using preview only texts")
            "assets/preview_texts.json"
        } else "assets/all_texts.json"

        val rootCategories = Json.decodeFromString<List<PessoaCategoryDto>>(File(filename).readText())

        return sequence {
            val categoriesToBeProcessed = rootCategories.map(PessoaCategory::fromRootCategory).toMutableList()

            while (categoriesToBeProcessed.isNotEmpty()) {
                val currentCategories = categoriesToBeProcessed.toList()
                categoriesToBeProcessed.clear()

                currentCategories.forEach { category ->
                    categoriesToBeProcessed.addAll(category.subcategories.map {
                        PessoaCategory.from(category.rootCategoryId ?: category.id, it)
                    })

                    category.texts
                        .filter { it.content.isNotBlank() }
                        .forEach { text ->
                            yield(
                                Document.document(
                                    text.content, Metadata.from(
                                        mapOf(
                                            TextAttributes.TITLE to text.title,
                                            TextAttributes.AUTHOR to text.author,
                                            TextAttributes.TEXT_ID to text.id,
                                            TextAttributes.CATEGORY_ID to category.id,
                                            TextAttributes.CATEGORY_NAME to category.title,
                                        )
                                    )
                                )
                            )
                        }
                }
            }
        }
    }

    private fun traceQueryExpansion(
        transformedQuery: Collection<Query>,
        originalQuery: Query
    ) {
        val transformedQueries = transformedQuery.joinToString(
            "\n",
            prefix = "[ ",
            postfix = " ]"
        ) { "'" + it.text() + "'" }

        log.info("Transformed original query '${originalQuery.text()}' to '$transformedQueries'")

        span().addEvent(
            "Query Transformed",
            attributes {
                put("original_query", originalQuery.text())
                put("transformed_queries", transformedQueries)
                put("transform_queries_count", transformedQuery.size.toLong())
            }
        )
    }
}
