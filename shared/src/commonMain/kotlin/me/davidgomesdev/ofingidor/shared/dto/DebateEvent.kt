package me.davidgomesdev.ofingidor.shared.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class DebateEvent {

    @Serializable
    @SerialName("start")
    data class Start(
        val traceId: String,
        val personaA: String,
        val personaB: String,
    ) : DebateEvent()

    @Serializable
    @SerialName("turn_start")
    data class TurnStart(
        val turnIndex: Int,
        val speaker: Persona,
    ) : DebateEvent()

    @Serializable
    @SerialName("token")
    data class Token(
        val turnIndex: Int,
        val speaker: Persona,
        val value: String,
    ) : DebateEvent()

    @Serializable
    @SerialName("sources")
    data class Sources(
        val turnIndex: Int,
        val speaker: Persona,
        val sources: ChatEvent.Sources,
    ) : DebateEvent()

    @Serializable
    @SerialName("turn_done")
    data class TurnDone(
        val turnIndex: Int,
        val speaker: Persona,
        val tokensUsed: Int,
        val timeTaken: String,
    ) : DebateEvent()

    @Serializable
    @SerialName("done")
    data object Done : DebateEvent()
}
