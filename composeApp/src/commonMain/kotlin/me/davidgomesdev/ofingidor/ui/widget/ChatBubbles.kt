package me.davidgomesdev.ofingidor.ui.widget

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.davidgomesdev.ofingidor.shared.dto.Persona
import me.davidgomesdev.ofingidor.ui.aiBubbleBackgroundColor
import me.davidgomesdev.ofingidor.ui.aiBubbleBorder
import me.davidgomesdev.ofingidor.ui.debateBubblePalette
import me.davidgomesdev.ofingidor.ui.errorBubbleBackgroundColor
import me.davidgomesdev.ofingidor.ui.errorBubbleBorderColor
import me.davidgomesdev.ofingidor.ui.errorBubbleTextColor
import me.davidgomesdev.ofingidor.ui.inputCardBackgroundColor
import me.davidgomesdev.ofingidor.ui.model.DebateSide
import me.davidgomesdev.ofingidor.ui.model.Source
import me.davidgomesdev.ofingidor.ui.personaLabelColor
import me.davidgomesdev.ofingidor.ui.userBubbleBorder

private val accentColorLight = Color(0xFFA07FD4)

@Composable
fun UserBubble(question: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 2.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(inputCardBackgroundColor)
                    .border(
                        width = 2.dp,
                        color = userBubbleBorder,
                        shape = RoundedCornerShape(
                            topStart = 10.dp,
                            topEnd = 2.dp,
                            bottomStart = 10.dp,
                            bottomEnd = 10.dp
                        )
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                BubbleText(question = question)
            }
        }
    }
}

@Composable
fun CenteredUserBubble(question: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(inputCardBackgroundColor)
                .border(2.dp, userBubbleBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 13.dp, vertical = 9.dp),
        ) {
            BubbleText(question = question)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiBubble(
    persona: Persona,
    message: String,
    sources: List<Source>,
    isLoading: Boolean,
) {
    val identity = chatPortraitIdentity(persona)
    val inlineContent = if (isLoading) {
        mapOf(
            "cursor" to InlineTextContent(
                placeholder = Placeholder(2.sp, 14.sp, PlaceholderVerticalAlign.TextCenter)
            ) { BlinkingCursor() }
        )
    } else {
        emptyMap()
    }

    val annotatedText = buildAnnotatedString {
        append(message)
        if (isLoading) appendInlineContent("cursor", "|")
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PersonaAvatar(
            persona = persona,
            modifier = Modifier.size(resolveChatAvatarSize(identity)),
            contentDescription = identity.label,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                    .background(aiBubbleBackgroundColor)
                    .border(
                        width = 2.dp,
                        color = aiBubbleBorder,
                        shape = RoundedCornerShape(
                            topStart = 2.dp,
                            topEnd = 10.dp,
                            bottomStart = 10.dp,
                            bottomEnd = 10.dp
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                BubbleMessageText(text = annotatedText, inlineContent = inlineContent)
            }
            BubbleSources(sources = sources)
        }
    }
}

@Composable
fun DebatePersonaBubble(
    speaker: Persona,
    side: DebateSide,
    message: String,
    sources: List<Source>,
    isLoading: Boolean,
) {
    val palette = debateBubblePalette(speaker)
    val shape = when (side) {
        DebateSide.LEFT -> RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
        DebateSide.RIGHT -> RoundedCornerShape(topStart = 10.dp, topEnd = 2.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
    }
    val inlineContent = loadingInlineContent(isLoading)
    val annotatedText = loadingAnnotatedText(message, isLoading)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (side == DebateSide.LEFT) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            val identity = debatePortraitIdentity(speaker)
            PersonaAvatar(
                persona = speaker,
                contentDescriptionMode = AvatarContentDescriptionMode.DECORATIVE,
            )
            Text(
                text = identity.label,
                color = palette.label,
                fontSize = 10.sp,
            )
        }
        Column(
            horizontalAlignment = if (side == DebateSide.LEFT) Alignment.Start else Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .clip(shape)
                    .background(palette.background)
                    .border(2.dp, palette.border, shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                BubbleMessageText(text = annotatedText, inlineContent = inlineContent)
            }
            BubbleSources(sources = sources)
        }
    }
}

@Composable
private fun ExpandToggleChip(expanded: Boolean, hiddenCount: Int, onClick: () -> Unit) {
    val label = if (expanded) "− menos" else "+$hiddenCount mais"
    DisableSelection {
        Text(
            label,
            color = accentColorLight.copy(alpha = 0.8f),
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(accentColorLight.copy(alpha = 0.10f))
                .border(1.dp, accentColorLight.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun BubbleText(question: String) {
    Text(
        text = question,
        color = Color(0xFFCCCCCC),
        fontSize = 14.sp,
    )
}

@Composable
private fun BubbleMessageText(
    text: androidx.compose.ui.text.AnnotatedString,
    inlineContent: Map<String, InlineTextContent>,
) {
    Text(
        text = text,
        color = Color(0xFFCCCCCC),
        fontSize = 14.sp,
        lineHeight = 22.sp,
        inlineContent = inlineContent,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BubbleSources(sources: List<Source>) {
    if (sources.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val visibleSources = if (sources.size > 3 && !expanded) sources.take(3) else sources

    FlowRow(
        modifier = Modifier.widthIn(max = 560.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visibleSources.forEach { source ->
            key(source.id) { SourceChip(source) }
        }
        if (sources.size > 3) {
            ExpandToggleChip(
                expanded = expanded,
                hiddenCount = sources.size - 3,
                onClick = { expanded = !expanded },
            )
        }
    }
}

private fun loadingInlineContent(isLoading: Boolean): Map<String, InlineTextContent> =
    if (isLoading) {
        mapOf(
            "cursor" to InlineTextContent(
                placeholder = Placeholder(2.sp, 14.sp, PlaceholderVerticalAlign.TextCenter)
            ) { BlinkingCursor() }
        )
    } else {
        emptyMap()
    }

private fun loadingAnnotatedText(message: String, isLoading: Boolean) = buildAnnotatedString {
    append(message)
    if (isLoading) appendInlineContent("cursor", "|")
}

@Composable
private fun BlinkingCursor() {
    val infiniteTransition = rememberInfiniteTransition()
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart,
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(cursorAlpha)
            .background(personaLabelColor)
    )
}

@Composable
fun ErrorBubble(errorDetail: String? = null) {
    Column(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(errorBubbleBackgroundColor)
            .border(
                width = 1.dp,
                color = errorBubbleBorderColor,
                shape = RoundedCornerShape(topStart = 2.dp, topEnd = 10.dp, bottomStart = 10.dp, bottomEnd = 10.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Algo correu mal. Tenta de novo.",
            color = errorBubbleTextColor,
            fontSize = 13.sp,
        )
        if (errorDetail != null) {
            SelectionContainer {
                Text(
                    errorDetail,
                    color = errorBubbleTextColor.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
