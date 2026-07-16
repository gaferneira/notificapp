package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.VibrationPattern

/**
 * Current alarm configuration shown in [dev.gaferneira.notificapp.features.ruleeditor.ui.components.AlarmOptionsSelector].
 *
 * @property soundUri Selected alarm sound URI, or null for the device default
 * @property soundEnabled Whether the alarm plays audio while ringing
 * @property vibrationEnabled Whether the alarm vibrates
 * @property vibrationPattern The named vibration pattern used while ringing
 * @property fullScreenEnabled Whether the alarm shows the full-screen, call-style UI
 * @property snoozeEnabled Whether the Snooze action is offered while the alarm rings
 * @property snoozeDurationMinutes The alarm-scoped snooze delay, in minutes
 * @property snoozeMaxCount The maximum number of times this alarm may be snoozed
 * @property backgroundType The full-screen UI background source
 * @property backgroundPresetId The selected background preset's id, meaningful only when
 * [backgroundType] is `PRESET`
 * @property backgroundImageUri The persisted custom background image URI, meaningful only when
 * [backgroundType] is `IMAGE`
 * @property backgroundImageIsDark Whether the custom background image is dark, meaningful only
 * when [backgroundType] is `IMAGE`
 * @property cooldownSeconds Rule-safety cooldown, in seconds (`0` disables it): a chatty source
 * app re-matching this rule within the window is suppressed instead of re-ringing
 */
data class AlarmOptions(
    val soundUri: String?,
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val vibrationPattern: VibrationPattern,
    val fullScreenEnabled: Boolean,
    val snoozeEnabled: Boolean,
    val snoozeDurationMinutes: Int,
    val snoozeMaxCount: Int,
    val backgroundType: AlarmBackgroundType,
    val backgroundPresetId: String?,
    val backgroundImageUri: String?,
    val backgroundImageIsDark: Boolean,
    val cooldownSeconds: Int,
)
