package dev.gaferneira.notificapp.testutil.fakes

import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.RulesFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference
import dev.gaferneira.notificapp.domain.model.preferences.UserPreferences
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Deterministic in-memory [UserPreferencesRepository] fake for VM tests, backed by a
 * [MutableStateFlow].
 */
class FakeUserPreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {

    private val preferences = MutableStateFlow(initial)

    fun current(): UserPreferences = preferences.value

    fun inboxFilters(): InboxFilterSettings = preferences.value.inboxFilterSettings

    fun rulesFilters(): RulesFilterSettings = preferences.value.rulesFilterSettings

    override fun observeUserPreferences(): Flow<UserPreferences> = preferences.asStateFlow()

    override suspend fun getUserPreferences(): Result<UserPreferences> = Result.success(preferences.value)

    override fun observeInboxFilters(): Flow<InboxFilterSettings> = preferences.map { it.inboxFilterSettings }

    override suspend fun setInboxFilters(filters: InboxFilterSettings): Result<Unit> {
        preferences.value = preferences.value.copy(inboxFilterSettings = filters)
        return Result.success(Unit)
    }

    override fun observeRulesFilters(): Flow<RulesFilterSettings> = preferences.map { it.rulesFilterSettings }

    override suspend fun setRulesFilters(filters: RulesFilterSettings): Result<Unit> {
        preferences.value = preferences.value.copy(rulesFilterSettings = filters)
        return Result.success(Unit)
    }

    override fun observeTheme(): Flow<ThemePreference> = preferences.map { it.themePreference }

    override suspend fun setTheme(theme: ThemePreference): Result<Unit> {
        preferences.value = preferences.value.copy(themePreference = theme)
        return Result.success(Unit)
    }

    override suspend fun resetToDefaults(): Result<Unit> {
        preferences.value = UserPreferences()
        return Result.success(Unit)
    }
}
