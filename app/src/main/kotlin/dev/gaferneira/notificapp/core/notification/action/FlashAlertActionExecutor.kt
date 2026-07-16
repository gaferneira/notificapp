package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.getFlashCooldownSeconds
import dev.gaferneira.notificapp.domain.model.getFlashCount
import dev.gaferneira.notificapp.domain.model.getFlashDurationMs
import timber.log.Timber
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.FLASH_ALERT] by blinking the
 * camera torch through [TorchController]. Skipped on devices without a flash, and while the
 * system is in battery saver mode.
 *
 * When [RuleAction.getFlashCooldownSeconds] is set (> 0), a chatty source app re-matching this
 * rule within the window is [ActionOutcome.SUPPRESSED] instead of re-blinking — a rule safety
 * net, since a repeatedly strobing torch is disruptive enough to be an uninstall-level bug.
 */
class FlashAlertActionExecutor @Inject constructor(
    private val torchController: TorchController,
    private val throttleTracker: NotificationThrottleTracker,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome = when {
        !torchController.hasFlash() || torchController.isPowerSaveMode() -> ActionOutcome.SKIPPED
        isInCooldown(notification, action) -> ActionOutcome.SUPPRESSED
        else -> {
            torchController.blink(action.getFlashCount(), action.getFlashDurationMs())
            ActionOutcome.SUCCESS
        }
    }

    private suspend fun isInCooldown(notification: Notification, action: RuleAction): Boolean {
        val cooldownSeconds = action.getFlashCooldownSeconds()
        if (cooldownSeconds <= 0) return false

        val deliver = throttleTracker.shouldDeliver(
            actionId = action.id,
            packageName = notification.packageName,
            windowMs = cooldownSeconds * 1_000L,
            resetAt = 0L,
        )
        if (!deliver) {
            Timber.d("Flash cooldown window still open for notification ${notification.id}, suppressing")
        }
        return !deliver
    }
}
