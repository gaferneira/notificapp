package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.domain.model.RuleActivity
import dev.gaferneira.notificapp.domain.model.TrendPoint
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for rule executions.
 *
 * Provides CRUD operations and queries for tracking when rules match notifications.
 */
@Dao
internal interface RuleExecutionDao {

    /**
     * Observe all rule executions as a Flow, ordered by most recent first.
     */
    @Query("SELECT * FROM rule_executions ORDER BY created_at DESC")
    fun observeAll(): Flow<List<RuleExecutionEntity>>

    /**
     * Get all rule executions.
     */
    @Query("SELECT * FROM rule_executions ORDER BY created_at DESC")
    suspend fun getAll(): List<RuleExecutionEntity>

    /**
     * Get executions for a specific notification.
     */
    @Query("SELECT * FROM rule_executions WHERE notification_id = :notificationId ORDER BY created_at DESC")
    suspend fun getExecutionsForNotification(notificationId: String): List<RuleExecutionEntity>

    /**
     * Get executions for a specific notification as a Flow, each with its extracted field values.
     *
     * `@Relation` makes Room run one extra `extracted_field_values ... WHERE rule_execution_id IN (...)`
     * query for the whole page of executions (inside one transaction), so there is no per-execution
     * fan-out. The flow re-emits when either table changes.
     */
    @Transaction
    @Query("SELECT * FROM rule_executions WHERE notification_id = :notificationId ORDER BY created_at DESC")
    fun observeExecutionsForNotification(notificationId: String): Flow<List<RuleExecutionWithValues>>

    /**
     * Get executions for a specific rule.
     */
    @Query("SELECT * FROM rule_executions WHERE rule_id = :ruleId ORDER BY created_at DESC")
    suspend fun getExecutionsForRule(ruleId: String): List<RuleExecutionEntity>

    /**
     * Get executions for a specific rule as a Flow.
     */
    @Query("SELECT * FROM rule_executions WHERE rule_id = :ruleId ORDER BY created_at DESC")
    fun observeExecutionsForRule(ruleId: String): Flow<List<RuleExecutionEntity>>

    /**
     * Get executions for notifications from a specific source app, created at or after [since]
     * (epoch millis). Used by the throttle tracker's DB lookback fallback - bounded to at most
     * one window's worth of rows via the `created_at` index, then filtered by action id in
     * Kotlin since a single action id is unique to one rule.
     */
    @Query(
        """
        SELECT re.* FROM rule_executions re
        INNER JOIN notifications n ON re.notification_id = n.id
        WHERE n.package_name = :packageName AND re.created_at >= :since
        ORDER BY re.created_at DESC
        """,
    )
    suspend fun getRecentExecutionsForPackageSince(packageName: String, since: Long): List<RuleExecutionEntity>

    /**
     * Insert a rule execution, or update it in place when the id already exists.
     *
     * Must be an in-place upsert, never `OnConflictStrategy.REPLACE`: REPLACE deletes the parent row
     * first, which would cascade-delete the execution's `extracted_field_values`.
     */
    @Upsert
    suspend fun upsert(execution: RuleExecutionEntity)

    /**
     * Insert or update multiple rule executions in place (see [upsert]).
     */
    @Upsert
    suspend fun upsertAll(executions: List<RuleExecutionEntity>)

    /**
     * Delete executions for a specific rule.
     */
    @Query("DELETE FROM rule_executions WHERE rule_id = :ruleId")
    suspend fun deleteExecutionsForRule(ruleId: String)

