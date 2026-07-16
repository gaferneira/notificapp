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
 * Configuration key for the rule-safety cooldown, in seconds: after this flash alert fires,
 * further matches are suppressed (backed by
 * [dev.gaferneira.notificapp.core.notification.action.NotificationThrottleTracker]) until the
 * window elapses. `0` disables cooldown - the flash fires on every match, as before this option
 * existed.
 */
const val FLASH_COOLDOWN_SECONDS_KEY = "flash_cooldown_seconds"

/**
 * Default flash cooldown in seconds - disabled, matching legacy rules persisted before this
 * option existed.
 */
const val DEFAULT_FLASH_COOLDOWN_SECONDS = 0

/**
 * Flash cooldown is clamped to this range, mirroring the [FLASH_COUNT_KEY] defense-in-depth
 * coercion pattern. The upper bound is generous (24h) since a rule author may legitimately want
 * a long-lived "don't re-flash today" cooldown.
 */
const val MIN_FLASH_COOLDOWN_SECONDS = 0
const val MAX_FLASH_COOLDOWN_SECONDS = 86_400

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

/**
 * Get the configured flash cooldown in seconds, clamped to a sane range regardless of what's
 * stored in config (defense in depth against a malformed or imported rule). `0` means disabled.
 */
fun RuleAction.getFlashCooldownSeconds(): Int = (config[FLASH_COOLDOWN_SECONDS_KEY]?.toIntOrNull() ?: DEFAULT_FLASH_COOLDOWN_SECONDS)
    .coerceIn(MIN_FLASH_COOLDOWN_SECONDS, MAX_FLASH_COOLDOWN_SECONDS)
