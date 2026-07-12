package dev.gaferneira.notificapp.domain.model

/**
 * Configuration key for snooze duration in minutes.
 */
const val SNOOZE_DURATION_MINUTES_KEY = "snooze_duration_minutes"

/**
 * Default snooze duration in minutes.
 */
const val DEFAULT_SNOOZE_DURATION_MINUTES = 15

/**
 * Configuration key for the snooze mode: `"duration"` (fixed relative duration, default) or
 * `"scheduled"` (until a specific time, optionally recurring within a window).
 */
const val SNOOZE_MODE_KEY = "snooze_mode"

/**
 * Configuration key for the scheduled-mode start time: hour of day, 0-23.
 */
const val SNOOZE_SCHEDULE_START_HOUR_KEY = "snooze_schedule_start_hour"

/**
 * Configuration key for the scheduled-mode start time: minute of hour, 0-59.
 */
const val SNOOZE_SCHEDULE_START_MINUTE_KEY = "snooze_schedule_start_minute"

/**
 * Configuration key for the scheduled-mode repeat interval in minutes. Absent means a single
 * daily checkpoint at the start time; present means a recurring checkpoint every N minutes.
 */
const val SNOOZE_SCHEDULE_INTERVAL_MINUTES_KEY = "snooze_schedule_interval_minutes"

/**
 * Configuration key for the scheduled-mode recurrence window end time: hour of day, 0-23.
 * Required whenever an interval is set; matches at or after this time pass through unsnoozed.
 */
const val SNOOZE_SCHEDULE_WINDOW_END_HOUR_KEY = "snooze_schedule_window_end_hour"

/**
 * Configuration key for the scheduled-mode recurrence window end time: minute of hour, 0-59.
 */
const val SNOOZE_SCHEDULE_WINDOW_END_MINUTE_KEY = "snooze_schedule_window_end_minute"

/**
 * Configuration key for the scheduled-mode weekday filter: a comma-separated list of
 * DayOfWeek names (e.g., "MONDAY,WEDNESDAY,FRIDAY"). Absent means every day (default).
 */
const val SNOOZE_SCHEDULE_WEEKDAYS_KEY = "snooze_schedule_weekdays"

/**
 * Configuration key for batch-at-time delivery times: comma-separated list of "HH:MM" strings.
 * Only used for non-recurring scheduled snoozes (when intervalMinutes is null).
 * Absent means only the start time is used.
 */
const val SNOOZE_SCHEDULE_TIMES_KEY = "snooze_schedule_times"

/**
 * Configuration key for the throttle-mode rate-limit window, in minutes: the first match opens
 * the window and delivers; further matches inside the window are dropped until it elapses.
 */
const val SNOOZE_THROTTLE_WINDOW_MINUTES_KEY = "snooze_throttle_window_minutes"

/**
 * Default throttle window, in minutes.
 */
const val DEFAULT_SNOOZE_THROTTLE_WINDOW_MINUTES = 10

/**
 * Throttle window is clamped to this range, mirroring the [FLASH_COUNT_KEY] defense-in-depth
 * coercion pattern, so a malformed import can't produce a zero/negative window.
 */
const val MIN_SNOOZE_THROTTLE_WINDOW_MINUTES = 1
const val MAX_SNOOZE_THROTTLE_WINDOW_MINUTES = 1440

/**
 * Configuration key for the throttle-mode reset watermark: an epoch-millis timestamp. Any
 * delivery recorded before this watermark no longer counts, so editing the window duration or
 * disabling/re-enabling the action makes the next match deliver a fresh window.
 */
const val SNOOZE_THROTTLE_RESET_AT_KEY = "snooze_throttle_reset_at"

/**
 * Default throttle reset watermark - epoch millis zero, so any prior delivery counts until an
 * edit stamps a real watermark.
 */
const val DEFAULT_SNOOZE_THROTTLE_RESET_AT = 0L

/**
 * Get snooze duration in minutes, or default if not set.
 */
fun RuleAction.getSnoozeDurationMinutes(): Int = config[SNOOZE_DURATION_MINUTES_KEY]?.toIntOrNull()
    ?: DEFAULT_SNOOZE_DURATION_MINUTES

/**
 * Get the snooze mode, defaulting to [SnoozeMode.DURATION] if not set or unrecognized - this
 * keeps rules saved/exported before [SnoozeMode.SCHEDULED] existed behaving exactly as before.
 */
