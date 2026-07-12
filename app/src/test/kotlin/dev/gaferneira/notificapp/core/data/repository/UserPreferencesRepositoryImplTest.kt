package dev.gaferneira.notificapp.core.data.repository

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.data.preferences.UserPreferencesLocalDataSource
import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference
import dev.gaferneira.notificapp.domain.model.preferences.UserPreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val localDataSource: UserPreferencesLocalDataSource = mockk()
    private val repository = UserPreferencesRepositoryImpl(localDataSource, testDispatcher)

    @Test
    fun `observeInboxFilters projects only the inbox filter settings`() = runTest(testDispatcher) {
        val prefs = UserPreferences(inboxFilterSettings = InboxFilterSettings(selectedApps = listOf("com.bank")))
        every { localDataSource.observeUserPreferences() } returns MutableStateFlow(prefs)

        repository.observeInboxFilters().test {
            awaitItem().selectedApps shouldBe listOf("com.bank")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeTheme projects only the theme preference`() = runTest(testDispatcher) {
        val prefs = UserPreferences(themePreference = ThemePreference.DARK)
        every { localDataSource.observeUserPreferences() } returns MutableStateFlow(prefs)

        repository.observeTheme().test {
            awaitItem() shouldBe ThemePreference.DARK
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setInboxFilters merges the new filters into the current preferences and persists them`() = runTest(testDispatcher) {
        val current = UserPreferences(themePreference = ThemePreference.DARK)
        coEvery { localDataSource.getUserPreferences() } returns Result.success(current)
        val slot = slot<UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val newFilters = InboxFilterSettings(selectedApps = listOf("com.bank"), statusFilter = NotificationStatusFilter.UNPROCESSED)
        val result = repository.setInboxFilters(newFilters)

        result.isSuccess shouldBe true
        slot.captured.inboxFilterSettings shouldBe newFilters
        slot.captured.themePreference shouldBe ThemePreference.DARK
    }

    @Test
    fun `setInboxFilters falls back to defaults when there is no existing snapshot`() = runTest(testDispatcher) {
        coEvery { localDataSource.getUserPreferences() } returns Result.success(UserPreferences())
        val slot = slot<UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val newFilters = InboxFilterSettings(selectedApps = listOf("com.bank"))
        repository.setInboxFilters(newFilters)

        slot.captured.inboxFilterSettings shouldBe newFilters
    }

    @Test
    fun `setInboxFilters maps a datasource exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { localDataSource.getUserPreferences() } throws IllegalStateException("io error")

        val result = repository.setInboxFilters(InboxFilterSettings())

        result.isFailure shouldBe true
    }

    @Test
    fun `setTheme merges the new theme into the current preferences and persists them`() = runTest(testDispatcher) {
        val current = UserPreferences(inboxFilterSettings = InboxFilterSettings(selectedApps = listOf("com.bank")))
        every { localDataSource.observeUserPreferences() } returns MutableStateFlow(current)
        val slot = slot<UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val result = repository.setTheme(ThemePreference.LIGHT)

        result.isSuccess shouldBe true
        slot.captured.themePreference shouldBe ThemePreference.LIGHT
        slot.captured.inboxFilterSettings.selectedApps shouldBe listOf("com.bank")
    }

    @Test
    fun `resetToDefaults persists a fresh default UserPreferences`() = runTest(testDispatcher) {
        val slot = slot<UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val result = repository.resetToDefaults()

        result.isSuccess shouldBe true
        slot.captured shouldBe UserPreferences()
        coVerify(exactly = 1) { localDataSource.updateUserPreferences(any()) }
    }

    @Test
    fun `getUserPreferences delegates to the local data source`() = runTest(testDispatcher) {
        coEvery { localDataSource.getUserPreferences() } returns Result.success(UserPreferences())

        val result = repository.getUserPreferences()

        result.isSuccess shouldBe true
    }
}
