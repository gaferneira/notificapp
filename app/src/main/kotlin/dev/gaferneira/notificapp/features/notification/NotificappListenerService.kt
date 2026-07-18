package dev.gaferneira.notificapp.features.notification

import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.notification.NotificationNormalizer
import dev.gaferneira.notificapp.core.notification.ProcessNotificationUseCase
import dev.gaferneira.notificapp.core.notification.action.SystemNotificationController
import dev.gaferneira.notificapp.core.notification.action.SystemNotificationControllerHolder
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmController
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
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
class NotificappListenerService :
    NotificationListenerService(),
    SystemNotificationController {

    @Inject
    lateinit var selectedAppRepository: SelectedAppRepository

    @Inject
    lateinit var normalizer: NotificationNormalizer

    @Inject
    lateinit var processNotificationUseCase: ProcessNotificationUseCase

    @Inject
    lateinit var systemNotificationControllerHolder: SystemNotificationControllerHolder

    @Inject
    lateinit var alarmController: AlarmController

    @Inject
    @field:Dispatcher(DispatcherType.IO)
    lateinit var ioDispatcher: CoroutineDispatcher

    private lateinit var serviceScope: CoroutineScope

    // Written from a coroutine on serviceScope, read from onNotificationPosted() on the system's
    // binder thread - @Volatile ensures the binder thread sees the latest published set.
    @Volatile
    private var enabledPackageNames: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + ioDispatcher)
        Timber.d("NotificationListenerService created")

        // Observe enabled apps
        serviceScope.launch {
            selectedAppRepository.observeEnabledApps()
                .catch { e -> Timber.e(e, "Enabled-apps observation failed; keeping last known list") }
                .collect { apps ->
                    enabledPackageNames = apps.mapTo(mutableSetOf()) { it.packageName }
                    Timber.d("Updated enabled apps: ${apps.size} apps")
                }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        systemNotificationControllerHolder.set(this)
        Timber.d("NotificationListenerService connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        systemNotificationControllerHolder.clear(this)
        Timber.d("NotificationListenerService disconnected")
    }

    override fun onDestroy() {
        super.onDestroy()
        systemNotificationControllerHolder.clear(this)
        serviceScope.cancel()
        Timber.d("NotificationListenerService destroyed")
    }

    override fun cancel(sbnKey: String) = cancelNotification(sbnKey)

    override fun snooze(sbnKey: String, durationMs: Long) = snoozeNotification(sbnKey, durationMs)

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
                val appName = packageManager.resolveAppName(packageName)
                val notification = normalizer.normalize(sbn.toRawData(), appName)

                // Run the full pipeline: dedup, save, evaluate rules, execute actions, persist executions
                processNotificationUseCase(notification)
                    .onSuccess { executions ->
                        Timber.d("Processed ${executions.size} rule matches for notification ${notification.id}")
                    }
                    .onFailure { e ->
                        Timber.e(e, "Failed to process notification from $packageName")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error processing notification from $packageName")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?, rankingMap: RankingMap?, reason: Int) {
        // We keep notifications in the database even after they're dismissed - this only handles
        // stopping an alarm that notification triggered.
        Timber.d("Notification removed: ${sbn?.packageName}_${sbn?.key} (reason=$reason)")

        val sbnKey = sbn?.key ?: return
        // Only a genuine user-initiated dismissal should stop the alarm: REASON_CANCEL/_ALL is a
        // swipe or clear-all, REASON_CLICK is an auto-cancel-on-tap. REASON_APP_CANCEL and
        // REASON_LISTENER_CANCEL (the latter is exactly what our own DISMISS_NOTIFICATION rule
        // action triggers via cancelNotification()) are programmatic removals and must NOT stop an
        // alarm a rule just started alongside that same dismiss.
        if (reason != REASON_CANCEL && reason != REASON_CANCEL_ALL && reason != REASON_CLICK) return

        alarmController.stopIfSource(sbnKey)
    }

    /**
     * Check if an app is enabled for monitoring.
     */
    private fun isAppEnabled(packageName: String): Boolean = packageName in enabledPackageNames

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
