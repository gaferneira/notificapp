package dev.gaferneira.notificapp.features.databrowser.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.model.saveDataFields
import dev.gaferneira.notificapp.domain.repository.DataBrowserRepository
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEvent
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserUiState
import dev.gaferneira.notificapp.features.databrowser.contract.FilterOption
import dev.gaferneira.notificapp.features.databrowser.contract.without
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.OutputStream

/**
 * ViewModel for the Data Browser Screen.
 *
 * Implements MVI pattern per ADR 001:
 * - StateFlow for UI state (filter, delete-confirmation, export-in-progress)
 * - Separate `Flow<PagingData<DataBrowserRow>>` for paginated content, recreated per filter change
 *   via `flatMapLatest` (mirrors `InboxViewModel`)
 * - Channel for effects
 * - Centralized event handling via onEvent()
 *
 * Bulk delete NEVER re-resolves the filter at confirm time: [onBulkDeleteClick] resolves and
 * caches the concrete ID list via `previewDelete`, and [onConfirmBulkDelete] deletes exactly that
 * cached list, per the data-deletion spec's "only the originally-resolved ID set is deleted".
 *
 * Single-row delete is deferred behind an Undo window: [onDeleteRowClick] only hides the row (via
 * [hiddenValueIds], filtered out of [rows]) and offers Undo; the repository delete happens when the
 * [UNDO_WINDOW_MS] timer owned by this ViewModel fires (so a rotation never commits early), on
 * [DataBrowserEvent.OnDeleteCommitted] (snackbar dismissed), when another delete starts, before an export
 * or bulk-delete preview (so the hidden row is not exported or counted), or in [onCleared]. Undo
 * restores the row without touching the repository.
 *
 * Export is a streamed write, so [exportTo] is a plain suspend function the UI calls directly with
 * a real `OutputStream` sink it owns - not routed through [DataBrowserEvent]/the effect channel,
 * since only the UI layer can create an Android file/Uri. See [DataBrowserEffect] doc for the full
 * split of responsibilities.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = DataBrowserViewModel.Factory::class)
class DataBrowserViewModel @AssistedInject constructor(
    @Assisted visitArgs: DataBrowserVisitArgs,
    private val savedStateHandle: SavedStateHandle,
    private val dataBrowserRepository: DataBrowserRepository,
    private val ruleRepository: RuleRepository,
    private val notificationRepository: NotificationRepository,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<DataBrowserUiState, DataBrowserEvent, DataBrowserEffect>(
    DataBrowserUiState(
        filter = DataBrowserFilter(
            ruleIds = listOfNotNull(savedStateHandle.resolveRuleId(visitArgs.ruleId)),
            dateFrom = savedStateHandle.resolveSince(visitArgs.since),
        ),
    ),
) {

    @AssistedFactory
    interface Factory {
        /**
         * [DataBrowserVisitArgs.ruleId] pre-filters the list to that rule and [DataBrowserVisitArgs.since] (inclusive epoch millis) to
         * data from then on, with no upper bound. Both are removable chips, seeded only on first creation: their
         * current values live in the `SavedStateHandle` so a removed chip stays removed after process death.
         */
        fun create(visitArgs: DataBrowserVisitArgs): DataBrowserViewModel
    }

    /** Rows swiped away: hidden from [rows] immediately, deleted for real once committed. */
    private val hiddenValueIds = MutableStateFlow<Set<String>>(emptySet())

    /** The single row whose delete is still inside its Undo window, if any. */
    private var pendingDeleteId: String? = null

    /** Fires [commitPendingDelete] once the Undo window of [pendingDeleteId] elapses. */
    private var undoTimerJob: Job? = null

    /** The last ID list resolved by [onBulkDeleteClick]; [onConfirmBulkDelete] deletes exactly this set. */
    private var previewedDeleteIds: List<String> = emptyList()

    /**
     * The paginated row stream. Recreated whenever [DataBrowserUiState.filter] changes via
     * `flatMapLatest`; a blank [DataBrowserFilter.searchQuery] uses the plain filtered browse
     * query, a non-blank one uses the FTS-backed search query.
     */
    val rows: Flow<PagingData<DataBrowserRow>> =
        uiState
            .map { it.filter }
            .distinctUntilChanged()
            .flatMapLatest { filter ->
                if (filter.searchQuery.isBlank()) {
                    dataBrowserRepository.browsePaged(filter)
                } else {
                    dataBrowserRepository.searchPaged(filter)
                }
            }
            .cachedIn(viewModelScope)
            .combine(hiddenValueIds) { paging, hidden ->
                if (hidden.isEmpty()) paging else paging.filter { it.valueId !in hidden }
            }

    init {
        observeFilterOptions()
    }

    override fun onEvent(event: DataBrowserEvent) {
        when (event) {
            is DataBrowserEvent.OnSearchQueryChange -> updateFilter { copy(searchQuery = event.query) }
            is DataBrowserEvent.OnFilterChange -> updateFilter { event.filter }
            is DataBrowserEvent.OnSortChange -> updateFilter { copy(sort = event.sort) }
            is DataBrowserEvent.OnRemoveFilterChip -> updateFilter { without(event.chip) }
            DataBrowserEvent.OnClearFilters -> updateFilter { DataBrowserFilter(sort = sort) }
            is DataBrowserEvent.OnExportClick -> onExportClick(event.format)
            is DataBrowserEvent.OnDeleteRowClick -> onDeleteRowClick(event.valueId)
            is DataBrowserEvent.OnUndoDelete -> onUndoDelete(event.valueId)
            is DataBrowserEvent.OnDeleteCommitted -> commitPendingDelete(event.valueId)
            DataBrowserEvent.OnBulkDeleteClick -> onBulkDeleteClick()
            DataBrowserEvent.OnConfirmBulkDelete -> onConfirmBulkDelete()
            DataBrowserEvent.OnCancelBulkDelete -> onCancelBulkDelete()
        }
    }

    private inline fun updateFilter(crossinline reducer: DataBrowserFilter.() -> DataBrowserFilter) {
        val newFilter = uiState.value.filter.reducer()
        setState { copy(filter = newFilter) }
        // Only the route-seeded dimensions are restorable; keep them current so removed chips stay removed.
        savedStateHandle[KEY_RULE_ID] = newFilter.ruleIds.singleOrNull()
        savedStateHandle[KEY_SINCE] = newFilter.dateFrom.takeIf { newFilter.dateTo == null }
    }

    /**
     * Feeds the filter sheet: rules limited to those that extract at least one field (so every
     * offered rule can return results) and apps that have captured notifications.
     */
    private fun observeFilterOptions() {
        viewModelScope.launch {
            ruleRepository.observeAllRules().collect { rules ->
                val options = rules
                    .filter { it.saveDataFields().isNotEmpty() }
                    .map { FilterOption(id = it.id, label = it.name) }
                    .sortedBy { it.label.lowercase() }
                setState { copy(ruleOptions = options.toImmutableList()) }
            }
        }
        viewModelScope.launch {
            notificationRepository.observeAppsWithNotifications().collect { apps ->
                val options = apps
                    .map { FilterOption(id = it.packageName, label = it.name) }
                    .sortedBy { it.label.lowercase() }
                setState { copy(appOptions = options.toImmutableList()) }
            }
        }
    }

    private fun onExportClick(format: ExportFormat) {
        sendEffect(DataBrowserEffect.RequestExportSink(format))
    }

    /**
     * Streams the currently filtered data to [sink] as [format] via the repository. Called
     * directly by the UI (not through [onEvent]) once it has opened a real cache-file
     * `OutputStream` in response to [DataBrowserEffect.RequestExportSink].
     */
    suspend fun exportTo(sink: OutputStream, format: ExportFormat): Result<Unit> {
        // A row hidden by a pending Undo must not leak into the export: make that delete final first.
        commitPendingNow()
        setState { copy(isExporting = true) }
        val result = try {
            dataBrowserRepository.exportRows(uiState.value.filter, sink, format)
        } finally {
            setState { copy(isExporting = false) }
        }
        result.onFailure { e ->
            Timber.e(e, "Failed to export data browser rows")
            sendEffect(DataBrowserEffect.ShowError(UiText.StringResource(R.string.data_error_export)))
        }
        return result
    }

    private fun onDeleteRowClick(valueId: String) {
        // A second swipe closes the previous Undo window: the first delete becomes final.
        pendingDeleteId?.let { commitPendingDelete(it) }
        pendingDeleteId = valueId
        hiddenValueIds.value += valueId
        sendEffect(DataBrowserEffect.ShowUndoDelete(valueId))
        undoTimerJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            commitPendingDelete(valueId)
        }
    }

    private fun onUndoDelete(valueId: String) {
        if (pendingDeleteId != valueId) return
        pendingDeleteId = null
        undoTimerJob?.cancel()
        hiddenValueIds.value -= valueId
    }

    /** Deletes [valueId] if it is still the pending one; stale commits (already committed/undone) are ignored. */
    private fun commitPendingDelete(valueId: String) {
        if (pendingDeleteId != valueId) return
        pendingDeleteId = null
        undoTimerJob?.cancel()
        viewModelScope.launch { deleteAndReport(valueId) }
    }

    /** Commits the pending delete (if any) and waits for it, so callers read a repository without the hidden row. */
    private suspend fun commitPendingNow() {
        val id = pendingDeleteId ?: return
        pendingDeleteId = null
        undoTimerJob?.cancel()
        deleteAndReport(id)
    }

    private suspend fun deleteAndReport(valueId: String) {
        dataBrowserRepository.deleteById(valueId)
            .onFailure { e ->
                Timber.e(e, "Failed to delete extracted value $valueId")
                hiddenValueIds.value -= valueId
                sendEffect(DataBrowserEffect.ShowError(UiText.StringResource(R.string.data_error_delete)))
            }
    }

    /** `viewModelScope` is already cancelled here, so a still-pending delete is committed on a detached scope. */
    override fun onCleared() {
        pendingDeleteId?.let { id ->
            pendingDeleteId = null
            CoroutineScope(SupervisorJob() + ioDispatcher).launch {
                dataBrowserRepository.deleteById(id)
                    .onFailure { e -> Timber.e(e, "Failed to delete extracted value $id on clear") }
            }
        }
        super.onCleared()
    }

    private fun onBulkDeleteClick() {
        viewModelScope.launch {
            commitPendingNow()
            dataBrowserRepository.previewDelete(uiState.value.filter)
                .onSuccess { ids ->
                    previewedDeleteIds = ids
                    setState { copy(pendingDeleteCount = ids.size) }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to preview bulk delete")
                    sendEffect(DataBrowserEffect.ShowError(UiText.StringResource(R.string.data_error_preview_delete)))
                }
        }
    }

    /**
     * Deletes exactly [previewedDeleteIds] - the set resolved by the most recent
     * [onBulkDeleteClick] - never re-resolving the filter, per the data-deletion spec.
     */
    private fun onConfirmBulkDelete() {
        val ids = previewedDeleteIds
        viewModelScope.launch {
            setState { copy(pendingDeleteCount = null) }
            dataBrowserRepository.deleteByIds(ids)
                .onSuccess { count ->
                    previewedDeleteIds = emptyList()
                    sendEffect(DataBrowserEffect.ShowBulkDeleted(count))
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to bulk delete data browser rows")
                    sendEffect(DataBrowserEffect.ShowError(UiText.StringResource(R.string.data_error_delete_entries)))
                }
        }
    }

    private fun onCancelBulkDelete() {
        previewedDeleteIds = emptyList()
        setState { copy(pendingDeleteCount = null) }
    }
}

/** The route's per-visit arguments: an optional rule pre-filter and an optional date lower bound. */
data class DataBrowserVisitArgs(val ruleId: String? = null, val since: Long? = null)

/** How long a swiped-away row can be restored with Undo before the delete becomes final. */
const val UNDO_WINDOW_MS = 5_000L

// File-scoped so the constructor's super-call argument can reach them.
private const val KEY_RULE_ID = "data_browser_rule_id"
private const val KEY_SINCE = "data_browser_since"

/** Seeds the route's rule only on first creation; afterwards the saved value (possibly removed) wins. */
private fun SavedStateHandle.resolveRuleId(initialRuleId: String?): String? {
    if (!contains(KEY_RULE_ID)) set(KEY_RULE_ID, initialRuleId)
    return get(KEY_RULE_ID)
}

/** Same first-creation-only seeding as [resolveRuleId], for the route's date lower bound. */
private fun SavedStateHandle.resolveSince(initialSince: Long?): Long? {
    if (!contains(KEY_SINCE)) set(KEY_SINCE, initialSince)
    return get(KEY_SINCE)
}
