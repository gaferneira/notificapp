package dev.gaferneira.notificapp.features.insights.ui

import dev.gaferneira.notificapp.core.ui.text.formatCount
import dev.gaferneira.notificapp.core.ui.text.integerFormat
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.TrendPoint
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.Locale

class InsightsFormatTest {

    private val locale = Locale.US

    // 2026-10-05 is a Monday.
    private fun week(vararg counts: Int) = counts.mapIndexed { i, count -> TrendPoint("2026-10-0${5 + i}", count) }

    @Test
    fun `unparseable date falls back to the raw string`() {
        // Given
        val trend = listOf(TrendPoint("not-a-date", 3), TrendPoint("2026-10-05", 1))

        // When
        val chart = buildTrendChart(trend, InsightsPeriod.DAYS_30, locale)

        // Then
        chart.entries.map { it.label } shouldBe listOf("not-a-date", "5")
        chart.peakDayLabel shouldBe "not-a-date"
        chart.total shouldBe 4
    }

    @Test
    fun `all-zero trend has no peak`() {
        // Given / When
        val chart = buildTrendChart(week(0, 0, 0), InsightsPeriod.DAYS_7, locale)

        // Then
        chart.peakDayLabel shouldBe null
        chart.peakCount shouldBe 0
        chart.total shouldBe 0
        chart.entries.size shouldBe 3
    }

    @Test
    fun `average is the total over every day of the trend, zero days included`() {
        // Given / When
        val chart = buildTrendChart(week(1, 5, 0, 2), InsightsPeriod.DAYS_7, locale)

        // Then
        chart.total shouldBe 8
        chart.average shouldBe 2.0
    }

    @Test
    fun `average of an empty trend is zero`() {
        // Given / When
        val chart = buildTrendChart(emptyList(), InsightsPeriod.DAYS_7, locale)

        // Then
        chart.average shouldBe 0.0
    }

    @Test
    fun `entries carry the full medium date as their description`() {
        // Given / When
        val chart = buildTrendChart(week(1, 5), InsightsPeriod.DAYS_7, locale)

        // Then
        chart.entries.map { it.description } shouldBe listOf("Oct 5, 2026", "Oct 6, 2026")
    }

    @Test
    fun `7-day labels are short weekdays and the peak uses the weekday`() {
        // Given / When
        val chart = buildTrendChart(week(1, 5, 2), InsightsPeriod.DAYS_7, locale)

        // Then
        chart.entries.map { it.label } shouldBe listOf("Mon", "Tue", "Wed")
        chart.peakDayLabel shouldBe "Tue"
        chart.peakCount shouldBe 5
    }

    @Test
    fun `30-day labels are the day of month and the peak is a medium date`() {
        // Given / When
        val chart = buildTrendChart(week(1, 5, 2), InsightsPeriod.DAYS_30, locale)

        // Then
        chart.entries.map { it.label } shouldBe listOf("5", "6", "7")
        chart.peakDayLabel shouldBe "Oct 6, 2026"
    }

    @Test
    fun `currency sums always show two decimals`() {
        // Given
        val formats = FieldSumFormats(locale)

        // When / Then
        formats.format(1234.5, RuleField.FieldType.CURRENCY) shouldBe "1,234.50"
        formats.format(12.0, RuleField.FieldType.CURRENCY) shouldBe "12.00"
    }

    @Test
    fun `number sums drop trailing zeros and cap at two decimals`() {
        // Given
        val formats = FieldSumFormats(locale)

        // When / Then
        formats.format(540.0, RuleField.FieldType.NUMBER) shouldBe "540"
        formats.format(1.2345, RuleField.FieldType.NUMBER) shouldBe "1.23"
    }

    @Test
    fun `counts use the locale grouping separators`() {
        // Given / When / Then
        formatCount(1234567, integerFormat(Locale.US)) shouldBe "1,234,567"
        formatCount(1234567, integerFormat(Locale.GERMANY)) shouldBe "1.234.567"
        formatCount(0, integerFormat(Locale.US)) shouldBe "0"
    }

    @Test
    fun `currency sum with a valid ISO unit uses the currency format`() {
        FieldSumFormats(Locale.US).format(1450.0, RuleField.FieldType.CURRENCY, "SEK").let {
            it.contains("1,450.00") shouldBe true
            (it.contains("SEK") || it.contains("kr")) shouldBe true
        }
        FieldSumFormats(Locale.US).format(120.0, RuleField.FieldType.CURRENCY, "USD") shouldBe "$120.00"
    }

    @Test
    fun `number sum appends the unit and no unit keeps the plain number`() {
        val formats = FieldSumFormats(Locale.US)

        formats.format(540.0, RuleField.FieldType.NUMBER, "min") shouldBe "540 min"
        formats.format(540.0, RuleField.FieldType.NUMBER) shouldBe "540"
        formats.format(2340.0, RuleField.FieldType.CURRENCY) shouldBe "2,340.00"
    }

    @Test
    fun `currency sum with a non-ISO unit appends it as text`() {
        FieldSumFormats(Locale.US).format(5.0, RuleField.FieldType.CURRENCY, "pts") shouldBe "5.00 pts"
    }
}
