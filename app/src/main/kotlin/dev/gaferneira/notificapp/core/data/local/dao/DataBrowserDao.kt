package dev.gaferneira.notificapp.core.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import dev.gaferneira.notificapp.domain.model.CountBucket
import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.TrendPoint

/**
 * Data Access Object backing the Data Browser: the joined/filtered/searched/aggregated/bulk-delete
 * surface over `extracted_field_values ⋈ rule_fields ⋈ rule_executions ⋈ rules ⋈ notifications`.
 *
 * Every query excludes dry-run executions (`rule_executions.was_dry_run = 0`) by default. Optional
 * filters use the `IN (:x) OR :hasFilter = 0` idiom from `NotificationDao.getFilteredPaged`. Result
 * ordering can't use a bound parameter as a raw SQL identifier, so [DataBrowserRow]-returning
 * queries select their sort key via a `CASE WHEN :sortKey = <ordinal> THEN ... END` per
 * `DataSort` entry - exactly one branch is non-null for every row (all others are `NULL` for every
 * row, which SQLite orders as ties, i.e. a no-op), with a stable `created_at DESC, id ASC`
 * tie-breaker appended.
 */
@Suppress("LongParameterList") // Room binds @Query params by name only, not via a POJO's nested
// properties, so the 5-dimension DataBrowserFilter (rule/app/field-type/date-from/date-to, each
// with its own IN(:x) OR :hasFilter=0 flag) can't be collapsed into a single bind parameter -
// mirrors the same tradeoff NotificationDao.getFilteredPaged makes at a smaller scale.
@Dao
internal interface DataBrowserDao {

    @Query(ROW_SELECT + FILTER_WHERE + SORT_ORDER)
    fun getFilteredPaged(
        ruleIds: List<String>,
        hasRuleFilter: Boolean,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        fieldTypes: List<String>,
        hasFieldTypeFilter: Boolean,
        dateFrom: Long,
        hasDateFromFilter: Boolean,
        dateTo: Long,
        hasDateToFilter: Boolean,
        sortKey: Int,
    ): PagingSource<Int, DataBrowserRow>

    @Query(ROW_SELECT_WITH_FTS_JOIN + FILTER_WHERE + FTS_MATCH_WHERE + SORT_ORDER)
    fun searchFtsPaged(
        ftsQuery: String,
        ruleIds: List<String>,
        hasRuleFilter: Boolean,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        fieldTypes: List<String>,
        hasFieldTypeFilter: Boolean,
        dateFrom: Long,
        hasDateFromFilter: Boolean,
        dateTo: Long,
        hasDateToFilter: Boolean,
        sortKey: Int,
    ): PagingSource<Int, DataBrowserRow>

    @Query("SELECT COUNT(*) $AGGREGATE_JOIN_NO_RULE $FILTER_WHERE")
    suspend fun getCount(
        ruleIds: List<String>,
        hasRuleFilter: Boolean,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        fieldTypes: List<String>,
        hasFieldTypeFilter: Boolean,
        dateFrom: Long,
        hasDateFromFilter: Boolean,
        dateTo: Long,
        hasDateToFilter: Boolean,
    ): Int

    @Query(
        """
        SELECT r.name AS ruleName, COUNT(*) AS count
        $AGGREGATE_JOIN_WITH_RULE
        $FILTER_WHERE
        GROUP BY re.rule_id, r.name
        ORDER BY count DESC, re.rule_id ASC
        LIMIT 1
        """,
    )
    suspend fun getMostActiveRule(
        ruleIds: List<String>,
        hasRuleFilter: Boolean,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        fieldTypes: List<String>,
        hasFieldTypeFilter: Boolean,
        dateFrom: Long,
        hasDateFromFilter: Boolean,
        dateTo: Long,
        hasDateToFilter: Boolean,
    ): MostActiveRuleRow?

