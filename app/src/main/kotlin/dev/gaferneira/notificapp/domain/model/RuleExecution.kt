package dev.gaferneira.notificapp.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Domain model representing a rule execution.
 *
 * Captures when a rule matched a notification and what data was extracted. The extracted values
 * are loaded from the typed `extracted_field_values` rows; they are never written through this model.
 *
 * @property id Unique identifier for this execution
 * @property notificationId The matched notification
 * @property ruleId The rule that matched, or `null` if that rule has since been deleted
 * @property ruleName Snapshot of the rule's name at execution time (survives rule deletion/rename)
 * @property extractedValues Values extracted by this execution, each carrying a snapshot of its field's
 *   name and type so it stays readable after the field is removed. Empty when not loaded.
 * @property triggeredActions List of action IDs that were triggered
 * @property actionOutcomes Map of action ID to the outcome of executing it
 * @property wasDryRun Snapshot of the rule's dry-run flag at match time
 * @property createdAt When the execution occurred
 */
data class RuleExecution(
    val id: String,
    val notificationId: String,
    val ruleId: String?,
    val ruleName: String,
    val triggeredActions: List<String>,
    val triggeredRuleActions: ImmutableList<RuleAction> = persistentListOf(),
    val actionOutcomes: Map<String, ActionOutcome> = emptyMap(),
    val extractedValues: List<ExtractedFieldValue> = emptyList(),
    /** Snapshot of the rule's dry-run flag at match time - actions never ran when true */
    val wasDryRun: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
