package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Room entity representing the many-to-many relationship between rules and target apps.
 *
 * This enables efficient querying: "get all rules that apply to app X"
 *
 * @property ruleId The rule ID (foreign key to rules table)
 * @property packageName The app package name
 */
@Entity(
    tableName = "rule_target_apps",
    primaryKeys = ["rule_id", "package_name"],
    foreignKeys = [
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["package_name"]), // For querying rules by app
    ],
)
internal data class RuleTargetAppEntity(
    @ColumnInfo(name = "rule_id")
    val ruleId: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,
)
