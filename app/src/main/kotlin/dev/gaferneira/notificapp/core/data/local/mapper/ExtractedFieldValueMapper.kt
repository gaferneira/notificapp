package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.core.extraction.UnitResolver
import dev.gaferneira.notificapp.domain.model.ExtractedFieldValue
import dev.gaferneira.notificapp.domain.model.RuleField
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.util.UUID

/**
 * Mapper functions for converting between ExtractedFieldValue domain models and database entities.
 *
 * Decoding policy: the `field_type` snapshot of a stored value with an unknown name falls back to
 * [RuleField.FieldType.STRING] (the value text is still shown).
 */
internal object ExtractedFieldValueMapper {

    private const val MAX_MONTH = 12
    private const val YEAR_BASE = 2000

    private val NUMBER_RUN = Regex("""(?:(?<![\p{L}\d])(-)\p{Sc}?)?(\d(?:[\d.,]*\d)?)""")
    private val ISO_DATE = Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""")
    private val MONTH_FIRST = Regex("""([A-Za-z]{3,9})\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})""", RegexOption.IGNORE_CASE)
    private val DAY_FIRST = Regex("""(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]{3,9})\.?,?\s+(\d{4})""", RegexOption.IGNORE_CASE)
    private val NUMERIC_DATE = Regex("""(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{4}|\d{2})""")

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
        fieldName = domain.fieldName,
        fieldType = domain.fieldType.name,
        valueText = domain.valueText,
        valueNumber = domain.valueNumber,
        valueEpochDay = domain.valueDate?.toEpochDay(),
        valueUnit = domain.unit,
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
        fieldName = entity.fieldName,
        fieldType = RuleFieldMapper.fieldTypeFromStorage(entity.fieldType),
        valueText = entity.valueText,
        valueNumber = entity.valueNumber,
        valueDate = entity.valueEpochDay?.let(LocalDate::ofEpochDay),
        unit = entity.valueUnit,
    )

    /**
     * Convert extracted data from a rule execution into typed field value entities.
     *
     * @param executionId The rule execution ID
     * @param extractedData Map of field IDs to extracted string values
     * @param fields List of field definitions with their types
     * @param zone Zone used only to read an epoch-millis input as a calendar day; parsed date strings are zone-independent
     * @return List of entities with properly typed values
     */
    fun fromExtractedData(
        executionId: String,
        extractedData: Map<String, String>,
        fields: List<RuleField>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ExtractedFieldValueEntity> {
        return extractedData.mapNotNull { (fieldId, stringValue) ->
            val field = fields.find { it.id == fieldId } ?: return@mapNotNull null
            createEntity(executionId, field, stringValue, zone)
        }
    }

    /**
     * Create a single entity with properly typed value based on field type.
     */
    private fun createEntity(
        executionId: String,
        field: RuleField,
        stringValue: String,
        zone: ZoneId,
    ): ExtractedFieldValueEntity = when (field.fieldType) {
        RuleField.FieldType.NUMBER -> {
            val number = extractNumericValue(stringValue)
            ExtractedFieldValueEntity(
                id = UUID.randomUUID().toString(),
                ruleExecutionId = executionId,
                ruleFieldId = field.id,
                fieldName = field.name,
                fieldType = field.fieldType.name,
                valueText = stringValue,
                valueNumber = number,
                valueEpochDay = null,
                valueUnit = UnitResolver.resolveUnit(field, stringValue),
            )
        }

        RuleField.FieldType.DATE -> {
            // Epoch millis (read in [zone]), or a detected date string; stored as a zone-independent epoch day, otherwise text only
            val date = parseLocalDateValue(stringValue, zone)
            ExtractedFieldValueEntity(
                id = UUID.randomUUID().toString(),
                ruleExecutionId = executionId,
                ruleFieldId = field.id,
                fieldName = field.name,
                fieldType = field.fieldType.name,
                valueText = stringValue,
                valueNumber = null,
                valueEpochDay = date?.toEpochDay(),
            )
        }

        RuleField.FieldType.CURRENCY -> {
            // Extract numeric value from currency string (e.g., "153.50 kr" -> 153.50)
            val number = extractNumericValue(stringValue)
            ExtractedFieldValueEntity(
                id = UUID.randomUUID().toString(),
                ruleExecutionId = executionId,
                ruleFieldId = field.id,
                fieldName = field.name,
                fieldType = field.fieldType.name,
                valueText = stringValue,
                valueNumber = number,
                valueEpochDay = null,
                valueUnit = UnitResolver.resolveUnit(field, stringValue),
            )
        }

        else -> { // STRING, BOOLEAN
            ExtractedFieldValueEntity(
                id = UUID.randomUUID().toString(),
                ruleExecutionId = executionId,
                ruleFieldId = field.id,
                fieldName = field.name,
                fieldType = field.fieldType.name,
                valueText = stringValue,
                valueNumber = null,
                valueEpochDay = null,
            )
        }
    }

    /**
     * Attempt to extract a numeric value from a NUMBER/CURRENCY string, locale-tolerantly.
     *
     * Handles both separator conventions so the expense-tracking use case works on real bank
     * notifications: US/UK `"$1,234.56"` and European/Spanish `"1.234,56 EUR"` both yield
     * `1234.56`, and `"153,50 kr"` yields `153.5`. A minus sign directly before the number
     * (`"-12.50"`) makes it negative. Returns null when there is no parseable number.
     *
     * Disambiguation heuristic within the first number run:
     * - Both `.` and `,` present: the one that appears *last* is the decimal separator (thousands
     *   separators always precede the decimal); the other groups thousands and is removed.
     * - Only one separator type, appearing more than once (`"1.234.567"`): thousands grouping.
     * - Only one separator, appearing once: a decimal point, unless it leaves exactly 3 trailing
     *   digits (`"1,234"` → 1234), which reads as thousands grouping — the standard convention for
     *   currency, where 3-decimal amounts are vanishingly rare.
     */
    private fun extractNumericValue(value: String): Double? {
        // First contiguous run of digits and separators, e.g. "1.234,56" out of "€1.234,56 EUR".
        // The run never captures a trailing separator, so it always ends on a digit. A leading
        // minus (optionally before a currency symbol) counts only when it is not glued to a preceding letter/digit (e.g. "2024-01").
        val match = NUMBER_RUN.find(value) ?: return null
        val magnitude = normalizeSeparators(match.groupValues[2]).toDoubleOrNull()
        return if (match.groupValues[1].isNotEmpty()) magnitude?.unaryMinus() else magnitude
    }

    /**
     * Parse [value] into a calendar day, or null when it is not a recognized, unambiguous date.
     * An epoch-millis input is read as a calendar day in [zone]; every other format carries its own
     * calendar day and ignores [zone]. Never throws.
     *
     * Accepts an epoch-millis long, ISO `yyyy-MM-dd`, English month-name formats ("Jan 12, 2024",
     * "12 January 2024", "Jan 12th, 2024") and numeric `d/m/y` with `/ - .` separators — the latter
     * only when one of the first two parts exceeds 12 (otherwise day/month order is ambiguous).
     * Two-digit years map to 2000+yy.
     */
    private fun parseLocalDateValue(value: String, zone: ZoneId): LocalDate? {
        val text = value.trim()
        return text.toLongOrNull()?.let { millis ->
            runCatching { Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() }.getOrNull()
        } ?: parseLocalDate(text)
    }

    private fun parseLocalDate(text: String): LocalDate? = runCatching {
        ISO_DATE.matchEntire(text)?.let { m ->
            return@runCatching LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        MONTH_FIRST.matchEntire(text)?.let { m ->
            val month = monthOf(m.groupValues[1]) ?: return@runCatching null
            return@runCatching LocalDate.of(expandYear(m.groupValues[3]), month, m.groupValues[2].toInt())
        }
        DAY_FIRST.matchEntire(text)?.let { m ->
            val month = monthOf(m.groupValues[2]) ?: return@runCatching null
            return@runCatching LocalDate.of(expandYear(m.groupValues[3]), month, m.groupValues[1].toInt())
        }
        NUMERIC_DATE.matchEntire(text)?.let { m ->
            val first = m.groupValues[1].toInt()
            val second = m.groupValues[2].toInt()
            val year = expandYear(m.groupValues[3])
            return@runCatching when {
                first > MAX_MONTH && second <= MAX_MONTH -> LocalDate.of(year, second, first)
                second > MAX_MONTH && first <= MAX_MONTH -> LocalDate.of(year, first, second)
                else -> null // ambiguous (both <= 12) or impossible (both > 12)
            }
        }
        null
    }.getOrNull()

    private fun expandYear(raw: String): Int = raw.toInt().let { if (raw.length <= 2) YEAR_BASE + it else it }

    private fun monthOf(name: String): Int? {
        val key = name.lowercase()
        return Month.entries.firstOrNull { month ->
            val full = month.name.lowercase()
            key == full || key == full.take(3) || (month == Month.SEPTEMBER && key == "sept")
        }?.value
    }

    /**
     * Collapse locale-specific thousands/decimal separators in [numberRun] into a plain
     * `.`-decimal string parseable by [String.toDoubleOrNull]. See [extractNumericValue] for the
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
