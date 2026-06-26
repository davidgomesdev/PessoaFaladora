package me.davidgomesdev.ofingidor.backend.llm

import dev.langchain4j.data.document.Document
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter
import dev.langchain4j.data.embedding.Embedding
import dev.langchain4j.data.segment.TextSegment
import dev.langchain4j.model.embedding.EmbeddingModel
import dev.langchain4j.model.output.Response
import dev.langchain4j.store.embedding.EmbeddingStore
import me.davidgomesdev.ofingidor.backend.llm.config.RAGConfig
import me.davidgomesdev.ofingidor.backend.llm.rag.RAG
import me.davidgomesdev.ofingidor.backend.llm.rag.SemanticDocumentSplitter
import me.davidgomesdev.ofingidor.backend.web.PersonaContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RAGIntegrationTest {
    @Test
    fun `should use regex splitter when semantic chunking is disabled`() {
        val embeddingModel = mock<EmbeddingModel>()
        val embeddingStore = mock<EmbeddingStore<TextSegment>>()
        val config = mock<RAGConfig>()
        val semanticChunkingConfig = mock<RAGConfig.SemanticChunkingConfig>()
        val personaContext = mock<PersonaContext>()

        whenever(config.semanticChunking()).thenReturn(semanticChunkingConfig)
        whenever(semanticChunkingConfig.enabled()).thenReturn(false)

        val rag =
            RAG(
                isPreviewOnly = true,
                recreateEmbeddings = false,
                config = config,
                personaContext = personaContext,
                embeddingModel = embeddingModel,
            )

        // Verify that the splitter is an instance of DocumentByRegexSplitter
        assertTrue(rag.splitter is DocumentByRegexSplitter)
    }

    @Test
    fun `should use semantic splitter when semantic chunking is enabled`() {
        val embeddingModel = mock<EmbeddingModel>()
        val embeddingStore = mock<EmbeddingStore<TextSegment>>()
        val config = mock<RAGConfig>()
        val semanticChunkingConfig = mock<RAGConfig.SemanticChunkingConfig>()
        val personaContext = mock<PersonaContext>()

        whenever(config.semanticChunking()).thenReturn(semanticChunkingConfig)
        whenever(semanticChunkingConfig.enabled()).thenReturn(true)
        whenever(semanticChunkingConfig.minChunkSize()).thenReturn(100)
        whenever(semanticChunkingConfig.maxChunkSize()).thenReturn(900)
        whenever(semanticChunkingConfig.similarityThreshold()).thenReturn(0.7)

        val rag =
            RAG(
                isPreviewOnly = true,
                recreateEmbeddings = false,
                config = config,
                personaContext = personaContext,
                embeddingModel = embeddingModel,
            )

        // Verify that the splitter is an instance of SemanticDocumentSplitter
        assertTrue(rag.splitter is SemanticDocumentSplitter)
    }

    @Test
    fun `semantic splitter should merge similar paragraphs when enabled`() {
        val embeddingModel = mock<EmbeddingModel>()
        val embeddingStore = mock<EmbeddingStore<TextSegment>>()
        val config = mock<RAGConfig>()
        val semanticChunkingConfig = mock<RAGConfig.SemanticChunkingConfig>()
        val personaContext = mock<PersonaContext>()

        whenever(config.semanticChunking()).thenReturn(semanticChunkingConfig)
        whenever(semanticChunkingConfig.enabled()).thenReturn(true)
        whenever(semanticChunkingConfig.minChunkSize()).thenReturn(5)
        whenever(semanticChunkingConfig.maxChunkSize()).thenReturn(900)
        whenever(semanticChunkingConfig.similarityThreshold()).thenReturn(0.7)

        // Mock embedAll to return identical vectors (high similarity)
        whenever(embeddingModel.embedAll(any<List<TextSegment>>())).thenReturn(
            Response.from(
                listOf(
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f)),
                    Embedding.from(floatArrayOf(1.0f, 0.0f, 0.0f)),
                ),
            ),
        )

        val rag =
            RAG(
                isPreviewOnly = true,
                recreateEmbeddings = false,
                config = config,
                personaContext = personaContext,
                embeddingModel = embeddingModel,
            )

        // Verify semantic splitter is being used
        assertTrue(rag.splitter is SemanticDocumentSplitter)

        val testDoc = Document.from("First paragraph.\n\nSecond paragraph.")
        val segments = rag.splitter.split(testDoc)

        // With high similarity (identical vectors) above threshold,
        // paragraphs should merge
        assertEquals(1, segments.size)
        assertEquals("First paragraph.\n\nSecond paragraph.", segments[0].text())
    }
}
