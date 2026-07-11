package dev.gaferneira.notificapp.domain.model

/**
 * Named vibration pattern for a ringing `CREATE_ALARM` action.
 *
 * Holds only primitives (`id`, `LongArray`, `Int`) so the domain layer stays Android-free —
 * `AndroidAlarmPlayer` is the only place that converts [timings]/[repeatIndex] into a platform
 * `VibrationEffect`.
 *
 * @property id Stable identifier persisted in [RuleAction] config, surviving enum reordering.
 * @property timings Vibrate/pause durations in milliseconds, in the shape expected by
 * `VibrationEffect.createWaveform(timings, repeatIndex)`.
 * @property repeatIndex Index into [timings] where the pattern loops back to while ringing, or
 * `-1` for a non-repeating pattern.
 */
enum class VibrationPattern(
    val id: String,
    val timings: LongArray,
    val repeatIndex: Int,
) {
    /** The original alarm vibration pattern, unchanged from before this option was configurable. */
    BASIC_CALL(id = "basic_call", timings = longArrayOf(0, 800, 1000), repeatIndex = 0),

    /** A shorter, quicker double-pulse loop. */
    PULSE(id = "pulse", timings = longArrayOf(0, 200, 200), repeatIndex = 0),

    /** A longer single buzz per cycle. */
    LONG(id = "long", timings = longArrayOf(0, 1200, 800), repeatIndex = 0),
    ;

    companion object {
        /**
         * Resolve a persisted pattern [id] to its [VibrationPattern], falling back to
         * [BASIC_CALL] when [id] is `null` or does not match any known pattern (e.g. a stale or
         * malformed imported rule) rather than crashing or vibrating silently.
         */
        fun fromId(id: String?): VibrationPattern = entries.firstOrNull { it.id == id } ?: BASIC_CALL
    }
}
