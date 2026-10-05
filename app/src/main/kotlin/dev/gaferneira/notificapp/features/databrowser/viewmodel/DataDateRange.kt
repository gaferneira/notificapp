package dev.gaferneira.notificapp.features.databrowser.viewmodel

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Maps the Material 3 `DateRangePicker` selection (UTC-midnight millis per picked day) to the
 * inclusive epoch-millis bounds of `DataBrowserFilter` in a local [ZoneId], and back. The zone is a
 * parameter so the mapping stays pure and testable.
 */
object DataDateRange {

    /** Inclusive epoch-millis bounds: the DAO compares with `>=` / `<=`. */
    data class Bounds(val dateFrom: Long, val dateTo: Long)

    /**
     * Start of the first selected day through the last millisecond of the last selected day.
     * A null [endPickerMillis] selects a single day.
     */
    fun toFilterBounds(startPickerMillis: Long, endPickerMillis: Long?, zone: ZoneId): Bounds {
        val startDate = Instant.ofEpochMilli(startPickerMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val endDate = Instant.ofEpochMilli(endPickerMillis ?: startPickerMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return Bounds(
            dateFrom = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
            dateTo = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
        )
    }

    /** Inverse of [toFilterBounds]: UTC-midnight millis used to seed the picker, null when absent. */
    fun toPickerMillis(dateFrom: Long?, dateTo: Long?, zone: ZoneId): Pair<Long?, Long?> = dateFrom.toPickerMillis(zone) to dateTo.toPickerMillis(zone)

    private fun Long?.toPickerMillis(zone: ZoneId): Long? = this?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
}
