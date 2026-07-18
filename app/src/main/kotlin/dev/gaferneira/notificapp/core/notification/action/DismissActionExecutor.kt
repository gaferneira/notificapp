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

    override suspend fun execute(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): ActionOutcome {
        val controller = controllerHolder.get()
        val sbnKey = notification.sbnKey
        return when {
            controller == null -> {
                Timber.w("Cannot dismiss notification ${notification.id}: listener not connected")
                ActionOutcome.SKIPPED
            }
            sbnKey == null -> {
                Timber.w("Cannot dismiss notification ${notification.id}: no SBN key")
                ActionOutcome.SKIPPED
            }
            else -> {
                controller.cancel(sbnKey)
                ActionOutcome.SUCCESS
            }
        }
    }
}
