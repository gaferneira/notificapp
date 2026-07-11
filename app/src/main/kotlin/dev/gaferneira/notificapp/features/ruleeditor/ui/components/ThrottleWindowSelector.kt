package dev.gaferneira.notificapp.features.ruleeditor.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.domain.model.MAX_SNOOZE_THROTTLE_WINDOW_MINUTES
import dev.gaferneira.notificapp.domain.model.MIN_SNOOZE_THROTTLE_WINDOW_MINUTES
import dev.gaferneira.notificapp.util.formatDurationMinutes

/**
 * Window presets for quick throttle selection: 5m / 10m / 30m / 1h.
 */
private val THROTTLE_WINDOW_PRESETS = listOf(5, 10, 30, 60)

/**
 * Composable for selecting the throttle rate-limit window with preset chips and a slider,
 * modeled on [SnoozeDurationSelector].
 *
 * @param selectedMinutes Currently selected window in minutes
 * @param onWindowChange Callback when the window changes
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThrottleWindowSelector(
    selectedMinutes: Int,
    onWindowChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isCustomSelected by remember { mutableStateOf(selectedMinutes !in THROTTLE_WINDOW_PRESETS) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        ThrottleWindowHeader(selectedMinutes)

        Spacer(modifier = Modifier.height(12.dp))

        ThrottleWindowPresetChips(
            selectedMinutes = selectedMinutes,
            isCustomSelected = isCustomSelected,
            onPresetSelected = {
                isCustomSelected = false
                onWindowChange(it)
            },
            onCustomSelected = { isCustomSelected = true },
        )

        if (isCustomSelected) {
            CustomThrottleWindowSlider(selectedMinutes = selectedMinutes, onWindowChange = onWindowChange)
        }
    }
}

@Composable
private fun ThrottleWindowHeader(selectedMinutes: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Throttle window",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThrottleWindowPresetChips(
    selectedMinutes: Int,
    isCustomSelected: Boolean,
    onPresetSelected: (Int) -> Unit,
    onCustomSelected: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        THROTTLE_WINDOW_PRESETS.forEach { preset ->
            val isSelected = !isCustomSelected && selectedMinutes == preset
            FilterChip(
                selected = isSelected,
                onClick = { onPresetSelected(preset) },
                label = { Text(formatDurationMinutes(preset)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }

        FilterChip(
            selected = isCustomSelected,
            onClick = onCustomSelected,
            label = { Text("Custom") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
private fun CustomThrottleWindowSlider(selectedMinutes: Int, onWindowChange: (Int) -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Drag to set custom window",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${MIN_SNOOZE_THROTTLE_WINDOW_MINUTES}m",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Slider(
            value = selectedMinutes.toFloat(),
            onValueChange = { onWindowChange(it.toInt()) },
            valueRange = MIN_SNOOZE_THROTTLE_WINDOW_MINUTES.toFloat()..MAX_SNOOZE_THROTTLE_WINDOW_MINUTES.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Text(
            text = formatDurationMinutes(MAX_SNOOZE_THROTTLE_WINDOW_MINUTES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
