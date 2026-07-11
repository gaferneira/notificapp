package dev.gaferneira.notificapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
 * Configuration key for the alarm sound URI (as a string). Absent/null means "use the device's
 * default alarm sound".
 */
const val ALARM_SOUND_URI_KEY = "alarm_sound_uri"

/**
 * Configuration key for whether the alarm should also vibrate the device.
 */
const val ALARM_VIBRATION_ENABLED_KEY = "alarm_vibration_enabled"

/**
 * Default vibration setting for the alarm action.
 */
const val DEFAULT_ALARM_VIBRATION_ENABLED = true

/**
 * Configuration key for whether the alarm shows the full-screen, call-style UI (vs. only the
 * ongoing notification).
 */
const val ALARM_FULLSCREEN_ENABLED_KEY = "alarm_fullscreen_enabled"

/**
 * Default full-screen setting for the alarm action.
 */
const val DEFAULT_ALARM_FULLSCREEN_ENABLED = false

/**
 * Configuration key for the number of torch flashes.
 */
const val FLASH_COUNT_KEY = "flash_count"

/**
 * Configuration key for the duration of each flash (on-time and off-time), in milliseconds.
 */
const val FLASH_DURATION_MS_KEY = "flash_duration_ms"

/**
 * Default number of flashes.
 */
const val DEFAULT_FLASH_COUNT = 3

/**
 * Default duration of each flash phase, in milliseconds.
 */
const val DEFAULT_FLASH_DURATION_MS = 300L

/**
 * Flash count is clamped to this range - a photosensitivity safety bound, not just a UI nicety,
 * since imported rules (Phase 2) could otherwise request an excessive strobe.
 */
const val MIN_FLASH_COUNT = 1
const val MAX_FLASH_COUNT = 10

/**
 * Minimum duration per flash phase (on or off), in milliseconds - the other half of the
 * photosensitivity safety bound. At this floor, a full on/off cycle is 400ms (2.5Hz), safely
 * under the commonly cited 3-flashes-per-second photosensitive-epilepsy threshold.
 */
const val MIN_FLASH_DURATION_MS = 200L
const val MAX_FLASH_DURATION_MS = 1000L

/**
 * Configuration key for whether the alarm plays audio while ringing.
 */
const val ALARM_SOUND_ENABLED_KEY = "alarm_sound_enabled"

/**
 * Default sound-enabled setting for the alarm action.
 */
const val DEFAULT_ALARM_SOUND_ENABLED = true

/**
 * Configuration key for the selected named vibration pattern's [VibrationPattern.id].
 */
const val ALARM_VIBRATION_PATTERN_KEY = "alarm_vibration_pattern"

/**
 * Configuration key for whether the Snooze action is offered while the alarm rings.
 */
const val ALARM_SNOOZE_ENABLED_KEY = "alarm_snooze_enabled"

/**
 * Default snooze-enabled setting for the alarm action.
 */
const val DEFAULT_ALARM_SNOOZE_ENABLED = false

/**
 * Configuration key for the alarm-scoped snooze delay, in minutes. Distinct from
 * [SNOOZE_DURATION_MINUTES_KEY], which belongs to the separate `SNOOZE_NOTIFICATION` action type.
 */
const val ALARM_SNOOZE_DURATION_MINUTES_KEY = "alarm_snooze_duration_minutes"

/**
 * Default alarm snooze delay, in minutes.
 */
const val DEFAULT_ALARM_SNOOZE_DURATION_MINUTES = 5

/**
 * Alarm snooze delay is clamped to this range, mirroring the [FLASH_COUNT_KEY] defense-in-depth
 * coercion pattern.
 */
const val MIN_ALARM_SNOOZE_DURATION_MINUTES = 1
const val MAX_ALARM_SNOOZE_DURATION_MINUTES = 60

/**
 * Configuration key for the maximum number of times an alarm may be snoozed before Snooze stops
 * being offered.
 */
