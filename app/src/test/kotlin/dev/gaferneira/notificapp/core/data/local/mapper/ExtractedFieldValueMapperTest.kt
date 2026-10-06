package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.testutil.createTestField
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Focuses on the locale-aware currency parsing in [ExtractedFieldValueMapper] (exercised through
 * the public `fromExtractedData`), the differentiator's expense-tracking path. The prior parser
 * blindly replaced every comma with a dot, so any thousands-separated amount ("$1,234.56",
 * "1.234,56 EUR") degraded to a null `value_number` and dropped out of numeric queries.
 */
class ExtractedFieldValueMapperTest {

    private val fieldId = "amount"
    private val currencyField = createTestField(
        id = fieldId,
        name = "Amount",
        fieldType = RuleField.FieldType.CURRENCY,
        method = RuleField.ExtractionMethod.SmartAmountDetection,
    )

    private fun parseCurrency(raw: String): Double? = ExtractedFieldValueMapper
        .fromExtractedData(
            executionId = "exec-1",
            extractedData = mapOf(fieldId to raw),
            fields = listOf(currencyField),
        )
        .single()
        .valueNumber

    @Test
    fun `snapshots the field name and type onto the stored value`() {
        val entity = ExtractedFieldValueMapper
            .fromExtractedData(executionId = "exec-1", extractedData = mapOf(fieldId to "10"), fields = listOf(currencyField))
            .single()

        entity.ruleFieldId shouldBe fieldId
        entity.fieldName shouldBe "Amount"
        entity.fieldType shouldBe "CURRENCY"
    }

    @ParameterizedTest
    @CsvSource(
        // US / UK: comma thousands, dot decimal
        "'\$1,234.56', 1234.56",
        "'1,234,567.89', 1234567.89",
        "'153.50 kr', 153.5",
        // European / Spanish (the launch locale): dot thousands, comma decimal
        "'1.234,56 EUR', 1234.56",
        "'1.234.567,89', 1234567.89",
        "'153,50 kr', 153.5",
        "'45,00', 45.0",
        // Single separator, no ambiguity from a 3-digit tail
        "'1,50 €', 1.5",
        "'12.34', 12.34",
        // Single separator leaving exactly 3 digits reads as thousands grouping
        "'1,234', 1234.0",
        "'1.500', 1500.0",
        // No separators, and value embedded in surrounding text
        "'42', 42.0",
        "'Received 45.00 from Maria', 45.0",
    )
    fun `parses locale-specific separators into value_number`(raw: String, expected: Double) {
        parseCurrency(raw) shouldBe expected
    }

    @Test
    fun `stores the raw text verbatim even when no number is parseable`() {
        // Given: a currency field whose extracted text carries no digits
        val entity = ExtractedFieldValueMapper.fromExtractedData(
            executionId = "exec-1",
            extractedData = mapOf(fieldId to "no amount here"),
            fields = listOf(currencyField),
        ).single()

        // Then: value_number is null but the original text is preserved for display
        entity.valueNumber shouldBe null
        entity.valueText shouldBe "no amount here"
    }

    @ParameterizedTest
    @CsvSource(
        "'-12.50', -12.5",
        "'Refund -45,00 EUR', -45.0",
        "'-\$1,234.56', -1234.56",
    )
    fun `currency honors a leading minus sign`(raw: String, expected: Double) {
        parseCurrency(raw) shouldBe expected
    }

    // --- NUMBER ---

    private val numberField = createTestField(
        id = fieldId,
        name = "Qty",
        fieldType = RuleField.FieldType.NUMBER,
        method = RuleField.ExtractionMethod.SmartAmountDetection,
    )

    private fun parseNumber(raw: String): Double? = map(numberField, raw).valueNumber

    private fun map(field: RuleField, raw: String, zone: ZoneId = utc) = ExtractedFieldValueMapper
        .fromExtractedData("exec-1", mapOf(fieldId to raw), listOf(field), zone)
        .single()

    @ParameterizedTest
    @CsvSource(
        "'1,234.56', 1234.56",
        "'1.234,56', 1234.56",
        "'Total 42', 42.0",
        "'12,5', 12.5",
        "'-12.50', -12.5",
        "'temp -3', -3.0",
    )
    fun `NUMBER parses locale-tolerant values embedded in text`(raw: String, expected: Double) {
        parseNumber(raw) shouldBe expected
    }

