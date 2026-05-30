package me.davidgomesdev.ofingidor.backend.service.debate

import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.ofingidor.shared.constants.DebateConstants
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.jboss.logging.Logger

@ApplicationScoped
class DebatePromptBuilder {

    private val log: Logger = Logger.getLogger(this::class.java)

    fun openingPrompt(userInput: String, speaker: Persona, opponent: Persona): String = """
        Responde como ${speaker.displayName}.
        Estás a participar num debate com ${opponent.displayName} sobre o tema pedido pelo utilizador:
        $userInput

        Exprime a tua posição com confiança e naturalidade — como quem começa a falar sobre algo que pensa há muito. Não uses preambles formais nem te dirijas a uma audiência genérica. Podes ou não dirigir-te a ${opponent.displayName} diretamente, conforme for natural para a tua voz.
    """.trimIndent()

    fun rebuttalPrompt(userInput: String, speaker: Persona, transcript: List<DebateTurnEntity>): String {
        val transcriptText = transcript.joinToString("\n") { turn ->
            when (turn.entryType) {
                DebateConstants.DEBATE_ENTRY_TYPE_USER_PROMPT -> "Utilizador: ${turn.text}"
                else -> {
                    val speakerCode = turn.speakerPersonaId
                    if (speakerCode == null) {
                        log.warnf(
                            "Skipping corrupted debate turn id=%s: speakerPersonaId is null for entry_type=%s",
                            turn.id,
                            turn.entryType
                        )
                        return@joinToString ""
                    }
                    "$speakerCode: ${turn.text}"
                }
            }
        }.lines().filter { it.isNotEmpty() }.joinToString("\n")

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
