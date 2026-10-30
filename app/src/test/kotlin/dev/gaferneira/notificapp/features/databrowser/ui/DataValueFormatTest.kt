package dev.gaferneira.notificapp.features.databrowser.ui

import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.RuleField
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class DataValueFormatTest {

    private fun row(
        type: RuleField.FieldType,
        text: String? = null,
        number: Double? = null,
        date: LocalDate? = null,
    ) = DataBrowserRow(
        valueId = "v",
        executionId = "e",
        notificationId = "n",
        ruleName = "Rule",
        packageName = "com.a",
        appName = "A",
        fieldName = "Field",
        fieldType = type,
        valueText = text,
        valueNumber = number,
        valueDate = date,
        notificationTitle = null,
        notificationContent = null,
        createdAt = 0L,
    )

    @Test
    fun `currency always shows two decimals with locale grouping`() {
        formatDataValue(row(RuleField.FieldType.CURRENCY, number = 2340.5), Locale.US) shouldBe "2,340.50"
        formatDataValue(row(RuleField.FieldType.CURRENCY, number = 10.0), Locale.GERMANY) shouldBe "10,00"
    }

    @Test
    fun `currency prefers original text and falls back when blank`() {
        formatDataValue(row(RuleField.FieldType.CURRENCY, text = "249.90 kr", number = 249.9), Locale.US) shouldBe "249.90 kr"
        formatDataValue(row(RuleField.FieldType.CURRENCY, text = "  ", number = 249.9), Locale.US) shouldBe "249.90"
    }

    @Test
    fun `numbers drop trailing zeros`() {
        formatDataValue(row(RuleField.FieldType.NUMBER, number = 1200.0), Locale.US) shouldBe "1,200"
    }

    @Test
    fun `text values are shown as stored and an empty row yields null`() {
        formatDataValue(row(RuleField.FieldType.STRING, text = "hello"), Locale.US) shouldBe "hello"
        formatDataValue(row(RuleField.FieldType.STRING), Locale.US) shouldBe null
    }

    @Test
    fun `an extracted date is an absolute localized date even when it is today`() {
        val today = LocalDate.now()

        val text = formatDataValue(row(RuleField.FieldType.DATE, date = today), Locale.US)

        text shouldBe DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US).format(today)
    }

    @Test
    fun `an extracted date uses the given locale`() {
        formatDataValue(row(RuleField.FieldType.DATE, date = LocalDate.of(2026, 3, 5)), Locale.US) shouldBe "Mar 5, 2026"
    }
}
