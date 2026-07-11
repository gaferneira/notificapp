package dev.gaferneira.notificapp.core.notification.action

import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Seam for reading the current wall-clock time, so scheduling logic that depends on "now" (e.g.
 * [SnoozeScheduleCalculator]) can be exercised with a fixed instant in tests instead of the real
 * clock.
 */
interface CurrentTimeProvider {
    fun now(): LocalDateTime
}

/**
 * Real, system-clock-backed [CurrentTimeProvider], using the device's default time zone - wall
 * clock local time is what a user means by "9am".
 */
class SystemCurrentTimeProvider @Inject constructor() : CurrentTimeProvider {
    override fun now(): LocalDateTime = LocalDateTime.now()
}
