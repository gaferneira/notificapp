package dev.gaferneira.notificapp.features.ruleeditor.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gaferneira.notificapp.domain.model.ActionType

/**
 * UI metadata for an [ActionType]: label, one-line description, and icon. Single source of truth
 * shared by the action type-picker dialog and the Do-section action cards, so the wording and
 * iconography stay consistent. `SAVE_DATA` is presented to users as "Extract data".
 */
data class ActionTypeUi(
    val type: ActionType,
    val label: String,
    val description: String,
    val icon: ImageVector,
)

fun ActionType.ui(): ActionTypeUi = when (this) {
    ActionType.SAVE_DATA -> ActionTypeUi(
        type = this,
        label = "Extract data",
        description = "Extract and store data fields from the notification",
        icon = Icons.Default.Save,
    )
    ActionType.CREATE_ALARM -> ActionTypeUi(
        type = this,
        label = "Create alarm",
        description = "Play an alarm sound and vibrate when this rule matches",
        icon = Icons.Default.Alarm,
    )
    ActionType.DISMISS_NOTIFICATION -> ActionTypeUi(
        type = this,
        label = "Dismiss notification",
        description = "Dismiss the notification after processing",
        icon = Icons.Default.Delete,
    )
    ActionType.SNOOZE_NOTIFICATION -> ActionTypeUi(
        type = this,
        label = "Snooze notification",
        description = "Temporarily dismiss and remind later",
        icon = Icons.Default.NotificationsPaused,
    )
    ActionType.FLASH_ALERT -> ActionTypeUi(
        type = this,
        label = "Flash alert",
        description = "Blink the camera flash when this rule matches",
        icon = Icons.Default.FlashOn,
    )
    ActionType.SEND_WEBHOOK -> ActionTypeUi(
        type = this,
        label = "Send webhook",
        description = "POST notification data to a configured webhook",
        icon = Icons.Default.Send,
    )
}

/**
 * Action types not yet configured on the rule — i.e. what the type-picker dialog should offer,
 * enforcing at most one action per type.
 */
fun availableActionTypes(configured: List<ActionType>): List<ActionType> = ActionType.entries.filter { it !in configured }
