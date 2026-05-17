package me.davidgomesdev.ofingidor.backend.llm

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.DocumentSplitter
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import org.jboss.logging.Logger
import kotlin.math.max
import kotlin.math.min

/**
 * A document splitter that uses semantic similarity to group related paragraphs into chunks.
 *
 * This splitter first breaks a document into paragraphs (using "\n\n" as delimiter),
 * then combines adjacent paragraphs into chunks based on semantic similarity.
 * When the similarity between consecutive paragraphs falls below a threshold,
 * a new chunk is created.
 *
 * For chunks that exceed maxChunkSize, falls back to DocumentBySentenceSplitter.
 *
 * @param embeddingModel The embedding model used to compute paragraph embeddings
 * @param minChunkSize Minimum number of characters per chunk
 * @param maxChunkSize Maximum number of characters per chunk
 * @param similarityThreshold The minimum cosine similarity (0.0 to 1.0) required to group paragraphs together
 */
class SemanticDocumentSplitter(
    private val embeddingModel: EmbeddingModel,
    private val minChunkSize: Int,
    private val maxChunkSize: Int,
    private val similarityThreshold: Double
) : DocumentSplitter {

    private val tracer = GlobalOpenTelemetry.getTracer(this::class.java.name)
    private val log: Logger = Logger.getLogger(this::class.java)
    private val sentenceSplitter = DocumentBySentenceSplitter(maxChunkSize, 0)

    init {
        require(similarityThreshold in 0.0..1.0) {
            "similarityThreshold must be between 0.0 and 1.0, got $similarityThreshold"
        }
        require(minChunkSize > 0) {
            "minChunkSize must be positive, got $minChunkSize"
        }
        require(maxChunkSize >= minChunkSize) {
            "maxChunkSize ($maxChunkSize) must be >= minChunkSize ($minChunkSize)"
        }
    }

    override fun split(document: Document): List<TextSegment> {
        return splitAll(listOf(document))
    }

    override fun splitAll(documents: List<Document>): List<TextSegment> {
        val span = tracer.spanBuilder("rag.semantic_chunking").setSpanKind(SpanKind.INTERNAL).apply {
            setAttribute("document_count", documents.size.toLong())
            setAttribute("min_chunk_size", minChunkSize.toLong())
            setAttribute("max_chunk_size", maxChunkSize.toLong())
            setAttribute("similarity_threshold", similarityThreshold)
        }.startSpan()
        val scope = span.makeCurrent()

        return try {
            val startTime = System.currentTimeMillis()
            var fallbackCount = 0
            var totalSimilarity = 0.0
            var mergeCount = 0
            val allSegments = mutableListOf<TextSegment>()

            for (document in documents) {
                val initialSegments = document.text().split("\n\n").map { it.trim() }.filter { it.isNotBlank() }

                if (initialSegments.isEmpty()) {
                    span.addEvent("Empty document skipped")
                    continue
                }

                if (initialSegments.size == 1) {
                    span.addEvent("Single paragraph document")
                    allSegments.add(TextSegment.from(initialSegments[0], document.metadata()))
                    continue
                }

                span.addEvent("Split into ${initialSegments.size} paragraphs")

                val embeddings = embedAllSegments(initialSegments)

                span.addEvent("Embeddings computed for ${embeddings.size} paragraphs")

                val triple = mergeEmbeddingsBySimilarity(initialSegments, embeddings, totalSimilarity, mergeCount)
                val mergedChunks = triple.first

                mergeCount = triple.second
                totalSimilarity = triple.third

                span.addEvent("Merged into ${mergedChunks.size} semantic chunks")

                fallbackCount = handleSizeConstraints(mergedChunks, fallbackCount, document, allSegments)
            }

            span.setAttribute("total_segments", allSegments.size.toLong())

            val avgSimilarity = if (mergeCount > 0) totalSimilarity / mergeCount else 0.0

            span.setAttribute("final_chunks", allSegments.size.toLong())
            span.setAttribute("avg_similarity", avgSimilarity)
            span.setAttribute("time_spent_ms", System.currentTimeMillis() - startTime)
            span.setAttribute("fallback_count", fallbackCount.toLong())

            log.info("Semantic chunking complete: ${allSegments.size} segments")
            span.setStatus(StatusCode.OK)

            allSegments
        } catch (ex: Exception) {
            span.recordException(ex)
            span.setStatus(StatusCode.ERROR)
            log.error("Semantic chunking failed", ex)
            throw ex
        } finally {
            scope.close()
            span.end()
        }
    }

    private fun embedAllSegments(initialSegments: List<String>): List<FloatArray> {
        val textSegments = initialSegments.map { TextSegment.from(it) }
        val embeddingsResponse = embeddingModel.embedAll(textSegments)
        val embeddings = embeddingsResponse.content().map { it.vector() }

        return embeddings
    }

    private fun handleSizeConstraints(
        mergedChunks: List<String>, fallbackCount: Int, document: Document, allSegments: MutableList<TextSegment>
    ): Int {
        val span = Span.current()
        var currentChunkFallbackCount = fallbackCount

        for (chunk in mergedChunks) {
            if (chunk.length > maxChunkSize) {
                log.warn("Chunk exceeds maxChunkSize (${chunk.length} > $maxChunkSize), falling back to sentence splitter")
                span.addEvent("Oversized chunk, using sentence splitter fallback")
                currentChunkFallbackCount++

                val fallbackDoc = Document.from(chunk, document.metadata())
                val splitChunks: List<TextSegment> = sentenceSplitter.split(fallbackDoc)

                allSegments.addAll(splitChunks)
            } else if (chunk.length < minChunkSize && allSegments.isNotEmpty()) {
                // Merge with previous chunk
                val previous = allSegments.removeLast()
                val merged = "${previous.text()}\n\n$chunk"

                if (merged.length <= maxChunkSize) {
                    allSegments.add(TextSegment.from(merged, document.metadata()))
                    span.addEvent("Merged undersized chunk with previous")
                } else {
                    // Can't merge, add both separately
                    allSegments.add(previous)
                    allSegments.add(TextSegment.from(chunk, document.metadata()))
                }
            } else {
                allSegments.add(TextSegment.from(chunk, document.metadata()))
            }
        }

        return currentChunkFallbackCount
    }

    private fun mergeEmbeddingsBySimilarity(
        initialSegments: List<String>, embeddings: List<FloatArray>, totalSimilarity: Double, mergeCount: Int
    ): Triple<MutableList<String>, Int, Double> {
        var totalSimilarity1 = totalSimilarity
        var mergeCount1 = mergeCount
        val mergedChunks = mutableListOf<String>()
        var currentChunk = initialSegments[0]

        for (i in 1 until initialSegments.size) {
            val similarity = cosineSimilarity(embeddings[i - 1], embeddings[i])
            val mergedSize = currentChunk.length + 2 + initialSegments[i].length

            if (similarity > similarityThreshold && mergedSize <= maxChunkSize) {
                currentChunk = "$currentChunk\n\n${initialSegments[i]}"
                totalSimilarity1 += similarity
                mergeCount1++
            } else {
                mergedChunks.add(currentChunk)
                currentChunk = initialSegments[i]
            }
        }
        mergedChunks.add(currentChunk)
        return Triple(mergedChunks, mergeCount1, totalSimilarity1)
    }

    /**
     * Computes cosine similarity between two embedding vectors.
     *
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine similarity value between 0.0 and 1.0
     */
    private fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Double {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have the same dimension"
        }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val denominator = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)

        return if (denominator == 0.0) {
            0.0
        } else {
            // Clamp to [0, 1] to handle floating point precision issues
            max(0.0, min(1.0, dotProduct / denominator))
        }
    }
}
