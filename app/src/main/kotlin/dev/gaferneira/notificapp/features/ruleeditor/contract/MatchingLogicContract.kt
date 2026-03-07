package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition

/**
 * MVI Contract for the MatchingLogicBottomSheet.
 *
 * Manages the state for creating and editing matching conditions (WHEN logic).
 */
object MatchingLogicContract {

    /**
     * UI State for the matching logic bottom sheet.
     */
    data class UiState(
        /** Current mode: adding new or editing existing */
        val mode: Mode = Mode.ADD,
        /** Matching condition (what to match against) */
        val matchingCondition: MatchingCondition = MatchingCondition.TEXT_CONTENT,
        /** Matching operator (how to match) */
        val matchingOperator: MatchingOperator = MatchingOperator.CONTAINS,
        /** Value to match */
        val matchingValue: String = "",
        /** Validation error message, if any */
        val validationError: String? = null,
    ) {
        enum class Mode {
            ADD,
            EDIT,
        }
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Initialize the sheet for editing an existing condition */
        data class InitForEdit(val condition: RuleCondition) : UiEvent()

        /** Update matching condition */
        data class OnMatchingConditionChange(val condition: MatchingCondition) : UiEvent()

        /** Update matching operator */
        data class OnMatchingOperatorChange(val operator: MatchingOperator) : UiEvent()

        /** Update matching value */
        data class OnMatchingValueChange(val value: String) : UiEvent()

        /** Clear validation error */
        data object OnClearError : UiEvent()

        /** Confirm and create/update condition */
        data object OnConfirm : UiEvent()

        /** Dismiss without saving */
        data object OnDismiss : UiEvent()
    }

    /**
     * One-time effects to communicate with the parent.
     */
    sealed class UiEffect {
        /** New condition created */
        data class ConditionCreated(val condition: RuleCondition) : UiEffect()

        /** Existing condition updated */
        data class ConditionUpdated(val conditionId: String, val condition: RuleCondition) : UiEffect()

        /** Dismiss the sheet */
        data object Dismiss : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}

// Extension functions for display names
internal fun MatchingCondition.displayName(): String = when (this) {
    MatchingCondition.TEXT_CONTENT -> "Text"
    MatchingCondition.TITLE -> "Title"
    MatchingCondition.APP_NAME -> "App Name"
    MatchingCondition.PACKAGE_NAME -> "Package Name"
    MatchingCondition.RAW_CONTENT -> "Raw Content"
}

internal fun MatchingOperator.displayName(): String = when (this) {
    MatchingOperator.CONTAINS -> "contains"
    MatchingOperator.STARTS_WITH -> "starts with"
    MatchingOperator.ENDS_WITH -> "ends with"
    MatchingOperator.EQUALS -> "equals"
    MatchingOperator.REGEX_MATCH -> "matches regex"
    MatchingOperator.NOT_CONTAINS -> "does not contain"
}
