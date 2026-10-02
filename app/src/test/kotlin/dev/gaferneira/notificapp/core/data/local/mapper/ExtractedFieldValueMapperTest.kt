package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.testutil.createTestField
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

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
}