    @Test
    fun `NUMBER without digits yields null number and keeps text`() {
        val entity = map(numberField, "n/a")

        entity.valueNumber shouldBe null
        entity.valueText shouldBe "n/a"
    }

    // --- DATE ---

    private val utc: ZoneId = ZoneOffset.UTC
    private val dateField = createTestField(
        id = fieldId,
        name = "When",
        fieldType = RuleField.FieldType.DATE,
        method = RuleField.ExtractionMethod.SmartDateDetection,
    )

    private fun epochDay(year: Int, month: Int, day: Int): Long = LocalDate.of(year, month, day).toEpochDay()

    private fun parseDate(raw: String): Long? = map(dateField, raw).valueEpochDay

    @ParameterizedTest
    @CsvSource(
        "'2024-01-15'",
        "'15/01/2024'",
        "'15.01.2024'",
        "'15-01-2024'",
        "'01/15/2024'",
        "'Jan 15, 2024'",
        "'January 15 2024'",
        "'15 January 2024'",
        "'15 Jan 2024'",
        "'Jan 15th, 2024'",
        "'15/01/24'",
    )
    fun `DATE parses detected formats to an epoch day`(raw: String) {
        parseDate(raw) shouldBe epochDay(2024, 1, 15)
    }

    @Test
    fun `DATE reads epoch millis as a calendar day in the supplied zone`() {
        // 2024-01-15T00:00:00Z is still Jan 14 in Bogota (UTC-5).
        map(dateField, "1705276800000", utc).valueEpochDay shouldBe epochDay(2024, 1, 15)
        map(dateField, "1705276800000", ZoneId.of("America/Bogota")).valueEpochDay shouldBe epochDay(2024, 1, 14)
    }

    @Test
    fun `DATE stores the same epoch day regardless of the default time zone`() {
        val original = TimeZone.getDefault()
        try {
            val stored = listOf("Pacific/Kiritimati", "Pacific/Pago_Pago").map { id ->
                TimeZone.setDefault(TimeZone.getTimeZone(id))
                ExtractedFieldValueMapper
                    .fromExtractedData("exec-1", mapOf(fieldId to "2024-01-15"), listOf(dateField))
                    .single().valueEpochDay
            }

            stored shouldBe listOf(epochDay(2024, 1, 15), epochDay(2024, 1, 15))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `DATE with ambiguous day and month leaves valueEpochDay null but keeps text`() {
        val entity = map(dateField, "03/04/2024")

        entity.valueEpochDay shouldBe null
        entity.valueText shouldBe "03/04/2024"
    }

    @Test
    fun `DATE two-digit year maps to 2000s`() {
        parseDate("25/12/99") shouldBe epochDay(2099, 12, 25)
    }

    @ParameterizedTest
    @CsvSource("'2024-02-30'", "'Feb 30, 2024'", "'31/02/2024'", "'garbage'", "'Foo 12, 2024'", "''")
    fun `DATE invalid or unrecognized values yield null without throwing`(raw: String) {
        parseDate(raw) shouldBe null
    }

    private fun unitOf(type: RuleField.FieldType, default: String?, raw: String): String? = ExtractedFieldValueMapper
        .fromExtractedData(
            executionId = "exec-1",
            extractedData = mapOf(fieldId to raw),
            fields = listOf(createTestField(id = fieldId, fieldType = type, method = RuleField.ExtractionMethod.SmartAmountDetection, defaultUnit = default)),
        )
        .single()
        .valueUnit

    @Test
    fun `stored unit - unambiguous marker wins over the default`() {
        unitOf(RuleField.FieldType.CURRENCY, "SEK", "€12.00") shouldBe "EUR"
    }

    @Test
    fun `stored unit - ambiguous marker with a default uses the default`() {
        unitOf(RuleField.FieldType.CURRENCY, "usd", "$20.00") shouldBe "USD"
        unitOf(RuleField.FieldType.CURRENCY, "SEK", "153.50 kr") shouldBe "SEK"
    }

    @Test
    fun `stored unit - no marker uses the default, ambiguous marker without default is null`() {
        unitOf(RuleField.FieldType.CURRENCY, "NOK", "153.50") shouldBe "NOK"
        unitOf(RuleField.FieldType.CURRENCY, null, "$20.00") shouldBe null
    }

    @Test
    fun `stored unit - NUMBER uses the default and TEXT stores none`() {
        unitOf(RuleField.FieldType.NUMBER, "km", "12") shouldBe "km"
        unitOf(RuleField.FieldType.STRING, "km", "12") shouldBe null
    }
}
