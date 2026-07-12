package dev.gaferneira.notificapp.domain.action

import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleExecution

/** Re-runs rule matching/extraction for a notification and persists executions, without dispatching actions. */
interface RuleReEvaluator {
    suspend fun reEvaluate(notification: Notification): Result<List<RuleExecution>>
}
