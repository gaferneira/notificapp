package dev.gaferneira.notificapp.domain.model

/**
 * Domain model representing a single extracted field value.
 *
 * Provides typed access to extracted data for filtering and display.
 *
 * @property id Unique identifier for this value
 * @property ruleExecutionId The parent rule execution
 * @property ruleFieldId The field definition (provides name and type)
 * @property valueText String value for STRING/CURRENCY types
 * @property valueNumber Numeric value for NUMBER type
 * @property valueDate Timestamp for DATE type
 */
data class ExtractedFieldValue(
    val id: String,
    val ruleExecutionId: String,
    val ruleFieldId: String,
    val valueText: String?,
    val valueNumber: Double?,
    val valueDate: Long?,
)
