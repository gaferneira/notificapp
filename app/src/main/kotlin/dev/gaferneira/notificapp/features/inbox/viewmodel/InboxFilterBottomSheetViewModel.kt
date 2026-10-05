package dev.gaferneira.notificapp.features.inbox.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilter
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract.AppOption
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Inbox filter bottom sheet.
 *
 * Holds an unapplied [InboxFilterContract.UiState.draft] and derives everything else from it plus
 * the repository:
 * - Options: apps that have notifications, with their notification count.
 * - Live match count for the draft, observed from the same filters the Inbox list uses
 *   (status, apps and the Inbox search text).
 *
 * Draft lifecycle: the draft is hydrated from the applied filter once per sheet opening (the first
 * [InboxFilterContract.UiEvent.Init] after the previous Apply/Dismiss). Re-sending Init only
 * refreshes the live count, so unapplied edits are never wiped. Selected apps that no longer have
 * notifications are kept (listed with 0 notifications) so the user can see and remove them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxFilterBottomSheetViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : MviViewModel<InboxFilterContract.UiState, InboxFilterContract.UiEvent, InboxFilterContract.UiEffect>(
    InboxFilterContract.UiState(),
) {

    private var apps: List<AppInfo> = emptyList()
    private var notificationCounts: Map<String, Int> = emptyMap()
    private var hydrated = false
    private val searchQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            combine(
                notificationRepository.observeAppsWithNotifications(),
                notificationRepository.observeNotificationCountsByApp(),
            ) { appList, counts -> appList to counts }
                .collect { (appList, counts) ->
                    apps = appList
                    notificationCounts = counts
                    refresh()
                }
        }
        viewModelScope.launch {
            combine(uiState.map { it.draft }.distinctUntilChanged(), searchQuery) { draft, query -> draft to query }
                .flatMapLatest { (draft, query) ->
                    notificationRepository.observeFilteredCount(
                        query = query,
                        packageNames = draft.selectedApps.sorted(),
                        isProcessed = draft.status.toIsProcessed(),
                    )
                }
                .collect { count -> setState { copy(matchCount = count) } }
        }
    }

    override fun onEvent(event: InboxFilterContract.UiEvent) {
        when (event) {
            is InboxFilterContract.UiEvent.Init -> initialize(event)
            is InboxFilterContract.UiEvent.OnAppToggle -> editDraft {
                copy(selectedApps = if (event.appPackageName in selectedApps) selectedApps - event.appPackageName else selectedApps + event.appPackageName)
            }
            is InboxFilterContract.UiEvent.OnAppSearchChange -> {
                setState { copy(appSearchQuery = event.query) }
                refresh()
            }
            is InboxFilterContract.UiEvent.OnStatusChange -> editDraft { copy(status = event.status) }
            InboxFilterContract.UiEvent.OnClearAll -> editDraft { InboxFilter() }
            InboxFilterContract.UiEvent.OnApply -> {
                val filter = uiState.value.draft
                hydrated = false
                sendEffect(InboxFilterContract.UiEffect.ApplyFilter(filter))
            }
            InboxFilterContract.UiEvent.OnDismiss -> {
                hydrated = false
                sendEffect(InboxFilterContract.UiEffect.Dismiss)
            }
        }
    }

    private fun initialize(event: InboxFilterContract.UiEvent.Init) {
        searchQuery.update { event.searchQuery }
        if (!hydrated) {
            hydrated = true
            setState { copy(draft = event.currentFilter, appSearchQuery = "") }
        }
        refresh()
    }

    private fun editDraft(edit: InboxFilter.() -> InboxFilter) {
        setState { copy(draft = draft.edit()) }
        refresh()
    }

    /** Recomputes every derived field from the draft and the repository data. */
    private fun refresh() {
        val current = uiState.value
        val query = current.appSearchQuery.trim()
        val allApps = buildAppOptions(current.draft.selectedApps)
        val visible = allApps.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }

        setState {
            copy(
                withNotificationsApps = visible.filter { it.notificationCount > 0 }.toImmutableList(),
                withoutNotificationsApps = visible.filter { it.notificationCount == 0 }.toImmutableList(),
                selectedApps = allApps.filter { it.packageName in draft.selectedApps }.toImmutableList(),
                hasAppOptions = allApps.isNotEmpty(),
            )
        }
    }

    /**
     * Apps with notifications plus (for stale selections) the selected packages themselves, sorted
     * case-insensitively by name.
     */
    private fun buildAppOptions(selected: Set<String>): List<AppOption> {
        val names = LinkedHashMap<String, String>()
        apps.forEach { names.putIfAbsent(it.packageName, it.name) }
        selected.forEach { names.putIfAbsent(it, it) }
        return names
            .map { (pkg, name) -> AppOption(pkg, name, notificationCount = notificationCounts[pkg] ?: 0) }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.packageName }))
    }
}
