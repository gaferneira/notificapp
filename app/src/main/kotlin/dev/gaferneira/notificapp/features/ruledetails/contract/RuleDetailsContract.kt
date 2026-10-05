package dev.gaferneira.notificapp.features.ruledetails.contract

import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleStats

/**
 * Contract for the Rule Details screen: a read-only view of a rule with edit, share and delete
 * entry points.
 */
object RuleDetailsContract {

    /**
     * UI State for the rule details screen.
     */
    data class UiState(
        /** The rule being viewed; null until the first emission or if loading failed */
        val rule: Rule? = null,
        /**
         * Match statistics for the rule; null until loaded or if loading failed. The Statistics
         * section is simply hidden in that case - it never blocks the rest of the screen.
         */
        val stats: RuleStats? = null,
        /** Whether the rule is loading */
        val isLoading: Boolean = true,
        /** Error to show in place of the content when loading failed */
        val error: UiText? = null,
        /** Whether the delete confirmation dialog is visible */
        val showDeleteConfirmation: Boolean = false,
        /** Whether the "Go live" confirmation dialog is visible */
        val showGoLiveConfirmation: Boolean = false,
        /** Whether the Go live save is in flight (guards against double taps) */
        val isGoingLive: Boolean = false,
    )

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** User clicked the back button */
        data object OnBackClicked : UiEvent()

        /** User clicked Edit - opens the rule editor */
        data object OnEditClicked : UiEvent()

        /** User toggled the rule's active switch */
        data object OnToggleActiveClicked : UiEvent()

        /** User clicked Share */
        data object OnShareClicked : UiEvent()

        /** User clicked Delete - shows the confirmation dialog */
        data object OnDeleteClicked : UiEvent()

        /** User confirmed the deletion */
        data object OnDeleteConfirmed : UiEvent()

        /** User dismissed the delete confirmation dialog */
        data object OnDeleteDismissed : UiEvent()

        /** User clicked "Go live" on the dry-run banner - shows the confirmation dialog */
        data object OnGoLiveClicked : UiEvent()

        /** User confirmed going live - saves the rule with dry run turned off */
        data object OnGoLiveConfirmed : UiEvent()

        /** User dismissed the Go live confirmation dialog */
        data object OnGoLiveDismissed : UiEvent()

        /** User clicked Retry on the load-error state */
        data object OnRetryClicked : UiEvent()
    }

    /**
     * One-time effects.
     */
    sealed class UiEffect {
        /** Open the system share sheet with the rule's exported JSON */
        data class ShareRule(val ruleName: String, val json: String) : UiEffect()

        /** Show a transient message (typically an error) */
        data class ShowMessage(val message: UiText) : UiEffect()
    }
}