const val ALARM_SNOOZE_MAX_COUNT_KEY = "alarm_snooze_max_count"

/**
 * Default maximum snooze count. Also the value legacy rules (persisted before this option
 * existed, with no `ALARM_SNOOZE_MAX_COUNT_KEY` in config) resolve to, closing the previously
 * unbounded snooze loop without touching stored data.
 */
const val DEFAULT_ALARM_SNOOZE_MAX_COUNT = 3

/**
 * Alarm max snooze count is clamped to this range, mirroring the [FLASH_COUNT_KEY]
 * defense-in-depth coercion pattern.
 */
const val MIN_ALARM_SNOOZE_MAX_COUNT = 1
const val MAX_ALARM_SNOOZE_MAX_COUNT = 10

/**
 * Configuration key for the alarm full-screen UI background source (an [AlarmBackgroundType] name).
 */
const val ALARM_BACKGROUND_TYPE_KEY = "alarm_background_type"

/**
 * Configuration key for the selected background preset's [AlarmBackgroundPreset.id], used when
 * [ALARM_BACKGROUND_TYPE_KEY] is `PRESET`.
 */
const val ALARM_BACKGROUND_PRESET_KEY = "alarm_background_preset"

/**
 * Configuration key for the persisted custom background image URI (as a string), used when
 * [ALARM_BACKGROUND_TYPE_KEY] is `IMAGE`.
 */
const val ALARM_BACKGROUND_IMAGE_URI_KEY = "alarm_background_image_uri"

/**
 * Configuration key for whether the user-picked custom background image (used when
 * [ALARM_BACKGROUND_TYPE_KEY] is `IMAGE`) is dark, so the ringing alarm UI should render its
 * title/text in light/white instead of following the system theme. Unlike [AlarmBackgroundPreset],
 * a custom image's brightness cannot be hardcoded per-instance, so the user is asked to set this.
 */
const val ALARM_BACKGROUND_IMAGE_IS_DARK_KEY = "alarm_background_image_is_dark"

/**
 * Default "is the custom background image dark" setting — `true`, matching the existing renderer's
 * fixed 35%-alpha black scrim drawn under every custom image (see `SCRIM_ALPHA` in
 * `AlarmActivity.kt`), so the default text color assumption matches what's already drawn until the
 * user says otherwise.
 */
const val DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK = true

/**
 * Domain model representing a rule action.
 *
 * Defines what to do when a rule matches a notification.
 */
