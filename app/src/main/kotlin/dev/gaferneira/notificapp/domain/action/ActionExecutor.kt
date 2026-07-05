package dev.gaferneira.notificapp.domain.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction

/**
 * Executes a single [RuleAction] against a matched [Notification].
 *
 * One implementation per [dev.gaferneira.notificapp.domain.model.ActionType], registered via
 * Hilt multibindings (see `core/di/ActionModule.kt`) and dispatched by
 * `features/notification/action/ActionDispatcher`. Per ADR 010, adding a new action type is an
 * additive change: a new executor + one binding, no existing code changes.
 */
interface ActionExecutor {
    suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome
}