    @Query(
        """
        SELECT r.name AS label, COUNT(*) AS count
        $AGGREGATE_JOIN_WITH_RULE
        $FILTER_WHERE
        GROUP BY re.rule_id, r.name
        ORDER BY count DESC, re.rule_id ASC
        """,
    )
    suspend fun getPerRuleCounts(
        ruleIds: List<String>,
        hasRuleFilter: Boolean,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        fieldTypes: List<String>,
        hasFieldTypeFilter: Boolean,
        dateFrom: Long,
        hasDateFromFilter: Boolean,
        dateTo: Long,
        hasDateToFilter: Boolean,
    ): List<CountBucket>

    @Query(
        """
        SELECT n.app_name AS label, COUNT(*) AS count
        $AGGREGATE_JOIN_NO_RULE
        $FILTER_WHERE
        GROUP BY n.package_name, n.app_name
        ORDER BY count DESC, n.package_name ASC
        """,
    )
    suspend fun getPerAppCounts(
        ruleIds: List<String>,
        hasRuleFilter: Boolean,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        fieldTypes: List<String>,
        hasFieldTypeFilter: Boolean,
        dateFrom: Long,
        hasDateFromFilter: Boolean,
        dateTo: Long,
        hasDateToFilter: Boolean,
    ): List<CountBucket>

    // LOCKED trend bucketing expression (design.md): buckets by the user's local calendar day.
    @Query(
        """
        SELECT strftime('%Y-%m-%d', re.created_at / 1000, 'unixepoch', 'localtime') AS date, COUNT(*) AS count
        $AGGREGATE_JOIN_NO_RULE
        WHERE re.was_dry_run = 0
        AND (re.rule_id IN (:ruleIds) OR :hasRuleFilter = 0)
        AND (n.package_name IN (:packageNames) OR :hasPackageFilter = 0)
        AND (rf.field_type IN (:fieldTypes) OR :hasFieldTypeFilter = 0)
        AND re.created_at >= :sinceMillis
        GROUP BY date
        ORDER BY date ASC
        """,
    )
    suspend fun getTrend(
        ruleIds: List<String>,
        hasRuleFilter: Boolean,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        fieldTypes: List<String>,
        hasFieldTypeFilter: Boolean,
        sinceMillis: Long,
    ): List<TrendPoint>

    /**
     * Resolve the concrete set of value IDs matching the given filter (search included via an FTS
     * subquery, since - unlike [searchFtsPaged] - this single method serves both filtered and
     * unfiltered-search callers). Drives both bulk-delete preview and the export ID snapshot.
     */
    @Query(
        ID_SELECT + FILTER_WHERE +
            """
            AND (:hasSearchFilter = 0 OR efv.rowid IN (
                SELECT rowid FROM extracted_field_values_fts WHERE extracted_field_values_fts MATCH :ftsQuery
            ))
            ORDER BY re.created_at DESC, efv.id ASC
            """,
    )
    suspend fun getMatchingIds(
        ruleIds: List<String>,
        hasRuleFilter: Boolean,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        fieldTypes: List<String>,
        hasFieldTypeFilter: Boolean,
        dateFrom: Long,
        hasDateFromFilter: Boolean,
        dateTo: Long,
        hasDateToFilter: Boolean,
        ftsQuery: String,
        hasSearchFilter: Boolean,
    ): List<String>

    /**
     * Read one fixed-size slice of a pre-resolved [getMatchingIds] snapshot, for streaming export.
     * Never re-evaluates the original filter - immune to concurrent inserts/deletes after the
     * snapshot was taken.
     */
    @Query("$ROW_SELECT WHERE efv.id IN (:batchOfIds) ORDER BY re.created_at DESC, efv.id ASC")
    suspend fun getMatchingBatch(batchOfIds: List<String>): List<DataBrowserRow>

