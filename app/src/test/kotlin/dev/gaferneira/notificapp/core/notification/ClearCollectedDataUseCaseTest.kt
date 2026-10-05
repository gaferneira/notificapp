package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ClearCollectedDataUseCaseTest {

    private val notificationRepository: NotificationRepository = mockk()
    private val useCase = ClearCollectedDataUseCase(notificationRepository)

    @Test
    fun `deletes all notifications through the repository`() = runTest {
        coEvery { notificationRepository.deleteAll() } returns Result.success(Unit)

        val result = useCase()

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { notificationRepository.deleteAll() }
    }

    @Test
    fun `propagates repository failure`() = runTest {
        val failure = IllegalStateException("db")
        coEvery { notificationRepository.deleteAll() } returns Result.failure(failure)

        val result = useCase()

        result.exceptionOrNull() shouldBe failure
    }
}
