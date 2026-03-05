package dev.gaferneira.notificapp.util

import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Extension function to format a Date as a "time ago" string.
 * Examples: "Just now", "5 minutes ago", "Yesterday", "3 days ago"
 *
 * @param showMinutesHours If true, shows minutes/hours for recent times
 * @return Formatted time ago string
 */
fun Date.timeAgo(showMinutesHours: Boolean = true): String {
    val calendar = Calendar.getInstance().apply {
        time = this@timeAgo
    }

    val now = Calendar.getInstance()

    if (showMinutesHours) {
        val diffMillis = now.timeInMillis - calendar.timeInMillis
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)

        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "$diffMinutes min ago"
            diffHours < 24 -> "$diffHours hr ago"
            else -> getDaysAgoString(calendar, now)
        }
    }

    return getDaysAgoString(calendar, now)
}

/**
 * Format a timestamp for display in the notification list.
 * Shows time for today, "Yesterday" for yesterday, and date for older.
 *
 * @return Formatted date/time string
 */
fun Date.formatNotificationTime(): String {
    val calendar = Calendar.getInstance().apply { time = this@formatNotificationTime }
    val now = Calendar.getInstance()

    return when (getDaysBetween(calendar, now)) {
        0 -> {
            // Today - show time only
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            String.format("%02d:%02d", hour, minute)
        }
        1 -> "Yesterday"
        else -> {
            // Show date
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val month = calendar.get(Calendar.MONTH) + 1
            val year = calendar.get(Calendar.YEAR)
            if (year == now.get(Calendar.YEAR)) {
                String.format("%02d/%02d", day, month)
            } else {
                String.format("%02d/%02d/%d", day, month, year)
            }
        }
    }
}

private fun getDaysAgoString(startDate: Calendar, endDate: Calendar): String {
    val daysAgo = getDaysBetween(startDate, endDate)
    val weeksAgo = daysAgo / 7
    val monthsAgo = daysAgo / 30

    return when {
        daysAgo == 0 -> "Today"
        daysAgo == 1 -> "Yesterday"
        daysAgo in 2..6 -> "$daysAgo days ago"
        weeksAgo == 1 -> "1 week ago"
        weeksAgo in 2..4 -> "$weeksAgo weeks ago"
        monthsAgo == 1 -> "1 month ago"
        monthsAgo in 2..12 -> "$monthsAgo months ago"
        else -> {
            val yearsAgo = daysAgo / 365
            if (yearsAgo == 1) "1 year ago" else "$yearsAgo years ago"
        }
    }
}

private fun getDaysBetween(startDate: Calendar, endDate: Calendar): Int {
    val start = (startDate.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val end = (endDate.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val diff = end.timeInMillis - start.timeInMillis
    return (diff / (1000 * 60 * 60 * 24)).toInt()
}
