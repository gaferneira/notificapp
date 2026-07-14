package dev.gaferneira.notificapp.core.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing notifications in the database.
 */
@Dao
internal interface NotificationDao {

    /**
     * Get all notifications as a Flow, ordered by timestamp descending (newest first).
     */
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NotificationEntity>>

    /**
     * Get the most recently captured notifications, bounded by [limit].
     * Used for rule backtesting so a preview never materializes the full table.
     */
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<NotificationEntity>

    /**
     * Get the most recently captured notifications for specific apps, bounded by [limit].
     */
    @Query(
        "SELECT * FROM notifications WHERE package_name IN (:packageNames) " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun getRecentByPackageNames(packageNames: List<String>, limit: Int): List<NotificationEntity>

    /**
     * Get the most recently captured notifications EXCLUDING specific apps, bounded by [limit].
     * This mirrors RuleDao's exclude-mode NOT EXISTS branch for backtesting exclude rules.
     */
    @Query(
        "SELECT * FROM notifications WHERE package_name NOT IN (:packageNames) " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun getRecentByPackageNamesExcluding(packageNames: List<String>, limit: Int): List<NotificationEntity>

    /**
     * Get notifications for a specific app.
     */
    @Query("SELECT * FROM notifications WHERE package_name = :packageName ORDER BY timestamp DESC")
    fun getByPackageName(packageName: String): Flow<List<NotificationEntity>>

    /**
     * Get notifications for specific apps (for filtering by selected apps).
     */
    @Query("SELECT * FROM notifications WHERE package_name IN (:packageNames) ORDER BY timestamp DESC")
    fun getByPackageNames(packageNames: List<String>): Flow<List<NotificationEntity>>

    /**
     * Get paginated notifications with optional app and status filters.
     * NULL or empty packageNames means no app filter.
     * NULL isProcessed means no status filter.
     */
    @Query(
        """
        SELECT * FROM notifications 
        WHERE (package_name IN (:packageNames) OR :hasPackageFilter = 0)
        AND (is_processed = :isProcessed OR :hasStatusFilter = 0)
        ORDER BY timestamp DESC
    """,
    )
    fun getFilteredPaged(
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        isProcessed: Boolean,
        hasStatusFilter: Boolean,
    ): PagingSource<Int, NotificationEntity>

    /**
     * Search paginated notifications with optional app and status filters, backed by the
     * `notifications_fts` FTS4 index (DATA-04) instead of a non-sargable `LIKE '%term%'` scan.
     * [ftsQuery] must already be a valid FTS4 MATCH expression (see [FtsQuerySanitizer]).
     */
    @Query(
        """
        SELECT n.* FROM notifications n
        JOIN notifications_fts fts ON n.rowid = fts.rowid
        WHERE (n.package_name IN (:packageNames) OR :hasPackageFilter = 0)
        AND (n.is_processed = :isProcessed OR :hasStatusFilter = 0)
        AND notifications_fts MATCH :ftsQuery
        ORDER BY n.timestamp DESC
    """,
    )
    fun searchFilteredPaged(
        ftsQuery: String,
        packageNames: List<String>,
        hasPackageFilter: Boolean,
        isProcessed: Boolean,
        hasStatusFilter: Boolean,
    ): PagingSource<Int, NotificationEntity>

    /**
     * Get a specific notification by ID.
     */
    @Query("SELECT * FROM notifications WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NotificationEntity?

    /**
     * Get unprocessed notifications (for rule matching).
     */
    @Query("SELECT * FROM notifications WHERE is_processed = 0 ORDER BY timestamp ASC")
    suspend fun getUnprocessed(): List<NotificationEntity>

    /**
     * Get recent notifications (last X hours).
     */
    @Query("SELECT * FROM notifications WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Long): List<NotificationEntity>

    /**
     * Check whether a matching content hash already exists for this app within the lookback
     * window, without pulling rows into Kotlin to re-hash them (PERF-006).
     */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM notifications " +
            "WHERE package_name = :packageName AND content_hash = :contentHash AND timestamp > :since)",
    )
    suspend fun existsRecentDuplicate(packageName: String, contentHash: String, since: Long): Boolean

    /**
     * Search notifications by content, backed by the `notifications_fts` FTS4 index (DATA-04).
     * [ftsQuery] must already be a valid FTS4 MATCH expression (see [FtsQuerySanitizer]).
     */
    @Query(
        """
        SELECT n.* FROM notifications n
        JOIN notifications_fts fts ON n.rowid = fts.rowid
        WHERE notifications_fts MATCH :ftsQuery
        ORDER BY n.timestamp DESC
    """,
    )
    suspend fun search(ftsQuery: String): List<NotificationEntity>

    /**
     * Insert a new notification.
     * Replaces on conflict (UPSERT behavior).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity)

    /**
     * Insert multiple notifications.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    /**
     * Mark a notification as processed.
     */
    @Query("UPDATE notifications SET is_processed = 1 WHERE id = :id")
    suspend fun markAsProcessed(id: String)

    /**
     * Mark multiple notifications as processed.
     */
    @Query("UPDATE notifications SET is_processed = 1 WHERE id IN (:ids)")
    suspend fun markAsProcessed(ids: List<String>)

    /**
     * Delete a notification.
     */
    @Delete
    suspend fun delete(notification: NotificationEntity)

    /**
     * Delete a notification by ID.
     */
    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Delete old notifications (cleanup).
     */
    @Query("DELETE FROM notifications WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    /**
     * Delete all notifications for a specific app.
     */
    @Query("DELETE FROM notifications WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    /**
     * Delete all notifications.
     */
    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    /**
     * Get the count of all notifications.
     */
    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getCount(): Int

    /**
     * Get the count of notifications for a specific app.
     */
    @Query("SELECT COUNT(*) FROM notifications WHERE package_name = :packageName")
    suspend fun getCountByPackageName(packageName: String): Int

    /**
     * Get the count of unprocessed notifications.
     */
    @Query("SELECT COUNT(*) FROM notifications WHERE is_processed = 0")
    suspend fun getUnprocessedCount(): Int

    /**
     * Get every distinct package with notifications, paired with its most recent app name, in a
     * single query.
     */
    @Query(
        """
        SELECT n.package_name AS packageName, n.app_name AS appName
        FROM notifications n
        WHERE n.timestamp = (
            SELECT MAX(n2.timestamp) FROM notifications n2 WHERE n2.package_name = n.package_name
        )
        GROUP BY n.package_name
        ORDER BY n.app_name ASC
        """,
    )
    fun observeAppsWithLatestName(): Flow<List<AppWithLatestName>>

    /**
     * Increment the applied rules count for a notification.
     */
    @Query("UPDATE notifications SET applied_rules_count = applied_rules_count + 1, is_processed = 1 WHERE id = :id")
    suspend fun incrementAppliedRulesCount(id: String)

    /**
     * Reset the applied rules count to 0 for a notification (e.g., when refreshing rules).
     */
    @Query("UPDATE notifications SET applied_rules_count = 0, is_processed = 0 WHERE id = :id")
    suspend fun resetAppliedRulesCount(id: String)
}

/** Projection for [NotificationDao.observeAppsWithLatestName]. */
internal data class AppWithLatestName(val packageName: String, val appName: String)
