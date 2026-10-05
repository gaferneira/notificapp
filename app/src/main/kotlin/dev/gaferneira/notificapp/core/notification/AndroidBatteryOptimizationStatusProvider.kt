package dev.gaferneira.notificapp.core.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.domain.BatteryOptimizationStatusProvider
import dev.gaferneira.notificapp.util.isIgnoringBatteryOptimizations
import javax.inject.Inject

internal class AndroidBatteryOptimizationStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : BatteryOptimizationStatusProvider {
    override fun isIgnoringBatteryOptimizations(): Boolean = isIgnoringBatteryOptimizations(context)
}
