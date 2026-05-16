package me.davidgomesdev.ofingidor.ui.model

import me.davidgomesdev.ofingidor.shared.dto.Persona

data class ConversationTurn(
    val question: String,
    val message: String,
    val sources: List<Source>,
    val traceId: String,
    val persona: Persona,
)

data class OngoingConversationTurn(
    val question: String,
    val persona: Persona,
    val message: String = "",
    val sources: List<Source> = emptyList(),
    val traceId: String = "",
) {
    fun toConversationTurn() = ConversationTurn(
        question = question,
        message = message,
        sources = sources,
        traceId = traceId,
        persona = persona,
    )
}
