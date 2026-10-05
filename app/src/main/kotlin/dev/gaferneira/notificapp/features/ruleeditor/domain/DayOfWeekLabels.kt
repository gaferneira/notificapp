package dev.gaferneira.notificapp.features.ruleeditor.domain

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Short day name for chips ("Mon" / "lun"), localized; never the raw enum name. */
internal fun DayOfWeek.shortLabel(locale: Locale): String = getDisplayName(TextStyle.SHORT_STANDALONE, locale)

/** Full day name for screen readers ("Monday" / "lunes"), localized. */
internal fun DayOfWeek.fullLabel(locale: Locale): String = getDisplayName(TextStyle.FULL_STANDALONE, locale)
