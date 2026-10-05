package dev.gaferneira.notificapp.domain

/**
 * Resolves whether the system exempts this app from battery optimizations, so ViewModels never
 * touch `Context`/`PowerManager` directly (platform statics that can't be stubbed in JVM unit tests).
 */
fun interface BatteryOptimizationStatusProvider {
    fun isIgnoringBatteryOptimizations(): Boolean
}
