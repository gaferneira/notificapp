package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import coil.compose.SubcomposeAsyncImage
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.utils.toBrush
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundPreset
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.MAX_ALARM_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.MAX_ALARM_SNOOZE_MAX_COUNT
import dev.gaferneira.notificapp.domain.model.MIN_ALARM_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.MIN_ALARM_SNOOZE_MAX_COUNT
import dev.gaferneira.notificapp.domain.model.VibrationPattern
import dev.gaferneira.notificapp.features.ruleeditor.contract.AlarmContract
import dev.gaferneira.notificapp.features.ruleeditor.domain.AlarmOptions

@Composable
internal fun VibrationRow(
    options: AlarmOptions,
    onEvent: (AlarmContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPickerVisible by remember { mutableStateOf(false) }

    AlarmValueRow(
        title = stringResource(R.string.alarm_vibration_label),
        subtitle = if (options.vibrationEnabled) options.vibrationPattern.displayName() else stringResource(R.string.alarm_off_label),
        toggle = AlarmRowToggle(
            checked = options.vibrationEnabled,
            onCheckedChange = { onEvent(AlarmContract.UiEvent.OnVibrationToggle(it)) },
        ),
        onClick = { isPickerVisible = true },
        modifier = modifier,
    )

    if (isPickerVisible) {
        VibrationPatternPickerDialog(
            selected = options.vibrationPattern,
            onSelect = { pattern ->
                onEvent(AlarmContract.UiEvent.OnVibrationPatternChange(pattern))
                isPickerVisible = false
            },
            onDismiss = { isPickerVisible = false },
        )
    }
}

@Composable
internal fun SnoozeRow(
    options: AlarmOptions,
    onEvent: (AlarmContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        AlarmValueRow(
            title = stringResource(R.string.alarm_snooze_label),
            subtitle = if (options.snoozeEnabled) {
                stringResource(R.string.alarm_snooze_subtitle, options.snoozeDurationMinutes, options.snoozeMaxCount)
            } else {
                stringResource(R.string.alarm_off_label)
            },
            toggle = AlarmRowToggle(
                checked = options.snoozeEnabled,
                onCheckedChange = { onEvent(AlarmContract.UiEvent.OnSnoozeToggle(it)) },
            ),
        )

        if (options.snoozeEnabled) {
            SnoozeSteppers(options = options, onEvent = onEvent)
        }
    }
}

@Composable
internal fun SoundRow(
    options: AlarmOptions,
    onEvent: (AlarmContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val soundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val pickedUri = result.data?.let { data ->
            IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        }
        onEvent(AlarmContract.UiEvent.OnSoundChange(pickedUri?.toString()))
    }

    val defaultSoundLabel = stringResource(R.string.alarm_sound_default)
    val soundTitle = remember(options.soundUri, defaultSoundLabel) { alarmSoundTitle(context, options.soundUri, defaultSoundLabel) }

    AlarmValueRow(
        title = stringResource(R.string.alarm_sound_label),
        subtitle = if (options.soundEnabled) soundTitle else stringResource(R.string.alarm_off_label),
        toggle = AlarmRowToggle(
            checked = options.soundEnabled,
            onCheckedChange = { onEvent(AlarmContract.UiEvent.OnSoundToggle(it)) },
        ),
        onClick = {
            if (!options.soundEnabled) return@AlarmValueRow
            val currentUri = options.soundUri?.let(Uri::parse)
                ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            }
            soundPickerLauncher.launch(intent)
        },
        modifier = modifier,
    )
}

/**
 * Resolves the display title for a picked (or default) alarm sound URI. `getRingtone`/`getTitle`
 * touch the content resolver and can throw `SecurityException` on some devices for a URI this app
 * no longer has access to (e.g. a picked ringtone whose permission grant lapsed) - fall back to
 * the default label rather than crashing.
 */
private fun alarmSoundTitle(context: Context, soundUri: String?, defaultLabel: String): String {
    val uri = soundUri?.let(Uri::parse)
        ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
    return uri?.let { runCatching { RingtoneManager.getRingtone(context, it)?.getTitle(context) }.getOrNull() }
        ?: defaultLabel
}

/** Human-readable label for a [VibrationPattern], kept local to the UI since domain stays flat. */
@Composable
private fun VibrationPattern.displayName(): String = when (this) {
    VibrationPattern.BASIC_CALL -> stringResource(R.string.alarm_vibration_pattern_basic_call)
    VibrationPattern.PULSE -> stringResource(R.string.alarm_vibration_pattern_pulse)
    VibrationPattern.LONG -> stringResource(R.string.alarm_vibration_pattern_long)
}

@Composable
private fun VibrationPatternPickerDialog(
    selected: VibrationPattern,
    onSelect: (VibrationPattern) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.alarm_vibration_pattern_dialog_title)) },
        text = {
            Column {
                VibrationPattern.entries.forEach { pattern ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(pattern) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = pattern == selected, onClick = { onSelect(pattern) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(pattern.displayName())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.alarm_dialog_close)) }
        },
    )
}

