package dev.gaferneira.notificapp.core.data.repository

import androidx.room.withTransaction
import app.cash.turbine.test
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleStatsRow
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.ExtractedDataUpdate
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleStats
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

private const val ACTION_ID = "action-1"
private const val PACKAGE_NAME = "com.test.app"
private const val NOW = 1_000_000_000_000L

@OptIn(ExperimentalCoroutinesApi::class)
class RuleExecutionRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val ruleExecutionDao = mockk<RuleExecutionDao>()
    private val timeProvider = mockk<CurrentTimeProvider> {
        every { nowEpochMillis() } returns NOW
    }

    private val database = mockk<AppDatabase>(relaxed = true)
    private val extractedFieldValueDao = mockk<ExtractedFieldValueDao>(relaxed = true)
    private val notificationDao = mockk<NotificationDao>(relaxed = true)

    private fun repository() = RuleExecutionRepositoryImpl(
        database = database,
        ruleExecutionDao = ruleExecutionDao,
        extractedFieldValueDao = extractedFieldValueDao,
        notificationDao = notificationDao,
        timeProvider = timeProvider,
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

    @Test
    fun `observeExecutionsForNotification maps every entity to a domain model`() = runTest(testDispatcher) {
        every { ruleExecutionDao.observeExecutionsForNotification("notif-e1") } returns
            flowOf(listOf(executionEntity("e1", createdAt = 1_000L, actionOutcomes = null)))
        val repository = repository()

        repository.observeExecutionsForNotification("notif-e1").test {
            awaitItem().single().id shouldBe "e1"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeRuleStats queries 7 and 30 day cutoffs from the injected clock and maps the row`() = runTest(testDispatcher) {
        // Given: the DAO returns an aggregate row
        val since7 = slot<Long>()
        val since30 = slot<Long>()
        every { ruleExecutionDao.observeRuleStats("rule-1", capture(since7), capture(since30)) } returns
            flowOf(RuleStatsRow(total = 10, last7Days = 3, last30Days = 6, live = 4, testMode = 6, lastTriggeredAt = 123L))

        // When: observing
        repository().observeRuleStats("rule-1").test {
            // Then: every field is mapped and cutoffs derive from the provider's now
            awaitItem() shouldBe RuleStats(
                totalMatches = 10,
                matchesLast7Days = 3,
                matchesLast30Days = 6,
                liveMatches = 4,
                testModeMatches = 6,
                lastTriggeredAt = 123L,
            )
            since7.captured shouldBe NOW - TimeUnit.DAYS.toMillis(7)
            since30.captured shouldBe NOW - TimeUnit.DAYS.toMillis(30)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeRuleStats maps an empty aggregate to zeros and a null last trigger`() = runTest(testDispatcher) {
        every { ruleExecutionDao.observeRuleStats(any(), any(), any()) } returns
            flowOf(RuleStatsRow(total = 0, last7Days = 0, last30Days = 0, live = 0, testMode = 0, lastTriggeredAt = null))

        repository().observeRuleStats("rule-1").test {
            awaitItem() shouldBe RuleStats()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeRuleStats surfaces DAO errors as a Failure`() = runTest(testDispatcher) {
        every { ruleExecutionDao.observeRuleStats(any(), any(), any()) } returns flow { throw IllegalStateException("db error") }

        repository().observeRuleStats("rule-1").test {
            awaitError().shouldBeInstanceOf<dev.gaferneira.notificapp.core.common.Failure>()
        }
    }

    @Test
    fun `updateExtractedData replaces only the extracted values inside one transaction`() = runTest(testDispatcher) {
        // Given: a transaction runner that just executes the block
        mockkStatic("androidx.room.RoomDatabaseKt")
        try {
            coEvery { database.withTransaction<Unit>(any()) } coAnswers {
                @Suppress("UNCHECKED_CAST")
                (it.invocation.args[1] as suspend () -> Unit).invoke()
            }
            coEvery { ruleExecutionDao.updateExtractedData(any(), any()) } returns Unit
            val field = RuleField(id = "f1", name = "Amount", fieldType = RuleField.FieldType.NUMBER, method = RuleField.ExtractionMethod.RegexPattern("\\d+"))
            val update = ExtractedDataUpdate("exec-1", listOf(field), mapOf("f1" to "42", "orphan" to "kept"))

            // When
            val result = repository().updateExtractedData(listOf(update))

            // Then: JSON column and typed rows for the current fields are replaced; nothing is
            // deleted at execution level and the applied-rules counter is not touched
            result.isSuccess shouldBe true
            coVerify(exactly = 1) { database.withTransaction<Unit>(any()) }
            coVerify { ruleExecutionDao.updateExtractedData("exec-1", """{"f1":"42","orphan":"kept"}""") }
            coVerify { extractedFieldValueDao.deleteValuesForExecutionFields("exec-1", listOf("f1")) }
            coVerify { extractedFieldValueDao.insertAll(match { it.single().ruleFieldId == "f1" && it.single().valueNumber == 42.0 }) }
            coVerify(exactly = 0) { ruleExecutionDao.insert(any()) }
            coVerify(exactly = 0) { ruleExecutionDao.delete(any()) }
            coVerify(exactly = 0) { notificationDao.incrementAppliedRulesCount(any()) }
        } finally {
            unmockkStatic("androidx.room.RoomDatabaseKt")
        }
    }

    @Test
    fun `updateExtractedData maps a failure inside the transaction to a Failure result`() = runTest(testDispatcher) {
        mockkStatic("androidx.room.RoomDatabaseKt")
        try {
            coEvery { database.withTransaction<Unit>(any()) } throws IllegalStateException("db error")
            val update = ExtractedDataUpdate("exec-1", emptyList(), emptyMap())

            val result = repository().updateExtractedData(listOf(update))

            result.exceptionOrNull().shouldBeInstanceOf<dev.gaferneira.notificapp.core.common.Failure>()
        } finally {
            unmockkStatic("androidx.room.RoomDatabaseKt")
        }
    }
}
