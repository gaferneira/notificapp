package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single extracted field value.
 *
 * Enables efficient type-specific filtering of extracted data.
 * Supplements the JSON extracted_data in rule_executions table.
 *
 * @property id Unique identifier for this value
 * @property ruleExecutionId Foreign key to the parent rule execution
 * @property ruleFieldId Foreign key to the field definition (provides name and type)
 * @property valueText String value for STRING/CURRENCY types
 * @property valueNumber Numeric value for NUMBER type
 * @property valueDate Timestamp for DATE type
 */
@Entity(
    tableName = "extracted_field_values",
    foreignKeys = [
        ForeignKey(
            entity = RuleExecutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_execution_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RuleFieldEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_field_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["rule_execution_id"]), // For querying values by execution
        Index(value = ["rule_field_id"]), // For querying values by field
        Index(value = ["value_number"]), // For numeric range queries
        Index(value = ["value_date"]), // For date range queries
    ],
)
data class ExtractedFieldValueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "rule_execution_id")
    val ruleExecutionId: String,

    @ColumnInfo(name = "rule_field_id")
    val ruleFieldId: String,

    @ColumnInfo(name = "value_text")
    val valueText: String?,

    @ColumnInfo(name = "value_number")
    val valueNumber: Double?,

    @ColumnInfo(name = "value_date")
    val valueDate: Long?,
)
