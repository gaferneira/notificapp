package dev.gaferneira.notificapp.features.databrowser.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.repository.DataBrowserRepository
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEffect
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserEvent
import dev.gaferneira.notificapp.features.databrowser.contract.DataBrowserUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.OutputStream
import javax.inject.Inject

/**
 * ViewModel for the Data Browser Screen.
 *
 * Implements MVI pattern per ADR 001:
 * - StateFlow for UI state (filter, stats, delete-confirmation, export-in-progress)
 * - Separate `Flow<PagingData<DataBrowserRow>>` for paginated content, recreated per filter change
 *   via `flatMapLatest` (mirrors `InboxViewModel`)
 * - Channel for effects
 * - Centralized event handling via onEvent()
 *
 * Bulk delete NEVER re-resolves the filter at confirm time: [onBulkDeleteClick] resolves and
 * caches the concrete ID list via `previewDelete`, and [onConfirmBulkDelete] deletes exactly that
 * cached list, per the data-deletion spec's "only the originally-resolved ID set is deleted".
 *
 * Export is a streamed write, so [exportTo] is a plain suspend function the UI calls directly with
 * a real `OutputStream` sink it owns - not routed through [DataBrowserEvent]/the effect channel,
 * since only the UI layer can create an Android file/Uri. See [DataBrowserEffect] doc for the full
 * split of responsibilities.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DataBrowserViewModel @Inject constructor(
    private val dataBrowserRepository: DataBrowserRepository,
) : MviViewModel<DataBrowserUiState, DataBrowserEvent, DataBrowserEffect>(DataBrowserUiState()) {

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

    init {
        loadStats()
    }

    override fun onEvent(event: DataBrowserEvent) {
        when (event) {
            is DataBrowserEvent.OnSearchQueryChange -> updateFilter { copy(searchQuery = event.query) }
            is DataBrowserEvent.OnFilterChange -> {
                setState { copy(filter = event.filter) }
                loadStats()
            }
            is DataBrowserEvent.OnSortChange -> updateFilter { copy(sort = event.sort) }
            DataBrowserEvent.OnRefreshStats -> loadStats()
            is DataBrowserEvent.OnExportClick -> onExportClick(event.format)
            is DataBrowserEvent.OnDeleteRowClick -> onDeleteRowClick(event.valueId)
            DataBrowserEvent.OnBulkDeleteClick -> onBulkDeleteClick()
            DataBrowserEvent.OnConfirmBulkDelete -> onConfirmBulkDelete()
            DataBrowserEvent.OnCancelBulkDelete -> onCancelBulkDelete()
        }
    }

    private inline fun updateFilter(crossinline reducer: DataBrowserFilter.() -> DataBrowserFilter) {
        setState { copy(filter = filter.reducer()) }
        loadStats()
    }

    private fun loadStats() {
        val filter = uiState.value.filter
        setState { copy(isStatsLoading = true) }
        viewModelScope.launch {
            dataBrowserRepository.statistics(filter)
                .onSuccess { stats -> setState { copy(stats = stats, isStatsLoading = false) } }
                .onFailure { e ->
                    Timber.e(e, "Failed to load data browser statistics")
                    setState { copy(isStatsLoading = false) }
                    sendEffect(DataBrowserEffect.ShowError("Failed to load statistics"))
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
        setState { copy(isExporting = true) }
        val result = dataBrowserRepository.exportRows(uiState.value.filter, sink, format)
        setState { copy(isExporting = false) }
        result.onFailure { e ->
            Timber.e(e, "Failed to export data browser rows")
            sendEffect(DataBrowserEffect.ShowError("Failed to export data"))
        }
        return result
    }

    private fun onDeleteRowClick(valueId: String) {
        viewModelScope.launch {
            dataBrowserRepository.deleteById(valueId)
                .onFailure { e ->
                    Timber.e(e, "Failed to delete extracted value $valueId")
                    sendEffect(DataBrowserEffect.ShowError("Failed to delete entry"))
                }
        }
    }

    private fun onBulkDeleteClick() {
        viewModelScope.launch {
            dataBrowserRepository.previewDelete(uiState.value.filter)
                .onSuccess { ids ->
                    previewedDeleteIds = ids
                    setState { copy(pendingDeleteCount = ids.size) }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to preview bulk delete")
                    sendEffect(DataBrowserEffect.ShowError("Failed to preview delete"))
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
                    sendEffect(DataBrowserEffect.ShowSuccess("Deleted $count entr${if (count == 1) "y" else "ies"}"))
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to bulk delete data browser rows")
                    sendEffect(DataBrowserEffect.ShowError("Failed to delete entries"))
                }
        }
    }

    private fun onCancelBulkDelete() {
        previewedDeleteIds = emptyList()
        setState { copy(pendingDeleteCount = null) }
    }
}
