package dev.gaferneira.notificapp.features.notificationdetail.contract

import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField

/**
 * Contract for the Notification Detail screen.
 *
 * Shows notification data and rule executions that matched this notification.
 */
object NotificationDetailContract {

    /**
     * UI State for the notification detail screen.
     */
    data class UiState(
        /** The notification being viewed */
        val notification: Notification? = null,
        /** Rule executions for this notification */
        val executions: List<ExecutionWithDetails> = emptyList(),
        /** Whether data is loading */
        val isLoading: Boolean = true,
        /** Error message if loading failed */
        val error: UiText? = null,
    ) {
        /** Count of rule executions */
        val executionCount: Int
            get() = executions.size

        /** Whether any rules matched this notification */
        val hasExecutions: Boolean
            get() = executions.isNotEmpty()
    }

    /**
     * Represents a rule execution with display details.
     */
    data class ExecutionWithDetails(
        val execution: RuleExecution,
        val ruleName: String,
        val extractedFields: List<ExtractedFieldDisplay>,
        val triggeredActions: List<TriggeredActionDisplay>,
    )

    /**
     * Display data for an extracted field.
     */
    data class ExtractedFieldDisplay(
        val fieldName: String,
        val fieldType: RuleField.FieldType,
        val value: String,
    )

    /**
     * Display data for a triggered action and how it turned out.
     *
     * [outcome] is `null` for legacy execution rows recorded before outcomes were tracked
     * (TD-5) — this is rendered as "no data", distinct from [ActionOutcome.SKIPPED], which means
     * the dispatcher actively decided not to run the action.
     */
    data class TriggeredActionDisplay(
        val name: String,
        val outcome: ActionOutcome?,
    )

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** User clicked back button */
        data object OnBackClicked : UiEvent()

        /** User clicked to create a new rule */
        data object OnCreateRuleClicked : UiEvent()

        /** User clicked "Re-run rules" - deletes and re-evaluates executions for this notification */
        data object OnRefreshClicked : UiEvent()

        /** User clicked Retry on the load-error state - reloads without re-running rules */
        data object OnRetryClicked : UiEvent()

        /** User dismissed error */
        data object OnDismissError : UiEvent()
    }

    /**
     * One-time effects (navigation, actions).
     */
    sealed class UiEffect {
        /** Navigate back to inbox */
        data object NavigateBack : UiEffect()

        /** Navigate to rule editor for new rule */
        data class NavigateToRuleEditor(val notificationId: String) : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}
