package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a queued `SEND_WEBHOOK` delivery job. One row per unresolved delivery; deleted
 * on success so the table naturally drains (see
 * [dev.gaferneira.notificapp.core.data.repository.WebhookDeliveryRepositoryImpl.markDelivered]).
 *
 * @property payload JSON snapshot to POST, encrypted at rest (SQLCipher).
 * @property failureType `NETWORK`/`SERVER`/`CLIENT`, or null while still PENDING.
 */
@Entity(tableName = "webhook_deliveries")
internal data class WebhookDeliveryEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "webhook_id")
    val webhookId: String,

    val payload: String,

    @ColumnInfo(name = "failure_type")
    val failureType: String?,

    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long?,
) {
    /**
     * Redacts [payload] (may carry extracted-field secrets) so a future maintainer logging this
     * entity never leaks plaintext via the default data-class `toString()` - see design.md's
     * no-log guarantee.
     */
    override fun toString(): String = "WebhookDeliveryEntity(id=$id, webhookId=$webhookId, " +
        "payload=REDACTED, failureType=$failureType, attemptCount=$attemptCount, " +
        "createdAt=$createdAt, lastAttemptAt=$lastAttemptAt)"
}
