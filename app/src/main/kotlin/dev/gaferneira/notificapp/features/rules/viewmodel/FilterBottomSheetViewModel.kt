package dev.gaferneira.notificapp.features.rules.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.features.rules.contract.FilterBottomSheetContract
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the FilterBottomSheet.
 *
 * Manages the state for filtering rules by category and target app.
 * Takes the list of all rules to extract available filter options.
 */
@HiltViewModel
class FilterBottomSheetViewModel @Inject constructor() :
    MviViewModel<FilterBottomSheetContract.UiState, FilterBottomSheetContract.UiEvent, FilterBottomSheetContract.UiEffect>(
        FilterBottomSheetContract.UiState(),
    ) {

    private val allRules = MutableStateFlow<List<Rule>>(emptyList())

    init {
        // Observe allRules and update available categories and apps
        viewModelScope.launch {
            allRules.collectLatest { rules ->
                val categories = rules.mapNotNull { it.category }.distinct().sorted()
                val apps = rules
                    .flatMap { it.targetApps ?: emptyList() }
                    .distinctBy { it.packageName }
                    .sortedBy { it.name }

                setState {
                    copy(
                        availableCategories = categories,
                        availableApps = apps,
                    )
                }
            }
        }
    }

    override fun onEvent(event: FilterBottomSheetContract.UiEvent) {
        when (event) {
            is FilterBottomSheetContract.UiEvent.Init -> initWithFilter(event.currentFilter)
            is FilterBottomSheetContract.UiEvent.OnCategoryToggle -> toggleCategory(event.category)
            is FilterBottomSheetContract.UiEvent.OnAppToggle -> toggleApp(event.appPackageName)
            is FilterBottomSheetContract.UiEvent.OnClearAll -> clearAllFilters()
            is FilterBottomSheetContract.UiEvent.OnApply -> applyFilters()
            is FilterBottomSheetContract.UiEvent.OnDismiss -> dismiss()
        }
    }

    /**
     * Initialize the ViewModel with the current filter and all rules.
     */
    fun initialize(allRules: List<Rule>, currentFilter: RuleFilter) {
        this.allRules.value = allRules
        onEvent(FilterBottomSheetContract.UiEvent.Init(currentFilter))
    }

    private fun initWithFilter(filter: RuleFilter) {
        setState {
            copy(
                selectedCategories = filter.selectedCategories,
                selectedApps = filter.selectedApps,
                statusFilter = filter.status,
                hasActiveFilters = filter.isActive(),
            )
        }
    }

    private fun toggleCategory(category: String) {
        setState {
            val newSelection = if (selectedCategories.contains(category)) {
                selectedCategories - category
            } else {
                selectedCategories + category
            }
            copy(
                selectedCategories = newSelection,
                hasActiveFilters = newSelection.isNotEmpty() || selectedApps.isNotEmpty() || statusFilter != RuleFilter.Status.ALL,
            )
        }
    }

    private fun toggleApp(appPackageName: String) {
        setState {
            val newSelection = if (selectedApps.contains(appPackageName)) {
                selectedApps - appPackageName
            } else {
                selectedApps + appPackageName
            }
            copy(
                selectedApps = newSelection,
                hasActiveFilters = selectedCategories.isNotEmpty() || newSelection.isNotEmpty() || statusFilter != RuleFilter.Status.ALL,
            )
        }
    }

    private fun clearAllFilters() {
        setState {
            copy(
                selectedCategories = emptySet(),
                selectedApps = emptySet(),
                hasActiveFilters = statusFilter != RuleFilter.Status.ALL,
            )
        }
    }

    private fun applyFilters() {
        val currentState = uiState.value
        val filter = RuleFilter(
            status = currentState.statusFilter,
            selectedCategories = currentState.selectedCategories,
            selectedApps = currentState.selectedApps,
        )
        sendEffect(FilterBottomSheetContract.UiEffect.ApplyFilter(filter))
    }

    private fun dismiss() {
        sendEffect(FilterBottomSheetContract.UiEffect.Dismiss)
    }
}