@Composable
private fun SnoozeSteppers(
    options: AlarmOptions,
    onEvent: (AlarmContract.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        NumberStepper(
            label = stringResource(R.string.alarm_snooze_delay_label),
            valueLabel = stringResource(R.string.alarm_snooze_delay_value, options.snoozeDurationMinutes),
            onDecrement = {
                onEvent(
                    AlarmContract.UiEvent.OnSnoozeDurationChange(
                        (options.snoozeDurationMinutes - 1).coerceIn(MIN_ALARM_SNOOZE_DURATION_MINUTES, MAX_ALARM_SNOOZE_DURATION_MINUTES),
                    ),
                )
            },
            onIncrement = {
                onEvent(
                    AlarmContract.UiEvent.OnSnoozeDurationChange(
                        (options.snoozeDurationMinutes + 1).coerceIn(MIN_ALARM_SNOOZE_DURATION_MINUTES, MAX_ALARM_SNOOZE_DURATION_MINUTES),
                    ),
                )
            },
        )
        Spacer(modifier = Modifier.height(4.dp))
        NumberStepper(
            label = stringResource(R.string.alarm_snooze_max_count_label),
            valueLabel = "${options.snoozeMaxCount}",
            onDecrement = {
                onEvent(
                    AlarmContract.UiEvent.OnSnoozeMaxCountChange(
                        (options.snoozeMaxCount - 1).coerceIn(MIN_ALARM_SNOOZE_MAX_COUNT, MAX_ALARM_SNOOZE_MAX_COUNT),
                    ),
                )
            },
            onIncrement = {
                onEvent(
                    AlarmContract.UiEvent.OnSnoozeMaxCountChange(
                        (options.snoozeMaxCount + 1).coerceIn(MIN_ALARM_SNOOZE_MAX_COUNT, MAX_ALARM_SNOOZE_MAX_COUNT),
                    ),
                )
            },
        )
    }
}

@Composable
private fun NumberStepper(
    label: String,
    valueLabel: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = stringResource(R.string.alarm_stepper_decrease_cd, label))
            }
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(56.dp),
            )
            IconButton(onClick = onIncrement) {
                Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(R.string.alarm_stepper_increase_cd, label))
            }
        }
    }
}

/**
 * Preset swatch grid + a trailing image swatch, shown only while full-screen is enabled. The image
 * swatch shows a "+" until an image is picked, then previews it in place of the icon. Picking is
 * the caller's ([dev.gaferneira.notificapp.features.ruleeditor.ui.AlarmBottomSheet]) concern since
 * it owns the picked-but-unsaved URI state; this section only offers the pick action and renders
 * [backgroundImageUri] once known.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlarmBackgroundSection(
    options: AlarmOptions,
    onBackgroundPresetSelected: (AlarmBackgroundPreset) -> Unit,
    onChooseImageClicked: () -> Unit,
    onImageIsDarkToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedPreset = if (options.backgroundType == AlarmBackgroundType.PRESET) {
        AlarmBackgroundPreset.fromId(options.backgroundPresetId)
    } else {
        null
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = stringResource(R.string.alarm_background_label),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AlarmBackgroundPreset.entries.forEach { preset ->
                AlarmPresetSwatch(
                    preset = preset,
                    isSelected = preset == selectedPreset,
                    onClick = { onBackgroundPresetSelected(preset) },
                )
            }

            AlarmImagePickerSwatch(
                imageUri = options.backgroundImageUri,
                isSelected = options.backgroundType == AlarmBackgroundType.IMAGE,
                onClick = onChooseImageClicked,
            )
        }

        if (options.backgroundType == AlarmBackgroundType.IMAGE) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = options.backgroundImageIsDark, onCheckedChange = onImageIsDarkToggle)
                Text(
                    text = stringResource(R.string.alarm_background_image_dark_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Trailing swatch in the background row: a "+" circle that becomes an image preview once
 * [imageUri] is picked. Tapping it always relaunches the picker, whether adding or changing.
 */
@Composable
private fun AlarmImagePickerSwatch(
    imageUri: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderModifier = if (isSelected) {
        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(if (isSelected) 42.dp else 36.dp)
            .then(borderModifier)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUri != null) {
            SubcomposeAsyncImage(
                model = imageUri,
                contentDescription = stringResource(R.string.alarm_background_image_cd),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.alarm_background_choose_image_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlarmPresetSwatch(
    preset: AlarmBackgroundPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderModifier = if (isSelected) {
        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(3.dp)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .size(if (isSelected) 42.dp else 36.dp)
            .then(borderModifier)
            .clip(CircleShape)
            .background(preset.toBrush())
            .clickable(onClick = onClick),
    )
}

/**
 * Warns when the full-screen alarm is enabled but the app cannot use full-screen intents (Android
 * 14+ restricts `USE_FULL_SCREEN_INTENT`). Without the grant, the OS downgrades the call-style
 * screen to a plain notification, so the toggle appears to do nothing. Routes to the system grant
 * screen. On Android 13 and below the permission is granted at install, so nothing is shown.
 */
@Composable
internal fun FullScreenIntentPermissionHint(modifier: Modifier = Modifier) {
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
 * A labeled switch row used for the alarm's on/off options (full-screen).
 */
@Composable
internal fun AlarmToggleRow(
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
 * A row's toggle state, grouped so [AlarmValueRow] stays under detekt's `LongParameterList`
 * threshold (6).
 */
private data class AlarmRowToggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit)

/**
 * Value-summarizing row for Sound/Vibration/Snooze: bold title + colored subtitle showing the
 * current value + trailing switch. The whole row is tappable to open its picker; the switch
 * toggles the feature on/off independently.
 */
@Composable
private fun AlarmValueRow(
    title: String,
    subtitle: String,
    toggle: AlarmRowToggle,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Switch(
            checked = toggle.checked,
            onCheckedChange = toggle.onCheckedChange,
        )
    }
}

/**
 * Warns the user when notification permission is missing and offers a manual grant. The alarm's
 * Dismiss/Snooze controls live on the ongoing notification, so without this permission the alarm is
 * refused at trigger time (it would otherwise be unstoppable). The permission is *requested* when
 * the user commits the alarm action (the sheet's "Add" button), not when this warning renders.
 */
@Composable
internal fun AlarmNotificationPermissionGate(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
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
