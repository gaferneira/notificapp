package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single action within a rule.
 *
 * Each action defines what to do when a rule matches a notification.
 *
 * @property id Unique identifier for this action
 * @property ruleId Foreign key to the parent rule
 * @property type The type of action (SAVE_DATA, DELETE_NOTIFICATION, CREATE_ALARM)
 * @property isEnabled Whether this action is enabled
 */
@Entity(
    tableName = "rule_actions",
    foreignKeys = [
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["rule_id"]), // For querying actions by rule
    ],
)
internal data class RuleActionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "rule_id")
    val ruleId: String,

    @ColumnInfo(name = "type")
    val type: String, // ActionType enum name

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean = true,

    @ColumnInfo(name = "config", defaultValue = "{}")
    val config: String = "{}",
)
