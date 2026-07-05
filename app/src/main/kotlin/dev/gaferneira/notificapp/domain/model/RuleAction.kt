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

    companion object {
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
            isEnabled: Boolean = true,
        ): RuleAction = RuleAction(
            id = id,
            type = ActionType.CREATE_ALARM,
            isEnabled = isEnabled,
            config = buildMap {
                soundUri?.let { put(ALARM_SOUND_URI_KEY, it) }
                put(ALARM_VIBRATION_ENABLED_KEY, vibrationEnabled.toString())
            },
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
}
