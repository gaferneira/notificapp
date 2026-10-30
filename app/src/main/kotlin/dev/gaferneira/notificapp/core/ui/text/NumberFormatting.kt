package dev.gaferneira.notificapp.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.util.Locale

/** Whole-number format with the [locale]'s grouping separators. Not thread-safe: keep it on the UI thread. */
fun integerFormat(locale: Locale): NumberFormat = NumberFormat.getIntegerInstance(locale)

/** Formats a whole count with [format] (see [rememberIntegerFormat]). */
fun formatCount(count: Int, format: NumberFormat): String = format.format(count)

/** The primary locale of the current configuration; recomposes when the user changes the app language. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** A [integerFormat] for the current locale, created once per locale instead of once per formatted number. */
@Composable
fun rememberIntegerFormat(): NumberFormat {
    val locale = currentLocale()
    return remember(locale) { integerFormat(locale) }
}
