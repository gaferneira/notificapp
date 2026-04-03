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
