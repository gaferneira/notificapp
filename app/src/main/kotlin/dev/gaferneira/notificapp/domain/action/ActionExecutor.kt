package dev.gaferneira.notificapp.domain.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction

/**
 * Executes a single [RuleAction] against a matched [Notification].
 *
 * One implementation per [dev.gaferneira.notificapp.domain.model.ActionType], registered via
 * Hilt multibindings (see `core/di/ActionModule.kt`) and dispatched by
 * `core/notification/action/ActionDispatcher`. Per ADR 010, adding a new action type is an
 * additive change: a new executor + one binding, no existing code changes.
 */
interface ActionExecutor {
    /**
     * @param extractedFields The matched rule's extracted field values, keyed by
     * [dev.gaferneira.notificapp.domain.model.RuleField.name] (not id) - resolved by
     * `ActionDispatcher.executeAll` from `RuleMatch.extractedData`. Empty for rules with no
     * enabled `SAVE_DATA` action, or for executors that don't need it (most ignore this param).
     */
    suspend fun execute(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): ActionOutcome
}
