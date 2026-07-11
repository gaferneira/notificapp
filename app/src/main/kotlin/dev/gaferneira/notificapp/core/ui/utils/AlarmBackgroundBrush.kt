package dev.gaferneira.notificapp.core.ui.utils

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundPreset

/**
 * Converts an [AlarmBackgroundPreset]'s flat [AlarmBackgroundPreset.colorHexStops] into a Compose
 * [Brush]. Kept in `core/ui` (not `domain/`) since [Brush] is an Android/Compose type, and shared
 * between the ringing alarm UI and the rule editor's background picker.
 */
fun AlarmBackgroundPreset.toBrush(): Brush = Brush.linearGradient(colorHexStops.map(::parseHexColor))

private fun parseHexColor(hex: String): Color = Color(android.graphics.Color.parseColor(hex))
