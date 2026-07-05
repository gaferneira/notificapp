package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import timber.log.Timber
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.DISMISS_NOTIFICATION] by cancelling
 * the system notification through the currently connected [SystemNotificationController].
 */
class DismissActionExecutor @Inject constructor(
    private val controllerHolder: SystemNotificationControllerHolder,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome {
        val controller = controllerHolder.get()
        if (controller == null) {
            Timber.w("Cannot dismiss notification ${notification.id}: listener not connected")
            return ActionOutcome.SKIPPED
        }

        val sbnKey = notification.sbnKey
        if (sbnKey == null) {
            Timber.w("Cannot dismiss notification ${notification.id}: no SBN key")
            return ActionOutcome.SKIPPED
        }

        controller.cancel(sbnKey)
        return ActionOutcome.SUCCESS
    }
}
