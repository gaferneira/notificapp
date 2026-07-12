package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_FULLSCREEN_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_MAX_COUNT
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SOUND_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_VIBRATION_ENABLED
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.VibrationPattern
import dev.gaferneira.notificapp.domain.model.getAlarmBackgroundImageUri
import dev.gaferneira.notificapp.domain.model.getAlarmBackgroundPresetId
import dev.gaferneira.notificapp.domain.model.getAlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.getAlarmSnoozeDurationMinutes
import dev.gaferneira.notificapp.domain.model.getAlarmSnoozeMaxCount
import dev.gaferneira.notificapp.domain.model.getAlarmSoundUri
import dev.gaferneira.notificapp.domain.model.getAlarmVibrationPattern
import dev.gaferneira.notificapp.domain.model.isAlarmBackgroundImageDark
import dev.gaferneira.notificapp.domain.model.isAlarmFullScreenEnabled
import dev.gaferneira.notificapp.domain.model.isAlarmSnoozeEnabled
import dev.gaferneira.notificapp.domain.model.isAlarmSoundEnabled
import dev.gaferneira.notificapp.domain.model.isAlarmVibrationEnabled
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.AlarmContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.AlarmContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.AlarmContract.UiState
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for [dev.gaferneira.notificapp.features.ruleeditor.ui.AlarmBottomSheet].
 *
 * Full Contract MVI per ADR 001/002.
 */
