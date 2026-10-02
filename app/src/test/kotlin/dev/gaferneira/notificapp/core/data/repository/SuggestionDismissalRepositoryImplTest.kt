package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.data.local.dao.SuggestionDismissalDao
import dev.gaferneira.notificapp.core.data.local.entity.SuggestionDismissalEntity
import dev.gaferneira.notificapp.domain.model.SuggestionDismissalKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionDismissalRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dao = mockk<SuggestionDismissalDao>()

    private fun repository() = SuggestionDismissalRepositoryImpl(
        dao = dao,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `observeDismissals maps every entity to a domain key set`() = runTest(testDispatcher) {
        every { dao.observeAll() } returns flowOf(
            listOf(
                SuggestionDismissalEntity(packageName = "com.a", normalizedTitleKey = "key-1", dismissedAt = 1_000L),
                SuggestionDismissalEntity(packageName = "com.b", normalizedTitleKey = "key-2", dismissedAt = 2_000L),
            ),
        )

        val result = repository().observeDismissals()

        var emitted: Set<SuggestionDismissalKey>? = null
        result.collect { emitted = it }
        emitted shouldBe setOf(
            SuggestionDismissalKey("com.a", "key-1"),
            SuggestionDismissalKey("com.b", "key-2"),
        )
    }

    @Test
    fun `dismiss maps the key to an entity and calls the DAO`() = runTest(testDispatcher) {
        val slot = slot<SuggestionDismissalEntity>()
        coEvery { dao.dismiss(capture(slot)) } returns Unit
        val repository = repository()

        val result = repository.dismiss("com.test.app", "your order is on the way")

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.dismiss(any()) }
        slot.captured.packageName shouldBe "com.test.app"
        slot.captured.normalizedTitleKey shouldBe "your order is on the way"
    }

    @Test
    fun `re-dismissing the same key calls the DAO again rather than erroring`() = runTest(testDispatcher) {
        coEvery { dao.dismiss(any()) } returns Unit
        val repository = repository()

        repository.dismiss("com.test.app", "your order is on the way")
        val secondResult = repository.dismiss("com.test.app", "your order is on the way")

        secondResult.isSuccess shouldBe true
        coVerify(exactly = 2) { dao.dismiss(any()) }
    }

    @Test
    fun `a DAO failure is mapped to a Failure, never a raw throwable`() = runTest(testDispatcher) {
        coEvery { dao.dismiss(any()) } throws IllegalStateException("db error")
        val repository = repository()

        val result = repository.dismiss("com.test.app", "your order is on the way")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<Failure>()
    }
}
