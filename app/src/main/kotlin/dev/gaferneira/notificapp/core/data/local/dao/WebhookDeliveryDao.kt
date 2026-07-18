package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gaferneira.notificapp.core.data.local.entity.WebhookDeliveryEntity

/**
 * Data Access Object for the `webhook_deliveries` queue/journal. Mirrors [WebhookDao]'s
 * no-separate-`update`-method pattern: `updateFailure` is a targeted column update instead, since
 * a full-row REPLACE would require the caller to re-read the row first for no benefit.
 */
@Dao
internal interface WebhookDeliveryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(delivery: WebhookDeliveryEntity)

    @Query("SELECT * FROM webhook_deliveries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WebhookDeliveryEntity?

    // Success deletes its row (markDelivered), so every row still present is unresolved -
    // PENDING (crashed before its worker ran, or a NetworkError left it PENDING for Result.retry())
    // or FAILED (SERVER/CLIENT) - and the app-open sweep must re-enqueue all of them (design.md's
    // Data Flow: "pendingFailures().forEach { re-enqueue }" is not filtered by failure_type).
    @Query("SELECT * FROM webhook_deliveries")
    suspend fun getAllUnresolved(): List<WebhookDeliveryEntity>

    @Query("DELETE FROM webhook_deliveries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        "UPDATE webhook_deliveries SET failure_type = :failureType, attempt_count = :attemptCount, " +
            "last_attempt_at = :at WHERE id = :id",
    )
    suspend fun updateFailure(id: String, failureType: String, attemptCount: Int, at: Long)
}
