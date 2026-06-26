package me.davidgomesdev.ofingidor.backend.llm.rag

import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.rag.DefaultRetrievalAugmentor
import dev.langchain4j.rag.RetrievalAugmentor as LCRetrievalAugmentor
import dev.langchain4j.rag.content.retriever.ContentRetriever
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
import dev.langchain4j.rag.query.Query
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
import io.qdrant.client.grpc.Collections.Distance
import io.qdrant.client.grpc.Collections.VectorParams
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Singleton
import me.davidgomesdev.ofingidor.backend.llm.config.RAGConfig
import me.davidgomesdev.ofingidor.backend.observability.attributes
import me.davidgomesdev.ofingidor.backend.observability.span
import me.davidgomesdev.ofingidor.backend.web.PersonaContext
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.eclipse.microprofile.context.ManagedExecutor
import org.jboss.logging.Logger

@ApplicationScoped
class RetrievalAugmentor(
    @param:ConfigProperty(name = "preview-only", defaultValue = "false")
    val isPreviewOnly: Boolean,
    @param:ConfigProperty(name = "recreate.embeddings", defaultValue = "false")
    val recreateEmbeddings: Boolean,
    val config: RAGConfig,
    val personaContext: PersonaContext,
    val retrievalIngestor: RetrievalIngestor,
) {
    val log: Logger = Logger.getLogger(this::class.java)
    private val tracer = GlobalOpenTelemetry.getTracer(this::class.java.name)

    @Singleton
    @Suppress("unused")
    fun augmentor(
        contentRetriever: ContentRetriever,
        queryTransformer: QueryTransformer,
        contentInjector: TextsContentInjector,
        managedExecutor: ManagedExecutor,
    ): LCRetrievalAugmentor =
        DefaultRetrievalAugmentor
            .builder()
            .executor(managedExecutor)
            .queryRouter { _ ->
                if (personaContext.persona == Persona.O_FINGIDOR) {
                    Span.current().addEvent("Skipping RAG")
                    log.info("Skipping RAG for persona ${Persona.O_FINGIDOR.codeName}")
                    emptyList()
                } else {
                    listOf(contentRetriever)
                }
            }.queryTransformer { originalQuery ->
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
        ingestor: EmbeddingStoreIngestor,
    ): ContentRetriever {
        log.info("Preparing content retriever")

        val span =
            tracer
                .spanBuilder("rag.initializing")
                .setSpanKind(SpanKind.INTERNAL)
                .apply {
                    setAttribute("mode", if (isPreviewOnly) "preview" else "full")
                    setAttribute("recreate-embeddings", recreateEmbeddings)
                    setAttribute("min-score", config.minScore())
                    setAttribute("max-results", config.maxResults().toLong())
                }.startSpan()

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

            qdrantClient
                .createCollectionAsync(
                    collectionName,
                    VectorParams
                        .newBuilder()
                        .setDistance(Distance.Cosine)
                        .setSize(embeddingModel.dimension().toLong())
                        .build(),
                ).get()

            log.info("Collection '$collectionName' created successfully with dimension ${embeddingModel.dimension()}")
            span.addEvent("Created collection")
        } else {
            log.info("Collection '$collectionName' already exists")
            span.addEvent("Collection exists, proceeding")
        }

        managedExecutor.runAsync {
            retrievalIngestor.ingestDocuments(
                qdrantClient,
                collectionName,
                ingestor,
            )
        }

        span.setStatus(StatusCode.OK)
        span.end()

        return EmbeddingStoreContentRetriever
            .builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .maxResults(config.maxResults())
            .minScore(config.minScore())
            .dynamicFilter(::filterPersona)
            .build()
    }

    @Singleton
    @Suppress("unused")
    fun embeddingStore(embeddingModel: EmbeddingModel): EmbeddingStore<TextSegment> {
        log.info("Creating Embedding store")

        val qdrantConfig = config.qdrant()
        val baseName = qdrantConfig.collection().name()
        val collectionName = if (isPreviewOnly) "${baseName}_preview" else baseName

        return QdrantEmbeddingStore
            .builder()
            .host(qdrantConfig.host())
            .apiKey(qdrantConfig.apiKey())
            .collectionName(collectionName)
            .build()
    }

    private fun filterPersona(
        @Suppress("unused_parameter")
        query: Query,
    ): Filter? {
        val persona = personaContext.persona

        if (persona == null) {
            Span.current().addEvent("⚠️ Received null persona when filtering for content!")
            log.warn("Received null persona when filtering for content!")
            return null
        }

        return when (persona) {
            Persona.FERNANDO_PESSOA -> null

            // Filter out any text
            Persona.O_FINGIDOR -> metadataKey(TextAttributes.TEXT_ID).isEqualTo(-1)

            else -> metadataKey(TextAttributes.AUTHOR).isEqualTo(persona.displayName)
        }
    }

    private fun traceQueryExpansion(
        transformedQuery: Collection<Query>,
        originalQuery: Query,
    ) {
        val transformedQueries =
            transformedQuery.joinToString(
                "\n",
                prefix = "[ ",
                postfix = " ]",
            ) { "'" + it.text() + "'" }

        log.info("Transformed original query '${originalQuery.text()}' to '$transformedQueries'")

        span().addEvent(
            "Query Transformed",
            attributes {
                put("original_query", originalQuery.text())
                put("transformed_queries", transformedQueries)
                put("transform_queries_count", transformedQuery.size.toLong())
            },
        )
    }
}
