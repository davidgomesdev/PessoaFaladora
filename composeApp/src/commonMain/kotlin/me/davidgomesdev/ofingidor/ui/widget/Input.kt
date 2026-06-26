package me.davidgomesdev.ofingidor.ui.widget

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.davidgomesdev.ofingidor.ui.cardBorderColor
import me.davidgomesdev.ofingidor.ui.componentsBackgroundColor
import me.davidgomesdev.ofingidor.ui.disableThinkButtonColor
import me.davidgomesdev.ofingidor.ui.exampleCardBackgroundColor
import me.davidgomesdev.ofingidor.ui.focusedIndicatorColor
import me.davidgomesdev.ofingidor.ui.inputCardBackgroundColor
import me.davidgomesdev.ofingidor.ui.isActionInputType

private val exampleQueries =
    listOf(
        "O que é o amor para ti?",
        "Tens medo da morte?",
        "Como encontrar sentido na vida?",
        "O que pensas sobre a saudade?",
        "Explica-me porquê que decidiste criar heterónimos.",
        "Quem és?",
        "Como te chamas?",
        "O que é para ti a arte?",
        "Achavas que ias ser reconhecido depois de morrer?",
        "Qual a utilidade da escrita a teu ver?",
    )

@Composable
fun ThinkInputCard(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onQuerySelected: (String) -> Unit,
    hasConversationStarted: Boolean = false,
    isCompact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue =
            when {
                isLoading -> cardBorderColor.copy(alpha = 0.4f)
                isFocused -> focusedIndicatorColor
                else -> cardBorderColor
            },
    )
    val bgColor by animateColorAsState(
        targetValue = if (isLoading) Color(0xFF171717) else inputCardBackgroundColor,
    )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ThinkInputField(text, onTextChange, isLoading, interactionSource, onSubmit)
            if (!isLoading && !hasConversationStarted) {
                ExampleQueriesRow(
                    onQuerySelected = onQuerySelected,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                )
            }
        }
        HorizontalDivider(color = cardBorderColor, thickness = 1.dp)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = if (isCompact) Arrangement.Center else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isCompact) {
                Text(
                    "Control + Enter para enviar",
                    color = Color.White.copy(alpha = 0.25f),
                    fontSize = 11.sp,
                )
            }
            ThinkButton(onSubmit, isLoading)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExampleQueriesRow(
    onQuerySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        exampleQueries.forEach { query ->
            Text(
                text = query,
                color = Color.White.copy(alpha = 0.3f),
                fontSize = 11.sp,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(exampleCardBackgroundColor)
                        .border(1.dp, cardBorderColor, RoundedCornerShape(5.dp))
                        .clickable { onQuerySelected(query) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun ThinkInputField(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    interactionSource: MutableInteractionSource,
    onSubmit: () -> Unit,
) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = {
            Text(
                "Escreve o que te inquieta a alma...",
                color = Color.White.copy(alpha = if (isLoading) 0.15f else 0.35f),
                fontSize = 14.sp,
            )
        },
        enabled = !isLoading,
        minLines = 2,
        interactionSource = interactionSource,
        modifier =
            Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { keyEvent ->
                    if (isActionInputType(keyEvent) && !isLoading) {
                        onSubmit()
                        true
                    } else {
                        false
                    }
                },
        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.4f),
                cursorColor = Color.White,
            ),
    )
}

@Composable
fun ThinkButton(
    onSubmit: () -> Unit,
    isLoading: Boolean,
) {
    Button(
        onClick = onSubmit,
        enabled = !isLoading,
        shape = RoundedCornerShape(6.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = componentsBackgroundColor,
                contentColor = Color.White,
                disabledContainerColor = disableThinkButtonColor,
                disabledContentColor = Color.White.copy(alpha = 0.3f),
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Always reserve space for the wider label so the button never resizes
            Text("A pensar…", fontSize = 13.sp, modifier = Modifier.alpha(0f))
            Text(
                if (isLoading) "A pensar…" else "Pensar",
                fontSize = 13.sp,
            )
        }
    }
}
