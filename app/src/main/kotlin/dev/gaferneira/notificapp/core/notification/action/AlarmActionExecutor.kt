package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
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
 */
class AlarmActionExecutor @Inject constructor(
    private val alarmController: AlarmController,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome {
        val started = alarmController.start(
            AlarmRequest(
                soundUri = action.getAlarmSoundUri(),
                vibrationEnabled = action.isAlarmVibrationEnabled(),
                fullScreenEnabled = action.isAlarmFullScreenEnabled(),
                title = notification.title ?: notification.appName,
                text = notification.content.orEmpty(),
                appName = notification.appName,
            ),
        )
        return if (started) ActionOutcome.SUCCESS else ActionOutcome.SKIPPED
    }
}
