package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationDeduplicatorTest {

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var deduplicator: NotificationDeduplicator

    @BeforeEach
    fun setUp() {
        notificationRepository = mockk()
        // The delay forces a genuine suspension point inside isDuplicate, so concurrent
        // callers actually interleave at the check-then-act boundary instead of each
        // running to completion before the next one starts.
        coEvery { notificationRepository.hasRecentDuplicate(any(), any(), any()) } coAnswers {
            delay(10)
            Result.success(false)
        }
        deduplicator = NotificationDeduplicator(notificationRepository)
    }

    @Test
    fun `first check for a notification is not a duplicate`() = runTest {
        // Given: a notification never seen before
        val notification = createTestNotification()

        // When: checking it once
        val result = deduplicator.isDuplicate(notification)

        // Then: it is not a duplicate
        result shouldBe false
    }

    @Test
    fun `second check for the same notification is a duplicate`() = runTest {
        // Given: a notification already recorded
        val notification = createTestNotification()
        deduplicator.isDuplicate(notification)

        // When: checking the identical notification again
        val result = deduplicator.isDuplicate(notification)

        // Then: it is flagged as a duplicate
        result shouldBe true
    }

    @Test
    fun `concurrent bursts of the identical notification only let one through`() = runTest {
        // Given: the same notification content arriving from many concurrent coroutines,
        // simulating a burst of near-simultaneous notifications from one app
        val notification = createTestNotification()

        // When: checking it concurrently many times
        val results = (1..50).map {
            async { deduplicator.isDuplicate(notification) }
        }.awaitAll()

        // Then: exactly one call observes it as new, the rest are duplicates
        results.count { !it } shouldBe 1
        results.count { it } shouldBe 49
    }
}
