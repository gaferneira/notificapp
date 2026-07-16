package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Enforces the user's notification retention preference by deleting notifications older than
 * the configured window. Invoked once on app start (see `MainActivity`) as an interim sweep -
 * there is no periodic WorkManager job (out of scope for this pass).
 *
 * @property userPreferencesRepository Source of the user's [RetentionPeriod] preference
 * @property notificationRepository Repository used to delete old notifications, never the DAO directly
 * @property timeProvider Seam for "now" so cutoff computation is testable
 * @property ioDispatcher Coroutine dispatcher for IO operations
 */
class EnforceRetentionUseCase @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val notificationRepository: NotificationRepository,
    private val timeProvider: CurrentTimeProvider,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke() {
        withContext(ioDispatcher) {
            val preferences = userPreferencesRepository.getUserPreferences().getOrNull() ?: return@withContext
            val retentionDays = preferences.retentionPeriod.toDaysOrNull() ?: return@withContext

            val cutoff = timeProvider.nowEpochMillis() - TimeUnit.DAYS.toMillis(retentionDays)
            notificationRepository.deleteOlderThan(cutoff)
                .onFailure { e -> Timber.e(e, "Failed to enforce notification retention") }
        }
    }

    private fun RetentionPeriod.toDaysOrNull(): Long? = when (this) {
        RetentionPeriod.DAYS_30 -> DAYS_30
        RetentionPeriod.DAYS_90 -> DAYS_90
        RetentionPeriod.NEVER -> null
    }

    private companion object {
        const val DAYS_30 = 30L
        const val DAYS_90 = 90L
    }
}
