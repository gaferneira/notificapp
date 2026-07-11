package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.SnoozeSchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

/**
 * Pure computation of the next [SnoozeSchedule] checkpoint. Takes `now` explicitly (rather than
 * reading the clock itself) so it stays unit-testable, including day-rollover and window-edge
 * cases, without touching real time. See `openspec/specs/snooze-scheduling/spec.md`.
 */
object SnoozeScheduleCalculator {

    /**
     * Returns the next checkpoint at or after [now], or null when [now] falls outside a
     * configured recurrence window and the match should pass through unsnoozed instead.
     * For batch-at-time mode (no interval), finds the next delivery time from the configured
     * list, respecting the schedule's weekday filter and rolling over to the next scheduled day.
     * For recurring digest snoozes, respects the schedule's weekday filter and interval.
     */
    fun nextCheckpoint(now: LocalDateTime, schedule: SnoozeSchedule): LocalDateTime? {
        val hasWeekdayFilter = schedule.weekdays.isNotEmpty()
        var checkpointDate = now.toLocalDate()

        val interval = schedule.intervalMinutes
        if (interval == null) {
            if (hasWeekdayFilter && now.toLocalDate().dayOfWeek !in schedule.weekdays) {
                checkpointDate = findNextScheduledDay(now.toLocalDate(), schedule.weekdays)
            }

            val times = if (schedule.times.isNotEmpty()) schedule.times else listOf(schedule.startHour to schedule.startMinute)
            val checkpoints = times.map { (hour, minute) -> checkpointDate.atTime(hour, minute) }.sorted()

            val nextToday = checkpoints.firstOrNull { it.isAfter(now) || it == now }
            if (nextToday != null) return nextToday

            val rolloverDate = if (hasWeekdayFilter) {
                findNextScheduledDay(checkpointDate, schedule.weekdays)
            } else {
                checkpointDate.plusDays(1)
            }
            return times.map { (hour, minute) -> rolloverDate.atTime(hour, minute) }.sorted().firstOrNull()
        }

        if (hasWeekdayFilter && now.toLocalDate().dayOfWeek !in schedule.weekdays) {
            checkpointDate = findNextScheduledDay(now.toLocalDate(), schedule.weekdays)
        }

        val start = checkpointDate.atTime(schedule.startHour, schedule.startMinute)
        val windowEndHour = schedule.windowEndHour
        val windowEndMinute = schedule.windowEndMinute
        if (windowEndHour == null || windowEndMinute == null) return null
        val windowEnd = checkpointDate.atTime(windowEndHour, windowEndMinute)

        if (now.isBefore(start)) return start
        if (!now.isBefore(windowEnd)) return null

        val cyclesElapsed = Duration.between(start, now).toMinutes() / interval + 1
        val checkpoint = start.plusMinutes(cyclesElapsed * interval)

        return if (checkpoint.isBefore(windowEnd)) checkpoint else null
    }

    private fun findNextScheduledDay(from: java.time.LocalDate, weekdays: Set<DayOfWeek>): java.time.LocalDate {
        var candidate = from
        repeat(7) {
            candidate = candidate.plusDays(1)
            if (candidate.dayOfWeek in weekdays) return candidate
        }
        return from
    }
}
