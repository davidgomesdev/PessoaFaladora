package me.davidgomesdev.ofingidor.backend.session

import me.davidgomesdev.ofingidor.shared.dto.Persona

data class ConversationParticipants(
    val type: ConversationType,
    val persona: Persona,
    val opponentPersona: Persona? = null,
)
