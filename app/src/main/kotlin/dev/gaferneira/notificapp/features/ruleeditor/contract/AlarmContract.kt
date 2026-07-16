package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.AlarmBackgroundConfig
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundPreset
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.AlarmOptionsConfig
import dev.gaferneira.notificapp.domain.model.AlarmSnoozeConfig
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_COOLDOWN_SECONDS
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_FULLSCREEN_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_MAX_COUNT
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SOUND_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_VIBRATION_ENABLED
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.VibrationPattern
import dev.gaferneira.notificapp.features.ruleeditor.domain.AlarmOptions
import java.util.UUID

/**
 * MVI Contract for [dev.gaferneira.notificapp.features.ruleeditor.ui.AlarmBottomSheet].
 */
object AlarmContract {

    /**
     * State for [dev.gaferneira.notificapp.features.ruleeditor.ui.AlarmBottomSheet], seeded from
     * an existing [RuleAction] when editing, or defaults when adding a new one.
     */
    data class UiState(
        val actionId: String = UUID.randomUUID().toString(),
        val initialIsEnabled: Boolean = true,
        val initialImageUri: String? = null,
        val initialBackgroundType: AlarmBackgroundType = AlarmBackgroundType.NONE,
        val soundUri: String? = null,
        val soundEnabled: Boolean = DEFAULT_ALARM_SOUND_ENABLED,
        val vibrationEnabled: Boolean = DEFAULT_ALARM_VIBRATION_ENABLED,
        val vibrationPattern: VibrationPattern = VibrationPattern.BASIC_CALL,
        val fullScreenEnabled: Boolean = DEFAULT_ALARM_FULLSCREEN_ENABLED,
        val snoozeEnabled: Boolean = DEFAULT_ALARM_SNOOZE_ENABLED,
        val snoozeDurationMinutes: Int = DEFAULT_ALARM_SNOOZE_DURATION_MINUTES,
        val snoozeMaxCount: Int = DEFAULT_ALARM_SNOOZE_MAX_COUNT,
        val backgroundType: AlarmBackgroundType = AlarmBackgroundType.NONE,
        val backgroundPresetId: String? = null,
        val backgroundImageUri: String? = null,
        val backgroundImageIsDark: Boolean = DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK,
        val cooldownSeconds: Int = DEFAULT_ALARM_COOLDOWN_SECONDS,
    ) {
        fun toOptions(): AlarmOptions = AlarmOptions(
            soundUri = soundUri,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            vibrationPattern = vibrationPattern,
            fullScreenEnabled = fullScreenEnabled,
            snoozeEnabled = snoozeEnabled,
            snoozeDurationMinutes = snoozeDurationMinutes,
            snoozeMaxCount = snoozeMaxCount,
            backgroundType = backgroundType,
            backgroundPresetId = backgroundPresetId,
            backgroundImageUri = backgroundImageUri,
            backgroundImageIsDark = backgroundImageIsDark,
            cooldownSeconds = cooldownSeconds,
        )

        fun toRuleAction(): RuleAction = RuleAction.createAlarm(
            id = actionId,
            soundUri = soundUri,
            vibrationEnabled = vibrationEnabled,
            isEnabled = initialIsEnabled,
            options = AlarmOptionsConfig(
                soundEnabled = soundEnabled,
                vibrationPattern = vibrationPattern,
                fullScreenEnabled = fullScreenEnabled,
                snooze = AlarmSnoozeConfig(enabled = snoozeEnabled, durationMinutes = snoozeDurationMinutes, maxCount = snoozeMaxCount),
                background = AlarmBackgroundConfig(
                    type = backgroundType,
                    presetId = backgroundPresetId,
                    imageUri = backgroundImageUri,
                    imageIsDark = backgroundImageIsDark,
                ),
                cooldownSeconds = cooldownSeconds,
            ),
        )
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Seed state from an existing [RuleAction] when editing, or defaults when adding a new one */
        data class Initialize(val initial: RuleAction?) : UiEvent()

        data class OnSoundToggle(val enabled: Boolean) : UiEvent()
        data class OnSoundChange(val uri: String?) : UiEvent()
        data class OnVibrationToggle(val enabled: Boolean) : UiEvent()
        data class OnVibrationPatternChange(val pattern: VibrationPattern) : UiEvent()
        data class OnFullScreenToggle(val enabled: Boolean) : UiEvent()
        data class OnSnoozeToggle(val enabled: Boolean) : UiEvent()
        data class OnSnoozeDurationChange(val minutes: Int) : UiEvent()
        data class OnSnoozeMaxCountChange(val count: Int) : UiEvent()
        data class OnCooldownSecondsChange(val seconds: Int) : UiEvent()
        data class OnBackgroundPresetSelected(val preset: AlarmBackgroundPreset) : UiEvent()

        /** A new background image was picked; see [AlarmViewModel.onEvent] for the URI-release logic */
        data class OnImagePicked(val uri: String) : UiEvent()

        /** The user toggled whether the custom background image is dark. */
        data class OnBackgroundImageIsDarkToggle(val isDark: Boolean) : UiEvent()

        /** The sheet's confirm ("Add"/"Save") action was tapped */
        data object OnConfirmClicked : UiEvent()

        /** The sheet was dismissed without saving */
        data object OnSheetDismissed : UiEvent()
    }

    /**
     * One-shot effects [dev.gaferneira.notificapp.features.ruleeditor.viewmodel.AlarmViewModel] hands
     * back to the composable for Android-framework work (`ContentResolver` URI-permission grants) the
     * ViewModel cannot perform itself.
     */
    sealed class UiEffect {
        data class ReleaseImageUri(val uri: String) : UiEffect()
        data class ConfirmSave(val action: RuleAction, val oldUriToCheck: String?) : UiEffect()
    }
}
