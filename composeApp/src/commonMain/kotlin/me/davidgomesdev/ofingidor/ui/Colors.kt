package me.davidgomesdev.ofingidor.ui

import androidx.compose.ui.graphics.Color
import me.davidgomesdev.ofingidor.shared.dto.Persona

val backgroundColor = Color(0xFF111111)
val componentsBackgroundColor = Color(0xFF323232)
val componentColumnBackgroundColor = Color(0xFF242424)
val focusedIndicatorColor = Color(0xFF575757)

val disableThinkButtonColor = Color(0xFF191919)

// Card surfaces and borders
val inputCardBackgroundColor = Color(0xFF1E1E1E)
val exampleCardBackgroundColor = Color(0xFF1A1A1A)
val cardBorderColor = Color(0xFF2A2A2A)

// --- Persona sidebar chip ---
//  - ortónimo active state
val orthonymChipColor = Color(0xFF1F1A2E)
val orthonymChipBorderColor = Color(0x557C5CBF)
val orthonymChipTextColor = Color(0xFFC4AFF5)

// Persona sidebar chip — dev mode active state (amber)
val devChipColor = Color(0xFF1F1800)
val devChipBorderColor = Color(0x55BFA040)
val devChipTextColor = Color(0xFFCFAA50)
val ninguemChipColor = Color(0xFF1F1F1F)
val ninguemChipBorderColor = Color(0xA46D6D6D)
val ninguemChipTextColor = Color(0xFF828282)

// Persona sidebar chip — semi-heterónimo active state (same hue, halfway between orthonym and heteronym)
val semiHeteronymChipColor = Color(0xFF211F2A)
val semiHeteronymChipBorderColor = Color(0x3D7C5CBF)
val semiHeteronymChipTextColor = Color(0xFFAD9EDC)

// Error bubble colors
val errorBubbleBackgroundColor = Color(0xFF1A1010)
val errorBubbleBorderColor = Color(0xFF4A2020)
val errorBubbleTextColor = Color(0xFFAA6666)

// Chat bubble colors
val userBubbleBorder = Color(0xFF7C5CBF)
val aiBubbleBackgroundColor = Color(0xFF161616)
val aiBubbleBorder = Color(0xFF3D2E5A)
val personaLabelColor = Color(0xFF7C5CBF)
val portraitThumbnailBackgroundColor = Color(0xFF1A1428)

data class DebateBubblePalette(
    val background: Color,
    val border: Color,
    val label: Color,
)

fun debateBubblePalette(persona: Persona): DebateBubblePalette = when (persona) {
    Persona.FERNANDO_PESSOA -> DebateBubblePalette(Color(0xFF16131E), Color(0xFF5D4C85), Color(0xFFC6B7ED))
    Persona.ALBERTO_CAEIRO -> DebateBubblePalette(Color(0xFF132018), Color(0xFF4C7A5A), Color(0xFFB7E2C4))
    Persona.ALVARO_DE_CAMPOS -> DebateBubblePalette(Color(0xFF1F1713), Color(0xFF9A5A3A), Color(0xFFF2C5AE))
    Persona.RICARDO_REIS -> DebateBubblePalette(Color(0xFF171A20), Color(0xFF596E8A), Color(0xFFC2D2EB))
    Persona.BERNARDO_SOARES -> DebateBubblePalette(Color(0xFF1B171C), Color(0xFF6E5673), Color(0xFFD7C1DB))
    Persona.O_FINGIDOR -> DebateBubblePalette(Color(0xFF201B12), Color(0xFF8B7140), Color(0xFFE4D0A5))
}
