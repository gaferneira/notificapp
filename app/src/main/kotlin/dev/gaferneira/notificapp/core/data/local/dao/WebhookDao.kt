package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gaferneira.notificapp.core.data.local.entity.WebhookEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for webhooks. No `update` method: `saveWebhook` always calls [insert] with
 * `OnConflictStrategy.REPLACE` for both create and edit, mirroring [SelectedAppDao]'s pattern
 * where its `.update()` is dead code in practice.
 */
@Dao
internal interface WebhookDao {

    @Query("SELECT * FROM webhooks ORDER BY created_at DESC")
    fun getAll(): Flow<List<WebhookEntity>>

    @Query("SELECT * FROM webhooks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WebhookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(webhook: WebhookEntity)

    @Delete
    suspend fun delete(webhook: WebhookEntity)

    @Query("DELETE FROM webhooks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE webhooks SET last_delivery_status = :status, last_delivery_at = :at WHERE id = :id")
    suspend fun updateDeliveryStatus(id: String, status: String, at: Long): Int
}
