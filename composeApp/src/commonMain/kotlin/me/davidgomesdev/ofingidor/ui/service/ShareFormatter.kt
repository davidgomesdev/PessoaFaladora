package me.davidgomesdev.ofingidor.ui.service

import me.davidgomesdev.ofingidor.ui.model.ConversationTurn
import me.davidgomesdev.ofingidor.ui.model.DebateQuestionEntry
import me.davidgomesdev.ofingidor.ui.model.DebateTurn

fun formatChatConversation(turns: List<ConversationTurn>): String =
    turns.joinToString(separator = "\n\n---\n\n") { turn ->
        val sourcesLine = if (turn.sources.isNotEmpty()) {
            "\nFontes: ${turn.sources.joinToString(", ") { it.title }}"
        } else ""
        "Pergunta: ${turn.question}\n\n${turn.persona.displayName}: ${turn.message}$sourcesLine"
    }

internal fun formatDebateConversation(
    questions: List<DebateQuestionEntry>,
    turns: List<DebateTurn>,
): String {
    val sections = questions.mapIndexed { index, question ->
        val nextQuestion = questions.getOrNull(index + 1)
        val sectionTurns = turns.subList(question.startOffset, nextQuestion?.startOffset ?: turns.size)

        buildString {
            append("Pergunta: ${question.question}")
            sectionTurns.forEach { turn ->
                append("\n\n${turn.speaker.displayName}: ${turn.message}")
                if (turn.sources.isNotEmpty()) {
                    append("\nFontes: ${turn.sources.joinToString(", ") { it.title }}")
                }
            }
        }
    }
    return sections.joinToString(separator = "\n\n---\n\n")
}