@Serializable
data class RuleAction(
    val id: String,
    val type: ActionType,
    val isEnabled: Boolean = true,
    val config: Map<String, String> = emptyMap(),
    /**
     * Extraction fields owned by this action. Only populated for [ActionType.SAVE_DATA] - every
     * other action type leaves this empty. `RuleField` is structured (a sealed `ExtractionMethod`),
     * so it is a first-class property rather than a `config` map entry.
     */
    val fields: List<RuleField> = emptyList(),
) {
    /**
     * Get snooze duration in minutes, or default if not set.
     */
    fun getSnoozeDurationMinutes(): Int = config[SNOOZE_DURATION_MINUTES_KEY]?.toIntOrNull()
        ?: DEFAULT_SNOOZE_DURATION_MINUTES

    /**
     * Get the snooze mode, defaulting to [SnoozeMode.DURATION] if not set or unrecognized - this
     * keeps rules saved/exported before [SnoozeMode.SCHEDULED] existed behaving exactly as before.
     */
    fun getSnoozeMode(): SnoozeMode = config[SNOOZE_MODE_KEY]
        ?.let { raw -> SnoozeMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } }
        ?: SnoozeMode.DURATION

    /**
     * Parse the [SnoozeMode.SCHEDULED] configuration, or null when this action isn't in that mode
     * or its required start-time keys are missing/malformed.
     */
    fun getSnoozeSchedule(): SnoozeSchedule? {
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
    fun getThrottleWindowMinutes(): Int = (
        config[SNOOZE_THROTTLE_WINDOW_MINUTES_KEY]?.toIntOrNull() ?: DEFAULT_SNOOZE_THROTTLE_WINDOW_MINUTES
        ).coerceIn(MIN_SNOOZE_THROTTLE_WINDOW_MINUTES, MAX_SNOOZE_THROTTLE_WINDOW_MINUTES)

    /**
     * Get the throttle reset watermark (epoch millis), or [DEFAULT_SNOOZE_THROTTLE_RESET_AT] if
     * not set or malformed.
     */
    fun getThrottleResetAt(): Long = config[SNOOZE_THROTTLE_RESET_AT_KEY]?.toLongOrNull()
        ?: DEFAULT_SNOOZE_THROTTLE_RESET_AT

    /**
     * Get the configured alarm sound URI, or null to use the device's default alarm sound.
     */
    fun getAlarmSoundUri(): String? = config[ALARM_SOUND_URI_KEY]

    /**
     * Whether the alarm should also vibrate the device, or the default if not set.
     */
    fun isAlarmVibrationEnabled(): Boolean = config[ALARM_VIBRATION_ENABLED_KEY]?.toBooleanStrictOrNull()
        ?: DEFAULT_ALARM_VIBRATION_ENABLED

    /**
     * Whether the alarm shows the full-screen, call-style UI, or the default if not set.
     */
    fun isAlarmFullScreenEnabled(): Boolean = config[ALARM_FULLSCREEN_ENABLED_KEY]?.toBooleanStrictOrNull()
        ?: DEFAULT_ALARM_FULLSCREEN_ENABLED

    /**
     * Get the number of torch flashes, clamped to a photosensitivity-safe range regardless of
     * what's stored in config (defense in depth against a malformed or imported rule).
     */
    fun getFlashCount(): Int = (config[FLASH_COUNT_KEY]?.toIntOrNull() ?: DEFAULT_FLASH_COUNT)
        .coerceIn(MIN_FLASH_COUNT, MAX_FLASH_COUNT)

    /**
     * Get the duration of each flash phase in milliseconds, clamped to a photosensitivity-safe
     * range regardless of what's stored in config.
     */
    fun getFlashDurationMs(): Long = (config[FLASH_DURATION_MS_KEY]?.toLongOrNull() ?: DEFAULT_FLASH_DURATION_MS)
        .coerceIn(MIN_FLASH_DURATION_MS, MAX_FLASH_DURATION_MS)

    /**
     * Whether the alarm plays audio while ringing, or the default if not set.
     */
    fun isAlarmSoundEnabled(): Boolean = config[ALARM_SOUND_ENABLED_KEY]?.toBooleanStrictOrNull()
        ?: DEFAULT_ALARM_SOUND_ENABLED

    /**
     * Get the selected named vibration pattern, falling back to the default pattern when unset
     * or when the stored id doesn't match any known pattern (e.g. a malformed import).
     */
    fun getAlarmVibrationPattern(): VibrationPattern = VibrationPattern.fromId(config[ALARM_VIBRATION_PATTERN_KEY])

    /**
     * Whether the Snooze action is offered while the alarm rings, or the default if not set.
     */
    fun isAlarmSnoozeEnabled(): Boolean = config[ALARM_SNOOZE_ENABLED_KEY]?.toBooleanStrictOrNull()
        ?: DEFAULT_ALARM_SNOOZE_ENABLED

    /**
     * Get the alarm-scoped snooze delay in minutes, clamped to a sane range regardless of what's
     * stored in config (defense in depth against a malformed or imported rule).
     */
    fun getAlarmSnoozeDurationMinutes(): Int = (
        config[ALARM_SNOOZE_DURATION_MINUTES_KEY]?.toIntOrNull() ?: DEFAULT_ALARM_SNOOZE_DURATION_MINUTES
        ).coerceIn(MIN_ALARM_SNOOZE_DURATION_MINUTES, MAX_ALARM_SNOOZE_DURATION_MINUTES)

    /**
     * Get the maximum number of times this alarm may be snoozed, clamped to a sane range
     * regardless of what's stored in config. Legacy rules persisted before this option existed
     * (no key present) resolve to [DEFAULT_ALARM_SNOOZE_MAX_COUNT].
     */
    fun getAlarmSnoozeMaxCount(): Int = (config[ALARM_SNOOZE_MAX_COUNT_KEY]?.toIntOrNull() ?: DEFAULT_ALARM_SNOOZE_MAX_COUNT)
        .coerceIn(MIN_ALARM_SNOOZE_MAX_COUNT, MAX_ALARM_SNOOZE_MAX_COUNT)

    /**
     * Get the alarm full-screen UI background source, falling back to [AlarmBackgroundType.NONE]
     * when unset or when the stored value doesn't match any known type.
     */
    fun getAlarmBackgroundType(): AlarmBackgroundType = AlarmBackgroundType.fromName(config[ALARM_BACKGROUND_TYPE_KEY])

    /**
     * Get the selected background preset's id (meaningful only when [getAlarmBackgroundType] is
     * `PRESET`); resolve it via [AlarmBackgroundPreset.fromId] before rendering.
     */
    fun getAlarmBackgroundPresetId(): String? = config[ALARM_BACKGROUND_PRESET_KEY]

    /**
     * Get the persisted custom background image URI (meaningful only when
     * [getAlarmBackgroundType] is `IMAGE`), or `null` if none was picked.
     */
    fun getAlarmBackgroundImageUri(): String? = config[ALARM_BACKGROUND_IMAGE_URI_KEY]

    /**
     * Whether the custom background image (meaningful only when [getAlarmBackgroundType] is
     * `IMAGE`) is dark, or the default if not set.
     */
    fun isAlarmBackgroundImageDark(): Boolean = config[ALARM_BACKGROUND_IMAGE_IS_DARK_KEY]?.toBooleanStrictOrNull()
        ?: DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK

    companion object {
        /**
         * Create a `SAVE_DATA` ("Extract data") action carrying the given fields.
         */
        fun createSaveData(
            id: String,
            fields: List<RuleField> = emptyList(),
            isEnabled: Boolean = true,
        ): RuleAction = RuleAction(
            id = id,
            type = ActionType.SAVE_DATA,
            isEnabled = isEnabled,
            fields = fields,
        )

        /**
         * Create a snooze action with specified duration.
         */
        fun createSnooze(
            id: String,
            durationMinutes: Int = DEFAULT_SNOOZE_DURATION_MINUTES,
            isEnabled: Boolean = true,
        ): RuleAction = RuleAction(
            id = id,
            type = ActionType.SNOOZE_NOTIFICATION,
            isEnabled = isEnabled,
            config = mapOf(SNOOZE_DURATION_MINUTES_KEY to durationMinutes.toString()),
        )

        /**
         * Create a [SnoozeMode.SCHEDULED] snooze action: batch at specific times or with intervals.
         * @param times list of (hour, minute) pairs for batch-at-time mode.
         * If set, [intervalMinutes] must be null.
         * @param intervalMinutes null means batch-at-time mode (use [times]);
         * non-null means recurring mode (repeat every N minutes until [windowEndHour]:[windowEndMinute]).
         * @param weekdays the days of the week this schedule applies to, for both batch-at-time
         * and recurring mode. Empty means every day.
         */
        fun createScheduledSnooze(
            id: String,
            schedule: SnoozeSchedule,
            isEnabled: Boolean = true,
        ): RuleAction = RuleAction(
            id = id,
            type = ActionType.SNOOZE_NOTIFICATION,
            isEnabled = isEnabled,
            config = buildMap {
                put(SNOOZE_MODE_KEY, SnoozeMode.SCHEDULED.name.lowercase())
                put(SNOOZE_SCHEDULE_START_HOUR_KEY, schedule.startHour.toString())
                put(SNOOZE_SCHEDULE_START_MINUTE_KEY, schedule.startMinute.toString())
                schedule.intervalMinutes?.let { put(SNOOZE_SCHEDULE_INTERVAL_MINUTES_KEY, it.toString()) }
                schedule.windowEndHour?.let { put(SNOOZE_SCHEDULE_WINDOW_END_HOUR_KEY, it.toString()) }
                schedule.windowEndMinute?.let { put(SNOOZE_SCHEDULE_WINDOW_END_MINUTE_KEY, it.toString()) }
                if (schedule.times.isNotEmpty()) {
                    put(SNOOZE_SCHEDULE_TIMES_KEY, schedule.times.joinToString("|") { (h, m) -> "%02d:%02d".format(h, m) })
                }
                if (schedule.weekdays.isNotEmpty()) {
                    put(SNOOZE_SCHEDULE_WEEKDAYS_KEY, schedule.weekdays.joinToString(",") { it.name })
                }
            },
        )

        /**
         * Create a [SnoozeMode.THROTTLE] snooze action: let the first match through, drop the
         * rest until [windowMinutes] elapses. [resetAt] stamps the watermark that invalidates any
         * delivery recorded before it (see [SNOOZE_THROTTLE_RESET_AT_KEY]) - callers pass the
         * current time to force a fresh window on window-duration edit or disable->re-enable, or
         * preserve the prior value otherwise.
         */
        fun createThrottleSnooze(
            id: String,
            windowMinutes: Int = DEFAULT_SNOOZE_THROTTLE_WINDOW_MINUTES,
            resetAt: Long = DEFAULT_SNOOZE_THROTTLE_RESET_AT,
            isEnabled: Boolean = true,
        ): RuleAction = RuleAction(
            id = id,
            type = ActionType.SNOOZE_NOTIFICATION,
            isEnabled = isEnabled,
            config = mapOf(
                SNOOZE_MODE_KEY to SnoozeMode.THROTTLE.name.lowercase(),
                SNOOZE_THROTTLE_WINDOW_MINUTES_KEY to windowMinutes.toString(),
                SNOOZE_THROTTLE_RESET_AT_KEY to resetAt.toString(),
            ),
        )

        /**
         * Create an alarm action with an optional custom sound and vibration setting, plus the
         * extended alarm options ([AlarmOptionsConfig]: sound-enabled, vibration pattern,
         * full-screen, snooze, and background). The extended options - including the legacy
         * `fullScreenEnabled` flag - are grouped into a single value object rather than flattened
         * into individual parameters to keep this factory under detekt's `LongParameterList`
         * function threshold (6).
         */
        fun createAlarm(
            id: String,
            soundUri: String? = null,
            vibrationEnabled: Boolean = DEFAULT_ALARM_VIBRATION_ENABLED,
            isEnabled: Boolean = true,
            options: AlarmOptionsConfig = AlarmOptionsConfig(),
        ): RuleAction = RuleAction(
            id = id,
            type = ActionType.CREATE_ALARM,
            isEnabled = isEnabled,
            config = buildMap {
                soundUri?.let { put(ALARM_SOUND_URI_KEY, it) }
                put(ALARM_VIBRATION_ENABLED_KEY, vibrationEnabled.toString())
                put(ALARM_FULLSCREEN_ENABLED_KEY, options.fullScreenEnabled.toString())
                put(ALARM_SOUND_ENABLED_KEY, options.soundEnabled.toString())
                put(ALARM_VIBRATION_PATTERN_KEY, options.vibrationPattern.id)
                put(ALARM_SNOOZE_ENABLED_KEY, options.snooze.enabled.toString())
                put(ALARM_SNOOZE_DURATION_MINUTES_KEY, options.snooze.durationMinutes.toString())
                put(ALARM_SNOOZE_MAX_COUNT_KEY, options.snooze.maxCount.toString())
                put(ALARM_BACKGROUND_TYPE_KEY, options.background.type.name)
                options.background.presetId?.let { put(ALARM_BACKGROUND_PRESET_KEY, it) }
                options.background.imageUri?.let { put(ALARM_BACKGROUND_IMAGE_URI_KEY, it) }
                put(ALARM_BACKGROUND_IMAGE_IS_DARK_KEY, options.background.imageIsDark.toString())
            },
        )

        /**
         * Create a flash alert action with an optional custom flash count and duration.
         */
        fun createFlashAlert(
            id: String,
            flashCount: Int = DEFAULT_FLASH_COUNT,
            flashDurationMs: Long = DEFAULT_FLASH_DURATION_MS,
            isEnabled: Boolean = true,
        ): RuleAction = RuleAction(
            id = id,
            type = ActionType.FLASH_ALERT,
            isEnabled = isEnabled,
            config = mapOf(
                FLASH_COUNT_KEY to flashCount.toString(),
                FLASH_DURATION_MS_KEY to flashDurationMs.toString(),
            ),
        )
    }
}

