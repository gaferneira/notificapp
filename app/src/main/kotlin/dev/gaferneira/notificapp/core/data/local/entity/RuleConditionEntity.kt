package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single trigger condition within a rule.
 *
 * Each condition defines when a rule should be applied. The condition's family and data (content
 * match, day-of-week, time-range, ...) are collapsed into a single JSON [payload] column - so
 * adding a new condition family never requires a schema migration.
 *
 * @property id Unique identifier for this condition
 * @property ruleId Foreign key to the parent rule
 * @property payload JSON-serialized, polymorphically-discriminated `ConditionDto`
 */
@Entity(
    tableName = "rule_conditions",
    foreignKeys = [
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["rule_id"]), // For querying conditions by rule
    ],
)
internal data class RuleConditionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "rule_id")
    val ruleId: String,

    @ColumnInfo(name = "payload")
    val payload: String,
)
