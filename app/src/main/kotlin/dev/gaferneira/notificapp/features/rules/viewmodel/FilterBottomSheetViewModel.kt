package dev.gaferneira.notificapp.features.rules.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the FilterBottomSheet.
 *
 * Manages the state for filtering rules by category and target app.
 * Takes the list of all rules to extract available filter options, and also observes
 * monitored apps so exclude-only apps remain selectable as filter facets.
 */
@HiltViewModel
class FilterBottomSheetViewModel @Inject constructor(
    private val selectedAppRepository: SelectedAppRepository,
) : MviViewModel<RulesFilterContract.UiState, RulesFilterContract.UiEvent, RulesFilterContract.UiEffect>(
    RulesFilterContract.UiState(),
) {

    private val allRules = MutableStateFlow<ImmutableList<Rule>>(persistentListOf())

    init {
        // Observe allRules and monitored apps, computing available apps as the union of
        // rule-referenced apps and monitored apps so exclude-only apps remain filterable.
        viewModelScope.launch {
            combine(
                allRules,
                selectedAppRepository.observeEnabledApps(),
            ) { rules, monitoredApps ->
                Pair(rules, monitoredApps)
            }.collectLatest { (rules, monitoredApps) ->
                val categories = rules.mapNotNull { it.category }.distinct().sorted()
                val ruleApps = rules
                    .flatMap { it.targetApps ?: emptyList() }
                val monitoredAppInfos = monitoredApps.map { AppInfo(it.packageName, it.appName) }
                val apps = (ruleApps + monitoredAppInfos)
                    .distinctBy { it.packageName }
                    .sortedBy { it.name }

                setState {
                    copy(
                        availableCategories = categories,
                        availableApps = apps.toImmutableList(),
                    )
                }
            }
        }
    }

    override fun onEvent(event: RulesFilterContract.UiEvent) {
        when (event) {
            is RulesFilterContract.UiEvent.Init -> initialize(event.allRules, event.currentFilter)
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
    private fun initialize(allRules: ImmutableList<Rule>, currentFilter: RuleFilter) {
        this.allRules.value = allRules
        setState {
            copy(
                selectedCategories = currentFilter.selectedCategories,
                selectedApps = currentFilter.selectedApps,
                statusFilter = currentFilter.status,
                sortBy = currentFilter.sortBy,
                hasActiveFilters = currentFilter.isActive(),
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