    /**
     * Delete exactly the given (previously previewed/snapshotted) IDs. Returns the number of rows
     * actually deleted.
     */
    @Query("DELETE FROM extracted_field_values WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int
}

/** Projection for [DataBrowserDao.getMostActiveRule]. */
internal data class MostActiveRuleRow(val ruleName: String, val count: Int)

private const val ROW_SELECT = """
    SELECT
        efv.id AS valueId,
        efv.rule_execution_id AS executionId,
        r.name AS ruleName,
        n.package_name AS packageName,
        n.app_name AS appName,
        rf.name AS fieldName,
        rf.field_type AS fieldType,
        efv.value_text AS valueText,
        efv.value_number AS valueNumber,
        efv.value_date AS valueDate,
        n.title AS notificationTitle,
        n.content AS notificationContent,
        re.created_at AS createdAt
    FROM extracted_field_values efv
    JOIN rule_fields rf ON efv.rule_field_id = rf.id
    JOIN rule_executions re ON efv.rule_execution_id = re.id
    JOIN rules r ON re.rule_id = r.id
    JOIN notifications n ON re.notification_id = n.id
"""

private const val ROW_SELECT_WITH_FTS_JOIN = ROW_SELECT +
    " JOIN extracted_field_values_fts fts ON efv.rowid = fts.rowid "

/** Base join for aggregation queries that need the rule name (per-rule counts, most-active). */
private const val AGGREGATE_JOIN_WITH_RULE = """
    FROM extracted_field_values efv
    JOIN rule_fields rf ON efv.rule_field_id = rf.id
    JOIN rule_executions re ON efv.rule_execution_id = re.id
    JOIN rules r ON re.rule_id = r.id
    JOIN notifications n ON re.notification_id = n.id
"""

/** Base join for aggregation/count queries that don't need the rule name. */
private const val AGGREGATE_JOIN_NO_RULE = """
    FROM extracted_field_values efv
    JOIN rule_fields rf ON efv.rule_field_id = rf.id
    JOIN rule_executions re ON efv.rule_execution_id = re.id
    JOIN notifications n ON re.notification_id = n.id
"""

private const val ID_SELECT = """
    SELECT efv.id
    FROM extracted_field_values efv
    JOIN rule_fields rf ON efv.rule_field_id = rf.id
    JOIN rule_executions re ON efv.rule_execution_id = re.id
    JOIN notifications n ON re.notification_id = n.id
"""

/**
 * Shared optional-filter predicate: rule / source app / field type / date range, plus the
 * mandatory dry-run exclusion. Every query above joins `efv`, `rf`, `re`, and `n` under those
 * aliases, so this fragment is reusable verbatim regardless of whether `rules r` is also joined.
 */
private const val FILTER_WHERE = """
    WHERE re.was_dry_run = 0
    AND (re.rule_id IN (:ruleIds) OR :hasRuleFilter = 0)
    AND (n.package_name IN (:packageNames) OR :hasPackageFilter = 0)
    AND (rf.field_type IN (:fieldTypes) OR :hasFieldTypeFilter = 0)
    AND (re.created_at >= :dateFrom OR :hasDateFromFilter = 0)
    AND (re.created_at <= :dateTo OR :hasDateToFilter = 0)
"""

private const val FTS_MATCH_WHERE = " AND extracted_field_values_fts MATCH :ftsQuery "

private const val SORT_ORDER = """
    ORDER BY
        CASE WHEN :sortKey = 0 THEN re.created_at END DESC,
        CASE WHEN :sortKey = 1 THEN re.created_at END ASC,
        CASE WHEN :sortKey = 2 THEN r.name END ASC,
        CASE WHEN :sortKey = 3 THEN r.name END DESC,
        CASE WHEN :sortKey = 4 THEN n.app_name END ASC,
        CASE WHEN :sortKey = 5 THEN n.app_name END DESC,
        CASE WHEN :sortKey = 6 THEN rf.name END ASC,
        CASE WHEN :sortKey = 7 THEN rf.name END DESC,
        re.created_at DESC,
        efv.id ASC
"""
