package dev.gaferneira.notificapp.features.insights.ui

import dev.gaferneira.notificapp.core.ui.components.BarChartEntry
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.TrendPoint
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private const val MONEY_FRACTION_DIGITS = 2

/** Number formats for field sums with the locale's grouping; currency fields always show 2 decimals. */
internal class FieldSumFormats(locale: Locale) {
    private val number = decimalFormat(locale, minFractionDigits = 0)
    private val currency = decimalFormat(locale, minFractionDigits = MONEY_FRACTION_DIGITS)

    fun format(sum: Double, fieldType: RuleField.FieldType): String = (if (fieldType == RuleField.FieldType.CURRENCY) currency else number).format(sum)

    private fun decimalFormat(locale: Locale, minFractionDigits: Int): NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = MONEY_FRACTION_DIGITS
        minimumFractionDigits = minFractionDigits
    }
}

/** Chart-ready data derived from a trend series. */
internal data class TrendChart(
    val entries: ImmutableList<BarChartEntry>,
    val total: Int,
    val peakCount: Int,
    /** Human-readable day of the highest bar, or null when every day is zero. */
    val peakDayLabel: String?,
)

/**
 * Maps [trend] to bar entries: localized short weekday labels for a 7-day period, day of month for
 * 30 days. Unparseable dates fall back to the raw string so a bad row never crashes the screen.
 */
internal fun buildTrendChart(trend: List<TrendPoint>, period: InsightsPeriod, locale: Locale): TrendChart {
    val parsed = trend.map { it to runCatching { LocalDate.parse(it.date) }.getOrNull() }
    val entries = parsed.map { (point, date) ->
        val label = when {
            date == null -> point.date
            period == InsightsPeriod.DAYS_7 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            else -> date.dayOfMonth.toString()
        }
        BarChartEntry(label = label, value = point.count)
    }.toImmutableList()
    val peak = parsed.maxByOrNull { it.first.count }?.takeIf { it.first.count > 0 }
    val peakLabel = peak?.let { (point, date) ->
        when {
            date == null -> point.date
            period == InsightsPeriod.DAYS_7 -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }
    }
    return TrendChart(
        entries = entries,
        total = trend.sumOf { it.count },
        peakCount = peak?.first?.count ?: 0,
        peakDayLabel = peakLabel,
    )
}
