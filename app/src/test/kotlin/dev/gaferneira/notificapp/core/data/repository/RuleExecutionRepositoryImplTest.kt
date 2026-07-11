package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private const val ACTION_ID = "action-1"
private const val PACKAGE_NAME = "com.test.app"

class RuleExecutionRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val ruleExecutionDao = mockk<RuleExecutionDao>()

    private fun repository() = RuleExecutionRepositoryImpl(
        database = mockk<AppDatabase>(relaxed = true),
        ruleExecutionDao = ruleExecutionDao,
        extractedFieldValueDao = mockk<ExtractedFieldValueDao>(relaxed = true),
        notificationDao = mockk<NotificationDao>(relaxed = true),
        ioDispatcher = testDispatcher,
    )

    private fun executionEntity(
        id: String,
        createdAt: Long,
        actionOutcomes: String?,
    ) = RuleExecutionEntity(
        id = id,
        notificationId = "notif-$id",
        ruleId = "rule-1",
        extractedData = "{}",
        triggeredActions = "[]",
        actionOutcomes = actionOutcomes,
        createdAt = createdAt,
    )

    @Test
    fun `returns the max createdAt among SUCCESS rows for the given action id`() = runTest(testDispatcher) {
        // Given: three rows in the window, two matching the action id with a SUCCESS outcome,
        // one for a different action id (should be excluded)
        coEvery { ruleExecutionDao.getRecentExecutionsForPackageSince(PACKAGE_NAME, any()) } returns listOf(
            executionEntity("e1", createdAt = 1_000L, actionOutcomes = """{"$ACTION_ID":"SUCCESS"}"""),
            executionEntity("e2", createdAt = 2_000L, actionOutcomes = """{"$ACTION_ID":"SUCCESS"}"""),
            executionEntity("e3", createdAt = 3_000L, actionOutcomes = """{"other-action":"SUCCESS"}"""),
        )
        val repository = repository()

        // When: looking up the last throttle delivery
        val result = repository.lastThrottleDeliveryAt(ACTION_ID, PACKAGE_NAME, sinceMs = 0L)

        // Then: it returns the newest matching SUCCESS row's timestamp
        result.getOrNull() shouldBe 2_000L
    }

    @Test
    fun `excludes SUPPRESSED rows for the same action id`() = runTest(testDispatcher) {
        // Given: a row where the action outcome was suppressed, not delivered
        coEvery { ruleExecutionDao.getRecentExecutionsForPackageSince(PACKAGE_NAME, any()) } returns listOf(
            executionEntity("e1", createdAt = 1_000L, actionOutcomes = """{"$ACTION_ID":"SUPPRESSED"}"""),
        )
        val repository = repository()

        // When: looking up the last throttle delivery
        val result = repository.lastThrottleDeliveryAt(ACTION_ID, PACKAGE_NAME, sinceMs = 0L)

        // Then: no delivery is found
        result.getOrNull() shouldBe null
    }

    @Test
    fun `returns null when there are no rows in range`() = runTest(testDispatcher) {
        // Given: an empty result set
        coEvery { ruleExecutionDao.getRecentExecutionsForPackageSince(PACKAGE_NAME, any()) } returns emptyList()
        val repository = repository()

        // When: looking up the last throttle delivery
        val result = repository.lastThrottleDeliveryAt(ACTION_ID, PACKAGE_NAME, sinceMs = 0L)

        // Then: it succeeds with null (fail-open territory, but a real empty result is distinct
        // from a failure)
        result.isSuccess shouldBe true
        result.getOrNull() shouldBe null
    }

    @Test
    fun `a DAO failure is surfaced as Result-failure so the tracker can fail open`() = runTest(testDispatcher) {
        // Given: the DAO throws
        coEvery { ruleExecutionDao.getRecentExecutionsForPackageSince(PACKAGE_NAME, any()) } throws IllegalStateException("db error")
        val repository = repository()

        // When: looking up the last throttle delivery
        val result = repository.lastThrottleDeliveryAt(ACTION_ID, PACKAGE_NAME, sinceMs = 0L)

        // Then: the failure is wrapped in Result.failure rather than thrown
        result.isFailure shouldBe true
    }
}
