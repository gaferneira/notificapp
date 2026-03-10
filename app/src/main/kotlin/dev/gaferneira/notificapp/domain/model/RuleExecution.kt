package dev.gaferneira.notificapp.domain.model

/**
 * Domain model representing a rule execution.
 *
 * Captures when a rule matched a notification and what data was extracted.
 *
 * @property id Unique identifier for this execution
 * @property notificationId The matched notification
 * @property ruleId The rule that matched
 * @property extractedData Map of field names to extracted values
 * @property triggeredActions List of action IDs that were triggered
 * @property createdAt When the execution occurred
 */
data class RuleExecution(
    val id: String,
    val notificationId: String,
    val ruleId: String,
    val extractedData: Map<String, String>,
    val triggeredActions: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
)
