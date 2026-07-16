package dev.gaferneira.notificapp.util

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

/**
 * Formats seconds into a human-readable duration string, for cooldown windows measured in
 * seconds rather than minutes (e.g., "30s", "1m", "1m 30s", "1h").
 *
 * @param seconds Duration in seconds
 * @return Formatted string
 */
internal fun formatDurationSeconds(seconds: Int): String = when {
    seconds < SECONDS_PER_MINUTE -> "${seconds}s"
    seconds < SECONDS_PER_HOUR -> {
        val mins = seconds / SECONDS_PER_MINUTE
        val secs = seconds % SECONDS_PER_MINUTE
        if (secs == 0) "${mins}m" else "${mins}m ${secs}s"
    }
    else -> {
        val hours = seconds / SECONDS_PER_HOUR
        val mins = (seconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