fun RuleAction.getSnoozeMode(): SnoozeMode = config[SNOOZE_MODE_KEY]
    ?.let { raw -> SnoozeMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
    ?: SnoozeMode.DURATION

/**
 * Parse the [SnoozeMode.SCHEDULED] configuration, or null when this action isn't in that mode
 * or its required start-time keys are missing/malformed.
 */
fun RuleAction.getSnoozeSchedule(): SnoozeSchedule? {
    val startHour = config[SNOOZE_SCHEDULE_START_HOUR_KEY]?.toIntOrNull()
    val startMinute = config[SNOOZE_SCHEDULE_START_MINUTE_KEY]?.toIntOrNull()
    if (getSnoozeMode() != SnoozeMode.SCHEDULED || startHour == null || startMinute == null) return null

    return SnoozeSchedule(
        startHour = startHour,
        startMinute = startMinute,
        intervalMinutes = config[SNOOZE_SCHEDULE_INTERVAL_MINUTES_KEY]?.toIntOrNull(),
        windowEndHour = config[SNOOZE_SCHEDULE_WINDOW_END_HOUR_KEY]?.toIntOrNull(),
        windowEndMinute = config[SNOOZE_SCHEDULE_WINDOW_END_MINUTE_KEY]?.toIntOrNull(),
        times = parseSnoozeScheduleTimes(config[SNOOZE_SCHEDULE_TIMES_KEY]),
        weekdays = parseSnoozeScheduleWeekdays(config[SNOOZE_SCHEDULE_WEEKDAYS_KEY]),
    )
}

/**
 * Get the throttle window in minutes, clamped to a sane range regardless of what's stored in
 * config (defense in depth against a malformed or imported rule).
 */
fun RuleAction.getThrottleWindowMinutes(): Int = (
    config[SNOOZE_THROTTLE_WINDOW_MINUTES_KEY]?.toIntOrNull() ?: DEFAULT_SNOOZE_THROTTLE_WINDOW_MINUTES
    ).coerceIn(MIN_SNOOZE_THROTTLE_WINDOW_MINUTES, MAX_SNOOZE_THROTTLE_WINDOW_MINUTES)

/**
 * Get the throttle reset watermark (epoch millis), or [DEFAULT_SNOOZE_THROTTLE_RESET_AT] if
 * not set or malformed.
 */
fun RuleAction.getThrottleResetAt(): Long = config[SNOOZE_THROTTLE_RESET_AT_KEY]?.toLongOrNull()
    ?: DEFAULT_SNOOZE_THROTTLE_RESET_AT

/**
 * How a [ActionType.SNOOZE_NOTIFICATION] [RuleAction] computes its snooze duration.
 */
enum class SnoozeMode {
    /** Fixed relative duration, read from [SNOOZE_DURATION_MINUTES_KEY]. */
    DURATION,

    /** Until a specific time, optionally recurring within a window - see [SnoozeSchedule]. */
    SCHEDULED,

    /**
     * Rate-limit: the first match in a rolling window delivers, further matches inside that
     * window are dropped (never re-delivered). See [SNOOZE_THROTTLE_WINDOW_MINUTES_KEY].
     */
    THROTTLE,
}

/**
 * Parsed [SnoozeMode.SCHEDULED] configuration for a [ActionType.SNOOZE_NOTIFICATION] [RuleAction].
 *
 * @property intervalMinutes null means batch-at-time mode (deliver at specific times);
 * non-null means recurring checkpoints every [intervalMinutes] minutes, bounded by
 * [windowEndHour]:[windowEndMinute] (both required together whenever [intervalMinutes] is set).
 * @property times only meaningful when [intervalMinutes] is null: list of (hour, minute) pairs
 * for delivery times. Null/empty means only [startHour]:[startMinute].
 * @property weekdays the days of the week this schedule applies to (e.g. Mon-Fri). Empty or null
 * means every day. DayOfWeek values are Java's standard: MONDAY=1, ..., SUNDAY=7.
 */
data class SnoozeSchedule(
    val startHour: Int,
    val startMinute: Int,
    val intervalMinutes: Int? = null,
    val windowEndHour: Int? = null,
    val windowEndMinute: Int? = null,
    val times: List<Pair<Int, Int>> = emptyList(),
    val weekdays: Set<java.time.DayOfWeek> = emptySet(),
)

/** Parses [getSnoozeSchedule]'s comma-separated weekday list; malformed entries are skipped. */
private fun parseSnoozeScheduleWeekdays(raw: String?): Set<java.time.DayOfWeek> = raw
    ?.split(",")
    ?.mapNotNull { day ->
        @Suppress("SwallowedException") // a malformed weekday entry is skipped, not fatal - same defensive-parsing intent as the config getters' coerceIn() clamps
        try {
            java.time.DayOfWeek.valueOf(day.trim())
        } catch (e: IllegalArgumentException) {
            null
        }
    }
    ?.toSet()
    ?: emptySet()

/** Parses [getSnoozeSchedule]'s `"HH:mm|HH:mm"` times list; malformed entries are skipped. */
private fun parseSnoozeScheduleTimes(raw: String?): List<Pair<Int, Int>> = raw
    ?.split("|")
    ?.mapNotNull { timeStr ->
        val parts = timeStr.trim().split(":")
        if (parts.size == 2) {
            parts[0].toIntOrNull()?.let { hour -> parts[1].toIntOrNull()?.let { minute -> hour to minute } }
        } else {
            null
        }
    }
    ?: emptyList()
