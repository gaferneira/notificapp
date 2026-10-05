package dev.gaferneira.notificapp.core.di

import dev.gaferneira.notificapp.core.notification.ClearCollectedDataUseCase
import dev.gaferneira.notificapp.core.notification.EnforceRetentionUseCase
import dev.gaferneira.notificapp.domain.repository.StorageStatsRepository
import javax.inject.Inject

/**
 * Groups the data-maintenance use cases the Settings ViewModel triggers, keeping its constructor
 * within the detekt parameter limit (same pattern as [HomeDataSources]).
 */
class SettingsDataActions @Inject constructor(
    val enforceRetention: EnforceRetentionUseCase,
    val clearCollectedData: ClearCollectedDataUseCase,
    val storageStatsRepository: StorageStatsRepository,
)
