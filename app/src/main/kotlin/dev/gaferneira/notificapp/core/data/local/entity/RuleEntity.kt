package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an extraction rule.
 *
 * Stores rule metadata. Rule fields, conditions, and actions are stored separately in their own tables.
 *
 * @property id Unique identifier
 * @property name Rule name
 * @property description Optional description
 * @property category Optional category
 * @property area Optional area/location
 * @property isActive Whether the rule is active
 * @property isDryRun When true, matches are logged but no actions execute
 * @property isIncludeMode Whether [targetApps] is an include-list (true) or exclude-list (false).
 * @property createdAt Creation timestamp
 * @property updatedAt Last update timestamp
 */
@Entity(
    tableName = "rules",
    indices = [
        Index(value = ["is_active"]),
        Index(value = ["updated_at"]),
    ],
)
internal data class RuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "category")
    val category: String?,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "is_dry_run", defaultValue = "0")
    val isDryRun: Boolean = false,

    @ColumnInfo(name = "is_include_mode", defaultValue = "1")
    val isIncludeMode: Boolean = true,

    @ColumnInfo(name = "condition_logic", defaultValue = "ALL")
    val conditionLogic: String = "ALL",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
