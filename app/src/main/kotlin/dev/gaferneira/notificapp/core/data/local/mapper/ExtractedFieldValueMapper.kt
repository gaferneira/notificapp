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
     * Attempt to extract a numeric value from a currency string, locale-tolerantly.
     *
     * Handles both separator conventions so the expense-tracking use case works on real bank
     * notifications: US/UK `"$1,234.56"` and European/Spanish `"1.234,56 EUR"` both yield
     * `1234.56`, and `"153,50 kr"` yields `153.5`. Returns null when there is no parseable number.
     *
     * Disambiguation heuristic within the first number run:
     * - Both `.` and `,` present: the one that appears *last* is the decimal separator (thousands
     *   separators always precede the decimal); the other groups thousands and is removed.
     * - Only one separator type, appearing more than once (`"1.234.567"`): thousands grouping.
     * - Only one separator, appearing once: a decimal point, unless it leaves exactly 3 trailing
     *   digits (`"1,234"` → 1234), which reads as thousands grouping — the standard convention for
     *   currency, where 3-decimal amounts are vanishingly rare.
     */
    private fun extractCurrencyValue(value: String): Double? {
        // First contiguous run of digits and separators, e.g. "1.234,56" out of "€1.234,56 EUR".
        // The run never captures a trailing separator, so it always ends on a digit.
        val numberRun = Regex("""\d(?:[\d.,]*\d)?""").find(value)?.value ?: return null
        return normalizeSeparators(numberRun).toDoubleOrNull()
    }

    /**
     * Collapse locale-specific thousands/decimal separators in [numberRun] into a plain
     * `.`-decimal string parseable by [String.toDoubleOrNull]. See [extractCurrencyValue] for the
     * heuristic.
     */
    private fun normalizeSeparators(numberRun: String): String {
        val lastDot = numberRun.lastIndexOf('.')
        val lastComma = numberRun.lastIndexOf(',')
        return when {
            lastDot >= 0 && lastComma >= 0 -> {
                val decimalSep = if (lastDot > lastComma) '.' else ','
                val thousandsSep = if (decimalSep == '.') ',' else '.'
                numberRun.replace(thousandsSep.toString(), "").replace(decimalSep, '.')
            }
            lastComma >= 0 -> disambiguateSingleSeparator(numberRun, ',')
            lastDot >= 0 -> disambiguateSingleSeparator(numberRun, '.')
            else -> numberRun
        }
    }

    private fun disambiguateSingleSeparator(numberRun: String, separator: Char): String {
        val occurrences = numberRun.count { it == separator }
        // A repeated single separator can only be thousands grouping ("1.234.567").
        if (occurrences > 1) return numberRun.replace(separator.toString(), "")
        val digitsAfter = numberRun.substringAfterLast(separator).length
        return if (digitsAfter == 3) {
            numberRun.replace(separator.toString(), "")
        } else {
            numberRun.replace(separator, '.')
        }
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
