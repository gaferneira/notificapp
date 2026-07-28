package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
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
     * Get executions for a specific notification as a Flow.
     */
    @Query("SELECT * FROM rule_executions WHERE notification_id = :notificationId ORDER BY created_at DESC")
    fun observeExecutionsForNotification(notificationId: String): Flow<List<RuleExecutionEntity>>

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
     * Insert a rule execution.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(execution: RuleExecutionEntity)

    /**
     * Insert multiple rule executions.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(executions: List<RuleExecutionEntity>)

    /**
     * Delete executions for a specific notification.
     */
    @Query("DELETE FROM rule_executions WHERE notification_id = :notificationId")
    suspend fun deleteExecutionsForNotification(notificationId: String)

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
     * for Home's "Recent Activity" feed.
     */
    @Query(
        """
        SELECT re.id AS executionId, re.rule_id AS ruleId, r.name AS ruleName,
               n.id AS notificationId, n.title AS notificationTitle, n.content AS notificationContent,
               n.package_name AS packageName, n.app_name AS appName, re.created_at AS executedAt
        FROM rule_executions re
        INNER JOIN notifications n ON re.notification_id = n.id
        INNER JOIN rules r ON re.rule_id = r.id
        WHERE re.was_dry_run = 0
        ORDER BY re.created_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecentActivity(limit: Int): Flow<List<RecentActivityRow>>
}

/** Projection for [RuleExecutionDao.observeRecentActivity]. */
internal data class RecentActivityRow(
    val executionId: String,
    val ruleId: String,
    val ruleName: String,
    val notificationId: String,
    val notificationTitle: String?,
    val notificationContent: String?,
    val packageName: String,
    val appName: String,
    val executedAt: Long,
)
