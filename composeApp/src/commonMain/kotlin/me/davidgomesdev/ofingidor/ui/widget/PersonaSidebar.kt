package me.davidgomesdev.ofingidor.ui.widget

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.davidgomesdev.ofingidor.shared.dto.Persona
import me.davidgomesdev.ofingidor.shared.dto.PersonaCategory
import me.davidgomesdev.ofingidor.ui.focusedIndicatorColor

@Composable
fun PersonaTab(
    selectedPersona: Persona,
    onPersonaSelected: (Persona) -> Unit,
    devMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val categories = PersonaCategory.entries.filter { it != PersonaCategory.DEV || devMode }

    Row(
        modifier =
            modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categories.forEach { category ->
            val personas = Persona.entries.filter { it.category == category }
            PersonaCategorySection(
                category = category,
                personas = personas,
                selectedPersona = selectedPersona,
                onPersonaSelected = onPersonaSelected,
            )
        }
    }
}

@Composable
fun PersonaCategorySection(
    category: PersonaCategory,
    personas: List<Persona>,
    selectedPersona: Persona,
    onPersonaSelected: (Persona) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            category.label.uppercase(),
            color = focusedIndicatorColor.copy(alpha = 0.5f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            personas.forEach { persona ->
                PersonaTabChip(
                    persona = persona,
                    isSelected = persona == selectedPersona,
                    onSelected = { onPersonaSelected(persona) },
                )
            }
        }
    }
}

@Composable
private fun PersonaTabChip(
    persona: Persona,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    PersonaIdentityChip(
        model = personaIdentityChipModel(persona = persona, isCompact = false, isSelected = isSelected),
        onSelected = onSelected,
    )
}
