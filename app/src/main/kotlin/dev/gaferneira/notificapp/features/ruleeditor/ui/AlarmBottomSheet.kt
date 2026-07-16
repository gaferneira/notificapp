package dev.gaferneira.notificapp.features.ruleeditor.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmRequest
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmRingOptions
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmService
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmSnoozeSettings
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundConfig
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.VibrationPattern
import dev.gaferneira.notificapp.features.alarm.ui.AlarmActivity
import dev.gaferneira.notificapp.features.ruleeditor.contract.AlarmContract
import dev.gaferneira.notificapp.features.ruleeditor.domain.AlarmOptions
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AdvancedSettingsSection
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AlarmBackgroundSection
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AlarmNotificationPermissionGate
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AlarmToggleRow
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.CooldownSecondsSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.FullScreenIntentPermissionHint
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.SnoozeRow
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.SoundRow
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.VibrationRow
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.AlarmViewModel
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Type-scoped sheet for the Create alarm action. State lives in [AlarmViewModel]
 *
 * Notification permission is requested when the user commits the action (the "Add" button), not
 * when the sheet opens - the alarm's stop/snooze controls live on the ongoing notification, so the
 * permission matters at the moment the alarm is actually added to the rule.
 *
 * @param initial The action being edited, or null when adding a new one
 * alarm action other than this one" - backed by `RuleEditorViewModel`/`RuleRepository`.
 */
@Composable
fun AlarmBottomSheet(
    initial: RuleAction?,
    onSave: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
    viewModel: AlarmViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val requestNotificationPermission = rememberNotificationPermissionRequest()
    val imagePickerLauncher = rememberAlarmImagePickerLauncher(viewModel, context)

    LaunchedEffect(Unit) { viewModel.onEvent(AlarmContract.UiEvent.Initialize(initial)) }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is AlarmContract.UiEffect.ReleaseImageUri -> releaseUriPermissionQuietly(context, effect.uri)
            is AlarmContract.UiEffect.ConfirmSave -> {
                coroutineScope.launch {
                    requestNotificationPermission()
                    val oldUri = effect.oldUriToCheck
                    if (oldUri != null && !viewModel.isImageUriReferencedByOtherAlarmAction(oldUri, uiState.actionId)) {
                        releaseUriPermissionQuietly(context, oldUri)
                    }
                    onSave(effect.action)
                }
            }
        }
    }

    ActionConfigSheet(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        title = "Create alarm",
        confirmLabel = confirmLabelFor(isEdit = initial != null),
        onConfirm = { viewModel.onEvent(AlarmContract.UiEvent.OnConfirmClicked) },
        onDismiss = {
            viewModel.onEvent(AlarmContract.UiEvent.OnSheetDismissed)
            onDismiss()
        },
    ) {
        ActionSheetDescription(ActionType.CREATE_ALARM.ui().description)
        AlarmOptionsSelector(
            options = uiState.toOptions(),
            onEvent = viewModel::onEvent,
            onChooseImageClicked = { imagePickerLauncher.launch(arrayOf("image/*")) },
        )

        Spacer(modifier = Modifier.height(16.dp))

        val simulateTitle = stringResource(R.string.alarm_simulate_title)
        val simulateText = stringResource(R.string.alarm_simulate_text)
        OutlinedButton(
            onClick = {
                coroutineScope.launch {
                    requestNotificationPermission()
                    val options = uiState.toOptions()
                    val request = options.toSimulationRequest(title = simulateTitle, text = simulateText)
                    launchSimulation(context, options, request)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.alarm_simulate_button))
        }
    }
}

/**
 * Starts the "Simulate alarm" preview for [request]. The system only honors a real full-screen
 * intent while the device is locked/off; from here (unlocked, in the sheet) it would just
 * downgrade to a heads-up notification, so the preview launches [AlarmActivity] directly instead
 * of relying on that gating. The service is still started (with the notification suppressed,
 * since the Activity is already showing the preview) so `AlarmStateHolder.isRinging` becomes true
 * - otherwise the Activity's `if (!ringing) finish()` closes it immediately.
 */
private fun launchSimulation(context: Context, options: AlarmOptions, request: AlarmRequest) {
    if (options.fullScreenEnabled) {
        val suppressedRequest = request.copy(options = request.options.copy(suppressNotification = true))
        context.startService(AlarmService.startIntent(context, suppressedRequest))
        context.startActivity(AlarmActivity.intent(context, suppressedRequest))
    } else {
        ContextCompat.startForegroundService(context, AlarmService.startIntent(context, request))
    }
}

