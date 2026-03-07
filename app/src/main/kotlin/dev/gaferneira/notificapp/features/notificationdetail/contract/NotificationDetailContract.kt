package dev.gaferneira.notificapp.features.notificationdetail.contract

import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.Rule

/**
 * Contract for the Notification Detail screen.
 *
 * Shows notification data and applicable extraction rules.
 */
object NotificationDetailContract {

    /**
     * UI State for the notification detail screen.
     */
    data class UiState(
        /** The notification being viewed */
        val notification: Notification? = null,
        /** Rules that apply to this notification's app */
        val applicableRules: List<ApplicableRule> = emptyList(),
        /** Whether data is loading */
        val isLoading: Boolean = true,
        /** Error message if loading failed */
        val error: String? = null,
    ) {
        /** Count of applicable rules */
        val applicableRulesCount: Int
            get() = applicableRules.size

        /** Count of active applicable rules */
        val activeRulesCount: Int
            get() = applicableRules.count { it.isActive }
    }

    /**
     * Represents a rule and whether it applies to this notification.
     */
    data class ApplicableRule(val rule: Rule, val isApplicable: Boolean, val isActive: Boolean)

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** User clicked back button */
        data object OnBackClicked : UiEvent()

        /** User clicked to create a new rule */
        data object OnCreateRuleClicked : UiEvent()

        /** User clicked to edit a rule */
        data class OnEditRuleClicked(val ruleId: String) : UiEvent()

        /** User clicked to toggle a rule */
        data class OnRuleToggleClicked(val ruleId: String) : UiEvent()

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

        /** Navigate to rule editor for existing rule */
        data class NavigateToEditRule(val ruleId: String) : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}
