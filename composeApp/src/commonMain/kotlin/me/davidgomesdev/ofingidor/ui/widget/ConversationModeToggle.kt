package me.davidgomesdev.ofingidor.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.davidgomesdev.ofingidor.ui.focusedIndicatorColor
import me.davidgomesdev.ofingidor.ui.model.ConversationMode
import me.davidgomesdev.ofingidor.ui.orthonymChipBorderColor
import me.davidgomesdev.ofingidor.ui.orthonymChipColor
import me.davidgomesdev.ofingidor.ui.orthonymChipTextColor

@Composable
fun ConversationModeToggle(
    mode: ConversationMode,
    onModeSelected: (ConversationMode) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        ConversationMode.entries.forEach { option ->
            val isSelected = option == mode
            val label = when (option) {
                ConversationMode.CHAT -> "Conversa"
                ConversationMode.DEBATE -> "Debate"
            }

            Text(
                text = label,
                color = if (isSelected) orthonymChipTextColor else Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) orthonymChipColor else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) orthonymChipBorderColor else focusedIndicatorColor.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(20.dp),
                    )
                    .clickable { onModeSelected(option) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}
