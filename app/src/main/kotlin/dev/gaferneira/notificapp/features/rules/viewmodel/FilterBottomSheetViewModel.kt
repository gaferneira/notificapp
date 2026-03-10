package dev.gaferneira.notificapp.features.rules.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract
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
    MviViewModel<RulesFilterContract.UiState, RulesFilterContract.UiEvent, RulesFilterContract.UiEffect>(
        RulesFilterContract.UiState(),
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

    override fun onEvent(event: RulesFilterContract.UiEvent) {
        when (event) {
            is RulesFilterContract.UiEvent.Init -> initWithFilter(event.currentFilter)
            is RulesFilterContract.UiEvent.OnCategoryToggle -> toggleCategory(event.category)
            is RulesFilterContract.UiEvent.OnAppToggle -> toggleApp(event.appPackageName)
            is RulesFilterContract.UiEvent.OnStatusChange -> changeStatus(event.status)
            is RulesFilterContract.UiEvent.OnSortChange -> changeSort(event.sortBy)
            is RulesFilterContract.UiEvent.OnClearAll -> clearAllFilters()
            is RulesFilterContract.UiEvent.OnApply -> applyFilters()
            is RulesFilterContract.UiEvent.OnDismiss -> dismiss()
        }
    }

    /**
     * Initialize the ViewModel with the current filter and all rules.
     */
    fun initialize(allRules: List<Rule>, currentFilter: RuleFilter) {
        this.allRules.value = allRules
        onEvent(RulesFilterContract.UiEvent.Init(currentFilter))
    }

    private fun initWithFilter(filter: RuleFilter) {
        setState {
            copy(
                selectedCategories = filter.selectedCategories,
                selectedApps = filter.selectedApps,
                statusFilter = filter.status,
                sortBy = filter.sortBy,
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
                hasActiveFilters = newSelection.isNotEmpty() ||
                    selectedApps.isNotEmpty() ||
                    statusFilter != RuleFilter.Status.ALL ||
                    sortBy != RuleFilter.SortBy.CATEGORY_ASC,
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
                hasActiveFilters = selectedCategories.isNotEmpty() ||
                    newSelection.isNotEmpty() ||
                    statusFilter != RuleFilter.Status.ALL ||
                    sortBy != RuleFilter.SortBy.CATEGORY_ASC,
            )
        }
    }

    private fun changeStatus(status: RuleFilter.Status) {
        setState {
            copy(
                statusFilter = status,
                hasActiveFilters = status != RuleFilter.Status.ALL ||
                    selectedCategories.isNotEmpty() ||
                    selectedApps.isNotEmpty() ||
                    sortBy != RuleFilter.SortBy.CATEGORY_ASC,
            )
        }
    }

    private fun changeSort(sortBy: RuleFilter.SortBy) {
        setState {
            copy(
                sortBy = sortBy,
                hasActiveFilters = statusFilter != RuleFilter.Status.ALL ||
                    selectedCategories.isNotEmpty() ||
                    selectedApps.isNotEmpty() ||
                    sortBy != RuleFilter.SortBy.CATEGORY_ASC,
            )
        }
    }

    private fun clearAllFilters() {
        setState {
            copy(
                selectedCategories = emptySet(),
                selectedApps = emptySet(),
                statusFilter = RuleFilter.Status.ALL,
                sortBy = RuleFilter.SortBy.CATEGORY_ASC,
                hasActiveFilters = false,
            )
        }
    }

    private fun applyFilters() {
        val currentState = uiState.value
        val filter = RuleFilter(
            status = currentState.statusFilter,
            selectedCategories = currentState.selectedCategories,
            selectedApps = currentState.selectedApps,
            sortBy = currentState.sortBy,
        )
        sendEffect(RulesFilterContract.UiEffect.ApplyFilter(filter))
    }

    private fun dismiss() {
        sendEffect(RulesFilterContract.UiEffect.Dismiss)
    }
}
