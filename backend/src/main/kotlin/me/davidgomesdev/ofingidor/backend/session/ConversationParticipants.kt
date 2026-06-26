package me.davidgomesdev.ofingidor.backend.session

import me.davidgomesdev.ofingidor.shared.dto.Persona

sealed class ConversationParticipants(
    val type: ConversationType,
    open val persona: Persona,
) {
    data class Single(
        override val persona: Persona,
    ) : ConversationParticipants(
            type = ConversationType.SINGLE,
            persona = persona,
        )

    data class Debate(
        override val persona: Persona,
        val opponentPersona: Persona,
    ) : ConversationParticipants(
            type = ConversationType.DEBATE,
            persona = persona,
        )
}
