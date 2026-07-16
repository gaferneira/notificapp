package dev.gaferneira.notificapp.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.room.withTransaction
import dev.gaferneira.notificapp.core.data.export.DataCsvSerializer
import dev.gaferneira.notificapp.core.data.export.DataJsonSerializer
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.DataBrowserDao
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.FtsQuerySanitizer
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.DataStatistics
import dev.gaferneira.notificapp.domain.model.ExportFormat
import dev.gaferneira.notificapp.domain.model.TrendPoint
import dev.gaferneira.notificapp.domain.repository.DataBrowserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Implementation of [DataBrowserRepository].
 *
 * Follows Repository Pattern (ADR 005) with `Result<T>` for explicit error handling (ADR 006) and
 * injected coroutine dispatchers for testability (ADR 008). The join/filter/sort/aggregation logic
 * itself lives entirely in [DataBrowserDao]'s raw `@Query`s; this repository composes those calls,
 * shapes the `DataBrowserFilter` into DAO parameter tuples, fills zero-count trend days, and wraps
 * the batched export/bulk-delete flows.
 *
 * `exportRows` receives an already-open [OutputStream] `sink` rather than a file path, so
 * temp-file-then-rename atomicity at the final share-sheet destination is the caller's
 * responsibility (see `design.md`): this method's contract is to snapshot the matching ID set once,
 * stream fixed-size batches without ever materializing the full result set, and surface any
 * failure as [Result.failure] so the caller knows not to rename a partial temp file into place.
 */
