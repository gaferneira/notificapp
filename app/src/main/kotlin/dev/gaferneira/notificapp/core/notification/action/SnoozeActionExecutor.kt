package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.SnoozeMode
import timber.log.Timber
import java.time.Duration
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.SNOOZE_NOTIFICATION] by snoozing
 * the system notification through the currently connected [SystemNotificationController].
 *
 * [SnoozeMode.DURATION] snoozes for a fixed relative duration. [SnoozeMode.SCHEDULED] snoozes
 * until the next checkpoint computed by [SnoozeScheduleCalculator], or passes the notification
 * through unsnoozed when outside a configured recurrence window. See
 * `openspec/specs/snooze-scheduling/spec.md`.
 */
class SnoozeActionExecutor @Inject constructor(
    private val controllerHolder: SystemNotificationControllerHolder,
    private val timeProvider: CurrentTimeProvider,
    private val releaseTracker: SnoozeReleaseTracker,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome {
        val controller = controllerHolder.get()
        if (controller == null) {
            Timber.w("Cannot snooze notification ${notification.id}: listener not connected")
            return ActionOutcome.SKIPPED
        }

        val sbnKey = notification.sbnKey
        if (sbnKey == null) {
            Timber.w("Cannot snooze notification ${notification.id}: no SBN key")
            return ActionOutcome.SKIPPED
        }

        return when (action.getSnoozeMode()) {
            SnoozeMode.DURATION -> executeDuration(controller, sbnKey, notification, action)
            SnoozeMode.SCHEDULED -> executeScheduled(controller, sbnKey, notification, action)
        }
    }

    private fun executeDuration(
        controller: SystemNotificationController,
        sbnKey: String,
        notification: Notification,
        action: RuleAction,
    ): ActionOutcome {
        val durationMinutes = action.getSnoozeDurationMinutes()
        val durationMs = durationMinutes * 60_000L
        controller.snooze(sbnKey, durationMs)
        Timber.d("Snoozed notification ${notification.id} for $durationMinutes minutes")
        return ActionOutcome.SUCCESS
    }

    private suspend fun executeScheduled(
        controller: SystemNotificationController,
        sbnKey: String,
        notification: Notification,
        action: RuleAction,
    ): ActionOutcome {
        if (releaseTracker.consumeIfPending(sbnKey)) {
            Timber.d("Notification ${notification.id} is a scheduled release, leaving it visible")
            return ActionOutcome.SUCCESS
        }

        val schedule = action.getSnoozeSchedule()
        if (schedule == null) {
            Timber.w("Cannot schedule-snooze notification ${notification.id}: invalid schedule config")
            return ActionOutcome.SKIPPED
        }

        val now = timeProvider.now()
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)
        if (checkpoint == null) {
            Timber.d("Notification ${notification.id} is outside the snooze window, passing through")
            return ActionOutcome.SUCCESS
        }

        val durationMs = Duration.between(now, checkpoint).toMillis()
        controller.snooze(sbnKey, durationMs)
        releaseTracker.markPending(sbnKey)
        Timber.d("Snoozed notification ${notification.id} until $checkpoint")
        return ActionOutcome.SUCCESS
    }
}
