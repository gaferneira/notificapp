package dev.gaferneira.notificapp.core.notification.action

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SnoozeReleaseTrackerTest {

    @Test
    fun `consuming a pending key returns true and clears it`() = runTest {
        // Given: a key marked pending
        val tracker = SnoozeReleaseTracker()
        tracker.markPending("sbn-key")

        // When: consuming it twice
        val first = tracker.consumeIfPending("sbn-key")
        val second = tracker.consumeIfPending("sbn-key")

        // Then: only the first consumption succeeds
        first shouldBe true
        second shouldBe false
    }

    @Test
    fun `consuming a key that was never marked returns false`() = runTest {
        // Given: a tracker with nothing marked
        val tracker = SnoozeReleaseTracker()

        // When: consuming an unmarked key
        val result = tracker.consumeIfPending("unknown-key")

        // Then: it returns false
        result shouldBe false
    }

    @Test
    fun `independent keys do not interfere with each other`() = runTest {
        // Given: two keys, only one marked pending
        val tracker = SnoozeReleaseTracker()
        tracker.markPending("key-a")

        // When: consuming each
        val resultA = tracker.consumeIfPending("key-a")
        val resultB = tracker.consumeIfPending("key-b")

        // Then: only the marked key reports pending
        resultA shouldBe true
        resultB shouldBe false
    }
}
