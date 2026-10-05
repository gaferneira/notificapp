package dev.gaferneira.notificapp.features.databrowser.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserFilterSheetContract.UiEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserFilterSheetContract.UiEvent
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserFilterSheetContract.UiState
import javax.inject.Inject

/**
 * ViewModel for the Data filter bottom sheet. Holds only the unapplied draft (mirrors
 * `InboxFilterBottomSheetViewModel`): hydrated once per opening, emitted on Apply, discarded on
 * dismiss. There is no live match count by design.
 */
@HiltViewModel
class DataBrowserFilterSheetViewModel @Inject constructor() : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    private var hydrated = false

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Init -> initialize(event.currentFilter)
            is UiEvent.OnRuleToggle -> editDraft { copy(ruleIds = ruleIds.toggled(event.ruleId)) }
            is UiEvent.OnAppToggle -> editDraft { copy(packageNames = packageNames.toggled(event.packageName)) }
            is UiEvent.OnDateRangeChange -> editDraft { copy(dateFrom = event.dateFrom, dateTo = event.dateTo) }
            UiEvent.OnDateRangeClear -> editDraft { copy(dateFrom = null, dateTo = null) }
            UiEvent.OnClearAll -> editDraft { copy(ruleIds = emptyList(), packageNames = emptyList(), dateFrom = null, dateTo = null) }
            UiEvent.OnApply -> {
                hydrated = false
                sendEffect(UiEffect.ApplyFilter(uiState.value.draft))
            }
            UiEvent.OnDismiss -> {
                hydrated = false
                sendEffect(UiEffect.Dismiss)
            }
        }
    }

    private fun initialize(currentFilter: DataBrowserFilter) {
        if (hydrated) return
        hydrated = true
        setState { copy(draft = currentFilter) }
    }

    private fun editDraft(edit: DataBrowserFilter.() -> DataBrowserFilter) {
        setState { copy(draft = draft.edit()) }
    }

    private fun List<String>.toggled(value: String): List<String> = if (value in this) this - value else this + value
}
