package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.SnoozeSchedule
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class SnoozeScheduleCalculatorTest {

    @Test
    fun `single daily checkpoint before start time snoozes until today's start`() {
        // Given: a schedule with no interval (single daily checkpoint at 09:00) and now before it
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0)
        val now = LocalDateTime.of(2026, 7, 11, 8, 45)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it lands on today's start time
        checkpoint shouldBe LocalDateTime.of(2026, 7, 11, 9, 0)
    }

    @Test
    fun `single daily checkpoint after start time rolls to tomorrow`() {
        // Given: a schedule with no interval and now after today's start time
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0)
        val now = LocalDateTime.of(2026, 7, 11, 10, 0)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it rolls to the same time tomorrow
        checkpoint shouldBe LocalDateTime.of(2026, 7, 12, 9, 0)
    }

    @Test
    fun `recurring schedule before start time snoozes until today's start`() {
        // Given: a recurring schedule and now before the start time
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, intervalMinutes = 60, windowEndHour = 18, windowEndMinute = 0)
        val now = LocalDateTime.of(2026, 7, 11, 7, 0)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it lands on today's start time
        checkpoint shouldBe LocalDateTime.of(2026, 7, 11, 9, 0)
    }

    @Test
    fun `recurring schedule mid-window snoozes until the next interval boundary`() {
        // Given: start 09:00, interval 60 min, window end 18:00, now 09:30
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, intervalMinutes = 60, windowEndHour = 18, windowEndMinute = 0)
        val now = LocalDateTime.of(2026, 7, 11, 9, 30)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it lands on the next hourly boundary (10:00)
        checkpoint shouldBe LocalDateTime.of(2026, 7, 11, 10, 0)
    }

    @Test
    fun `recurring schedule exactly at a checkpoint returns the following checkpoint`() {
        // Given: start 09:00, interval 60 min, now exactly at 10:00
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, intervalMinutes = 60, windowEndHour = 18, windowEndMinute = 0)
        val now = LocalDateTime.of(2026, 7, 11, 10, 0)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it lands on the following boundary (11:00), never the current instant
        checkpoint shouldBe LocalDateTime.of(2026, 7, 11, 11, 0)
    }

    @Test
    fun `recurring schedule exactly at window end passes through`() {
        // Given: start 09:00, interval 60 min, window end 18:00, now exactly 18:00
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, intervalMinutes = 60, windowEndHour = 18, windowEndMinute = 0)
        val now = LocalDateTime.of(2026, 7, 11, 18, 0)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it passes through (window end is exclusive)
        checkpoint.shouldBeNull()
    }

    @Test
    fun `recurring schedule past window end passes through`() {
        // Given: start 09:00, interval 60 min, window end 18:00, now 18:15
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, intervalMinutes = 60, windowEndHour = 18, windowEndMinute = 0)
        val now = LocalDateTime.of(2026, 7, 11, 18, 15)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it passes through
        checkpoint.shouldBeNull()
    }

    @Test
    fun `recurring schedule whose computed checkpoint would land at or after window end passes through`() {
        // Given: start 09:00, interval 540 min (9h), window end 18:00, now 09:30 - the only
        // checkpoint after start (18:00) lands exactly at the window end
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, intervalMinutes = 540, windowEndHour = 18, windowEndMinute = 0)
        val now = LocalDateTime.of(2026, 7, 11, 9, 30)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it passes through rather than landing on/after the window end
        checkpoint.shouldBeNull()
    }

    @Test
    fun `recurring schedule without a window end passes through`() {
        // Given: an interval configured but no window end (invalid/incomplete configuration)
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, intervalMinutes = 60)
        val now = LocalDateTime.of(2026, 7, 11, 9, 30)

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it passes through rather than recurring forever
        checkpoint.shouldBeNull()
    }

    @Test
    fun `single daily checkpoint with weekday filter on a scheduled day snoozes until today's start`() {
        // Given: a schedule with no interval (single daily checkpoint at 09:00), weekdays Mon-Fri,
        // and now on Friday before the start time
        val schedule = SnoozeSchedule(
            startHour = 9,
            startMinute = 0,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        val now = LocalDateTime.of(2026, 7, 10, 8, 45) // Friday, 08:45

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it lands on today's start time (Friday 09:00)
        checkpoint shouldBe LocalDateTime.of(2026, 7, 10, 9, 0)
    }

    @Test
    fun `single daily checkpoint with weekday filter on non-scheduled day skips to next scheduled day`() {
        // Given: a schedule with weekdays Mon-Fri and now on Saturday morning
        val schedule = SnoozeSchedule(
            startHour = 9,
            startMinute = 0,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        val now = LocalDateTime.of(2026, 7, 11, 8, 45) // Saturday, 08:45

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it skips to Monday 09:00
        checkpoint shouldBe LocalDateTime.of(2026, 7, 13, 9, 0) // Monday
    }

    @Test
    fun `recurring schedule with weekday filter on a scheduled day computes next checkpoint`() {
        // Given: a recurring schedule (09:00-18:00 hourly), weekdays Mon-Fri, and now on Friday at 09:30
        val schedule = SnoozeSchedule(
            startHour = 9,
            startMinute = 0,
            intervalMinutes = 60,
            windowEndHour = 18,
            windowEndMinute = 0,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        val now = LocalDateTime.of(2026, 7, 10, 9, 30) // Friday, 09:30

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it computes the next hourly checkpoint on Friday (10:00)
        checkpoint shouldBe LocalDateTime.of(2026, 7, 10, 10, 0)
    }

    @Test
    fun `recurring schedule with weekday filter on non-scheduled day skips to next day's start time`() {
        // Given: a recurring schedule with weekdays Mon-Fri and now on Saturday at 09:30
        val schedule = SnoozeSchedule(
            startHour = 9,
            startMinute = 0,
            intervalMinutes = 60,
            windowEndHour = 18,
            windowEndMinute = 0,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        val now = LocalDateTime.of(2026, 7, 11, 9, 30) // Saturday, 09:30

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it skips to Monday 09:00
        checkpoint shouldBe LocalDateTime.of(2026, 7, 13, 9, 0) // Monday
    }

    @Test
    fun `batch-at-time checkpoint with weekday filter skips to the next matching weekday`() {
        // Given: a batch-at-time schedule that only fires on Mondays, and now is Saturday
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, weekdays = setOf(DayOfWeek.MONDAY))
        val now = LocalDateTime.of(2026, 7, 11, 10, 0) // Saturday 10:00

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it skips to the next Monday at 09:00
        checkpoint shouldBe LocalDateTime.of(2026, 7, 13, 9, 0)
    }

    @Test
    fun `batch-at-time checkpoint with weekday filter rolls to next occurrence once today's time has passed`() {
        // Given: a batch-at-time schedule that only fires on Saturdays, and today's time already passed
        val schedule = SnoozeSchedule(startHour = 9, startMinute = 0, weekdays = setOf(DayOfWeek.SATURDAY))
        val now = LocalDateTime.of(2026, 7, 11, 10, 0) // Saturday 10:00, after 09:00

        // When: computing the next checkpoint
        val checkpoint = SnoozeScheduleCalculator.nextCheckpoint(now, schedule)

        // Then: it rolls over to the following Saturday at 09:00
        checkpoint shouldBe LocalDateTime.of(2026, 7, 18, 9, 0)
    }
}
