package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.data.preferences.UserPreferencesLocalDataSource
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.RulesFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference
import dev.gaferneira.notificapp.domain.model.preferences.UserPreferences
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of [UserPreferencesRepository].
 *
 * Follows Repository Pattern (ADR 005) with:
 * - Local data source (DataStore) for local persistence
 * - Result<T> return type for explicit error handling (ADR 006)
 * - Injected coroutine dispatchers for testability (ADR 008)
 *
 * This implementation stores preferences locally per-device. Future enhancement:
 * Add a remote data source for API sync across devices.
 */
internal class UserPreferencesRepositoryImpl @Inject constructor(
    private val localDataSource: UserPreferencesLocalDataSource,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : UserPreferencesRepository {

    override fun observeUserPreferences(): Flow<UserPreferences> = localDataSource.observeUserPreferences()
        .flowOn(ioDispatcher)

    override suspend fun getUserPreferences(): Result<UserPreferences> = withContext(ioDispatcher) {
        localDataSource.getUserPreferences()
    }

    override fun observeInboxFilters(): Flow<InboxFilterSettings> = localDataSource.observeUserPreferences()
        .map { it.inboxFilterSettings }
        .flowOn(ioDispatcher)

    override suspend fun setInboxFilters(filters: InboxFilterSettings): Result<Unit> = withContext(ioDispatcher) {
        try {
            val current = localDataSource.getUserPreferences().getOrNull() ?: UserPreferences()

            val updated = current.copy(inboxFilterSettings = filters)
            localDataSource.updateUserPreferences(updated)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set inbox filters")
            Result.failure(e)
        }
    }

    override fun observeRulesFilters(): Flow<RulesFilterSettings> = localDataSource.observeUserPreferences()
        .map { it.rulesFilterSettings }
        .flowOn(ioDispatcher)

    override suspend fun setRulesFilters(filters: RulesFilterSettings): Result<Unit> = withContext(ioDispatcher) {
        try {
            val current = localDataSource.observeUserPreferences()
                .map { it }.flowOn(ioDispatcher)
                .firstOrNull() ?: UserPreferences()

            val updated = current.copy(rulesFilterSettings = filters)
            localDataSource.updateUserPreferences(updated)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set rules filters")
            Result.failure(e)
        }
    }

    override fun observeTheme(): Flow<ThemePreference> = localDataSource.observeUserPreferences()
        .map { it.themePreference }
        .flowOn(ioDispatcher)

    override suspend fun setTheme(theme: ThemePreference): Result<Unit> = withContext(ioDispatcher) {
        try {
            val current = localDataSource.observeUserPreferences()
                .map { it }.flowOn(ioDispatcher)
                .firstOrNull() ?: UserPreferences()

            val updated = current.copy(themePreference = theme)
            localDataSource.updateUserPreferences(updated)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set theme preference")
            Result.failure(e)
        }
    }

    override suspend fun resetToDefaults(): Result<Unit> = withContext(ioDispatcher) {
        try {
            localDataSource.updateUserPreferences(UserPreferences())
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset preferences to defaults")
            Result.failure(e)
        }
    }
}