/**
 * Type of action to perform when rule matches.
 */
@Serializable
enum class ActionType {
    @SerialName("save_data")
    SAVE_DATA,

    @SerialName("dismiss_notification")
    DISMISS_NOTIFICATION,

    @SerialName("snooze_notification")
    SNOOZE_NOTIFICATION,

    @SerialName("create_alarm")
    CREATE_ALARM,

    @SerialName("flash_alert")
    FLASH_ALERT,
}

/**
 * How a [SNOOZE_NOTIFICATION] [RuleAction] computes its snooze duration.
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
 * Parsed [SnoozeMode.SCHEDULED] configuration for a [SNOOZE_NOTIFICATION] [RuleAction].
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

/** Parses [RuleAction.getSnoozeSchedule]'s comma-separated weekday list; malformed entries are skipped. */
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

/** Parses [RuleAction.getSnoozeSchedule]'s `"HH:mm|HH:mm"` times list; malformed entries are skipped. */
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

/**
 * Grouping value object for the extended `CREATE_ALARM` options ([RuleAction.createAlarm]),
 * including the legacy `fullScreenEnabled` flag, that don't fit as flat parameters without
 * exceeding detekt's `LongParameterList` function threshold (6). [snooze] and [background] are
 * further grouped into their own value objects to keep this constructor comfortably under
 * detekt's separate, more permissive constructor threshold.
 */
