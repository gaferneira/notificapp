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
 * Default full-screen setting for the alarm action — on, so the alarm commands the screen like a
 * call by default; users can switch to notification-only per alarm.
 */
const val DEFAULT_ALARM_FULLSCREEN_ENABLED = true

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
         * Create an alarm action with an optional custom sound and vibration setting.
         */
        fun createAlarm(
            id: String,
            soundUri: String? = null,
            vibrationEnabled: Boolean = DEFAULT_ALARM_VIBRATION_ENABLED,
            fullScreenEnabled: Boolean = DEFAULT_ALARM_FULLSCREEN_ENABLED,
            isEnabled: Boolean = true,
        ): RuleAction = RuleAction(
            id = id,
            type = ActionType.CREATE_ALARM,
            isEnabled = isEnabled,
            config = buildMap {
                soundUri?.let { put(ALARM_SOUND_URI_KEY, it) }
                put(ALARM_VIBRATION_ENABLED_KEY, vibrationEnabled.toString())
                put(ALARM_FULLSCREEN_ENABLED_KEY, fullScreenEnabled.toString())
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
