package me.davidgomesdev.ofingidor.ui.model

import me.davidgomesdev.ofingidor.shared.dto.Persona

enum class DebateSide { LEFT, RIGHT }

data class DebatePair(val left: Persona, val right: Persona) {
    init {
        require(left != right) { "Debate personas must be different" }
    }

    fun sideFor(persona: Persona): DebateSide =
        when (persona) {
            left -> DebateSide.LEFT
            right -> DebateSide.RIGHT
            else -> error("Persona $persona is not part of this debate pair")
        }
}

data class DebateTurn(
    val turnIndex: Int,
    val speaker: Persona,
    val message: String = "",
    val sources: List<Source> = emptyList(),
    val isComplete: Boolean = false,
)
