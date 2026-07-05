package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.CREATE_ALARM] by playing an alarm
 * sound (and optionally vibrating) through [AlarmPlayer].
 */
class AlarmActionExecutor @Inject constructor(
    private val alarmPlayer: AlarmPlayer,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome {
        alarmPlayer.play(action.getAlarmSoundUri())
        if (action.isAlarmVibrationEnabled()) {
            alarmPlayer.vibrate()
        }
        return ActionOutcome.SUCCESS
    }
}