data class AlarmOptionsConfig(
    val soundEnabled: Boolean = DEFAULT_ALARM_SOUND_ENABLED,
    val vibrationPattern: VibrationPattern = VibrationPattern.BASIC_CALL,
    val fullScreenEnabled: Boolean = DEFAULT_ALARM_FULLSCREEN_ENABLED,
    val snooze: AlarmSnoozeConfig = AlarmSnoozeConfig(),
    val background: AlarmBackgroundConfig = AlarmBackgroundConfig(),
)

/**
 * Snooze-specific portion of [AlarmOptionsConfig], grouped separately so [AlarmOptionsConfig]'s
 * own constructor parameter count stays comfortably under detekt's threshold.
 */
data class AlarmSnoozeConfig(
    val enabled: Boolean = DEFAULT_ALARM_SNOOZE_ENABLED,
    val durationMinutes: Int = DEFAULT_ALARM_SNOOZE_DURATION_MINUTES,
    val maxCount: Int = DEFAULT_ALARM_SNOOZE_MAX_COUNT,
)

/**
 * Background-specific portion of [AlarmOptionsConfig], grouped separately so
 * [AlarmOptionsConfig]'s own constructor parameter count stays comfortably under detekt's
 * threshold.
 */
data class AlarmBackgroundConfig(
    val type: AlarmBackgroundType = AlarmBackgroundType.NONE,
    val presetId: String? = null,
    val imageUri: String? = null,
    val imageIsDark: Boolean = DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK,
)
