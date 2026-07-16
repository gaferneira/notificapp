package dev.gaferneira.notificapp.domain.repository

import androidx.paging.PagingData
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.DataStatistics
import dev.gaferneira.notificapp.domain.model.ExportFormat
import kotlinx.coroutines.flow.Flow
import java.io.OutputStream

/**
 * Repository for browsing, searching, aggregating, exporting, and deleting extracted rule data
 * (the "Data Browser" surface). Separate from [RuleExecutionRepository], which stays scoped to
 * audit/throttle concerns.
 *
 * Every query excludes extractions whose source `RuleExecution.wasDryRun` is true by default.
 */
interface DataBrowserRepository {

    /**
     * Paginated, filtered, sorted browse of extracted data. No free-text search.
     */
    fun browsePaged(filter: DataBrowserFilter): Flow<PagingData<DataBrowserRow>>

    /**
     * Paginated, filtered, sorted browse of extracted data, additionally narrowed by
     * [DataBrowserFilter.searchQuery] via full-text search over `value_text`.
     */
    fun searchPaged(filter: DataBrowserFilter): Flow<PagingData<DataBrowserRow>>

    /**
     * Aggregate statistics (total, this-week, most-active rule, per-rule/per-app counts, trend
     * series) over the data matching [filter]'s rule/app/field-type/date constraints.
     */
    suspend fun statistics(filter: DataBrowserFilter): Result<DataStatistics>

    /**
     * Stream the data matching [filter] to [sink] as [format], one bounded batch at a time so the
     * full result set is never materialized in memory. Snapshots the matching ID set once up
     * front, so the export is immune to concurrent inserts/deletes.
     */
    suspend fun exportRows(filter: DataBrowserFilter, sink: OutputStream, format: ExportFormat): Result<Unit>

    /**
     * Resolve and return the concrete set of value IDs matching [filter], for bulk-delete preview.
     * The returned list is what a subsequent [deleteByIds] call MUST use - callers must not
     * re-resolve the filter at confirm time.
     */
    suspend fun previewDelete(filter: DataBrowserFilter): Result<List<String>>

    /**
     * Delete a single extracted value by ID. A no-op (not a failure) if [valueId] no longer
     * exists.
     */
    suspend fun deleteById(valueId: String): Result<Unit>

    /**
     * Delete exactly the given [ids] (the previously previewed set) inside one transaction.
     * Never re-resolves a filter. Returns the number of rows actually deleted.
     */
    suspend fun deleteByIds(ids: List<String>): Result<Int>
}
