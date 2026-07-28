package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Room entity persisting a dismissed recurring-notification suggestion, keyed by app package +
 * normalized title. Purpose-built for this one feature - not a generic/reusable dismissal
 * mechanism (an explicit product decision; do not parameterize this for hypothetical future use).
 *
 * @property packageName The app package the dismissed suggestion was for.
 * @property normalizedTitleKey The normalized title key that was dismissed.
 * @property dismissedAt When the dismissal happened (epoch millis). Stored but unused in v1
 *   (dismissal is permanent) - exists so a future "expire after N days" needs no schema change.
 */
@Entity(
    tableName = "suggestion_dismissals",
    primaryKeys = ["package_name", "normalized_title_key"],
    indices = [
        Index(value = ["dismissed_at"]),
    ],
)
internal data class SuggestionDismissalEntity(
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "normalized_title_key")
    val normalizedTitleKey: String,

    @ColumnInfo(name = "dismissed_at")
    val dismissedAt: Long,
)
