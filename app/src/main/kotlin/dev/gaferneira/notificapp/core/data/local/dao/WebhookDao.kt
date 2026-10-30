package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import dev.gaferneira.notificapp.core.data.local.entity.WebhookEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for webhooks. No `update` method: `saveWebhook` always calls [upsert] for both create and edit.
 * It must be an in-place upsert, never `OnConflictStrategy.REPLACE`: REPLACE deletes the parent row
 * first, which would cascade-delete the webhook's queued `webhook_deliveries` on every edit.
 */
@Dao
internal interface WebhookDao {

    @Query("SELECT * FROM webhooks ORDER BY created_at DESC")
    fun getAll(): Flow<List<WebhookEntity>>

    @Query("SELECT * FROM webhooks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WebhookEntity?

    @Upsert
    suspend fun upsert(webhook: WebhookEntity)

    @Delete
    suspend fun delete(webhook: WebhookEntity)

    @Query("DELETE FROM webhooks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE webhooks SET last_delivery_status = :status, last_delivery_at = :at WHERE id = :id")
    suspend fun updateDeliveryStatus(id: String, status: String, at: Long): Int
}
