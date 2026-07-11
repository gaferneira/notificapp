package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.domain.model.ExtractedFieldValue
import dev.gaferneira.notificapp.domain.model.RuleField
import java.util.UUID

/**
 * Mapper functions for converting between ExtractedFieldValue domain models and database entities.
 */
internal object ExtractedFieldValueMapper {

    /**
     * Convert an ExtractedFieldValue domain model to an entity.
     *
     * @param domain The domain model
     * @return The database entity
     */
    fun toEntity(domain: ExtractedFieldValue): ExtractedFieldValueEntity = ExtractedFieldValueEntity(
        id = domain.id,
        ruleExecutionId = domain.ruleExecutionId,
        ruleFieldId = domain.ruleFieldId,
        valueText = domain.valueText,
        valueNumber = domain.valueNumber,
        valueDate = domain.valueDate,
    )

    /**
     * Convert an ExtractedFieldValueEntity to a domain model.
     *
     * @param entity The database entity
     * @return The domain model
     */
    fun toDomain(entity: ExtractedFieldValueEntity): ExtractedFieldValue = ExtractedFieldValue(
        id = entity.id,
        ruleExecutionId = entity.ruleExecutionId,
        ruleFieldId = entity.ruleFieldId,
        valueText = entity.valueText,
        valueNumber = entity.valueNumber,
        valueDate = entity.valueDate,
    )

    /**
     * Convert extracted data from a rule execution into typed field value entities.
     *
     * @param executionId The rule execution ID
     * @param extractedData Map of field IDs to extracted string values
     * @param fields List of field definitions with their types
     * @return List of entities with properly typed values
     */
    fun fromExtractedData(
        executionId: String,
        extractedData: Map<String, String>,
        fields: List<RuleField>,
    ): List<ExtractedFieldValueEntity> {
        return extractedData.mapNotNull { (fieldId, stringValue) ->
            val field = fields.find { it.id == fieldId } ?: return@mapNotNull null
            createEntity(executionId, fieldId, stringValue, field.fieldType)
        }
    }

    /**
     * Create a single entity with properly typed value based on field type.
     */
    private fun createEntity(
        executionId: String,
        fieldId: String,
        stringValue: String,
        fieldType: RuleField.FieldType,
    ): ExtractedFieldValueEntity = when (fieldType) {
        RuleField.FieldType.NUMBER -> {
            val number = stringValue.replace(",", ".").toDoubleOrNull()
            ExtractedFieldValueEntity(
                id = UUID.randomUUID().toString(),
                ruleExecutionId = executionId,
                ruleFieldId = fieldId,
                valueText = stringValue,
                valueNumber = number,
                valueDate = null,
            )
        }

        RuleField.FieldType.DATE -> {
            // Try to parse as timestamp, otherwise store as text only
            val date = stringValue.toLongOrNull()
            ExtractedFieldValueEntity(
                id = UUID.randomUUID().toString(),
                ruleExecutionId = executionId,
                ruleFieldId = fieldId,
                valueText = stringValue,
                valueNumber = null,
                valueDate = date,
            )
        }

        RuleField.FieldType.CURRENCY -> {
            // Extract numeric value from currency string (e.g., "153.50 kr" -> 153.50)
            val number = extractCurrencyValue(stringValue)
            ExtractedFieldValueEntity(
                id = UUID.randomUUID().toString(),
                ruleExecutionId = executionId,
                ruleFieldId = fieldId,
                valueText = stringValue,
                valueNumber = number,
                valueDate = null,
            )
        }

        else -> { // STRING, BOOLEAN
            ExtractedFieldValueEntity(
                id = UUID.randomUUID().toString(),
                ruleExecutionId = executionId,
                ruleFieldId = fieldId,
                valueText = stringValue,
                valueNumber = null,
                valueDate = null,
            )
        }
    }

    /**
     * Attempt to extract numeric value from currency string.
     * Handles formats like "153.50 kr", "$1,234.56", "1.234,56 EUR"
     */
    private fun extractCurrencyValue(value: String): Double? {
        // Remove common currency symbols and whitespace
        val cleaned = value.replace(Regex("[\\p{Sc}\\s]"), "")
            .replace(",", ".")
        // Extract the first number sequence
        val matchResult = Regex("[\\d.]+").find(cleaned)
        return matchResult?.value?.toDoubleOrNull()
    }

    /**
     * Convert a list of domain models to entities.
     */
    fun toEntityList(domains: List<ExtractedFieldValue>): List<ExtractedFieldValueEntity> = domains.map { toEntity(it) }

    /**
     * Convert a list of entities to domain models.
     */
    fun toDomainList(entities: List<ExtractedFieldValueEntity>): List<ExtractedFieldValue> = entities.map { toDomain(it) }
}
