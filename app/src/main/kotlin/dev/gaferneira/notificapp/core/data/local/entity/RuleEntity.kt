package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField

/**
 * Room entity representing an extraction rule.
 *
 * Stores rule metadata and JSON-serialized complex objects.
 *
 * @property id Unique identifier
 * @property name Rule name
 * @property description Optional description
 * @property category Optional category
 * @property area Optional area/location
 * @property isActive Whether the rule is active
 * @property isGlobal Whether this rule applies to all apps (true) or specific apps (false)
 * @property ruleFields JSON list of fields to extract
 * @property triggers JSON list of trigger conditions
 * @property actions JSON list of actions to take
 * @property createdAt Creation timestamp
 * @property updatedAt Last update timestamp
 */
@Entity(
    tableName = "rules",
    indices = [
        Index(value = ["is_active"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_global"]),
    ],
)
data class RuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "category")
    val category: String?,

    @ColumnInfo(name = "area")
    val area: String?,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "is_global")
    val isGlobal: Boolean = true,

    @ColumnInfo(name = "rule_fields")
    val ruleFields: List<RuleField> = emptyList(),

    @ColumnInfo(name = "triggers")
    val triggers: List<RuleCondition> = emptyList(),

    @ColumnInfo(name = "actions")
    val actions: List<RuleAction> = emptyList(),

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
