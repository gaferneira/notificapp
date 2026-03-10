package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single trigger condition within a rule.
 *
 * Each condition defines when a rule should be applied based on notification properties.
 *
 * @property id Unique identifier for this condition
 * @property ruleId Foreign key to the parent rule
 * @property condition What to match against (TEXT_CONTENT, TITLE, etc.)
 * @property operator How to perform the match (CONTAINS, EQUALS, etc.)
 * @property value The value to match against
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
data class RuleConditionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "rule_id")
    val ruleId: String,

    @ColumnInfo(name = "condition")
    val condition: String?, // MatchingCondition enum name

    @ColumnInfo(name = "operator")
    val operator: String?, // MatchingOperator enum name

    @ColumnInfo(name = "value")
    val value: String?,
)
