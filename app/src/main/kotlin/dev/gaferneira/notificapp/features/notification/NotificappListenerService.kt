package dev.gaferneira.notificapp.features.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.notification.NotificationNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Android NotificationListenerService that captures notifications from user-selected apps.
 *
 * This service listens for new notifications, filters them based on user preferences,
 * normalizes them, and stores them in the database for processing.
 */
@AndroidEntryPoint
class NotificappListenerService : NotificationListenerService() {

    @Inject
    lateinit var notificationRepository: NotificationRepository

    @Inject
    lateinit var selectedAppRepository: SelectedAppRepository

    @Inject
    lateinit var normalizer: NotificationNormalizer

    @Inject
    lateinit var deduplicator: NotificationDeduplicator

    @Inject
    lateinit var ruleEngine: RuleEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var enabledApps: List<SelectedApp> = emptyList()

    override fun onCreate() {
        super.onCreate()
        Timber.d("NotificationListenerService created")

        // Observe enabled apps
        serviceScope.launch {
            selectedAppRepository.observeEnabledApps().collect { apps ->
                enabledApps = apps
                Timber.d("Updated enabled apps: ${apps.size} apps")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Timber.d("NotificationListenerService destroyed")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName

        // Check if this app is in the user's selected apps list
        if (!isAppEnabled(packageName)) {
            return
        }

        // Skip system notifications and our own app
        if (shouldSkipNotification(sbn)) {
            return
        }

        serviceScope.launch {
            try {
                // Normalize the notification
                val notification = normalizer.normalize(sbn, packageManager)

                // Check for duplicates
                if (deduplicator.isDuplicate(notification)) {
                    Timber.d("Duplicate notification skipped from $packageName")
                    return@launch
                }

                // Save to database
                notificationRepository.saveNotification(notification)
                    .onSuccess {
                        Timber.d("Saved notification from $packageName: ${notification.id}")
                        // Process rules against this notification
                        processRules(notification)
                    }
                    .onFailure { e ->
                        Timber.e(e, "Failed to save notification from $packageName")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error processing notification from $packageName")
            }
        }
    }

    /**
     * Process rules against a saved notification.
     */
    private suspend fun processRules(notification: dev.gaferneira.notificapp.domain.model.Notification) {
        try {
            val result = ruleEngine.process(notification)
            result
                .onSuccess { executions ->
                    if (executions.isNotEmpty()) {
                        Timber.d("Processed ${executions.size} rule matches for notification ${notification.id}")
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to process rules for notification ${notification.id}")
                }
        } catch (e: Exception) {
            Timber.e(e, "Error in rule processing for notification ${notification.id}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // We could handle notification removal here if needed
        // For now, we keep notifications in the database even after they're dismissed
        Timber.d("Notification removed: ${sbn?.packageName}_${sbn?.key}")
    }

    /**
     * Check if an app is enabled for monitoring.
     */
    private fun isAppEnabled(packageName: String): Boolean = enabledApps.any { it.packageName == packageName && it.isEnabled }

    /**
     * Determine if a notification should be skipped.
     */
    private fun shouldSkipNotification(sbn: StatusBarNotification): Boolean {
        // Skip our own app
        if (sbn.packageName == packageName) return true

        // Skip system notifications with no content
        if (sbn.notification?.extras == null) return true

        // Skip ongoing notifications that are just system indicators
        if (sbn.isOngoing && isSystemPackage(sbn.packageName)) return true

        // Skip notifications that are too old (could be from a reboot or service restart)
        val age = System.currentTimeMillis() - sbn.postTime
        if (age > MAX_AGE_MS) return true

        return false
    }

    /**
     * Check if a package is a system package.
     */
    private fun isSystemPackage(packageName: String): Boolean = packageName.startsWith("android") ||
        packageName.startsWith("com.android.systemui") ||
        packageName.startsWith("com.google.android.gms")

    companion object {
        // Don't process notifications older than 50 minutes
        private const val MAX_AGE_MS = 1000L * 60 * 50
    }
}
