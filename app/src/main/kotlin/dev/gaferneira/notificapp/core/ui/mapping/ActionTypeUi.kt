package dev.gaferneira.notificapp.core.ui.mapping

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.domain.model.ActionType

/**
 * UI metadata for an [ActionType]: label and one-line description (as string resources, resolved with
 * `stringResource` at the point they're rendered), and icon. Single source of truth
 * shared by the action type-picker dialog and the Do-section action cards, so the wording and
 * iconography stay consistent. `SAVE_DATA` is presented to users as "Extract data".
 */
data class ActionTypeUi(
    val type: ActionType,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
)

fun ActionType.ui(): ActionTypeUi = when (this) {
    ActionType.SAVE_DATA -> ActionTypeUi(
        type = this,
        labelRes = R.string.action_type_save_data_label,
        descriptionRes = R.string.action_type_save_data_description,
        icon = Icons.Default.Save,
    )
    ActionType.CREATE_ALARM -> ActionTypeUi(
        type = this,
        labelRes = R.string.action_type_create_alarm_label,
        descriptionRes = R.string.action_type_create_alarm_description,
        icon = Icons.Default.Alarm,
    )
    ActionType.DISMISS_NOTIFICATION -> ActionTypeUi(
        type = this,
        labelRes = R.string.action_type_dismiss_label,
        descriptionRes = R.string.action_type_dismiss_description,
        icon = Icons.Default.Delete,
    )
    ActionType.SNOOZE_NOTIFICATION -> ActionTypeUi(
        type = this,
        labelRes = R.string.action_type_snooze_label,
        descriptionRes = R.string.action_type_snooze_description,
        icon = Icons.Default.NotificationsPaused,
    )
    ActionType.FLASH_ALERT -> ActionTypeUi(
        type = this,
        labelRes = R.string.action_type_flash_label,
        descriptionRes = R.string.action_type_flash_description,
        icon = Icons.Default.FlashOn,
    )
    ActionType.SEND_WEBHOOK -> ActionTypeUi(
        type = this,
        labelRes = R.string.action_type_webhook_label,
        descriptionRes = R.string.action_type_webhook_description,
        icon = Icons.Default.Send,
    )
    ActionType.READ_ALOUD -> ActionTypeUi(
        type = this,
        labelRes = R.string.action_type_read_aloud_label,
        descriptionRes = R.string.action_type_read_aloud_description,
        icon = Icons.Default.RecordVoiceOver,
    )
    ActionType.SEND_REPLY -> ActionTypeUi(
        type = this,
        labelRes = R.string.action_type_send_reply_label,
        descriptionRes = R.string.action_type_send_reply_description,
        icon = Icons.AutoMirrored.Filled.Reply,
    )
}

/**
 * Action types not yet configured on the rule — i.e. what the type-picker dialog should offer,
 * enforcing at most one action per type.
 */
fun availableActionTypes(configured: List<ActionType>): List<ActionType> = ActionType.entries.filter { it !in configured }
