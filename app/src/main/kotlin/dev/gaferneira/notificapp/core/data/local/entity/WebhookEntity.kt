package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a user-defined webhook.
 *
 * `authType` is a flattened discriminator of the sealed `WebhookAuth` (NONE/API_KEY/BEARER);
 * `authHeaderName`/`authValue` are only populated for the variants that use them. See
 * [dev.gaferneira.notificapp.core.data.local.mapper.WebhookMapper] for flatten/reconstruct logic.
 *
 * @property headers Custom header key/value map, persisted via `RuleTypeConverters` (already
 * registered at [dev.gaferneira.notificapp.core.data.local.AppDatabase] level).
 */
@Entity(tableName = "webhooks")
internal data class WebhookEntity(
    @PrimaryKey
    val id: String,

    val name: String,

    val url: String,

    val headers: Map<String, String>,

    @ColumnInfo(name = "auth_type")
    val authType: String,

    @ColumnInfo(name = "auth_header_name")
    val authHeaderName: String?,

    @ColumnInfo(name = "auth_value")
    val authValue: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
) {
    /**
     * Redacts [headers] values AND [authValue] so a future maintainer logging this entity
     * (instead of a scalar field, per this repo's existing per-field logging convention) never
     * leaks a plaintext secret via the default data-class `toString()` - see design.md's no-log
     * guarantee.
     */
    override fun toString(): String = "WebhookEntity(id=$id, name=$name, url=$url, " +
        "headers=${headers.mapValues { "REDACTED" }}, authType=$authType, " +
        "authHeaderName=$authHeaderName, authValue=REDACTED, createdAt=$createdAt)"
}
