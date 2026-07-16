package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.StorageStats

/**
 * Repository for reading a one-shot snapshot of on-device storage usage (DB file size + row
 * counts), surfaced in the Settings screen's Storage section.
 */
interface StorageStatsRepository {

    /**
     * Get the current storage usage snapshot.
     */
    suspend fun getStorageStats(): Result<StorageStats>
}
