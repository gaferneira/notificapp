package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.domain.model.MAX_FLASH_COUNT
import dev.gaferneira.notificapp.domain.model.MAX_FLASH_DURATION_MS
import dev.gaferneira.notificapp.domain.model.MIN_FLASH_COUNT
import dev.gaferneira.notificapp.domain.model.MIN_FLASH_DURATION_MS
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.getFlashCount
import dev.gaferneira.notificapp.domain.model.getFlashDurationMs

/**
 * Composable for configuring the flash alert action: number of flashes and the duration of each
 * flash phase. Both ranges are clamped by [RuleAction.getFlashCount]/[RuleAction.getFlashDurationMs]
 * as a photosensitivity safety bound, not just here.
 *
 * @param flashCount Currently configured number of flashes
 * @param flashDurationMs Currently configured duration of each flash phase, in milliseconds
 * @param onFlashCountChange Callback when the flash count changes
 * @param onFlashDurationChange Callback when the flash duration changes
 * @param modifier Modifier for the component
 */
@Composable
fun FlashOptionsSelector(
    flashCount: Int,
    flashDurationMs: Long,
    onFlashCountChange: (Int) -> Unit,
    onFlashDurationChange: (Long) -> Unit,
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
        Text(
            text = "Flash options",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))

        FlashCountSlider(flashCount = flashCount, onFlashCountChange = onFlashCountChange)

        Spacer(modifier = Modifier.height(12.dp))

        FlashDurationSlider(flashDurationMs = flashDurationMs, onFlashDurationChange = onFlashDurationChange)
    }
}

@Composable
private fun FlashCountSlider(
    flashCount: Int,
    onFlashCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Number of flashes: $flashCount",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = flashCount.toFloat(),
            onValueChange = { onFlashCountChange(it.toInt()) },
            valueRange = MIN_FLASH_COUNT.toFloat()..MAX_FLASH_COUNT.toFloat(),
            steps = MAX_FLASH_COUNT - MIN_FLASH_COUNT - 1,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun FlashDurationSlider(
    flashDurationMs: Long,
    onFlashDurationChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Flash speed: ${flashDurationMs}ms per phase",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = flashDurationMs.toFloat(),
            onValueChange = { onFlashDurationChange(it.toLong()) },
            valueRange = MIN_FLASH_DURATION_MS.toFloat()..MAX_FLASH_DURATION_MS.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}
