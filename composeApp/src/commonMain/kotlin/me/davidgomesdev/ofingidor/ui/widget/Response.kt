package me.davidgomesdev.ofingidor.ui.widget

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.davidgomesdev.ofingidor.ui.backgroundColor
import me.davidgomesdev.ofingidor.ui.cardBorderColor
import me.davidgomesdev.ofingidor.ui.focusedIndicatorColor
import me.davidgomesdev.ofingidor.ui.inputCardBackgroundColor
import me.davidgomesdev.ofingidor.ui.model.Source
import me.davidgomesdev.ofingidor.ui.service.isMobileDevice
import me.davidgomesdev.ofingidor.ui.service.openUrl

private val accentColorLight = Color(0xFFA07FD4)
private const val textReaderUrl = "https://pessoa.davidgomes.blog/textReader"

@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("kotlin:S3776")
internal fun SourceChip(source: Source, tappedSourceId: Long? = null, onTap: (Long?) -> Unit = {}) {
    val isMobile = isMobileDevice()
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isTapped = tappedSourceId == source.id
    val showTooltip = if (isMobile) isTapped else isHovered

    Layout(
        content = {
            DisableSelection {
                Text(
                    source.title,
                    color = if (showTooltip) accentColorLight else focusedIndicatorColor.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    modifier = Modifier
                        .hoverable(interactionSource)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                if (isMobile) {
                                    val tappedSourceId = if (isTapped) null else source.id

                                    onTap(tappedSourceId)
                                } else openUrl("$textReaderUrl/${source.id}")
                            },
                            onLongClick = if (isMobile) {
                                { openUrl("$textReaderUrl/${source.id}") }
                            } else null,
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (showTooltip) accentColorLight.copy(alpha = 0.15f) else inputCardBackgroundColor)
                        .border(
                            1.dp,
                            if (showTooltip) accentColorLight.copy(alpha = 0.5f) else cardBorderColor,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (showTooltip) {
                SourcesTooltip(source)
            }
        }, measurePolicy = MeasureScope::centerTooltip
    )
}

@Composable
private fun SourcesTooltip(source: Source) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(1.dp, cardBorderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SourceTooltipRow("Autor", source.author)
        SourceTooltipRow("Categoria", source.category)
        SourceTooltipRow("Relevância", "${source.score}%")
    }
}

@Composable
private fun SourceTooltipRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            color = focusedIndicatorColor.copy(alpha = 0.5f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
        Text(
            value,
            color = focusedIndicatorColor.copy(alpha = 0.9f),
            fontSize = 10.sp
        )
    }
}

private fun MeasureScope.centerTooltip(
    measurables: List<Measurable>,
    constraints: Constraints
): MeasureResult {
    val chipPlaceable = measurables[0].measure(constraints)
    val tooltipPlaceable = measurables.getOrNull(1)?.measure(Constraints())

    return layout(chipPlaceable.width, chipPlaceable.height) {
        chipPlaceable.place(0, 0)
        tooltipPlaceable?.place(
            x = (chipPlaceable.width - tooltipPlaceable.width) / 2,
            y = -tooltipPlaceable.height - 4.dp.roundToPx()
        )
    }
}
