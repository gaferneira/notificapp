package dev.gaferneira.notificapp.core.data.local.dao

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
interface NotificationDao {

    /**
     * Get all notifications as a Flow, ordered by timestamp descending (newest first).
     */
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NotificationEntity>>

    /**
     * Get all notifications synchronously.
     */
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    suspend fun getAllSync(): List<NotificationEntity>

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
     * Get recent notifications from a specific app (for deduplication).
     */
    @Query(
        "SELECT * FROM notifications WHERE package_name = :packageName AND timestamp > :since ORDER BY timestamp DESC",
    )
    suspend fun getRecentByPackageName(packageName: String, since: Long): List<NotificationEntity>

    /**
     * Search notifications by content.
     */
    @Query(
        """
        SELECT * FROM notifications 
        WHERE title LIKE '%' || :query || '%' 
        OR content LIKE '%' || :query || '%'
        OR raw_content LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """,
    )
    suspend fun search(query: String): List<NotificationEntity>

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
}
