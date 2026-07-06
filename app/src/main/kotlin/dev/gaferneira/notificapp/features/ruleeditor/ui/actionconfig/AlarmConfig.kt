package dev.gaferneira.notificapp.features.ruleeditor.ui.actionconfig

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import dev.gaferneira.notificapp.R

/**
 * Composable for configuring the alarm action: picking an alarm sound via the system ringtone
 * picker and toggling vibration and the full-screen call-style UI.
 *
 * @param options Current alarm configuration (sound, vibration, full-screen)
 * @param onSoundChange Callback when a new sound is picked (null if the user picked the default)
 * @param onVibrationToggle Callback when the vibration toggle changes
 * @param onFullScreenToggle Callback when the full-screen (call-style) toggle changes
 * @param modifier Modifier for the component
 */
@Composable
fun AlarmOptionsSelector(
    options: AlarmOptions,
    onSoundChange: (String?) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
    onFullScreenToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = "Alarm options",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(12.dp))

        AlarmNotificationPermissionGate()

        AlarmSoundPickerButton(soundUri = options.soundUri, onSoundChange = onSoundChange)

        Spacer(modifier = Modifier.height(12.dp))

        AlarmToggleRow(
            label = "Vibrate",
            checked = options.vibrationEnabled,
            onCheckedChange = onVibrationToggle,
        )

        Spacer(modifier = Modifier.height(4.dp))

        AlarmToggleRow(
            label = "Full-screen alarm (call style)",
            checked = options.fullScreenEnabled,
            onCheckedChange = onFullScreenToggle,
        )

        if (options.fullScreenEnabled) {
            FullScreenIntentPermissionHint()
        }
    }
}

/**
 * Warns when the full-screen alarm is enabled but the app cannot use full-screen intents (Android
 * 14+ restricts `USE_FULL_SCREEN_INTENT`). Without the grant, the OS downgrades the call-style
 * screen to a plain notification, so the toggle appears to do nothing. Routes to the system grant
 * screen. On Android 13 and below the permission is granted at install, so nothing is shown.
 */
@Composable
private fun FullScreenIntentPermissionHint(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val context = LocalContext.current
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    if (notificationManager?.canUseFullScreenIntent() == true) return

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.alarm_fullscreen_permission_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(Uri.fromParts("package", context.packageName, null)),
                )
            },
        ) {
            Text(stringResource(R.string.alarm_fullscreen_permission_grant))
        }
    }
}

/**
 * A labeled switch row used for the alarm's on/off options (vibration, full-screen).
 */
@Composable
private fun AlarmToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Requests notification permission when the alarm action is configured, and warns the user when it
 * is missing. The alarm's Dismiss/Snooze controls live on the ongoing notification, so without this
 * permission the alarm is refused at trigger time (it would otherwise be unstoppable).
 */
@Composable
private fun AlarmNotificationPermissionGate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // Ask up front when the alarm action is selected (Android 13+). On older versions the
    // permission is granted at install, so there is nothing to request.
    LaunchedEffect(Unit) {
        if (!notificationsEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (notificationsEnabled) return

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            text = stringResource(R.string.alarm_permission_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            },
        ) {
            Text(stringResource(R.string.alarm_permission_grant))
        }
    }
}

/**
 * Button that opens the system ringtone picker (scoped to alarm sounds) and reports the picked
 * sound URI back, or null if the user picked the device default.
 */
@Composable
private fun AlarmSoundPickerButton(
    soundUri: String?,
    onSoundChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pickedUri = result.data?.let { data ->
            IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        onSoundChange(pickedUri?.toString())
    }

    val soundTitle = remember(soundUri) {
        val uri = soundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        // getRingtone/getTitle touch the content resolver and can throw SecurityException on some
        // devices for a URI this app no longer has access to (e.g. a picked ringtone whose
        // permission grant lapsed) - fall back to the default label rather than crashing.
        uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
            ?: "Default alarm sound"
    }

    OutlinedButton(
        onClick = {
            val currentUri = soundUri?.let(Uri::parse)
                ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            }
            soundPickerLauncher.launch(intent)
        },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(soundTitle)
    }
}
