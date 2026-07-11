package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single extraction field owned by a `SAVE_DATA` ("Extract data")
 * action.
 *
 * Each field defines one piece of data to extract from notifications.
 *
 * @property id Unique identifier for this field
 * @property actionId Foreign key to the owning `SAVE_DATA` action
 * @property name Human-readable name for the field
 * @property fieldType The type of data this field represents (STRING, NUMBER, DATE, CURRENCY, BOOLEAN)
 * @property methodType The type of extraction method (discriminator)
 * @property methodConfig JSON configuration for the specific extraction method
 * @property isRequired Whether this field is required for a match
 */
@Entity(
    tableName = "rule_fields",
    foreignKeys = [
        ForeignKey(
            entity = RuleActionEntity::class,
            parentColumns = ["id"],
            childColumns = ["action_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["action_id"]), // For querying fields by action
    ],
)
internal data class RuleFieldEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "action_id")
    val actionId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "field_type")
    val fieldType: String,

    @ColumnInfo(name = "method_type")
    val methodType: String,

    @ColumnInfo(name = "method_config")
    val methodConfig: String, // JSON serialized ExtractionMethod

    @ColumnInfo(name = "is_required")
    val isRequired: Boolean = false,
)
