package me.davidgomesdev.ofingidor.backend.llm

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import me.davidgomesdev.ofingidor.backend.llm.rag.SemanticDocumentSplitter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SemanticDocumentSplitterTest {

    @Test
    fun `should split document on paragraph boundaries and filter empty segments`() {
        val embeddingModel = mock<EmbeddingModel>()
        val splitter = SemanticDocumentSplitter(
            embeddingModel = embeddingModel,
            minChunkSize = 5,
            maxChunkSize = 1000,
            similarityThreshold = 0.5
        )

        val text = "First paragraph.\n\nSecond paragraph.\n\n\n\nThird paragraph."
        val document = Document.from(text)

        // Mock embedAll to return orthogonal vectors (similarity = 0)
        // This ensures each paragraph stays separate
        whenever(embeddingModel.embedAll(any<List<TextSegment>>())).thenReturn(
            Response.from(
                listOf(
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f)),
                    Embedding.from(floatArrayOf(0.0f, 1.0f, 0.0f)),
                    Embedding.from(floatArrayOf(0.0f, 0.0f, 1.0f))
                )
            )
        )

        val segments = splitter.split(document)

        assertEquals(3, segments.size)
        assertEquals("First paragraph.", segments[0].text())
        assertEquals("Second paragraph.", segments[1].text())
        assertEquals("Third paragraph.", segments[2].text())
    }

    @Test
    fun `should merge paragraphs with high similarity`() {
        val embeddingModel = mock<EmbeddingModel>()
        val splitter = SemanticDocumentSplitter(
            embeddingModel = embeddingModel,
            minChunkSize = 5,
            maxChunkSize = 1000,
            similarityThreshold = 0.8
        )

        val text = "First paragraph.\n\nSecond paragraph."
        val document = Document.from(text)

        // Mock embedAll to return identical vectors (similarity = 1.0)
        whenever(embeddingModel.embedAll(any<List<TextSegment>>())).thenReturn(
            Response.from(
                listOf(
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f)),
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f))
                )
            )
        )

        val segments = splitter.split(document)

        assertEquals(1, segments.size)
        assertEquals("First paragraph.\n\nSecond paragraph.", segments[0].text())
    }

    @Test
    fun `should not merge paragraphs when combined size exceeds maxChunkSize`() {
        val embeddingModel = mock<EmbeddingModel>()
        val splitter = SemanticDocumentSplitter(
            embeddingModel = embeddingModel,
            minChunkSize = 5,
            maxChunkSize = 30,
            similarityThreshold = 0.8
        )

        val text = "First paragraph.\n\nSecond paragraph."
        val document = Document.from(text)

        // Mock embedAll to return identical vectors (similarity = 1.0)
        whenever(embeddingModel.embedAll(any<List<TextSegment>>())).thenReturn(
            Response.from(
                listOf(
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f)),
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f))
                )
            )
        )

        val segments = splitter.split(document)

        assertEquals(2, segments.size)
        assertEquals("First paragraph.", segments[0].text())
        assertEquals("Second paragraph.", segments[1].text())
    }

    @Test
    fun `should handle single paragraph document`() {
        val embeddingModel = mock<EmbeddingModel>()
        val splitter = SemanticDocumentSplitter(
            embeddingModel = embeddingModel,
            minChunkSize = 5,
            maxChunkSize = 1000,
            similarityThreshold = 0.5
        )

        val text = "Single paragraph."
        val document = Document.from(text)

        val segments = splitter.split(document)

        assertEquals(1, segments.size)
        assertEquals("Single paragraph.", segments[0].text())
    }

    @Test
    fun `should use batch embedAll for multiple paragraphs`() {
        val embeddingModel = mock<EmbeddingModel>()
        val splitter = SemanticDocumentSplitter(
            embeddingModel = embeddingModel,
            minChunkSize = 5,
            maxChunkSize = 1000,
            similarityThreshold = 0.5
        )

        val text = "Para 1.\n\nPara 2.\n\nPara 3."
        val document = Document.from(text)

        whenever(embeddingModel.embedAll(any<List<TextSegment>>())).thenReturn(
            Response.from(
                listOf(
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f)),
                    Embedding.from(floatArrayOf(0.0f, 1.0f, 0.0f)),
                    Embedding.from(floatArrayOf(0.0f, 0.0f, 1.0f))
                )
            )
        )

        splitter.split(document)

        // Verify embedAll was called (batch operation)
        org.mockito.kotlin.verify(embeddingModel).embedAll(any<List<TextSegment>>())
    }

    @Test
    fun `should validate similarity threshold range`() {
        val embeddingModel = mock<EmbeddingModel>()

        assertThrows<IllegalArgumentException> {
            SemanticDocumentSplitter(
                embeddingModel = embeddingModel,
                minChunkSize = 5,
                maxChunkSize = 1000,
                similarityThreshold = 1.5
            )
        }

        assertThrows<IllegalArgumentException> {
            SemanticDocumentSplitter(
                embeddingModel = embeddingModel,
                minChunkSize = 5,
                maxChunkSize = 1000,
                similarityThreshold = -0.1
            )
        }
    }

    @Test
    fun `should merge undersized final chunk with previous`() {
        val embeddingModel = mock<EmbeddingModel>()
        val splitter = SemanticDocumentSplitter(
            embeddingModel = embeddingModel,
            minChunkSize = 20,
            maxChunkSize = 100,
            similarityThreshold = 0.7
        )

        val text = "This is a normal paragraph.\n\nShort."
        val document = Document.from(text)

        // Mock low similarity (different vectors)
        whenever(embeddingModel.embedAll(any<List<TextSegment>>())).thenReturn(
            Response.from(
                listOf(
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f)),
                    Embedding.from(floatArrayOf(0.0f, 1.0f, 0.0f))
                )
            )
        )

        val segments = splitter.split(document)

        // "Short." is below minChunkSize, should merge with previous
        assertEquals(1, segments.size)
        assertTrue(segments[0].text().contains("normal paragraph"))
        assertTrue(segments[0].text().contains("Short"))
    }

    @Test
    fun `should fallback to sentence splitter for oversized paragraph`() {
        val embeddingModel = mock<EmbeddingModel>()
        val splitter = SemanticDocumentSplitter(
            embeddingModel = embeddingModel,
            minChunkSize = 10,
            maxChunkSize = 50,
            similarityThreshold = 0.7
        )

        // Two paragraphs: one oversized that needs fallback, one normal
        // The oversized paragraph by itself exceeds maxChunkSize
        val text = "This is a very long first paragraph that definitely exceeds the maximum chunk size. " +
                "It has multiple sentences to ensure it's big enough.\n\n" +
                "Short second para."
        val document = Document.from(text)

        // Mock embeddings for two paragraphs
        whenever(embeddingModel.embedAll(any<List<TextSegment>>())).thenReturn(
            Response.from(
                listOf(
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f)),
                    Embedding.from(floatArrayOf(0.0f, 1.0f, 0.0f))
                )
            )
        )

        val segments = splitter.split(document)

        // First paragraph should be split into multiple chunks via fallback
        // Plus the second paragraph
        assertTrue(segments.size > 2)
        segments.forEach { segment ->
            assertTrue(segment.text().length <= 50)
        }
    }
}
