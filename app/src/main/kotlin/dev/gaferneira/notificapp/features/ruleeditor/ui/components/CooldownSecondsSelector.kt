package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.util.formatDurationSeconds

/**
 * Cooldown presets: 30s / 1m / 5m / 15m - a chatty source app re-matching a rule within this
 * window has its disruptive action (Alarm/Flash Alert) suppressed instead of re-firing.
 */
private val COOLDOWN_SECONDS_PRESETS = listOf(30, 60, 300, 900)

/**
 * The slider's practical upper bound for usability. The domain-level clamp
 * (`MAX_ALARM_COOLDOWN_SECONDS`/`MAX_FLASH_COOLDOWN_SECONDS`, 24h) is a defense-in-depth ceiling
 * against a malformed or imported config, not a UI target - a cooldown longer than an hour is a
 * rare enough case that it doesn't need dedicated slider real estate.
 */
private const val MAX_SLIDER_COOLDOWN_SECONDS = 1800

/**
 * Composable for enabling and selecting a per-action cooldown window, modeled on
 * [ThrottleWindowSelector]. Unlike the throttle window (always active, minute-scoped), cooldown
 * defaults to disabled (`0` = off) so most Alarm/Flash Alert actions are unaffected, and is
 * seconds-scoped to fit tighter safety windows.
 *
 * @param selectedSeconds Currently selected cooldown in seconds; `0` means disabled
 * @param onSecondsChange Callback when the cooldown changes (0 to disable)
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CooldownSecondsSelector(
    selectedSeconds: Int,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = selectedSeconds > 0
    var isCustomSelected by remember(enabled) {
        mutableStateOf(enabled && selectedSeconds !in COOLDOWN_SECONDS_PRESETS)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        CooldownHeader(
            enabled = enabled,
            onEnabledChange = { checked ->
                onSecondsChange(if (checked) COOLDOWN_SECONDS_PRESETS.first() else 0)
            },
        )

        if (enabled) {
            Spacer(modifier = Modifier.height(12.dp))

            CooldownPresetChips(
                selectedSeconds = selectedSeconds,
                isCustomSelected = isCustomSelected,
                onPresetSelected = {
                    isCustomSelected = false
                    onSecondsChange(it)
                },
                onCustomSelected = { isCustomSelected = true },
            )

            if (isCustomSelected) {
                CustomCooldownSlider(selectedSeconds = selectedSeconds, onSecondsChange = onSecondsChange)
            }
        }
    }
}

@Composable
private fun CooldownHeader(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "Cooldown",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Suppress repeated firing from a chatty app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (enabled) {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CooldownPresetChips(
    selectedSeconds: Int,
    isCustomSelected: Boolean,
    onPresetSelected: (Int) -> Unit,
    onCustomSelected: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        COOLDOWN_SECONDS_PRESETS.forEach { preset ->
            val isSelected = !isCustomSelected && selectedSeconds == preset
            FilterChip(
                selected = isSelected,
                onClick = { onPresetSelected(preset) },
                label = { Text(formatDurationSeconds(preset)) },
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
private fun CustomCooldownSlider(selectedSeconds: Int, onSecondsChange: (Int) -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Drag to set custom cooldown",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = formatDurationSeconds(selectedSeconds),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDurationSeconds(COOLDOWN_SECONDS_PRESETS.first()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Slider(
            value = selectedSeconds.toFloat(),
            onValueChange = { onSecondsChange(it.toInt()) },
            valueRange = COOLDOWN_SECONDS_PRESETS.first().toFloat()..MAX_SLIDER_COOLDOWN_SECONDS.toFloat(),
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )

        Text(
            text = formatDurationSeconds(MAX_SLIDER_COOLDOWN_SECONDS),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true, name = "Disabled")
@Composable
private fun CooldownSecondsSelectorDisabledPreview() {
    NotificappTheme {
        CooldownSecondsSelector(
            selectedSeconds = 0,
            onSecondsChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "Preset selected")
@Composable
private fun CooldownSecondsSelectorPresetPreview() {
    NotificappTheme {
        CooldownSecondsSelector(
            selectedSeconds = 60,
            onSecondsChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "Custom value Light")
@Preview(showBackground = true, name = "Custom value Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CooldownSecondsSelectorCustomPreview() {
    NotificappTheme {
        CooldownSecondsSelector(
            selectedSeconds = 450,
            onSecondsChange = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
