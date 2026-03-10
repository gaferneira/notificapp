package dev.gaferneira.notificapp.features.notificationdetail.contract

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
        val error: String? = null,
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
        val triggeredActionNames: List<String>,
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
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** User clicked back button */
        data object OnBackClicked : UiEvent()

        /** User clicked to create a new rule */
        data object OnCreateRuleClicked : UiEvent()

        /** User clicked to refresh/re-execute rules */
        data object OnRefreshClicked : UiEvent()

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
