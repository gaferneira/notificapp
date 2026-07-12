package dev.gaferneira.notificapp.domain.model

/**
 * Configuration key for the number of torch flashes.
 */
const val FLASH_COUNT_KEY = "flash_count"

/**
 * Configuration key for the duration of each flash (on-time and off-time), in milliseconds.
 */
const val FLASH_DURATION_MS_KEY = "flash_duration_ms"

/**
 * Default number of flashes.
 */
const val DEFAULT_FLASH_COUNT = 3

/**
 * Default duration of each flash phase, in milliseconds.
 */
const val DEFAULT_FLASH_DURATION_MS = 300L

/**
 * Flash count is clamped to this range - a photosensitivity safety bound, not just a UI nicety,
 * since imported rules (Phase 2) could otherwise request an excessive strobe.
 */
const val MIN_FLASH_COUNT = 1
const val MAX_FLASH_COUNT = 10

/**
 * Minimum duration per flash phase (on or off), in milliseconds - the other half of the
 * photosensitivity safety bound. At this floor, a full on/off cycle is 400ms (2.5Hz), safely
 * under the commonly cited 3-flashes-per-second photosensitive-epilepsy threshold.
 */
const val MIN_FLASH_DURATION_MS = 200L
const val MAX_FLASH_DURATION_MS = 1000L

/**
 * Get the number of torch flashes, clamped to a photosensitivity-safe range regardless of
 * what's stored in config (defense in depth against a malformed or imported rule).
 */
fun RuleAction.getFlashCount(): Int = (config[FLASH_COUNT_KEY]?.toIntOrNull() ?: DEFAULT_FLASH_COUNT)
    .coerceIn(MIN_FLASH_COUNT, MAX_FLASH_COUNT)

/**
 * Get the duration of each flash phase in milliseconds, clamped to a photosensitivity-safe
 * range regardless of what's stored in config.
 */
fun RuleAction.getFlashDurationMs(): Long = (config[FLASH_DURATION_MS_KEY]?.toLongOrNull() ?: DEFAULT_FLASH_DURATION_MS)
    .coerceIn(MIN_FLASH_DURATION_MS, MAX_FLASH_DURATION_MS)
