@file:Suppress("MatchingDeclarationName") // Compose component file; the composable shares the file with its small input type

package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** One bar: [label] is the x-axis text (only shown for some bars when there are many), [value] its height. */
@Immutable
data class BarChartEntry(val label: String, val value: Int)

private const val MAX_FULL_LABELS = 7
private const val BAR_FILL_FRACTION = 0.6f
private val MIN_VISIBLE_BAR = 2.dp
private val BAR_CORNER = 2.dp

/**
 * Simple bar chart of per-day counts, drawn on a Canvas (no dependency, no animation).
 *
 * Up to 7 entries show every x-axis label; more show only the first, middle and last. All-zero data
 * draws just a flat baseline. The chart is a single accessible node announcing [contentDescription]
 * (the caller's summary), so individual bars and labels are not read out.
 *
 * @param highlightLast draws the last bar (typically "today") in the primary color and the others muted
 */
@Composable
fun BarChart(
    entries: ImmutableList<BarChartEntry>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    highlightLast: Boolean = false,
    chartHeight: Dp = 120.dp,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val mutedBarColor = MaterialTheme.colorScheme.primaryContainer
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
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
                val isHighlighted = !highlightLast || index == entries.lastIndex
                drawRoundRect(
                    color = if (isHighlighted) barColor else mutedBarColor,
                    topLeft = Offset(left, available - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(BAR_CORNER.toPx()),
                )
            }
        }
        BarChartLabels(entries)
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
            BarChart(entries = previewEntries(7) { (it * 3) % 5 + 1 }, contentDescription = "7 days", highlightLast = true)
            BarChart(entries = previewEntries(30) { (it * 7) % 11 }, contentDescription = "30 days", highlightLast = true)
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
