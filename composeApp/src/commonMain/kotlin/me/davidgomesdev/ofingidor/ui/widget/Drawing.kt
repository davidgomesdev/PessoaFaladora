package me.davidgomesdev.ofingidor.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.davidgomesdev.ofingidor.shared.dto.Persona
import me.davidgomesdev.ofingidor.ui.aiBubbleBorder
import me.davidgomesdev.ofingidor.ui.cardBorderColor
import me.davidgomesdev.ofingidor.ui.devChipBorderColor
import me.davidgomesdev.ofingidor.ui.devChipColor
import me.davidgomesdev.ofingidor.ui.devChipTextColor
import me.davidgomesdev.ofingidor.ui.model.PersonaPortrait
import me.davidgomesdev.ofingidor.ui.personaLabelColor
import me.davidgomesdev.ofingidor.ui.portraitThumbnailBackgroundColor

data class AppHeaderIdentity(
    val portrait: PersonaPortrait,
    val personaLabel: String,
)

fun appHeaderIdentity(persona: Persona): AppHeaderIdentity = AppHeaderIdentity(
    portrait = requireNotNull(personaPortrait(persona)) { "Missing portrait for $persona" },
    personaLabel = persona.displayName.uppercase(),
)

@Composable
fun AppHeader(
    selectedPersona: Persona,
    devMode: Boolean,
    hasConversationStarted: Boolean,
    onDevModeToggle: (() -> Unit)?,
    onNewConversation: (() -> Unit)?,
    isCompact: Boolean = false,
) {
    val identity = appHeaderIdentity(selectedPersona)
    val horizontalPadding = if (isCompact) 12.dp else 24.dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(portraitThumbnailBackgroundColor)
                        .border(1.dp, aiBubbleBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    PersonaPortraitThumbnail(
                        portrait = identity.portrait,
                        modifier = Modifier
                            .width(32.dp)
                            .height(46.dp)
                    )
                }
                Column {
                    Text(
                        "O Fingidor",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        identity.personaLabel,
                        color = personaLabelColor,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (hasConversationStarted && onNewConversation != null) {
                    NewConversationButton(
                        label = if (isCompact) "Nova" else "Nova conversa",
                        onClick = onNewConversation
                    )
                }
                if (onDevModeToggle != null) {
                    DevModeToggle(active = devMode, onToggle = onDevModeToggle)
                }
            }
        }
        HorizontalDivider(color = cardBorderColor, thickness = 1.dp)
    }
}

@Composable
private fun NewConversationButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun DevModeToggle(active: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) devChipColor else Color.Transparent)
            .border(
                1.dp,
                if (active) devChipBorderColor else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            "Modo DEV",
            color = if (active) devChipTextColor else Color.White.copy(alpha = 0.2f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.5.sp
        )
    }
}
