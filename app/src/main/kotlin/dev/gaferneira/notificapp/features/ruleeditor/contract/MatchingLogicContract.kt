package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * MVI Contract for the MatchingLogicBottomSheet.
 *
 * Manages the state for creating and editing matching conditions (WHEN logic), across all three
 * [RuleCondition] families: content-match, day-of-week, and time-range.
 */
object MatchingLogicContract {

    /**
     * Which [RuleCondition] family the sheet is currently configuring.
     */
    enum class ConditionType {
        CONTENT,
        DAY_OF_WEEK,
        TIME_RANGE,
    }

    /**
     * UI State for the matching logic bottom sheet.
     */
    data class UiState(
        /** Current mode: adding new or editing existing */
        val mode: Mode = Mode.ADD,
        /** Which condition family is being configured */
        val conditionType: ConditionType = ConditionType.CONTENT,
        /** Matching condition (what to match against) - CONTENT family only */
        val matchingCondition: MatchingCondition = MatchingCondition.TEXT_CONTENT,
        /** Matching operator (how to match) - CONTENT family only */
        val matchingOperator: MatchingOperator = MatchingOperator.CONTAINS,
        /** Value to match - CONTENT family only */
        val matchingValue: String = "",
        /** Selected days - DAY_OF_WEEK family only */
        val selectedDays: Set<DayOfWeek> = emptySet(),
        /** Range start - TIME_RANGE family only */
        val startTime: LocalTime = LocalTime.of(9, 0),
        /** Range end - TIME_RANGE family only */
        val endTime: LocalTime = LocalTime.of(17, 0),
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

        /** Switch which condition family is being configured */
        data class OnConditionTypeChange(val type: ConditionType) : UiEvent()

        /** Update matching condition */
        data class OnMatchingConditionChange(val condition: MatchingCondition) : UiEvent()

        /** Update matching operator */
        data class OnMatchingOperatorChange(val operator: MatchingOperator) : UiEvent()

        /** Update matching value */
        data class OnMatchingValueChange(val value: String) : UiEvent()

        /** Toggle a day of week on/off in the selected set */
        data class OnDayToggled(val day: DayOfWeek) : UiEvent()

        /** Update the time range's start */
        data class OnStartTimeChange(val time: LocalTime) : UiEvent()

        /** Update the time range's end */
        data class OnEndTimeChange(val time: LocalTime) : UiEvent()

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

internal fun MatchingLogicContract.ConditionType.displayName(): String = when (this) {
    MatchingLogicContract.ConditionType.CONTENT -> "Content"
    MatchingLogicContract.ConditionType.DAY_OF_WEEK -> "Day of week"
    MatchingLogicContract.ConditionType.TIME_RANGE -> "Time range"
}
