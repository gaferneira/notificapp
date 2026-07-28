package dev.gaferneira.notificapp.core.data.repository

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.data.preferences.UserPreferencesLocalDataSource
import dev.gaferneira.notificapp.domain.model.preferences.AppLanguage
import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
    fun `setInboxFilters delegates a transform that merges the new filters into the current preferences`() = runTest(testDispatcher) {
        val slot = slot<(UserPreferences) -> UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val newFilters = InboxFilterSettings(selectedApps = listOf("com.bank"), statusFilter = NotificationStatusFilter.UNPROCESSED)
        val result = repository.setInboxFilters(newFilters)

        result.isSuccess shouldBe true
        val updated = slot.captured(UserPreferences(themePreference = ThemePreference.DARK))
        updated.inboxFilterSettings shouldBe newFilters
        updated.themePreference shouldBe ThemePreference.DARK
    }

    @Test
    fun `setInboxFilters transform falls back to defaults when applied to an empty snapshot`() = runTest(testDispatcher) {
        val slot = slot<(UserPreferences) -> UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val newFilters = InboxFilterSettings(selectedApps = listOf("com.bank"))
        repository.setInboxFilters(newFilters)

        val updated = slot.captured(UserPreferences())
        updated.inboxFilterSettings shouldBe newFilters
    }

    @Test
    fun `setInboxFilters maps a datasource exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { localDataSource.updateUserPreferences(any()) } throws IllegalStateException("io error")

        val result = repository.setInboxFilters(InboxFilterSettings())

        result.isFailure shouldBe true
    }

    @Test
    fun `setTheme delegates a transform that merges the new theme into the current preferences`() = runTest(testDispatcher) {
        val slot = slot<(UserPreferences) -> UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val result = repository.setTheme(ThemePreference.LIGHT)

        result.isSuccess shouldBe true
        val updated = slot.captured(UserPreferences(inboxFilterSettings = InboxFilterSettings(selectedApps = listOf("com.bank"))))
        updated.themePreference shouldBe ThemePreference.LIGHT
        updated.inboxFilterSettings.selectedApps shouldBe listOf("com.bank")
    }

    @Test
    fun `observeRetentionPeriod projects only the retention period`() = runTest(testDispatcher) {
        val prefs = UserPreferences(retentionPeriod = RetentionPeriod.DAYS_90)
        every { localDataSource.observeUserPreferences() } returns MutableStateFlow(prefs)

        repository.observeRetentionPeriod().test {
            awaitItem() shouldBe RetentionPeriod.DAYS_90
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setRetentionPeriod delegates a transform that merges the new period into the current preferences`() = runTest(testDispatcher) {
        val slot = slot<(UserPreferences) -> UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val result = repository.setRetentionPeriod(RetentionPeriod.DAYS_30)

        result.isSuccess shouldBe true
        val updated = slot.captured(UserPreferences(themePreference = ThemePreference.DARK))
        updated.retentionPeriod shouldBe RetentionPeriod.DAYS_30
        updated.themePreference shouldBe ThemePreference.DARK
    }

    @Test
    fun `setRetentionPeriod maps a datasource exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { localDataSource.updateUserPreferences(any()) } throws IllegalStateException("io error")

        val result = repository.setRetentionPeriod(RetentionPeriod.DAYS_30)

        result.isFailure shouldBe true
    }

    @Test
    fun `resetToDefaults persists a fresh default UserPreferences`() = runTest(testDispatcher) {
        val slot = slot<(UserPreferences) -> UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val result = repository.resetToDefaults()

        result.isSuccess shouldBe true
        slot.captured(UserPreferences(themePreference = ThemePreference.DARK)) shouldBe UserPreferences()
        coVerify(exactly = 1) { localDataSource.updateUserPreferences(any()) }
    }

    @Test
    fun `observeMonitoringPaused projects only the monitoring paused flag`() = runTest(testDispatcher) {
        val prefs = UserPreferences(monitoringPaused = true)
        every { localDataSource.observeUserPreferences() } returns MutableStateFlow(prefs)

        repository.observeMonitoringPaused().test {
            awaitItem() shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setMonitoringPaused delegates a transform that merges the new flag into the current preferences`() = runTest(testDispatcher) {
        val slot = slot<(UserPreferences) -> UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val result = repository.setMonitoringPaused(true)

        result.isSuccess shouldBe true
        val updated = slot.captured(UserPreferences(themePreference = ThemePreference.DARK))
        updated.monitoringPaused shouldBe true
        updated.themePreference shouldBe ThemePreference.DARK
    }

    @Test
    fun `setMonitoringPaused maps a datasource exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { localDataSource.updateUserPreferences(any()) } throws IllegalStateException("io error")

        val result = repository.setMonitoringPaused(true)

        result.isFailure shouldBe true
    }

    @Test
    fun `observeLanguage projects only the app language`() = runTest(testDispatcher) {
        val prefs = UserPreferences(appLanguage = AppLanguage.ES)
        every { localDataSource.observeUserPreferences() } returns MutableStateFlow(prefs)

        repository.observeLanguage().test {
            awaitItem() shouldBe AppLanguage.ES
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setLanguage delegates a transform that merges the new language into the current preferences`() = runTest(testDispatcher) {
        val slot = slot<(UserPreferences) -> UserPreferences>()
        coEvery { localDataSource.updateUserPreferences(capture(slot)) } returns Result.success(Unit)

        val result = repository.setLanguage(AppLanguage.EN)

        result.isSuccess shouldBe true
        val updated = slot.captured(UserPreferences(themePreference = ThemePreference.DARK))
        updated.appLanguage shouldBe AppLanguage.EN
        updated.themePreference shouldBe ThemePreference.DARK
    }

    @Test
    fun `setLanguage maps a datasource exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { localDataSource.updateUserPreferences(any()) } throws IllegalStateException("io error")

        val result = repository.setLanguage(AppLanguage.EN)

        result.isFailure shouldBe true
    }

    @Test
    fun `getUserPreferences delegates to the local data source`() = runTest(testDispatcher) {
        coEvery { localDataSource.getUserPreferences() } returns Result.success(UserPreferences())

        val result = repository.getUserPreferences()

        result.isSuccess shouldBe true
    }

    /**
     * Regression test for the lost-update race: two setters touching different fields used to
     * each read the same stale snapshot and write back a full object, silently clobbering
     * whichever write landed second. This fake stands in for the real DataStore-backed data
     * source by guarding its own read-modify-write with a [Mutex] — exactly what
     * [androidx.datastore.core.DataStore.updateData] does internally — and forces a suspension
     * point between the read and the write so the two updates can genuinely interleave under
     * [StandardTestDispatcher]. If the repository (or the data source) ever regresses to a
     * separate read-then-write outside of a single atomic transform call, this test would start
     * losing one of the two fields.
     */
    @Test
    fun `concurrent setLanguage and setTheme calls both persist without losing either update`() = runTest(testDispatcher) {
        var persisted = UserPreferences()
        val mutex = Mutex()
        coEvery { localDataSource.updateUserPreferences(any()) } coAnswers {
            val transform = firstArg<(UserPreferences) -> UserPreferences>()
            mutex.withLock {
                val current = persisted
                yield() // suspension point between "read" and "write" so calls can interleave
                persisted = transform(current)
            }
            Result.success(Unit)
        }

        val languageJob = launch { repository.setLanguage(AppLanguage.ES) }
        val themeJob = launch { repository.setTheme(ThemePreference.DARK) }
        advanceUntilIdle()
        languageJob.join()
        themeJob.join()

        persisted.appLanguage shouldBe AppLanguage.ES
        persisted.themePreference shouldBe ThemePreference.DARK
    }
}
