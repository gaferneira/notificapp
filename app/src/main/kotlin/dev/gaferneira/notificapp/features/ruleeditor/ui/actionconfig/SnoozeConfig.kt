package dev.gaferneira.notificapp.features.ruleeditor.ui.actionconfig

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.features.ruleeditor.ui.formatDurationMinutes

/**
 * Duration presets for quick snooze selection.
 */
private val SNOOZE_PRESETS = listOf(5, 10, 15, 30, 60)

/**
 * Minimum and maximum snooze duration in minutes.
 */
private const val MIN_SNOOZE_MINUTES = 1
private const val MAX_SNOOZE_MINUTES = 120

/**
 * Composable for selecting snooze duration with preset chips and a slider.
 * Provides the best UX for minute selection on mobile.
 *
 * @param selectedMinutes Currently selected duration in minutes
 * @param onDurationChange Callback when duration changes
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SnoozeDurationSelector(
    selectedMinutes: Int,
    onDurationChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        // Header with current selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Snooze duration",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Duration display badge
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = formatDurationMinutes(selectedMinutes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Preset chips for quick selection
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SNOOZE_PRESETS.forEach { preset ->
                val isSelected = selectedMinutes == preset
                FilterChip(
                    selected = isSelected,
                    onClick = { onDurationChange(preset) },
                    label = { Text("${preset}m") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slider for fine-grained control
        Text(
            text = "Or drag to set custom time",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${MIN_SNOOZE_MINUTES}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Slider(
                value = selectedMinutes.toFloat(),
                onValueChange = { onDurationChange(it.toInt()) },
                valueRange = MIN_SNOOZE_MINUTES.toFloat()..MAX_SNOOZE_MINUTES.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            Text(
                text = "${MAX_SNOOZE_MINUTES}m",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
