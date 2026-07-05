package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a rule execution (when a rule matches a notification).
 *
 * Stores the extracted data and triggered actions for audit and history purposes.
 *
 * @property id Unique identifier for this execution
 * @property notificationId Foreign key to the matched notification
 * @property ruleId Foreign key to the rule that matched
 * @property extractedData JSON map of extracted field data (field name → extracted value)
 * @property triggeredActions JSON list of action IDs that were triggered
 * @property actionOutcomes JSON map of action ID to outcome, or null if none recorded
 */
@Entity(
    tableName = "rule_executions",
    foreignKeys = [
        ForeignKey(
            entity = NotificationEntity::class,
            parentColumns = ["id"],
            childColumns = ["notification_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["notification_id"]), // For querying executions by notification
        Index(value = ["rule_id"]), // For querying executions by rule
        Index(value = ["created_at"]), // For time-based queries
    ],
)
data class RuleExecutionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "notification_id")
    val notificationId: String,

    @ColumnInfo(name = "rule_id")
    val ruleId: String,

    @ColumnInfo(name = "extracted_data")
    val extractedData: String, // JSON Map<String, String>

    @ColumnInfo(name = "triggered_actions")
    val triggeredActions: String, // JSON List<String>

    @ColumnInfo(name = "action_outcomes")
    val actionOutcomes: String? = null, // JSON Map<String, ActionOutcome>

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
