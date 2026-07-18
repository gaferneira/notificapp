package dev.gaferneira.notificapp.core.notification.action.alarm

import dev.gaferneira.notificapp.core.notification.action.NotificationThrottleTracker
import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundConfig
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.getAlarmBackgroundImageUri
import dev.gaferneira.notificapp.domain.model.getAlarmBackgroundPresetId
import dev.gaferneira.notificapp.domain.model.getAlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.getAlarmCooldownSeconds
import dev.gaferneira.notificapp.domain.model.getAlarmSnoozeDurationMinutes
import dev.gaferneira.notificapp.domain.model.getAlarmSnoozeMaxCount
import dev.gaferneira.notificapp.domain.model.getAlarmSoundUri
import dev.gaferneira.notificapp.domain.model.getAlarmVibrationPattern
import dev.gaferneira.notificapp.domain.model.isAlarmBackgroundImageDark
import dev.gaferneira.notificapp.domain.model.isAlarmFullScreenEnabled
import dev.gaferneira.notificapp.domain.model.isAlarmSnoozeEnabled
import dev.gaferneira.notificapp.domain.model.isAlarmSoundEnabled
import dev.gaferneira.notificapp.domain.model.isAlarmVibrationEnabled
import timber.log.Timber
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.CREATE_ALARM] by starting the alarm
 * foreground service (via [AlarmController]), which then owns the ringing until the user dismisses
 * or snoozes it.
 *
 * Per ADR 013 this executor delegates: a returned [ActionOutcome.SUCCESS] means "the alarm was
 * successfully started," not "the alarm has finished ringing" — the ring outlives this call under
 * the service's ownership. It returns [ActionOutcome.SKIPPED] when the alarm cannot be started
 * safely (e.g. app notifications disabled, which would leave the alarm with no way to be stopped).
 *
 * When [RuleAction.getAlarmCooldownSeconds] is set (> 0), a chatty source app re-matching this
 * rule within the window is [ActionOutcome.SUPPRESSED] instead of re-ringing — a rule safety net,
 * since a repeatedly firing alarm is disruptive enough to be an uninstall-level bug.
 */
class AlarmActionExecutor @Inject constructor(
    private val alarmController: AlarmController,
    private val throttleTracker: NotificationThrottleTracker,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): ActionOutcome {
        val cooldownSeconds = action.getAlarmCooldownSeconds()
        if (cooldownSeconds > 0) {
            val deliver = throttleTracker.shouldDeliver(
                actionId = action.id,
                packageName = notification.packageName,
                windowMs = cooldownSeconds * 1_000L,
                resetAt = 0L,
            )
            if (!deliver) {
                Timber.d("Alarm cooldown window still open for notification ${notification.id}, suppressing")
                return ActionOutcome.SUPPRESSED
            }
        }

        val started = alarmController.start(
            AlarmRequest(
                soundUri = action.getAlarmSoundUri(),
                title = notification.title ?: notification.appName,
                text = notification.content.orEmpty(),
                appName = notification.appName,
                options = AlarmRingOptions(
                    vibrationEnabled = action.isAlarmVibrationEnabled(),
                    fullScreenEnabled = action.isAlarmFullScreenEnabled(),
                    soundEnabled = action.isAlarmSoundEnabled(),
                    vibrationPattern = action.getAlarmVibrationPattern(),
                    snooze = AlarmSnoozeSettings(
                        enabled = action.isAlarmSnoozeEnabled(),
                        durationMinutes = action.getAlarmSnoozeDurationMinutes(),
                        maxCount = action.getAlarmSnoozeMaxCount(),
                    ),
                    background = AlarmBackgroundConfig(
                        type = action.getAlarmBackgroundType(),
                        presetId = action.getAlarmBackgroundPresetId(),
                        imageUri = action.getAlarmBackgroundImageUri(),
                        imageIsDark = action.isAlarmBackgroundImageDark(),
                    ),
                ),
                sourceKey = notification.sbnKey,
            ),
        )
        return if (started) ActionOutcome.SUCCESS else ActionOutcome.SKIPPED
    }
}
