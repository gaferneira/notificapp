package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a captured notification.
 *
 * @property id Unique identifier
 * @property packageName Package name of the app
 * @property appName Display name of the app
 * @property title Notification title
 * @property content Notification content text
 * @property rawContent Full raw text representation
 * @property timestamp When notification was received
 * @property isProcessed Whether processed by rules
 * @property appliedRulesCount Number of rules applied to this notification
 * @property contentHash MD5 hash of package/title/content, precomputed at write time so
 *   deduplication can be answered by a `SELECT EXISTS(...)` query instead of re-hashing rows (PERF-006)
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["package_name", "timestamp"]), // For filtering by app and sorting
        Index(value = ["is_processed"]), // For querying unprocessed notifications
        Index(value = ["timestamp"]), // For time-based queries
        Index(value = ["package_name", "content_hash", "timestamp"]), // For dedup existence checks
    ],
)
internal data class NotificationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "content")
    val content: String?,

    @ColumnInfo(name = "raw_content")
    val rawContent: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_processed")
    val isProcessed: Boolean = false,

    @ColumnInfo(name = "applied_rules_count", defaultValue = "0")
    val appliedRulesCount: Int = 0,

    @ColumnInfo(name = "sbn_key")
    val sbnKey: String? = null,

    @ColumnInfo(name = "content_hash", defaultValue = "''")
    val contentHash: String = "",
)
