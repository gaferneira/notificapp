package dev.gaferneira.notificapp.features.appselection.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.AppInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class PackageManagerInstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(DispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
) : InstalledAppsProvider {

    override suspend fun getMonitorableApps(): List<AppInfo> = withContext(defaultDispatcher) {
        val packageManager = context.packageManager
        val apps = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()

        // Query all apps that can handle MAIN/LAUNCHER intent.
        // This works on Android 11+ with the <queries> declaration in the manifest.
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, PackageManager.GET_META_DATA)
        }

        for (resolveInfo in resolveInfoList) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue

            // Skip duplicates
            if (packageName in seenPackages) continue
            seenPackages.add(packageName)

            // Skip our own app
            if (packageName == context.packageName) continue

            // Load app info
            val applicationInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getApplicationInfo(packageName, 0)
                }
            } catch (e: Exception) {
                continue
            }

            // Skip pure system apps that can't send notifications meaningfully
            val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Include user apps and updated system apps
            if (!isSystemApp || isUpdatedSystemApp) {
                val appName = applicationInfo.loadLabel(packageManager).toString()
                    .ifBlank { packageName }

                // Try to determine category
                val category = when {
                    packageName.contains("mail", ignoreCase = true) ||
                        packageName.contains("gmail", ignoreCase = true) ||
                        packageName.contains("outlook", ignoreCase = true) ||
                        packageName.contains("yahoo", ignoreCase = true) -> "Email"
                    packageName.contains("whatsapp", ignoreCase = true) ||
                        packageName.contains("telegram", ignoreCase = true) ||
                        packageName.contains("messenger", ignoreCase = true) ||
                        packageName.contains("slack", ignoreCase = true) ||
                        packageName.contains("discord", ignoreCase = true) -> "Messaging"
                    packageName.contains("bank", ignoreCase = true) ||
                        packageName.contains("finance", ignoreCase = true) ||
                        packageName.contains("revolut", ignoreCase = true) ||
                        packageName.contains("paypal", ignoreCase = true) ||
                        packageName.contains("crypto", ignoreCase = true) -> "Financial"
                    packageName.contains("shop", ignoreCase = true) ||
                        packageName.contains("amazon", ignoreCase = true) ||
                        packageName.contains("ebay", ignoreCase = true) ||
                        packageName.contains("aliexpress", ignoreCase = true) ||
                        packageName.contains("food", ignoreCase = true) ||
                        packageName.contains("deliver", ignoreCase = true) -> "Shopping"
                    packageName.contains("uber", ignoreCase = true) ||
                        packageName.contains("lyft", ignoreCase = true) ||
                        packageName.contains("transport", ignoreCase = true) ||
                        packageName.contains("travel", ignoreCase = true) -> "Transport"
                    else -> null
                }

                apps.add(
                    AppInfo(
                        packageName = packageName,
                        name = appName,
                        category = category,
                    ),
                )
            }
        }

        apps
    }
}
