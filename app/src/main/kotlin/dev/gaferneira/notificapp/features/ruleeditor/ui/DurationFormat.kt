package dev.gaferneira.notificapp.features.ruleeditor.ui

/**
 * Formats minutes into a human-readable duration string.
 * Shows hours when applicable (e.g., "1h 30m", "45 min", "2h").
 *
 * @param minutes Duration in minutes
 * @return Formatted string
 */
internal fun formatDurationMinutes(minutes: Int): String = if (minutes >= 60) {
    val hours = minutes / 60
    val mins = minutes % 60
    if (mins == 0) {
        "${hours}h"
    } else {
        "${hours}h ${mins}m"
    }
} else {
    "$minutes min"
}
