package dev.gaferneira.notificapp.features.ruleeditor.ui.actionconfig

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat

/**
 * Composable for configuring the alarm action: picking an alarm sound via the system ringtone
 * picker and toggling vibration.
 *
 * @param soundUri Currently selected alarm sound URI, or null for the device default
 * @param vibrationEnabled Whether vibration is currently enabled
 * @param onSoundChange Callback when a new sound is picked (null if the user picked the default)
 * @param onVibrationToggle Callback when the vibration toggle changes
 * @param modifier Modifier for the component
 */
@Composable
fun AlarmOptionsSelector(
    soundUri: String?,
    vibrationEnabled: Boolean,
    onSoundChange: (String?) -> Unit,
    onVibrationToggle: (Boolean) -> Unit,
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

        AlarmSoundPickerButton(soundUri = soundUri, onSoundChange = onSoundChange)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Vibrate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = vibrationEnabled,
                onCheckedChange = onVibrationToggle,
            )
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
        uri?.let { RingtoneManager.getRingtone(context, it)?.getTitle(context) } ?: "Default alarm sound"
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
