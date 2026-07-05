package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

/**
 * Looks up the [ActionExecutor] registered for each enabled [RuleAction]'s [ActionType] via Hilt
 * multibindings (`core/di/ActionModule.kt`) and runs it.
 *
 * Per ADR 010, a missing executor (e.g. `CREATE_ALARM`, deliberately unimplemented) yields an
 * explicit [ActionOutcome.SKIPPED] rather than a silent no-op, and an executor that throws yields
 * [ActionOutcome.FAILED] instead of crashing the pipeline.
 */
class ActionDispatcher @Inject constructor(
    private val executors: Map<ActionType, @JvmSuppressWildcards Provider<ActionExecutor>>,
) {

    /**
     * Execute all enabled [actions] against [notification], returning the outcome per action id.
     */
    suspend fun executeAll(notification: Notification, actions: List<RuleAction>): Map<String, ActionOutcome> = actions.filter { it.isEnabled }.associate { action ->
        val outcome = executors[action.type]?.get()?.let { executor ->
            runCatching { executor.execute(notification, action) }
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    Timber.e(e, "Action ${action.type} failed")
                    ActionOutcome.FAILED
                }
        } ?: ActionOutcome.SKIPPED
        action.id to outcome
    }
}
