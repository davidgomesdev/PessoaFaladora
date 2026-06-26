package me.davidgomesdev.ofingidor.backend.service.debate

import dev.langchain4j.model.input.PromptTemplate
import jakarta.enterprise.context.ApplicationScoped
import me.davidgomesdev.ofingidor.shared.constants.DebateConstants
import me.davidgomesdev.ofingidor.shared.dto.Persona
import org.jboss.logging.Logger

@ApplicationScoped
class DebatePromptBuilder {
    private val log: Logger = Logger.getLogger(this::class.java)

    private val openingTemplate: String = loadResource("prompts/debate_opening.txt")
    private val rebuttalTemplate: String = loadResource("prompts/debate_rebuttal.txt")

    fun openingPrompt(
        userInput: String,
        speaker: Persona,
        opponent: Persona,
    ): String =
        PromptTemplate
            .from(openingTemplate)
            .apply(
                mapOf(
                    "speakerName" to speaker.displayName,
                    "opponentName" to opponent.displayName,
                    "userInput" to userInput,
                ),
            ).text()

    fun rebuttalPrompt(
        userInput: String,
        speaker: Persona,
        transcript: List<DebateTurnEntity>,
    ): String {
        val transcriptText =
            transcript
                .joinToString("\n") { turn ->
                    when (turn.entryType) {
                        DebateConstants.DEBATE_ENTRY_TYPE_USER_PROMPT -> {
                            "${DebateConstants.DEBATE_TRANSCRIPT_USER_PREFIX}: ${turn.text}"
                        }

                        else -> {
                            val speakerCode = turn.speakerPersonaId
                            if (speakerCode == null) {
                                log.warnf(
                                    "Skipping corrupted debate turn id=%s: speakerPersonaId is null for entry_type=%s",
                                    turn.id,
                                    turn.entryType,
                                )
                                return@joinToString ""
                            }
                            "$speakerCode: ${turn.text}"
                        }
                    }
                }.lines()
                .filter { it.isNotEmpty() }
                .joinToString("\n")

        return PromptTemplate
            .from(rebuttalTemplate)
            .apply(
                mapOf(
                    "speakerName" to speaker.displayName,
                    "userInput" to userInput,
                    "transcript" to transcriptText,
                ),
            ).text()
    }

    private fun loadResource(path: String): String =
        Thread
            .currentThread()
            .contextClassLoader
            .getResourceAsStream(path)!!
            .reader()
            .readText()
}
