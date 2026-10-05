package dev.gaferneira.notificapp.features.databrowser.viewmodel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DataDateRangeTest {

    private val bogota = ZoneId.of("America/Bogota")
    private val newYork = ZoneId.of("America/New_York")

    private fun pickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun localMillis(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `start is the start of the first day in the given zone`() {
        val bounds = DataDateRange.toFilterBounds(
            startPickerMillis = pickerMillis(LocalDate.of(2026, 3, 1)),
            endPickerMillis = pickerMillis(LocalDate.of(2026, 3, 3)),
            zone = bogota,
        )

        bounds.dateFrom shouldBe localMillis(LocalDate.of(2026, 3, 1), bogota)
    }

    @Test
    fun `end is the last millisecond of the last selected day`() {
        val bounds = DataDateRange.toFilterBounds(
            startPickerMillis = pickerMillis(LocalDate.of(2026, 3, 1)),
            endPickerMillis = pickerMillis(LocalDate.of(2026, 3, 3)),
            zone = bogota,
        )

        bounds.dateTo shouldBe localMillis(LocalDate.of(2026, 3, 4), bogota) - 1
    }

    @Test
    fun `a value at 23-59-59 on the last day is included and midnight of the next day is not`() {
        val bounds = DataDateRange.toFilterBounds(
            startPickerMillis = pickerMillis(LocalDate.of(2026, 3, 1)),
            endPickerMillis = pickerMillis(LocalDate.of(2026, 3, 3)),
            zone = bogota,
        )
        val lastSecond = LocalDate.of(2026, 3, 3).atTime(23, 59, 59).atZone(bogota).toInstant().toEpochMilli()
        val nextMidnight = localMillis(LocalDate.of(2026, 3, 4), bogota)

        (lastSecond in bounds.dateFrom..bounds.dateTo) shouldBe true
        (nextMidnight in bounds.dateFrom..bounds.dateTo) shouldBe false
    }

    @Test
    fun `a missing end date behaves as a single day range`() {
        val day = pickerMillis(LocalDate.of(2026, 3, 1))

        val single = DataDateRange.toFilterBounds(day, null, bogota)
        val explicit = DataDateRange.toFilterBounds(day, day, bogota)

        single shouldBe explicit
        single.dateTo shouldBe localMillis(LocalDate.of(2026, 3, 2), bogota) - 1
    }

    @Test
    fun `a DST spring-forward day spans 23 hours`() {
        val day = pickerMillis(LocalDate.of(2026, 3, 8))

        val bounds = DataDateRange.toFilterBounds(day, day, newYork)

        (bounds.dateTo - bounds.dateFrom + 1) shouldBe 23L * 60 * 60 * 1000
    }

    @Test
    fun `toPickerMillis round-trips the selected days`() {
        val start = pickerMillis(LocalDate.of(2026, 3, 1))
        val end = pickerMillis(LocalDate.of(2026, 3, 3))

        for (zone in listOf(bogota, newYork)) {
            val bounds = DataDateRange.toFilterBounds(start, end, zone)

            DataDateRange.toPickerMillis(bounds.dateFrom, bounds.dateTo, zone) shouldBe (start to end)
        }
    }

    @Test
    fun `toPickerMillis of an absent bound is null`() {
        val instant = Instant.parse("2026-03-01T12:00:00Z").toEpochMilli()

        DataDateRange.toPickerMillis(null, null, bogota) shouldBe (null to null)
        DataDateRange.toPickerMillis(instant, null, bogota).second shouldBe null
    }
}
