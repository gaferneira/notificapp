package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an app selected by the user for notification monitoring.
 *
 * @property packageName The unique package name of the app (e.g., "com.whatsapp")
 * @property appName The display name of the app (e.g., "WhatsApp")
 * @property isEnabled Whether notification monitoring is enabled for this app
 * @property createdAt Timestamp when the app was added
 */
@Entity(
    tableName = "selected_apps",
    indices = [
        Index(value = ["is_enabled"]), // For querying enabled apps quickly
    ],
)
internal data class SelectedAppEntity(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "app_name")
    val appName: String,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