    /**
     * Delete a specific execution by ID.
     */
    @Query("DELETE FROM rule_executions WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Delete all executions older than a given timestamp.
     */
    @Query("DELETE FROM rule_executions WHERE created_at < :timestamp")
    suspend fun deleteExecutionsOlderThan(timestamp: Long)

    /**
     * Get count of total executions.
     */
    @Query("SELECT COUNT(*) FROM rule_executions")
    suspend fun getCount(): Int

    /**
     * Get count of executions for a specific rule.
     */
    @Query("SELECT COUNT(*) FROM rule_executions WHERE rule_id = :ruleId")
    suspend fun getCountForRule(ruleId: String): Int

    /**
     * Get count of executions for a specific notification.
     */
    @Query("SELECT COUNT(*) FROM rule_executions WHERE notification_id = :notificationId")
    suspend fun getCountForNotification(notificationId: String): Int

    /**
     * Count executions recorded at or after [since] (epoch millis), excluding dry-run rules -
     * Home's "This Week" stats row only counts rules that actually acted.
     */
    @Query("SELECT COUNT(*) FROM rule_executions WHERE created_at >= :since AND was_dry_run = 0")
    fun observeExecutionCountSince(since: Long): Flow<Int>

    /**
     * The most recent non-dry-run executions, joined with their source notification and rule name,
     * for Home's "Recent Activity" feed. Executions of deleted rules are kept (`LEFT JOIN rules`):
     * `ruleId` is null and the name falls back to the execution-time snapshot.
     */
    @Query(
        """
        SELECT re.id AS executionId, re.rule_id AS ruleId, COALESCE(r.name, re.rule_name) AS ruleName,
               n.id AS notificationId, n.title AS notificationTitle, n.content AS notificationContent,
               n.package_name AS packageName, n.app_name AS appName, re.created_at AS executedAt
        FROM rule_executions re
        INNER JOIN notifications n ON re.notification_id = n.id
        LEFT JOIN rules r ON re.rule_id = r.id
        WHERE re.was_dry_run = 0
        ORDER BY re.created_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecentActivity(limit: Int): Flow<List<RecentActivityRow>>

    /**
     * Aggregate match statistics for one rule in a single pass over its rows (served by the
     * `rule_id` index). Always returns exactly one row: with no executions the counts are 0 and
     * `lastTriggeredAt` is null. [since7Days] / [since30Days] are inclusive epoch-millis cutoffs.
     */
    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN created_at >= :since7Days THEN 1 ELSE 0 END), 0) AS last7Days,
               COALESCE(SUM(CASE WHEN created_at >= :since30Days THEN 1 ELSE 0 END), 0) AS last30Days,
               COALESCE(SUM(CASE WHEN was_dry_run = 0 THEN 1 ELSE 0 END), 0) AS live,
               COALESCE(SUM(CASE WHEN was_dry_run = 1 THEN 1 ELSE 0 END), 0) AS testMode,
               MAX(created_at) AS lastTriggeredAt
        FROM rule_executions
        WHERE rule_id = :ruleId
        """,
    )
    fun observeRuleStats(ruleId: String, since7Days: Long, since30Days: Long): Flow<RuleStatsRow>

    /**
     * Rule matches per local calendar day since [since] (epoch millis), non-dry-run only. Days
     * without matches are omitted; the repository zero-fills them. Same `localtime` bucketing as
     * `DataBrowserDao.getTrend`. Assumes SQLite's `localtime` equals `ZoneId.systemDefault()`, which
     * `InsightsRepositoryImpl` uses to zero-fill the missing days.
     */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', created_at / 1000, 'unixepoch', 'localtime') AS date, COUNT(*) AS count
        FROM rule_executions
        WHERE was_dry_run = 0 AND created_at >= :since
        GROUP BY date
        ORDER BY date ASC
        """,
    )
    fun observeDailyCountsSince(since: Long): Flow<List<TrendPoint>>

    /** Raw `action_outcomes` JSON of every non-dry-run execution since [since] that recorded any. */
    @Query(
        """
        SELECT action_outcomes FROM rule_executions
        WHERE was_dry_run = 0 AND created_at >= :since AND action_outcomes IS NOT NULL
        """,
    )
    fun observeActionOutcomesSince(since: Long): Flow<List<String>>

    /**
     * Raw `action_outcomes` JSON per local calendar day since [since], same filter and `localtime`
     * bucketing as [observeDailyCountsSince]; the repository counts the failed ones per day.
     */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', created_at / 1000, 'unixepoch', 'localtime') AS date, action_outcomes AS actionOutcomes
        FROM rule_executions
        WHERE was_dry_run = 0 AND created_at >= :since AND action_outcomes IS NOT NULL
        """,
    )
    fun observeDailyActionOutcomesSince(since: Long): Flow<List<DailyOutcomesRow>>

    /**
     * Raw `action_outcomes` JSON with the owning rule, same filter as [observeActionOutcomesSince].
     * Outcomes are a JSON blob, so the repository does the per-(action type, rule) grouping.
     * Deleted rules (`LEFT JOIN`) have a null `ruleId` and their snapshot name.
     */
    @Query(
        """
        SELECT re.rule_id AS ruleId, COALESCE(r.name, re.rule_name) AS ruleName, re.action_outcomes AS actionOutcomes
        FROM rule_executions re
        LEFT JOIN rules r ON re.rule_id = r.id
        WHERE re.was_dry_run = 0 AND re.created_at >= :since AND re.action_outcomes IS NOT NULL
        """,
    )
    fun observeRuleActionOutcomesSince(since: Long): Flow<List<RuleOutcomesRow>>

    /**
     * Most active rules by non-dry-run runs since [since], busiest first, capped at [limit].
     * Includes deleted rules (`LEFT JOIN`): they have a null `ruleId` and their snapshot name.
     */
    @Query(
        """
        SELECT re.rule_id AS ruleId, COALESCE(r.name, re.rule_name) AS ruleName, COUNT(*) AS runCount
        FROM rule_executions re
        LEFT JOIN rules r ON re.rule_id = r.id
        WHERE re.was_dry_run = 0 AND re.created_at >= :since
        GROUP BY re.rule_id, COALESCE(r.name, re.rule_name)
        ORDER BY runCount DESC, ruleName ASC, re.rule_id ASC
        LIMIT :limit
        """,
    )
    fun observeTopRulesSince(since: Long, limit: Int): Flow<List<RuleActivity>>
}

/** An execution together with its extracted field values (see [RuleExecutionDao.observeExecutionsForNotification]). */
internal data class RuleExecutionWithValues(
    @Embedded val execution: RuleExecutionEntity,
    @Relation(parentColumn = "id", entityColumn = "rule_execution_id")
    val values: List<ExtractedFieldValueEntity>,
)

/** Projection for [RuleExecutionDao.observeRuleStats]. */
internal data class RuleStatsRow(
    val total: Int,
    val last7Days: Int,
    val last30Days: Int,
    val live: Int,
    val testMode: Int,
    val lastTriggeredAt: Long?,
)

/** Projection for [RuleExecutionDao.observeRecentActivity]. */
internal data class RecentActivityRow(
    val executionId: String,
    val ruleId: String?,
    val ruleName: String,
    val notificationId: String,
    val notificationTitle: String?,
    val notificationContent: String?,
    val packageName: String,
    val appName: String,
    val executedAt: Long,
)

/** Projection for [RuleExecutionDao.observeDailyActionOutcomesSince]. */
internal data class DailyOutcomesRow(val date: String, val actionOutcomes: String)

/** Projection for [RuleExecutionDao.observeRuleActionOutcomesSince]. */
internal data class RuleOutcomesRow(val ruleId: String?, val ruleName: String, val actionOutcomes: String)
