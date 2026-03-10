package dev.gaferneira.notificapp.domain.repository

import androidx.paging.PagingData
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Notification
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing captured notifications.
 *
 * This interface defines the contract for notification operations following
 * the Repository Pattern per ADR 005.
 */
interface NotificationRepository {

    /**
     * Observe all notifications as a Flow, ordered by timestamp descending.
     */
    fun observeAllNotifications(): Flow<List<Notification>>

    /**
     * Observe notifications for specific apps.
     */
    fun observeNotificationsByApps(packageNames: List<String>): Flow<List<Notification>>

    /**
     * Get paginated notifications with optional app and status filters.
     *
     * @param packageNames Filter by app package names (empty = all apps)
     * @param isProcessed Filter by processed status (null = all statuses)
     */
    fun observeNotificationsPaged(
        packageNames: List<String> = emptyList(),
        isProcessed: Boolean? = null,
    ): Flow<PagingData<Notification>>

    /**
     * Search paginated notifications with optional app and status filters.
     *
     * @param query Search query for title/content
     * @param packageNames Filter by app package names (empty = all apps)
     * @param isProcessed Filter by processed status (null = all statuses)
     */
    fun searchNotificationsPaged(
        query: String,
        packageNames: List<String> = emptyList(),
        isProcessed: Boolean? = null,
    ): Flow<PagingData<Notification>>

    /**
     * Get all notifications.
     */
    suspend fun getAllNotifications(): Result<List<Notification>>

    /**
     * Get a specific notification by ID.
     */
    suspend fun getNotification(id: String): Result<Notification?>

    /**
     * Get unprocessed notifications for rule matching.
     */
    suspend fun getUnprocessedNotifications(): Result<List<Notification>>

    /**
     * Search notifications by content.
     */
    suspend fun searchNotifications(query: String): Result<List<Notification>>

    /**
     * Save a new notification.
     */
    suspend fun saveNotification(notification: Notification): Result<Unit>

    /**
     * Mark a notification as processed.
     */
    suspend fun markAsProcessed(id: String): Result<Unit>

    /**
     * Delete a notification.
     */
    suspend fun deleteNotification(id: String): Result<Unit>

    /**
     * Delete old notifications (cleanup).
     */
    suspend fun deleteOlderThan(timestamp: Long): Result<Unit>

    /**
     * Delete all notifications for an app.
     */
    suspend fun deleteByApp(packageName: String): Result<Unit>

    /**
     * Get the total count of notifications.
     */
    suspend fun getNotificationCount(): Result<Int>

    /**
     * Get recent notifications from a specific app within a time window.
     * Used for deduplication.
     */
    suspend fun getRecentNotifications(packageName: String, lookbackMs: Long): Result<List<Notification>>

    /**
     * Get the count of unprocessed notifications.
     */
    suspend fun getUnprocessedCount(): Result<Int>

    /**
     * Get all unique apps that have notifications, sorted by name.
     */
    fun observeAppsWithNotifications(): Flow<List<AppInfo>>
}
