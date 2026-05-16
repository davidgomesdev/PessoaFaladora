package me.davidgomesdev.ofingidor.backend.service.debate

import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.ofingidor.shared.constants.DebateConstants
import me.davidgomesdev.ofingidor.shared.dto.Persona

@ApplicationScoped
class DebatePromptBuilder {

    fun openingPrompt(userInput: String, speaker: Persona): String = """
        Responde como ${speaker.displayName}.
        O utilizador pediu:
        $userInput

        Assume uma posição clara e responde em tom de debate, dirigindo-te ao outro poeta mesmo que ele ainda não tenha falado.
    """.trimIndent()

    fun rebuttalPrompt(userInput: String, speaker: Persona, transcript: List<DebateTurnEntity>): String {
        val transcriptText = transcript.joinToString("\n") { turn ->
            when (turn.entryType) {
                DebateConstants.DEBATE_ENTRY_TYPE_USER_PROMPT -> "Utilizador: ${turn.text}"
                else -> "${requireNotNull(turn.speakerPersonaCode) { "speakerPersonaCode is required for ${turn.entryType}" }}: ${turn.text}"
            }
        }

        return """
            Responde como ${speaker.displayName}.
            Pergunta original:
            $userInput

            Debate até agora:
            $transcriptText

            Faz uma resposta curta de refutação, falando para o outro poeta e não para um moderador neutro.
        """.trimIndent()
    }
}