@HiltViewModel
class AlarmViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Initialize -> initialize(event.initial)
            is UiEvent.OnBackgroundPresetSelected -> setState {
                copy(backgroundType = AlarmBackgroundType.PRESET, backgroundPresetId = event.preset.id)
            }
            is UiEvent.OnImagePicked -> onImagePicked(event.uri)
            is UiEvent.OnConfirmClicked -> onConfirmClicked()
            is UiEvent.OnSheetDismissed -> onSheetDismissed()
            else -> setState { applyFieldChange(event) }
        }
    }

    /**
     * Handles the remaining events that only copy a single field, kept out of [onEvent] so its
     * `when` stays comfortably under detekt's `CyclomaticComplexMethod` threshold (15).
     */
    private fun UiState.applyFieldChange(event: UiEvent): UiState = when (event) {
        is UiEvent.OnSoundToggle -> copy(soundEnabled = event.enabled)
        is UiEvent.OnSoundChange -> copy(soundUri = event.uri)
        is UiEvent.OnVibrationToggle -> copy(vibrationEnabled = event.enabled)
        is UiEvent.OnVibrationPatternChange -> copy(vibrationPattern = event.pattern)
        is UiEvent.OnFullScreenToggle -> copy(fullScreenEnabled = event.enabled)
        is UiEvent.OnSnoozeToggle -> copy(snoozeEnabled = event.enabled)
        is UiEvent.OnSnoozeDurationChange -> copy(snoozeDurationMinutes = event.minutes)
        is UiEvent.OnSnoozeMaxCountChange -> copy(snoozeMaxCount = event.count)
        is UiEvent.OnBackgroundImageIsDarkToggle -> copy(backgroundImageIsDark = event.isDark)
        else -> this
    }

    private fun initialize(initial: RuleAction?) {
        setState {
            UiState(
                actionId = initial?.id ?: UUID.randomUUID().toString(),
                initialIsEnabled = initial?.isEnabled ?: true,
                initialImageUri = initial?.getAlarmBackgroundImageUri(),
                initialBackgroundType = initial?.getAlarmBackgroundType() ?: AlarmBackgroundType.NONE,
                soundUri = initial?.getAlarmSoundUri(),
                soundEnabled = initial?.isAlarmSoundEnabled() ?: DEFAULT_ALARM_SOUND_ENABLED,
                vibrationEnabled = initial?.isAlarmVibrationEnabled() ?: DEFAULT_ALARM_VIBRATION_ENABLED,
                vibrationPattern = initial?.getAlarmVibrationPattern() ?: VibrationPattern.BASIC_CALL,
                fullScreenEnabled = initial?.isAlarmFullScreenEnabled() ?: DEFAULT_ALARM_FULLSCREEN_ENABLED,
                snoozeEnabled = initial?.isAlarmSnoozeEnabled() ?: DEFAULT_ALARM_SNOOZE_ENABLED,
                snoozeDurationMinutes = initial?.getAlarmSnoozeDurationMinutes() ?: DEFAULT_ALARM_SNOOZE_DURATION_MINUTES,
                snoozeMaxCount = initial?.getAlarmSnoozeMaxCount() ?: DEFAULT_ALARM_SNOOZE_MAX_COUNT,
                backgroundType = initial?.getAlarmBackgroundType() ?: AlarmBackgroundType.NONE,
                backgroundPresetId = initial?.getAlarmBackgroundPresetId(),
                backgroundImageUri = initial?.getAlarmBackgroundImageUri(),
                backgroundImageIsDark = initial?.isAlarmBackgroundImageDark() ?: DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK,
            )
        }
    }

    /**
     * Answers "is [uri] still referenced by another `CREATE_ALARM` action's background image
     * other than [excludingActionId]" - used by [dev.gaferneira.notificapp.features.ruleeditor.ui.AlarmBottomSheet]
     * to decide whether it's safe to release a persistable URI permission when an alarm action's
     * background image changes or is removed. Exposed as a plain suspend delegate (not going
     * through [onEvent]/`UiEvent`) so the Composable can await its result directly, keeping the
     * actual `contentResolver` grant release entirely in the Composable - the ViewModel never
     * touches `Context`.
     *
     * On repository failure, fails closed (reports the URI as still referenced) so the caller
     * doesn't wrongly release a persistable URI grant that may still be in use.
     */
    suspend fun isImageUriReferencedByOtherAlarmAction(uri: String, excludingActionId: String): Boolean = ruleRepository.isImageUriReferencedByOtherAlarmAction(uri, excludingActionId).getOrDefault(true)

    /**
     * Replacing a picked-but-unsaved uri from earlier in this same sheet session: its grant was
     * never persisted, so it's safe to release directly without the repository check.
     */
    private fun onImagePicked(uri: String) {
        val state = uiState.value
        val previouslyPicked = state.backgroundImageUri
        if (previouslyPicked != null && previouslyPicked != state.initialImageUri && previouslyPicked != uri) {
            sendEffect(UiEffect.ReleaseImageUri(previouslyPicked))
        }
        setState { copy(backgroundImageUri = uri, backgroundType = AlarmBackgroundType.IMAGE) }
    }

    /**
     * On save, when the newly-saved uri differs from the action's previous persisted uri (edit
     * case) or the background type changed away from `IMAGE`, the composable must check whether
     * the old uri is still referenced by another alarm action before releasing its grant - the
     * same image may still be in use elsewhere.
     */
    private fun onConfirmClicked() {
        val state = uiState.value
        val oldUri = state.initialImageUri
        val uriChanged = oldUri != null && oldUri != state.backgroundImageUri
        val typeLeftImage = state.initialBackgroundType == AlarmBackgroundType.IMAGE && state.backgroundType != AlarmBackgroundType.IMAGE
        val oldUriToCheck = oldUri.takeIf { uriChanged || typeLeftImage }
        sendEffect(UiEffect.ConfirmSave(state.toRuleAction(), oldUriToCheck))
    }

    /**
     * Closes the cancel-path leak: a picked-but-never-saved uri would otherwise hold its grant
     * forever. Only releases if it differs from the already-persisted uri (if any).
     */
    private fun onSheetDismissed() {
        val state = uiState.value
        val pickedUri = state.backgroundImageUri
        if (pickedUri != null && pickedUri != state.initialImageUri) {
            sendEffect(UiEffect.ReleaseImageUri(pickedUri))
        }
    }
}
