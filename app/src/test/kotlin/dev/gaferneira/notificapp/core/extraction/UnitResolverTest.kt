package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.testutil.createTestField
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UnitResolverTest {

    @ParameterizedTest
    @CsvSource(
        "€,EUR",
        "£,GBP",
        "¥,JPY",
        "₹,INR",
        "₩,KRW",
        "₽,RUB",
        "₺,TRY",
        "₪,ILS",
        "₱,PHP",
        "฿,THB",
        "R$,BRL",
        "SEK,SEK",
        "USD,USD",
        "eur,EUR",
    )
    fun `resolves an unambiguous marker to its ISO code`(marker: String, iso: String) {
        UnitResolver.resolveMarker(marker) shouldBe iso
    }

    @ParameterizedTest
    @CsvSource("$", "kr")
    fun `ambiguous markers resolve to null`(marker: String) {
        UnitResolver.resolveMarker(marker) shouldBe null
    }

    @Test
    fun `null or unknown marker resolves to null`() {
        UnitResolver.resolveMarker(null) shouldBe null
        UnitResolver.resolveMarker("zł") shouldBe null
    }

    @Test
    fun `detects the marker inside extracted text`() {
        UnitResolver.detectMarker("153.50 kr") shouldBe "kr"
        UnitResolver.detectMarker("R$ 20,00") shouldBe "R$"
        UnitResolver.detectMarker("Total 1.234,56 EUR") shouldBe "EUR"
        UnitResolver.detectMarker("42") shouldBe null
    }

    @Test
    fun `smart amount detection surfaces the raw marker as unit`() {
        val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

        (FieldExtractor.extract("Paid 153.50 kr at ICA", field) as ExtractionResult.Success).unit shouldBe "kr"
        (FieldExtractor.extract("Paid €12.00", field) as ExtractionResult.Success).unit shouldBe "€"
        (FieldExtractor.extract("Paid 12.00", field) as ExtractionResult.Success).unit shouldBe null
    }

    @Test
    fun `other methods leave the extraction unit null`() {
        val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "Total: "))

        (FieldExtractor.extract("Total: 12 kr", field) as ExtractionResult.Success).unit shouldBe null
    }

    private fun field(type: RuleField.FieldType, default: String?) = createTestField(
        fieldType = type,
        method = ExtractionMethod.SmartAmountDetection,
        defaultUnit = default,
    )

    @Test
    fun `currency - an unambiguous marker wins over the default`() {
        UnitResolver.resolveUnit(field(RuleField.FieldType.CURRENCY, "SEK"), "€12.00") shouldBe "EUR"
    }

    @Test
    fun `currency - an ambiguous marker falls back to the default`() {
        UnitResolver.resolveUnit(field(RuleField.FieldType.CURRENCY, "usd"), "$20.00") shouldBe "USD"
        UnitResolver.resolveUnit(field(RuleField.FieldType.CURRENCY, " sek "), "153.50 kr") shouldBe "SEK"
    }

    @Test
    fun `currency - no marker uses the default, and null without one`() {
        UnitResolver.resolveUnit(field(RuleField.FieldType.CURRENCY, "NOK"), "153.50") shouldBe "NOK"
        UnitResolver.resolveUnit(field(RuleField.FieldType.CURRENCY, null), "153.50") shouldBe null
        UnitResolver.resolveUnit(field(RuleField.FieldType.CURRENCY, "  "), "$20") shouldBe null
    }

    @Test
    fun `number uses the default unit trimmed as typed`() {
        UnitResolver.resolveUnit(field(RuleField.FieldType.NUMBER, " GB "), "12") shouldBe "GB"
        UnitResolver.resolveUnit(field(RuleField.FieldType.NUMBER, null), "12") shouldBe null
    }

    @Test
    fun `other types never carry a unit`() {
        UnitResolver.resolveUnit(field(RuleField.FieldType.STRING, "km"), "€12") shouldBe null
        UnitResolver.resolveUnit(field(RuleField.FieldType.DATE, "km"), "2024-01-01") shouldBe null
    }
}
