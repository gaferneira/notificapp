package dev.gaferneira.notificapp.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Domain model representing a rule action.
 *
 * Defines what to do when a rule matches a notification. Per-action-type config readers live in
 * sibling files as extension functions ([snoozeConfig] readers in `SnoozeActionConfig.kt`, alarm
 * readers in `AlarmActionConfig.kt`, flash readers in `FlashActionConfig.kt`) so this class stays
 * the shared model + constructors, not a growing surface of every action type's accessors.
 */
@Immutable
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
     *
     * `@Transient` (kotlinx.serialization): [RuleAction] is never encoded/decoded as a whole via
     * kotlinx.serialization directly - only through the `core/rulesharing/dto` DTO layer, which has
     * its own `List<FieldDto>` property. Marking this `@Transient` avoids requiring a `KSerializer`
     * for `ImmutableList<RuleField>` that would otherwise never be used.
     */
    @Transient
    val fields: ImmutableList<RuleField> = persistentListOf(),
) {
    companion object {
        /**
         * Create a `SAVE_DATA` ("Extract data") action carrying the given fields.
         */
        fun createSaveData(
            id: String,
            fields: ImmutableList<RuleField> = persistentListOf(),
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
