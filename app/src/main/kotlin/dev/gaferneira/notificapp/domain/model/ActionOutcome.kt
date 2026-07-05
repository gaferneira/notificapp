package dev.gaferneira.notificapp.domain.model

import kotlinx.serialization.Serializable

/**
 * Outcome of executing a single [RuleAction].
 *
 * Recorded on [RuleExecution] so execution history reflects what actually happened,
 * rather than assuming every enabled action succeeded.
 */
@Serializable
enum class ActionOutcome {
    /** The action executor ran and completed successfully. */
    SUCCESS,

    /** The action executor ran but failed (exception was thrown). */
    FAILED,

    /** No executor was registered for the action type, or a precondition was not met. */
    SKIPPED,
}
