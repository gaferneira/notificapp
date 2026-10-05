package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import javax.inject.Inject

/**
 * Clears all collected data: notifications plus the rule executions and extracted field values
 * that cascade from them. Rules and monitored apps are deliberately kept.
 *
 * Goes through [NotificationRepository] (ADR 005); failures arrive already mapped to `Failure` (ADR 006).
 */
class ClearCollectedDataUseCase @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {
    suspend operator fun invoke(): Result<Unit> = notificationRepository.deleteAll()
}
