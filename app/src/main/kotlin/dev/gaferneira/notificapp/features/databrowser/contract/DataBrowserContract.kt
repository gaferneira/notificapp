package dev.gaferneira.notificapp.features.databrowser.contract

import androidx.paging.LoadState
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataSort
import dev.gaferneira.notificapp.domain.model.DataStatistics
import dev.gaferneira.notificapp.domain.model.ExportFormat

/**
 * UI state for the Data Browser screen. The paged row list itself is exposed as a separate
 * `Flow<PagingData<DataBrowserRow>>` from the ViewModel (mirroring `InboxViewModel`), not stored
 * here - this state only holds filter configuration, stats, and delete-confirmation state.
 *
 * No `core.extraction` imports here (architectureCheck rule 7: contract purity) - `DataBrowserRow`
 * / `DataBrowserFilter` / `DataStatistics` are plain `domain.model` types, not extraction internals.
 */
data class DataBrowserUiState(
    val filter: DataBrowserFilter = DataBrowserFilter(),
    val stats: DataStatistics? = null,
    val isStatsLoading: Boolean = false,
    /** Count of entries a bulk delete would remove, shown in the confirmation dialog. Null = dialog hidden. */
    val pendingDeleteCount: Int? = null,
    val isExporting: Boolean = false,
)

/**
 * True when the user narrowed the list by a rule, app, date bound or a non-blank search. Sort and the
 * (UI-hidden) field-type filter do not count, so "nothing matches" is only reported when the user
 * can actually undo something.
 */
fun DataBrowserFilter.isNarrowed(): Boolean = ruleIds.isNotEmpty() ||
    packageNames.isNotEmpty() ||
    dateFrom != null ||
    dateTo != null ||
    searchQuery.isNotBlank()

/** What the Data list area renders for a given paging refresh state. */
enum class DataListContent { Loading, Error, FilterEmpty, NoData, Rows }

/** Pure branch selection for the Data list; empty states never show while loading (no flicker). */
fun dataListContent(refresh: LoadState, itemCount: Int, filter: DataBrowserFilter): DataListContent = when {
    refresh is LoadState.Loading -> DataListContent.Loading
    refresh is LoadState.Error -> DataListContent.Error
    itemCount > 0 -> DataListContent.Rows
    filter.isNarrowed() -> DataListContent.FilterEmpty
    else -> DataListContent.NoData
}

/**
 * UI Events for DataBrowserScreen.
 */
sealed interface DataBrowserEvent {
    data class OnSearchQueryChange(val query: String) : DataBrowserEvent
    data class OnFilterChange(val filter: DataBrowserFilter) : DataBrowserEvent
    data class OnSortChange(val sort: DataSort) : DataBrowserEvent
    data object OnRefreshStats : DataBrowserEvent

    /** Resets rule, app, date, field-type filters and the search query; keeps the sort. */
    data object OnClearFilters : DataBrowserEvent
    data class OnExportClick(val format: ExportFormat) : DataBrowserEvent
    data class OnDeleteRowClick(val valueId: String) : DataBrowserEvent
    data object OnBulkDeleteClick : DataBrowserEvent
    data object OnConfirmBulkDelete : DataBrowserEvent
    data object OnCancelBulkDelete : DataBrowserEvent
}

/**
 * UI Effects (one-time events) for DataBrowserScreen.
 *
 * Export is a streamed, potentially large write (design.md: "never a single in-memory
 * materialization"), so the effect only tells the UI *when* to open a real file `OutputStream` -
 * it never carries the exported content itself. The UI opens a cache-file sink on
 * [RequestExportSink] and calls `DataBrowserViewModel.exportTo(sink, format)` directly (a plain
 * suspend function, not routed through [DataBrowserEvent]/this channel, since only the UI layer
 * can create an Android file/Uri), then renames the temp file into place and launches the share
 * sheet itself on success - mirroring the file-ownership split already used by
 * `RulesScreen.shareRuleJson`.
 */
sealed interface DataBrowserEffect {
    data class RequestExportSink(val format: ExportFormat) : DataBrowserEffect
    data class ShowError(val message: String) : DataBrowserEffect
    data class ShowSuccess(val message: String) : DataBrowserEffect
}
