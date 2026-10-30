package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a rule execution (when a rule matches a notification).
 *
 * Stores the triggered actions and outcomes for audit and history purposes. The values extracted by
 * the match live in `extracted_field_values` (one typed row per field), not in this table.
 *
 * @property id Unique identifier for this execution
 * @property notificationId Foreign key to the matched notification
 * @property ruleId Foreign key to the rule that matched; `NULL` once the rule was deleted
 *   (`ON DELETE SET NULL`) so the execution history survives
 * @property ruleName Snapshot of the rule's name at execution time, shown when [ruleId] is `NULL`
 * @property triggeredActions JSON list of action IDs that were triggered
 * @property actionOutcomes JSON map of action ID to outcome, or null if none recorded
 * @property wasDryRun Snapshot of the rule's dry-run flag at match time
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
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["notification_id"]), // For querying executions by notification
        Index(value = ["rule_id", "created_at"]), // Executions by rule (also serves rule_id-only lookups), newest first
        Index(value = ["was_dry_run", "created_at"]), // Insights/data browser: live executions in a time window
        Index(value = ["created_at"]), // Time-only queries without was_dry_run (history list, retention purge)
    ],
)
internal data class RuleExecutionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "notification_id")
    val notificationId: String,

    @ColumnInfo(name = "rule_id")
    val ruleId: String?,

    @ColumnInfo(name = "rule_name")
    val ruleName: String,

    @ColumnInfo(name = "triggered_actions")
    val triggeredActions: String, // JSON List<String>

    @ColumnInfo(name = "action_outcomes")
    val actionOutcomes: String? = null, // JSON Map<String, ActionOutcome>

    @ColumnInfo(name = "was_dry_run", defaultValue = "0")
    val wasDryRun: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
