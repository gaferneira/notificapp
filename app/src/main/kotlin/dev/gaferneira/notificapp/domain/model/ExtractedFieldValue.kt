package dev.gaferneira.notificapp.domain.model

import java.time.LocalDate

/**
 * Domain model representing a single extracted field value.
 *
 * Provides typed access to extracted data for filtering and display.
 *
 * @property id Unique identifier for this value
 * @property ruleExecutionId The parent rule execution
 * @property ruleFieldId The field definition, or `null` if the field was removed since extraction
 * @property fieldName Snapshot of the field's name at extraction time
 * @property fieldType Snapshot of the field's type at extraction time
 * @property valueText String value for STRING/CURRENCY types
 * @property valueNumber Numeric value for NUMBER type
 * @property valueDate Calendar day for DATE type (zone-independent)
 */
data class ExtractedFieldValue(
    val id: String,
    val ruleExecutionId: String,
    val ruleFieldId: String?,
    val fieldName: String,
    val fieldType: RuleField.FieldType,
    val valueText: String?,
    val valueNumber: Double?,
    val valueDate: LocalDate?,
)
