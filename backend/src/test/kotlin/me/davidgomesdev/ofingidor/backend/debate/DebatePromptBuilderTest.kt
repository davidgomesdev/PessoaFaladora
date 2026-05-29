package me.davidgomesdev.ofingidor.backend.debate

import me.davidgomesdev.ofingidor.backend.service.debate.DebatePromptBuilder
import me.davidgomesdev.ofingidor.backend.service.debate.DebateTurnEntity
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebatePromptBuilderTest {
    private val promptBuilder = DebatePromptBuilder()

    @Test
    fun `opening prompt includes speaker, opponent and user input`() {
        val prompt = promptBuilder.openingPrompt(
            "O que é a sinceridade poética?",
            Persona.FERNANDO_PESSOA,
            Persona.ALBERTO_CAEIRO
        )

        assertTrue(prompt.contains("Fernando Pessoa"))
        assertTrue(prompt.contains("Alberto Caeiro"))
        assertTrue(prompt.contains("O que é a sinceridade poética?"))
        assertTrue(prompt.contains("Exprime a tua posição com confiança"))
    }

    @Test
    fun `rebuttal prompt includes prior transcript`() {
        val transcript = listOf(
            DebateTurnEntity().apply {
                entryType = "user_prompt"
                text = "O que é a sinceridade poética?"
            },
            DebateTurnEntity().apply {
                entryType = "persona_turn"
                speakerPersonaCode = "fernando_pessoa"
                text = "É uma máscara consciente."
            },
        )

        val prompt = promptBuilder.rebuttalPrompt(
            userInput = "O que é a sinceridade poética?",
            speaker = Persona.ALBERTO_CAEIRO,
            transcript = transcript,
        )

        assertTrue(prompt.contains("Utilizador: O que é a sinceridade poética?"))
        assertTrue(prompt.contains("fernando_pessoa: É uma máscara consciente."))
        assertTrue(prompt.contains("Alberto Caeiro"))
    }
}