/** Builds a ringable [AlarmRequest] straight from the sheet's current, unsaved [AlarmOptions] for the "Simulate alarm" button. */
private fun AlarmOptions.toSimulationRequest(title: String, text: String): AlarmRequest = AlarmRequest(
    soundUri = soundUri,
    title = title,
    text = text,
    appName = "",
    options = AlarmRingOptions(
        vibrationEnabled = vibrationEnabled,
        fullScreenEnabled = fullScreenEnabled,
        soundEnabled = soundEnabled,
        vibrationPattern = vibrationPattern,
        snooze = AlarmSnoozeSettings(
            enabled = snoozeEnabled,
            durationMinutes = snoozeDurationMinutes,
            maxCount = snoozeMaxCount,
        ),
        background = AlarmBackgroundConfig(
            type = backgroundType,
            presetId = backgroundPresetId,
            imageUri = backgroundImageUri,
            imageIsDark = backgroundImageIsDark,
        ),
    ),
)

/**
 * Composable for configuring the alarm action: a card of value-summarizing rows for Sound,
 * Vibration and Snooze (bold title + colored subtitle + trailing switch, tap to open a picker),
 * plus the full-screen toggle and its background section.
 *
 * @param options Current alarm configuration
 * @param onEvent Dispatches user interactions as [AlarmContract.UiEvent]s
 * @param onChooseImageClicked Callback when the "Choose image" button is tapped - launches the
 * system document picker, which needs [Context] and so cannot be an [AlarmContract.UiEvent].
 * @param modifier Modifier for the component
 */
@Composable
fun AlarmOptionsSelector(
    options: AlarmOptions,
    onEvent: (AlarmContract.UiEvent) -> Unit,
    onChooseImageClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.alarm_options_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (LocalInspectionMode.current.not()) {
                AlarmNotificationPermissionGate()
            }

            SoundRow(options = options, onEvent = onEvent)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            VibrationRow(options = options, onEvent = onEvent)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            AlarmToggleRow(
                label = "Full-screen alarm (when screen is off)",
                checked = options.fullScreenEnabled,
                onCheckedChange = { onEvent(AlarmContract.UiEvent.OnFullScreenToggle(it)) },
            )

            if (options.fullScreenEnabled) {
                FullScreenIntentPermissionHint()
                AlarmBackgroundSection(
                    options = options,
                    onBackgroundPresetSelected = { onEvent(AlarmContract.UiEvent.OnBackgroundPresetSelected(it)) },
                    onChooseImageClicked = onChooseImageClicked,
                    onImageIsDarkToggle = { onEvent(AlarmContract.UiEvent.OnBackgroundImageIsDarkToggle(it)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AdvancedSettingsSection {
            SnoozeRow(options = options, onEvent = onEvent)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            CooldownSecondsSelector(
                selectedSeconds = options.cooldownSeconds,
                onSecondsChange = { onEvent(AlarmContract.UiEvent.OnCooldownSecondsChange(it)) },
            )
        }
    }
}

/**
 * Launches the system document picker for a custom background image, taking the persistable read
 * grant immediately on pick (per the design's URI-permission lifecycle). Only the grant itself is
 * handled here (needs [Context])
 */
@Composable
private fun rememberAlarmImagePickerLauncher(
    viewModel: AlarmViewModel,
    context: Context,
): ActivityResultLauncher<Array<String>> = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
) { uri ->
    // A null uri means the user cancelled the picker.
    if (uri == null) return@rememberLauncherForActivityResult

    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    viewModel.onEvent(AlarmContract.UiEvent.OnImagePicked(uri.toString()))
}

/** Best-effort grant release: the grant may already be gone (e.g. released by another flow). */
private fun releaseUriPermissionQuietly(context: Context, uri: String) {
    runCatching {
        context.contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/**
 * Returns a suspend callback that requests `POST_NOTIFICATIONS` when it is missing (Android 13+) and
 * suspends until the user answers the system dialog. On older versions, or when the permission is
 * already granted, it returns immediately. Callers that act on notification state right after calling
 * this (e.g. starting the alarm) must await it instead of firing it and continuing synchronously -
 * otherwise they race the still-open system dialog.
 */
@Composable
private fun rememberNotificationPermissionRequest(): suspend () -> Unit {
    val context = LocalContext.current
    var pendingResult by remember { mutableStateOf<CancellableContinuation<Unit>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        pendingResult?.resume(Unit)
        pendingResult = null
    }

    return {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                pendingResult = continuation
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Preview(showBackground = true, name = "Full-screen enabled")
@Preview(showBackground = true, name = "Full-screen enabled - Dark", uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlarmOptionsSelectorPreview() {
    NotificappTheme {
        AlarmOptionsSelector(
            options = AlarmContract.UiState(
                fullScreenEnabled = true,
                backgroundType = AlarmBackgroundType.PRESET,
                backgroundPresetId = "blue_gradient",
            ).toOptions(),
            onEvent = {},
            onChooseImageClicked = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, name = "Pulse Vibration")
@Composable
private fun AlarmOptionsSelectorPulsePreview() {
    NotificappTheme {
        AlarmOptionsSelector(
            options = AlarmContract.UiState(
                vibrationPattern = VibrationPattern.PULSE,
            ).toOptions(),
            onEvent = {},
            onChooseImageClicked = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
