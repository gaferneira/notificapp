package dev.gaferneira.notificapp.features.ruleeditor.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_FULLSCREEN_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_VIBRATION_ENABLED
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.actionconfig.AlarmOptions
import dev.gaferneira.notificapp.features.ruleeditor.ui.actionconfig.AlarmOptionsSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import java.util.UUID

/**
 * Type-scoped sheet for the Create alarm action. Owns its own sound/vibration/full-screen state.
 *
 * Notification permission is requested when the user commits the action (the "Add" button), not
 * when the sheet opens - the alarm's stop/snooze controls live on the ongoing notification, so the
 * permission matters at the moment the alarm is actually added to the rule.
 *
 * @param initial The action being edited, or null when adding a new one
 */
@Composable
fun AlarmBottomSheet(
    initial: RuleAction?,
    onSave: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var soundUri by remember { mutableStateOf(initial?.getAlarmSoundUri()) }
    var vibrationEnabled by remember {
        mutableStateOf(initial?.isAlarmVibrationEnabled() ?: DEFAULT_ALARM_VIBRATION_ENABLED)
    }
    var fullScreenEnabled by remember {
        mutableStateOf(initial?.isAlarmFullScreenEnabled() ?: DEFAULT_ALARM_FULLSCREEN_ENABLED)
    }

    val requestNotificationPermission = rememberNotificationPermissionRequest()

    ActionConfigSheet(
        title = "Create alarm",
        confirmLabel = confirmLabelFor(isEdit = initial != null),
        onConfirm = {
            // Check/request notification permission at commit time, not on open.
            requestNotificationPermission()
            onSave(
                RuleAction.createAlarm(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    soundUri = soundUri,
                    vibrationEnabled = vibrationEnabled,
                    fullScreenEnabled = fullScreenEnabled,
                    isEnabled = initial?.isEnabled ?: true,
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        ActionSheetDescription(ActionType.CREATE_ALARM.ui().description)
        AlarmOptionsSelector(
            options = AlarmOptions(
                soundUri = soundUri,
                vibrationEnabled = vibrationEnabled,
                fullScreenEnabled = fullScreenEnabled,
            ),
            onSoundChange = { soundUri = it },
            onVibrationToggle = { vibrationEnabled = it },
            onFullScreenToggle = { fullScreenEnabled = it },
        )
    }
}

/**
 * Returns a callback that requests `POST_NOTIFICATIONS` when it is missing (Android 13+). On older
 * versions the permission is granted at install, so the callback is a no-op.
 */
@Composable
private fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result surfaced via the in-sheet warning on recomposition */ }

    return {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
