package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract.AppInfo
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract.MatchingCondition
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract.MatchingOperator
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract.TriggerType

/**
 * MVI Contract for the MatchingLogicBottomSheet.
 *
 * Manages the state for creating and editing trigger conditions (WHEN logic).
 */
object MatchingLogicContract {

    /**
     * UI State for the matching logic bottom sheet.
     */
    data class UiState(
        /** Current mode: adding new or editing existing */
        val mode: Mode = Mode.ADD,
        /** Trigger type: condition-based or app-based */
        val triggerType: TriggerType = TriggerType.CONDITION,
        /** Matching condition (for CONDITION type) */
        val matchingCondition: MatchingCondition = MatchingCondition.TEXT_CONTENT,
        /** Matching operator (for CONDITION type) */
        val matchingOperator: MatchingOperator = MatchingOperator.CONTAINS,
        /** Value to match (for CONDITION type) */
        val matchingValue: String = "",
        /** Selected apps (for APP type) */
        val selectedApps: List<AppInfo> = emptyList(),
        /** Whether the app selection picker is visible */
        val isAppPickerVisible: Boolean = false,
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
        /** Initialize the sheet for editing an existing trigger */
        data class InitForEdit(
            val triggerId: String,
            val triggerType: TriggerType,
            val condition: MatchingCondition?,
            val operator: MatchingOperator?,
            val value: String?,
            val selectedApps: List<AppInfo>,
        ) : UiEvent()

        /** Change trigger type between CONDITION and APP */
        data class OnTriggerTypeChange(val triggerType: TriggerType) : UiEvent()

        /** Update matching condition */
        data class OnMatchingConditionChange(val condition: MatchingCondition) : UiEvent()

        /** Update matching operator */
        data class OnMatchingOperatorChange(val operator: MatchingOperator) : UiEvent()

        /** Update matching value */
        data class OnMatchingValueChange(val value: String) : UiEvent()

        /** Add apps to selection */
        data class OnAppsSelected(val apps: List<AppInfo>) : UiEvent()

        /** Remove app from selection */
        data class OnRemoveApp(val packageName: String) : UiEvent()

        /** Clear validation error */
        data object OnClearError : UiEvent()

        /** Confirm and create/update trigger */
        data object OnConfirm : UiEvent()

        /** Dismiss without saving */
        data object OnDismiss : UiEvent()

        /** Show app selection picker */
        data object OnShowAppPicker : UiEvent()

        /** Dismiss app selection picker */
        data object OnDismissAppPicker : UiEvent()
    }

    /**
     * One-time effects to communicate with the parent.
     */
    sealed class UiEffect {
        /** New trigger created */
        data class TriggerCreated(val trigger: TriggerUiModel) : UiEffect()

        /** Existing trigger updated */
        data class TriggerUpdated(val triggerId: String, val trigger: TriggerUiModel) : UiEffect()

        /** Dismiss the sheet */
        data object Dismiss : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }

    /**
     * Type of trigger.
     */
    enum class TriggerType {
        CONDITION,
        APP,
    }

    /**
     * What to match against in the notification.
     */
    enum class MatchingCondition {
        TEXT_CONTENT,
        TITLE,
        APP_NAME,
        PACKAGE_NAME,
        RAW_CONTENT,
    }

    /**
     * How to perform the match.
     */
    enum class MatchingOperator {
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        EQUALS,
        REGEX_MATCH,
        NOT_CONTAINS,
    }

    /**
     * App information for trigger selection.
     */
    data class AppInfo(val packageName: String, val name: String)
}

data class TriggerUiModel(
    val id: String,
    val type: TriggerType,
    val condition: MatchingCondition? = null,
    val operator: MatchingOperator? = null,
    val value: String? = null,
    val selectedApps: List<AppInfo> = emptyList(),
) {
    val displayText: String
        get() = when (type) {
            TriggerType.CONDITION -> "${condition?.displayName()} ${operator?.displayName()} '$value'"
            TriggerType.APP -> if (selectedApps.isEmpty()) {
                "All apps selected"
            } else if (selectedApps.size == 1) {
                "App is ${selectedApps.first().name}"
            } else {
                "${selectedApps.size} apps selected"
            }
        }
}

// Extension functions for display names
internal fun MatchingLogicContract.MatchingCondition.displayName(): String = when (this) {
    MatchingLogicContract.MatchingCondition.TEXT_CONTENT -> "Text"
    MatchingLogicContract.MatchingCondition.TITLE -> "Title"
    MatchingLogicContract.MatchingCondition.APP_NAME -> "App name"
    MatchingLogicContract.MatchingCondition.PACKAGE_NAME -> "Package"
    MatchingLogicContract.MatchingCondition.RAW_CONTENT -> "Raw content"
}

internal fun MatchingLogicContract.MatchingOperator.displayName(): String = when (this) {
    MatchingLogicContract.MatchingOperator.CONTAINS -> "contains"
    MatchingLogicContract.MatchingOperator.STARTS_WITH -> "starts with"
    MatchingLogicContract.MatchingOperator.ENDS_WITH -> "ends with"
    MatchingLogicContract.MatchingOperator.EQUALS -> "equals"
    MatchingLogicContract.MatchingOperator.REGEX_MATCH -> "matches"
    MatchingLogicContract.MatchingOperator.NOT_CONTAINS -> "doesn't contain"
}
