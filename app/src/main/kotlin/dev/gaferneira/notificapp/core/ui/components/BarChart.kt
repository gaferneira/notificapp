@file:Suppress("MatchingDeclarationName") // Compose component file; the composable shares the file with its small input type

package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * One bar: [label] is the x-axis text (only shown for some bars when there are many), [value] its
 * height and [description] the full wording shown when the bar is selected (e.g. a complete date).
 */
@Immutable
data class BarChartEntry(val label: String, val value: Int, val description: String = label)

private const val MAX_FULL_LABELS = 7
private const val BAR_FILL_FRACTION = 0.6f
private val MIN_VISIBLE_BAR = 2.dp
private val BAR_CORNER = 2.dp

/**
 * Simple bar chart of per-day counts, drawn on a Canvas (no dependency, no animation).
 *
 * Up to 7 entries show every x-axis label; more show only the first, middle and last. All-zero data
 * draws just a flat baseline. Tapping a bar selects it (the last bar, typically "today", is selected
 * by default): the selected bar is drawn in the primary color, the others muted, and [selectionLabel]
 * is shown above the chart. The chart is a single accessible node announcing [contentDescription]
 * (the caller's summary) plus the selected bar's label, so individual bars are not read out.
 *
 * @param maxLabel scale hint shown above the chart's top-left corner (e.g. "Max 8"); null hides it
 * @param selectionLabel text for the selected bar, shown above the chart's end; defaults to "description: value"
 * @param onBarSelected called with the entry index when the user taps a bar
 */
@Composable
fun BarChart(
    entries: ImmutableList<BarChartEntry>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    maxLabel: String? = null,
    selectionLabel: (BarChartEntry) -> String = { "${it.description}: ${it.value}" },
    onBarSelected: ((Int) -> Unit)? = null,
    chartHeight: Dp = 120.dp,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val mutedBarColor = MaterialTheme.colorScheme.primaryContainer
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    // Reset to the last bar (today) whenever the data changes, e.g. on a period switch.
    var selectedIndex by remember(entries) { mutableIntStateOf(entries.lastIndex) }
    val tapRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val selectedEntry = entries.getOrNull(selectedIndex)
    val selectedText = selectedEntry?.let(selectionLabel)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                this.contentDescription = if (selectedText == null) contentDescription else "$contentDescription. $selectedText"
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BarChartHeader(maxLabel = maxLabel, selectedText = selectedText)
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .pointerInput(entries, tapRtl) {
                    detectTapGestures { tap ->
                        if (entries.isEmpty()) return@detectTapGestures
                        val slot = (tap.x / (size.width / entries.size)).toInt().coerceIn(0, entries.lastIndex)
                        val index = if (tapRtl) entries.lastIndex - slot else slot
                        selectedIndex = index
                        onBarSelected?.invoke(index)
                    }
                },
        ) {
            val baselineHeight = 1.dp.toPx()
            drawRect(
                color = baselineColor,
                topLeft = Offset(0f, size.height - baselineHeight),
                size = Size(size.width, baselineHeight),
            )
            if (entries.isEmpty()) return@Canvas
            val maxValue = entries.maxOf { it.value }.coerceAtLeast(1)
            val slot = size.width / entries.size
            val barWidth = slot * BAR_FILL_FRACTION
            val available = size.height - baselineHeight
            val rtl = layoutDirection == LayoutDirection.Rtl
            entries.forEachIndexed { index, entry ->
                if (entry.value <= 0) return@forEachIndexed
                val barHeight = (available * entry.value / maxValue).coerceAtLeast(MIN_VISIBLE_BAR.toPx())
                val slotIndex = if (rtl) entries.lastIndex - index else index
                val left = slotIndex * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = if (index == selectedIndex) barColor else mutedBarColor,
                    topLeft = Offset(left, available - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(BAR_CORNER.toPx()),
                )
            }
        }
        BarChartLabels(entries)
    }
}

/** Scale hint at the start and the selected bar's text at the end; takes no space when both are absent. */
@Composable
private fun BarChartHeader(maxLabel: String?, selectedText: String?) {
    if (maxLabel == null && selectedText == null) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = maxLabel.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            text = selectedText.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BarChartLabels(entries: ImmutableList<BarChartEntry>) {
    if (entries.isEmpty()) return
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (entries.size <= MAX_FULL_LABELS) {
        Row(modifier = Modifier.fillMaxWidth()) {
            entries.forEach { entry ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = entry.label,
                        style = style,
                        color = color,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                    )
                }
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(entries.first(), entries[entries.lastIndex / 2], entries.last()).forEach {
                Text(text = it.label, style = style, color = color, maxLines = 1)
            }
        }
    }
}

private fun previewEntries(count: Int, value: (Int) -> Int) = List(count) { BarChartEntry(label = "D${it + 1}", value = value(it)) }.toImmutableList()

@Composable
private fun BarChartPreviewContent() {
    NotificappTheme(dynamicColor = false) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)) {
            BarChart(entries = previewEntries(7) { 0 }, contentDescription = "No activity")
            BarChart(entries = previewEntries(7) { (it * 3) % 5 + 1 }, contentDescription = "7 days", maxLabel = "Max 5")
            BarChart(entries = previewEntries(30) { (it * 7) % 11 }, contentDescription = "30 days", maxLabel = "Max 10", selectionLabel = { "Day ${it.label}: ${it.value} runs" })
            BarChart(entries = persistentListOf(), contentDescription = "Empty")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BarChartPreview() = BarChartPreviewContent()

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BarChartPreviewDark() = BarChartPreviewContent()
