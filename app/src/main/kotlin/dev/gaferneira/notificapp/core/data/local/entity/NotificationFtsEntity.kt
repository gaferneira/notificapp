package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 shadow table backing full-text search over [NotificationEntity] (DATA-04). Room keeps this
 * synchronized with `notifications` via generated triggers; queries join on `rowid` and filter
 * with `MATCH` instead of a non-sargable `LIKE '%term%'` scan.
 */
@Fts4(contentEntity = NotificationEntity::class)
@Entity(tableName = "notifications_fts")
internal data class NotificationFtsEntity(
    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "content")
    val content: String?,

    @ColumnInfo(name = "raw_content")
    val rawContent: String,
)