internal class DataBrowserRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val dataBrowserDao: DataBrowserDao,
    private val extractedFieldValueDao: ExtractedFieldValueDao,
    private val currentTimeProvider: CurrentTimeProvider,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : DataBrowserRepository {

    override fun browsePaged(filter: DataBrowserFilter): Flow<PagingData<DataBrowserRow>> {
        val params = filter.toQueryParams()
        return pager {
            dataBrowserDao.getFilteredPaged(
                ruleIds = params.ruleIds,
                hasRuleFilter = params.hasRuleFilter,
                packageNames = params.packageNames,
                hasPackageFilter = params.hasPackageFilter,
                fieldTypes = params.fieldTypeNames,
                hasFieldTypeFilter = params.hasFieldTypeFilter,
                dateFrom = params.dateFrom,
                hasDateFromFilter = params.hasDateFromFilter,
                dateTo = params.dateTo,
                hasDateToFilter = params.hasDateToFilter,
                sortKey = filter.sort.ordinal,
            )
        }.flowOn(ioDispatcher)
    }

    override fun searchPaged(filter: DataBrowserFilter): Flow<PagingData<DataBrowserRow>> {
        val params = filter.toQueryParams()
        return pager {
            dataBrowserDao.searchFtsPaged(
                ftsQuery = FtsQuerySanitizer.toMatchExpression(filter.searchQuery),
                ruleIds = params.ruleIds,
                hasRuleFilter = params.hasRuleFilter,
                packageNames = params.packageNames,
                hasPackageFilter = params.hasPackageFilter,
                fieldTypes = params.fieldTypeNames,
                hasFieldTypeFilter = params.hasFieldTypeFilter,
                dateFrom = params.dateFrom,
                hasDateFromFilter = params.hasDateFromFilter,
                dateTo = params.dateTo,
                hasDateToFilter = params.hasDateToFilter,
                sortKey = filter.sort.ordinal,
            )
        }.flowOn(ioDispatcher)
    }

    override suspend fun statistics(filter: DataBrowserFilter): Result<DataStatistics> = withContext(ioDispatcher) {
        dbCatching("Failed to compute data browser statistics") {
            val params = filter.toQueryParams()
            val total = dataBrowserDao.getCount(
                ruleIds = params.ruleIds, hasRuleFilter = params.hasRuleFilter,
                packageNames = params.packageNames, hasPackageFilter = params.hasPackageFilter,
                fieldTypes = params.fieldTypeNames, hasFieldTypeFilter = params.hasFieldTypeFilter,
                dateFrom = params.dateFrom, hasDateFromFilter = params.hasDateFromFilter,
                dateTo = params.dateTo, hasDateToFilter = params.hasDateToFilter,
            )
            val weekStartMillis = startOfCurrentLocalWeekMillis()
            val existingFrom = params.dateFrom.takeIf { params.hasDateFromFilter } ?: Long.MIN_VALUE
            // Intersect with an already-narrower user date filter, so "this week" never returns
            // data outside the base filter the caller requested (e.g. a dateFrom in the future
            // relative to the week start still wins).
            val thisWeek = dataBrowserDao.getCount(
                ruleIds = params.ruleIds, hasRuleFilter = params.hasRuleFilter,
                packageNames = params.packageNames, hasPackageFilter = params.hasPackageFilter,
                fieldTypes = params.fieldTypeNames, hasFieldTypeFilter = params.hasFieldTypeFilter,
                dateFrom = maxOf(existingFrom, weekStartMillis), hasDateFromFilter = true,
                dateTo = params.dateTo, hasDateToFilter = params.hasDateToFilter,
            )
            val mostActiveRuleName = dataBrowserDao.getMostActiveRule(
                ruleIds = params.ruleIds, hasRuleFilter = params.hasRuleFilter,
                packageNames = params.packageNames, hasPackageFilter = params.hasPackageFilter,
                fieldTypes = params.fieldTypeNames, hasFieldTypeFilter = params.hasFieldTypeFilter,
                dateFrom = params.dateFrom, hasDateFromFilter = params.hasDateFromFilter,
                dateTo = params.dateTo, hasDateToFilter = params.hasDateToFilter,
            )?.ruleName
            val perRule = dataBrowserDao.getPerRuleCounts(
                ruleIds = params.ruleIds, hasRuleFilter = params.hasRuleFilter,
                packageNames = params.packageNames, hasPackageFilter = params.hasPackageFilter,
                fieldTypes = params.fieldTypeNames, hasFieldTypeFilter = params.hasFieldTypeFilter,
                dateFrom = params.dateFrom, hasDateFromFilter = params.hasDateFromFilter,
                dateTo = params.dateTo, hasDateToFilter = params.hasDateToFilter,
            )
            val perApp = dataBrowserDao.getPerAppCounts(
                ruleIds = params.ruleIds, hasRuleFilter = params.hasRuleFilter,
                packageNames = params.packageNames, hasPackageFilter = params.hasPackageFilter,
                fieldTypes = params.fieldTypeNames, hasFieldTypeFilter = params.hasFieldTypeFilter,
                dateFrom = params.dateFrom, hasDateFromFilter = params.hasDateFromFilter,
                dateTo = params.dateTo, hasDateToFilter = params.hasDateToFilter,
            )
            val trend = buildTrend(params)
            DataStatistics(
                total = total,
                thisWeek = thisWeek,
                mostActiveRuleName = mostActiveRuleName,
                perRule = perRule,
                perApp = perApp,
                trend = trend,
            )
        }
    }

    override suspend fun exportRows(
        filter: DataBrowserFilter,
        sink: OutputStream,
        format: ExportFormat,
    ): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to export data browser rows") {
            val ids = resolveMatchingIds(filter)
            val writer = sink.bufferedWriter()
            when (format) {
                ExportFormat.CSV -> {
                    DataCsvSerializer.writeHeader(writer)
                    ids.chunked(EXPORT_BATCH_SIZE).forEach { batchIds ->
                        DataCsvSerializer.writeRows(writer, dataBrowserDao.getMatchingBatch(batchIds))
                    }
                }
                ExportFormat.JSON -> {
                    DataJsonSerializer.writeArrayStart(writer)
                    ids.chunked(EXPORT_BATCH_SIZE).forEachIndexed { index, batchIds ->
                        DataJsonSerializer.writeRows(
                            writer,
                            dataBrowserDao.getMatchingBatch(batchIds),
                            isFirstBatch = index == 0,
                        )
                    }
                    DataJsonSerializer.writeArrayEnd(writer)
                }
            }
            writer.flush()
        }
    }

    override suspend fun previewDelete(filter: DataBrowserFilter): Result<List<String>> = withContext(ioDispatcher) {
        dbCatching("Failed to preview data browser bulk delete") { resolveMatchingIds(filter) }
    }

    override suspend fun deleteById(valueId: String): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to delete extracted value $valueId") { extractedFieldValueDao.delete(valueId) }
    }

    override suspend fun deleteByIds(ids: List<String>): Result<Int> = withContext(ioDispatcher) {
        dbCatching("Failed to bulk delete data browser rows") {
            if (ids.isEmpty()) return@dbCatching 0
            // Chunk the bound `IN (:ids)` parameter list the same way exportRows batches reads
            // (EXPORT_BATCH_SIZE) - an unbounded single delete on a large/unfiltered selection can
            // exceed SQLite's bound-parameter limit. The whole delete still stays atomic: every
            // chunk runs inside this one withTransaction block.
            database.withTransaction {
                ids.chunked(EXPORT_BATCH_SIZE).sumOf { chunk -> dataBrowserDao.deleteByIds(chunk) }
            }
        }
    }

    private suspend fun resolveMatchingIds(filter: DataBrowserFilter): List<String> {
        val params = filter.toQueryParams()
        return dataBrowserDao.getMatchingIds(
            ruleIds = params.ruleIds,
            hasRuleFilter = params.hasRuleFilter,
            packageNames = params.packageNames,
            hasPackageFilter = params.hasPackageFilter,
            fieldTypes = params.fieldTypeNames,
            hasFieldTypeFilter = params.hasFieldTypeFilter,
            dateFrom = params.dateFrom,
            hasDateFromFilter = params.hasDateFromFilter,
            dateTo = params.dateTo,
            hasDateToFilter = params.hasDateToFilter,
            ftsQuery = FtsQuerySanitizer.toMatchExpression(filter.searchQuery),
            hasSearchFilter = filter.searchQuery.isNotBlank(),
        )
    }

    /**
     * Day-bucketed trend for the last [TREND_WINDOW_DAYS] local calendar days (oldest first),
     * filling in zero-count days the `GROUP BY` query omits - a superset covering both the 7- and
     * 30-day views the statistics spec asks for; callers wanting the 7-day window take the last 7
     * entries.
     */
    private suspend fun buildTrend(params: DataBrowserQueryParams): List<TrendPoint> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(currentTimeProvider.nowEpochMillis()), zone)
        val windowStart = today.minusDays((TREND_WINDOW_DAYS - 1).toLong())
        val sinceMillis = windowStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val buckets = dataBrowserDao.getTrend(
            params.ruleIds,
            params.hasRuleFilter,
            params.packageNames,
            params.hasPackageFilter,
            params.fieldTypeNames,
            params.hasFieldTypeFilter,
            sinceMillis,
        )
        val countsByDate = buckets.associate { it.date to it.count }
        return (0 until TREND_WINDOW_DAYS).map { offset ->
            val date = windowStart.plusDays(offset.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            TrendPoint(date = date, count = countsByDate[date] ?: 0)
        }
    }

    private fun startOfCurrentLocalWeekMillis(): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(currentTimeProvider.nowEpochMillis()), zone)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun pager(pagingSourceFactory: () -> PagingSource<Int, DataBrowserRow>): Flow<PagingData<DataBrowserRow>> = Pager(
        config = PagingConfig(
            pageSize = PAGE_SIZE,
            prefetchDistance = PREFETCH_DISTANCE,
            enablePlaceholders = false,
            initialLoadSize = INITIAL_LOAD_SIZE,
        ),
        pagingSourceFactory = pagingSourceFactory,
    ).flow

    private companion object {
        const val PAGE_SIZE = 50
        const val PREFETCH_DISTANCE = 100
        const val INITIAL_LOAD_SIZE = 100
        const val TREND_WINDOW_DAYS = 30

        /** Fixed-size ID-set batching for streaming export (design.md, LOCKED). */
        const val EXPORT_BATCH_SIZE = 1000
    }
}

/** DAO-parameter shape shared by every filtered [DataBrowserDao] query. */
private data class DataBrowserQueryParams(
    val ruleIds: List<String>,
    val hasRuleFilter: Boolean,
    val packageNames: List<String>,
    val hasPackageFilter: Boolean,
    val fieldTypeNames: List<String>,
    val hasFieldTypeFilter: Boolean,
    val dateFrom: Long,
    val hasDateFromFilter: Boolean,
    val dateTo: Long,
    val hasDateToFilter: Boolean,
)

private fun DataBrowserFilter.toQueryParams(): DataBrowserQueryParams = DataBrowserQueryParams(
    ruleIds = ruleIds,
    hasRuleFilter = ruleIds.isNotEmpty(),
    packageNames = packageNames,
    hasPackageFilter = packageNames.isNotEmpty(),
    fieldTypeNames = fieldTypes.map { it.name },
    hasFieldTypeFilter = fieldTypes.isNotEmpty(),
    dateFrom = dateFrom ?: 0L,
    hasDateFromFilter = dateFrom != null,
    dateTo = dateTo ?: Long.MAX_VALUE,
    hasDateToFilter = dateTo != null,
)
