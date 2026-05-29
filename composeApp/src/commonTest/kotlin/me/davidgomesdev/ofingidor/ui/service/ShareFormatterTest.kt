package me.davidgomesdev.ofingidor.ui.service

import me.davidgomesdev.ofingidor.shared.dto.Persona
import me.davidgomesdev.ofingidor.ui.model.ConversationTurn
import me.davidgomesdev.ofingidor.ui.model.DebateQuestionEntry
import me.davidgomesdev.ofingidor.ui.model.DebateTurn
import me.davidgomesdev.ofingidor.ui.model.Source
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareFormatterTest {

    private fun source(title: String) = Source(id = 1, title = title, author = "A", category = "C", score = 90)

    private fun chatTurn(
        question: String,
        message: String,
        persona: Persona = Persona.FERNANDO_PESSOA,
        sources: List<Source> = emptyList(),
    ) = ConversationTurn(
        question = question,
        message = message,
        sources = sources,
        traceId = "trace",
        persona = persona,
    )

    // --- formatChatConversation ---

    @Test
    fun singleTurnNoSources() {
        val result = formatChatConversation(listOf(chatTurn("Olá?", "Olá, amigo.")))
        assertEquals("Pergunta: Olá?\n\nFernando Pessoa: Olá, amigo.", result)
    }

    @Test
    fun singleTurnWithSources() {
        val result = formatChatConversation(
            listOf(chatTurn("Sobre o tempo?", "O tempo passa.", sources = listOf(source("Mensagem"))))
        )
        assertEquals("Pergunta: Sobre o tempo?\n\nFernando Pessoa: O tempo passa.\nFontes: Mensagem", result)
    }

    @Test
    fun multipleTurnsAreSeparatedByDivider() {
        val turns = listOf(
            chatTurn("Pergunta 1", "Resposta 1"),
            chatTurn("Pergunta 2", "Resposta 2", persona = Persona.ALBERTO_CAEIRO),
        )
        val result = formatChatConversation(turns)
        assertEquals(
            "Pergunta: Pergunta 1\n\nFernando Pessoa: Resposta 1\n\n---\n\nPergunta: Pergunta 2\n\nAlberto Caeiro: Resposta 2",
            result
        )
    }

    @Test
    fun emptyTurnListReturnsBlank() {
        assertEquals("", formatChatConversation(emptyList()))
    }

    // --- formatDebateConversation ---

    @Test
    fun debateSingleQuestionTwoSpeakers() {
        val questions = listOf(DebateQuestionEntry(question = "Saudade?", startOffset = 0))
        val turns = listOf(
            DebateTurn(turnIndex = 0, speaker = Persona.FERNANDO_PESSOA, message = "É a presença da ausência."),
            DebateTurn(turnIndex = 1, speaker = Persona.ALBERTO_CAEIRO, message = "Não tenho saudades."),
        )
        val result = formatDebateConversation(questions, turns)
        assertEquals(
            "Pergunta: Saudade?\n\nFernando Pessoa: É a presença da ausência.\n\nAlberto Caeiro: Não tenho saudades.",
            result
        )
    }

    @Test
    fun debateMultipleQuestions() {
        val questions = listOf(
            DebateQuestionEntry(question = "Primeira?", startOffset = 0),
            DebateQuestionEntry(question = "Segunda?", startOffset = 2),
        )
        val turns = listOf(
            DebateTurn(turnIndex = 0, speaker = Persona.FERNANDO_PESSOA, message = "R1"),
            DebateTurn(turnIndex = 1, speaker = Persona.ALBERTO_CAEIRO, message = "R2"),
            DebateTurn(turnIndex = 2, speaker = Persona.FERNANDO_PESSOA, message = "R3"),
            DebateTurn(turnIndex = 3, speaker = Persona.ALBERTO_CAEIRO, message = "R4"),
        )
        val result = formatDebateConversation(questions, turns)
        assertEquals(
            "Pergunta: Primeira?\n\nFernando Pessoa: R1\n\nAlberto Caeiro: R2\n\n---\n\nPergunta: Segunda?\n\nFernando Pessoa: R3\n\nAlberto Caeiro: R4",
            result
        )
    }

    @Test
    fun debateTurnWithSources() {
        val questions = listOf(DebateQuestionEntry(question = "Q?", startOffset = 0))
        val turns = listOf(
            DebateTurn(
                turnIndex = 0,
                speaker = Persona.FERNANDO_PESSOA,
                message = "Msg",
                sources = listOf(source("Obra A"), source("Obra B")),
            )
        )
        val result = formatDebateConversation(questions, turns)
        assertEquals("Pergunta: Q?\n\nFernando Pessoa: Msg\nFontes: Obra A, Obra B", result)
    }

    @Test
    fun emptyDebateReturnsBlank() {
        assertEquals("", formatDebateConversation(emptyList(), emptyList()))
    }
}
