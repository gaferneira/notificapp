package dev.gaferneira.notificapp.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber

/** Whether the system exempts this app from battery optimizations (Doze / app standby restrictions). */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Opens this app's system settings page, where the user picks Battery > Unrestricted. Deliberately avoids
 * `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`: that list defaults to the "Not optimized" filter, so an app that
 * is still optimized (exactly when we show the hint) is not listed. `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
 * would need a permission that Google Play restricts.
 */
fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "App settings screen not found")
    }
}
