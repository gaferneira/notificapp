package dev.gaferneira.notificapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
)

/**
 * Type of action to perform when rule matches.
 */
@Serializable
enum class ActionType {
    @SerialName("save_data")
    SAVE_DATA,

    @SerialName("delete_notification")
    DELETE_NOTIFICATION,

    @SerialName("create_alarm")
    CREATE_ALARM,
}
