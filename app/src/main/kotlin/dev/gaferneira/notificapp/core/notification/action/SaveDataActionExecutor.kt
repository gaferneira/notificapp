package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.SAVE_DATA].
 *
 * This is a no-op: extraction persistence is a pipeline concern, not an executor side effect.
 * `ProcessNotificationUseCase` gates whether extracted field values are saved on the presence of an
 * enabled `SAVE_DATA` action; this executor only reports the action ran. Registering an explicit,
 * always-succeeding executor makes `SAVE_DATA` a truthful `SUCCESS` in execution history instead of
 * silently falling through to `SKIPPED` for lack of a handler.
 */
class SaveDataActionExecutor @Inject constructor() : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome = ActionOutcome.SUCCESS
}
