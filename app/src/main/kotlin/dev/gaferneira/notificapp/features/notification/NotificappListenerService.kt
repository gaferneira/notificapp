package dev.gaferneira.notificapp.features.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
    lateinit var selectedAppRepository: SelectedAppRepository

    @Inject
    lateinit var normalizer: NotificationNormalizer

    @Inject
    lateinit var processNotificationUseCase: ProcessNotificationUseCase

    @Inject
    @field:Dispatcher(DispatcherType.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    private lateinit var serviceScope: CoroutineScope

    private var enabledApps: List<SelectedApp> = emptyList()

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)
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

                // Run the full pipeline: dedup, save, evaluate rules, persist executions
                processNotificationUseCase(notification)
                    .onSuccess { executions ->
                        for (execution in executions) {
                            for (action in execution.triggeredRuleActions) {
                                try {
                                    executeAction(notification, action)
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to execute action ${action.type} for notification ${notification.id}")
                                }
                            }
                        }
                    }
                    .onFailure { e ->
                        Timber.e(e, "Failed to process notification from $packageName")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error processing notification from $packageName")
            }
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

        // Skip notifications without title or text content
        if (!hasTitleOrContent(sbn)) return true

        // Skip ongoing notifications that are just system indicators
        if (sbn.isOngoing && isSystemPackage(sbn.packageName)) return true

        // Skip notifications that are too old (could be from a reboot or service restart)
        val age = System.currentTimeMillis() - sbn.postTime
        if (age > MAX_AGE_MS) return true

        return false
    }

    /**
     * Check if the notification has a title or any text content.
     * Notifications without both are not useful for extraction.
     */
    private fun hasTitleOrContent(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras

        // Check for title
        val hasTitle = extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.isNotBlank() == true ||
            extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.isNotBlank() == true

        // Check for content in various forms
        val hasContent = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.isNotBlank() == true ||
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.isNotBlank() == true ||
            extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)?.isNotEmpty() == true ||
            extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.isNotBlank() == true ||
            sbn.notification.tickerText?.isNotBlank() == true

        return hasTitle || hasContent
    }

    private suspend fun executeAction(notification: Notification, action: RuleAction) {
        when (action.type) {
            ActionType.DISMISS_NOTIFICATION -> {
                val sbnKey = notification.sbnKey
                if (sbnKey != null) {
                    cancelNotification(sbnKey)
                } else {
                    Timber.w("Cannot dismiss notification ${notification.id}: no SBN key")
                }
            }
            ActionType.SNOOZE_NOTIFICATION -> executeSnooze(notification, action)
            else -> {
                // No-op for other actions for now
            }
        }
    }

    private fun executeSnooze(notification: Notification, action: RuleAction) {
        val sbnKey = notification.sbnKey
        if (sbnKey == null) {
            Timber.w("Cannot snooze notification ${notification.id}: no SBN key")
            return
        }

        val durationMinutes = action.getSnoozeDurationMinutes()
        val durationMs = durationMinutes * 60_000L

        snoozeNotification(sbnKey, durationMs)

        Timber.d("Snoozed notification ${notification.id} for $durationMinutes minutes")
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
