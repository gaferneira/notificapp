package dev.gaferneira.notificapp.util

import android.icu.text.RelativeDateTimeFormatter
import android.icu.text.RelativeDateTimeFormatter.AbsoluteUnit
import android.icu.text.RelativeDateTimeFormatter.Direction
import android.icu.text.RelativeDateTimeFormatter.RelativeUnit
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DAYS_PER_WEEK = 7
private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_YEAR = 365
private const val HOURS_PER_DAY = 24L
private const val MINUTES_PER_HOUR = 60L

/**
 * Formats a Date as a localized "time ago" string using the current default locale (which follows
 * the in-app language). Examples: "now", "5 minutes ago", "yesterday", "3 days ago".
 *
 * @param showMinutesHours If true, shows minutes/hours for recent times
 * @return Formatted time ago string
 */
fun Date.timeAgo(showMinutesHours: Boolean = true, locale: Locale = Locale.getDefault()): String {
    val calendar = Calendar.getInstance().apply { time = this@timeAgo }
    val now = Calendar.getInstance()
    val formatter = RelativeDateTimeFormatter.getInstance(locale)

    if (showMinutesHours) {
        val diffMillis = now.timeInMillis - calendar.timeInMillis
        val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)

        return when {
            diffMinutes < 1 -> formatter.format(Direction.PLAIN, AbsoluteUnit.NOW)
            diffMinutes < MINUTES_PER_HOUR -> formatter.format(diffMinutes.toDouble(), Direction.LAST, RelativeUnit.MINUTES)
            diffHours < HOURS_PER_DAY -> formatter.format(diffHours.toDouble(), Direction.LAST, RelativeUnit.HOURS)
            else -> getDaysAgoString(formatter, calendar, now)
        }
    }

    return getDaysAgoString(formatter, calendar, now)
}

/**
 * Format a timestamp for display in lists: time for today, "yesterday" for yesterday, and a short
 * localized date for older ones.
 *
 * @return Formatted date/time string
 */
fun Date.formatNotificationTime(locale: Locale = Locale.getDefault()): String {
    val calendar = Calendar.getInstance().apply { time = this@formatNotificationTime }
    val now = Calendar.getInstance()

    return when (getDaysBetween(calendar, now)) {
        0 -> SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "jm"), locale).format(this)
        1 -> RelativeDateTimeFormatter.getInstance(locale).format(Direction.LAST, AbsoluteUnit.DAY)
        else -> {
            val skeleton = if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR)) "dMMM" else "dMMMy"
            SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(this)
        }
    }
}

private fun getDaysAgoString(formatter: RelativeDateTimeFormatter, startDate: Calendar, endDate: Calendar): String {
    val daysAgo = getDaysBetween(startDate, endDate)
    val weeksAgo = daysAgo / DAYS_PER_WEEK
    val monthsAgo = daysAgo / DAYS_PER_MONTH

    return when {
        daysAgo == 0 -> formatter.format(Direction.THIS, AbsoluteUnit.DAY)
        daysAgo == 1 -> formatter.format(Direction.LAST, AbsoluteUnit.DAY)
        daysAgo < DAYS_PER_WEEK -> formatter.format(daysAgo.toDouble(), Direction.LAST, RelativeUnit.DAYS)
        monthsAgo < 1 -> formatter.format(weeksAgo.toDouble(), Direction.LAST, RelativeUnit.WEEKS)
        daysAgo < DAYS_PER_YEAR -> formatter.format(monthsAgo.toDouble(), Direction.LAST, RelativeUnit.MONTHS)
        else -> formatter.format((daysAgo / DAYS_PER_YEAR).toDouble(), Direction.LAST, RelativeUnit.YEARS)
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
    return TimeUnit.MILLISECONDS.toDays(diff).toInt()
}
