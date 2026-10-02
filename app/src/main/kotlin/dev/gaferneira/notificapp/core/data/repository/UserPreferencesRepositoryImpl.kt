package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.data.preferences.UserPreferencesLocalDataSource
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.preferences.AppLanguage
import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.domain.model.preferences.RulesFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference
import dev.gaferneira.notificapp.domain.model.preferences.UserPreferences
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * All setters delegate to [UserPreferencesLocalDataSource.updateUserPreferences]'s
 * transform-based overload, which performs the read-modify-write inside a single DataStore
 * transaction. This keeps concurrent updates to different fields from clobbering each other
 * (lost-update race) — see that method's KDoc for details.
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
            localDataSource.updateUserPreferences { current -> current.copy(inboxFilterSettings = filters) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set inbox filters")
            e.toFailureResult()
        }
    }

    override fun observeRulesFilters(): Flow<RulesFilterSettings> = localDataSource.observeUserPreferences()
        .map { it.rulesFilterSettings }
        .flowOn(ioDispatcher)

    override suspend fun setRulesFilters(filters: RulesFilterSettings): Result<Unit> = withContext(ioDispatcher) {
        try {
            localDataSource.updateUserPreferences { current -> current.copy(rulesFilterSettings = filters) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set rules filters")
            e.toFailureResult()
        }
    }

    override fun observeTheme(): Flow<ThemePreference> = localDataSource.observeUserPreferences()
        .map { it.themePreference }
        .flowOn(ioDispatcher)

    override suspend fun setTheme(theme: ThemePreference): Result<Unit> = withContext(ioDispatcher) {
        try {
            localDataSource.updateUserPreferences { current -> current.copy(themePreference = theme) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set theme preference")
            e.toFailureResult()
        }
    }

    override fun observeRetentionPeriod(): Flow<RetentionPeriod> = localDataSource.observeUserPreferences()
        .map { it.retentionPeriod }
        .flowOn(ioDispatcher)

    override suspend fun setRetentionPeriod(period: RetentionPeriod): Result<Unit> = withContext(ioDispatcher) {
        try {
            localDataSource.updateUserPreferences { current -> current.copy(retentionPeriod = period) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set retention period")
            e.toFailureResult()
        }
    }

    override fun observeMonitoringPaused(): Flow<Boolean> = localDataSource.observeUserPreferences()
        .map { it.monitoringPaused }
        .flowOn(ioDispatcher)

    override suspend fun setMonitoringPaused(paused: Boolean): Result<Unit> = withContext(ioDispatcher) {
        try {
            localDataSource.updateUserPreferences { current -> current.copy(monitoringPaused = paused) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set monitoring paused flag")
            e.toFailureResult()
        }
    }

    /**
     * Emits only on actual language changes. The whole [UserPreferences] blob is one DataStore
     * value, so without [distinctUntilChanged] every unrelated preference write (theme, filters,
     * retention) would re-emit the same language. [LocaleController] relies on that: it treats a
     * repeat `SYSTEM` emission as an explicit in-app choice and resets the app locale, which
     * would otherwise clobber an OS-level per-app locale on any unrelated settings toggle.
     */
    override fun observeLanguage(): Flow<AppLanguage> = localDataSource.observeUserPreferences()
        .map { it.appLanguage }
        .distinctUntilChanged()
        .flowOn(ioDispatcher)

    override suspend fun setLanguage(language: AppLanguage): Result<Unit> = withContext(ioDispatcher) {
        try {
            localDataSource.updateUserPreferences { current -> current.copy(appLanguage = language) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to set app language preference")
            e.toFailureResult()
        }
    }

    override suspend fun resetToDefaults(): Result<Unit> = withContext(ioDispatcher) {
        try {
            localDataSource.updateUserPreferences { UserPreferences() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset preferences to defaults")
            e.toFailureResult()
        }
    }
}
