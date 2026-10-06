package dev.gaferneira.notificapp.features.databrowser.ui

import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import dev.gaferneira.notificapp.domain.model.RuleField
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val MONEY_FRACTION_DIGITS = 2
private const val NUMBER_MAX_FRACTION_DIGITS = 6

/**
 * Display text for a row's extracted value, or null when the row holds no value. Currency prefers the
 * original text (keeps the unit the user saw) and otherwise uses locale grouping with 2 decimals; numbers
 * use the locale's grouping, dates use an absolute localized date (never the relative notification time,
 * which would show "12:00 AM" for today); anything else falls back to the stored text.
 */
internal fun formatDataValue(row: DataBrowserRow, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String? {
    val number = row.valueNumber
    return when {
        row.fieldType == RuleField.FieldType.CURRENCY && !row.valueText.isNullOrBlank() -> row.valueText
        number != null && row.fieldType == RuleField.FieldType.CURRENCY -> {
            NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = MONEY_FRACTION_DIGITS
                maximumFractionDigits = MONEY_FRACTION_DIGITS
            }.format(number)
        }
        number != null && row.fieldType == RuleField.FieldType.NUMBER -> {
            NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = NUMBER_MAX_FRACTION_DIGITS }.format(number)
        }
        row.valueDate != null -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            .format(Instant.ofEpochMilli(row.valueDate).atZone(zone))
        else -> row.valueText ?: number?.let { NumberFormat.getNumberInstance(locale).format(it) }
    }
}
