package dev.gaferneira.notificapp.features.databrowser.contract

import dev.gaferneira.notificapp.domain.model.DataBrowserFilter

/**
 * MVI Contract for the Data filter bottom sheet.
 *
 * The sheet edits an unapplied [UiState.draft]; nothing reaches the Data screen until
 * [UiEvent.OnApply]. Rule and app options are owned by `DataBrowserViewModel` and passed to the
 * sheet as parameters, so this contract only carries the draft.
 */
object DataBrowserFilterSheetContract {

    data class UiState(
        /** The unapplied filter being edited. Search and sort ride along untouched. */
        val draft: DataBrowserFilter = DataBrowserFilter(),
    )

    sealed class UiEvent {
        /**
         * Provide the applied filter. The draft is hydrated once per sheet opening; later Init
         * events never discard unapplied edits.
         */
        data class Init(val currentFilter: DataBrowserFilter) : UiEvent()

        data class OnRuleToggle(val ruleId: String) : UiEvent()

        data class OnAppToggle(val packageName: String) : UiEvent()

        /** Inclusive epoch-millis bounds, already mapped from the picker via `DataDateRange`. */
        data class OnDateRangeChange(val dateFrom: Long, val dateTo: Long) : UiEvent()

        data object OnDateRangeClear : UiEvent()

        /** Clears rule, app and date selections of the draft. */
        data object OnClearAll : UiEvent()

        data object OnApply : UiEvent()

        /** Dismiss without applying. */
        data object OnDismiss : UiEvent()
    }

    sealed class UiEffect {
        data object Dismiss : UiEffect()

        data class ApplyFilter(val filter: DataBrowserFilter) : UiEffect()
    }
}
