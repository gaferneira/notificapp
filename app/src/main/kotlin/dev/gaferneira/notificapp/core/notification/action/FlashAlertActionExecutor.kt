package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.getFlashCount
import dev.gaferneira.notificapp.domain.model.getFlashDurationMs
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.FLASH_ALERT] by blinking the
 * camera torch through [TorchController]. Skipped on devices without a flash, and while the
 * system is in battery saver mode.
 */
class FlashAlertActionExecutor @Inject constructor(
    private val torchController: TorchController,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome {
        if (!torchController.hasFlash() || torchController.isPowerSaveMode()) {
            return ActionOutcome.SKIPPED
        }

        torchController.blink(action.getFlashCount(), action.getFlashDurationMs())
        return ActionOutcome.SUCCESS
    }
}
