package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.domain.model.preferences.UserPreferences
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class EnforceRetentionUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var timeProvider: CurrentTimeProvider

    private val now = 1_000_000_000_000L

    @BeforeEach
    fun setUp() {
        userPreferencesRepository = mockk()
        notificationRepository = mockk()
        timeProvider = mockk()
        every { timeProvider.nowEpochMillis() } returns now
        coEvery { notificationRepository.deleteOlderThan(any()) } returns Result.success(Unit)
    }

    private fun createUseCase(): EnforceRetentionUseCase = EnforceRetentionUseCase(
        userPreferencesRepository = userPreferencesRepository,
        notificationRepository = notificationRepository,
        timeProvider = timeProvider,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `NEVER retention does not delete anything`() = runTest(testDispatcher) {
        coEvery { userPreferencesRepository.getUserPreferences() } returns
            Result.success(UserPreferences(retentionPeriod = RetentionPeriod.NEVER))

        createUseCase().invoke()

        coVerify(exactly = 0) { notificationRepository.deleteOlderThan(any()) }
    }

    @Test
    fun `DAYS_30 retention deletes notifications older than 30 days from now`() = runTest(testDispatcher) {
        coEvery { userPreferencesRepository.getUserPreferences() } returns
            Result.success(UserPreferences(retentionPeriod = RetentionPeriod.DAYS_30))

        createUseCase().invoke()

        val expectedCutoff = now - TimeUnit.DAYS.toMillis(30)
        coVerify(exactly = 1) { notificationRepository.deleteOlderThan(expectedCutoff) }
    }

    @Test
    fun `DAYS_90 retention deletes notifications older than 90 days from now`() = runTest(testDispatcher) {
        coEvery { userPreferencesRepository.getUserPreferences() } returns
            Result.success(UserPreferences(retentionPeriod = RetentionPeriod.DAYS_90))

        createUseCase().invoke()

        val expectedCutoff = now - TimeUnit.DAYS.toMillis(90)
        coVerify(exactly = 1) { notificationRepository.deleteOlderThan(expectedCutoff) }
    }

    @Test
    fun `failure to read preferences does not crash and skips deletion`() = runTest(testDispatcher) {
        coEvery { userPreferencesRepository.getUserPreferences() } returns
            Result.failure(IllegalStateException("io error"))

        createUseCase().invoke()

        coVerify(exactly = 0) { notificationRepository.deleteOlderThan(any()) }
    }

    @Test
    fun `deletion failure is swallowed without throwing`() = runTest(testDispatcher) {
        coEvery { userPreferencesRepository.getUserPreferences() } returns
            Result.success(UserPreferences(retentionPeriod = RetentionPeriod.DAYS_30))
        coEvery { notificationRepository.deleteOlderThan(any()) } returns Result.failure(IllegalStateException("db error"))

        // Should complete without propagating the repository failure - runTest fails the test
        // if an exception escapes the use case.
        createUseCase().invoke()

        coVerify(exactly = 1) { notificationRepository.deleteOlderThan(any()) }
    }
}
