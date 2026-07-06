package dev.gaferneira.notificapp.core.notification.action

/**
 * Narrow interface over the camera torch / power-state APIs that [FlashAlertActionExecutor]
 * needs, kept separate so the executor can be unit tested without touching real system services.
 */
interface TorchController {
    /**
     * Whether this device has a usable camera flash.
     */
    fun hasFlash(): Boolean

    /**
     * Whether the system is currently in battery saver mode - flashing the torch is skipped in
     * this state to respect the user's power-saving choice.
     */
    fun isPowerSaveMode(): Boolean

    /**
     * Blink the torch [count] times, each phase (on and off) lasting [phaseDurationMs].
     */
    suspend fun blink(count: Int, phaseDurationMs: Long)
}
