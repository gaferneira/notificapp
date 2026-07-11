package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

private class TestClock(var epochMillis: Long = 0L) : CurrentTimeProvider {
    override fun now(): LocalDateTime = LocalDateTime.of(2026, 7, 11, 9, 30)

    override fun nowEpochMillis(): Long = epochMillis
}

private const val WINDOW_MS = 600_000L // 10 minutes
private const val ACTION_ID = "action-1"
private const val PACKAGE_NAME = "com.test.app"

class NotificationThrottleTrackerTest {

    private fun repository(lastDelivery: Result<Long?> = Result.success(null)): RuleExecutionRepository = mockk {
        coEvery { lastThrottleDeliveryAt(any(), any(), any()) } returns lastDelivery
    }

    @Test
    fun `first match for a key delivers`() = runTest {
        // Given: a tracker with no prior deliveries
        val tracker = NotificationThrottleTracker(repository(), TestClock())

        // When: the first match arrives
        val delivered = tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L)

        // Then: it delivers
        delivered shouldBe true
    }

    @Test
    fun `a second match inside the window is suppressed`() = runTest {
        // Given: a tracker that already delivered at t=0
        val clock = TestClock(0L)
        val tracker = NotificationThrottleTracker(repository(), clock)
        tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L)

        // When: a second match arrives 2 minutes later, inside the 10-minute window
        clock.epochMillis = 120_000L
        val delivered = tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L)

        // Then: it is suppressed
        delivered shouldBe false
    }

    @Test
    fun `a match exactly at the window boundary delivers`() = runTest {
        // Given: a tracker that delivered at t=0 with a 10-minute window
        val clock = TestClock(0L)
        val tracker = NotificationThrottleTracker(repository(), clock)
        tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L)

        // When: a match arrives exactly when the window elapses (now - last == window)
        clock.epochMillis = WINDOW_MS
        val delivered = tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L)

        // Then: it delivers - the window has fully elapsed
        delivered shouldBe true
    }

    @Test
    fun `bumping the reset watermark invalidates a stale in-memory delivery`() = runTest {
        // Given: a tracker that delivered at t=0
        val clock = TestClock(0L)
        val tracker = NotificationThrottleTracker(repository(), clock)
        tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L)

        // When: a match arrives 2 minutes later (still inside the window) but the action was
        // edited, bumping resetAt past the prior delivery
        clock.epochMillis = 120_000L
        val delivered = tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 100_000L)

        // Then: the stale delivery no longer counts, so this match opens a fresh window
        delivered shouldBe true
    }

    @Test
    fun `a cold key falls back to the database lookback`() = runTest {
        // Given: a fresh tracker (nothing in memory) and a DB delivery 3 minutes ago
        val clock = TestClock(600_000L)
        val repo = repository(lastDelivery = Result.success(420_000L))
        val tracker = NotificationThrottleTracker(repo, clock)

        // When: the first in-process match for this key arrives
        val delivered = tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L)

        // Then: the DB lookback is consulted and the match is suppressed (still inside the window)
        delivered shouldBe false
        coVerify(exactly = 1) { repo.lastThrottleDeliveryAt(ACTION_ID, PACKAGE_NAME, 600_000L - WINDOW_MS) }
    }

    @Test
    fun `a suppressed cold-key decision caches the DB result so a burst of further matches never re-queries`() = runTest {
        // Given: a fresh tracker and a DB delivery 3 minutes ago, inside the 10-minute window
        val clock = TestClock(600_000L)
        val repo = repository(lastDelivery = Result.success(420_000L))
        val tracker = NotificationThrottleTracker(repo, clock)

        // When: several further matches for the same key arrive in quick succession, all still
        // inside the window
        repeat(5) {
            clock.epochMillis += 1_000L
            tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L) shouldBe false
        }

        // Then: only the first (cache-miss) decision hit the database - the rest used the cached
        // in-memory value, so a burst of suppressed notifications never fans out to the DB
        coVerify(exactly = 1) { repo.lastThrottleDeliveryAt(any(), any(), any()) }
    }

    @Test
    fun `a DB lookback failure fails open and delivers`() = runTest {
        // Given: a fresh tracker whose DB lookback fails
        val clock = TestClock(0L)
        val repo = repository(lastDelivery = Result.failure(IllegalStateException("db error")))
        val tracker = NotificationThrottleTracker(repo, clock)

        // When: the first in-process match for this key arrives
        val delivered = tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L)

        // Then: it fails open and delivers
        delivered shouldBe true
    }

    @Test
    fun `independent windows per targeted app`() = runTest {
        // Given: a tracker that already delivered for package A at t=0
        val clock = TestClock(0L)
        val tracker = NotificationThrottleTracker(repository(), clock)
        val firstAppDelivered = tracker.shouldDeliver(ACTION_ID, "com.app.a", WINDOW_MS, resetAt = 0L)

        // When: a match for a different package B under the same rule/action arrives, then a
        // second match for package A arrives 2 minutes later (still inside A's window)
        clock.epochMillis = 120_000L
        val firstBDelivered = tracker.shouldDeliver(ACTION_ID, "com.app.b", WINDOW_MS, resetAt = 0L)
        val secondADelivered = tracker.shouldDeliver(ACTION_ID, "com.app.a", WINDOW_MS, resetAt = 0L)

        // Then: package B's first match delivers on its own independent window (unaffected by A's
        // already-open window), while package A's second match is suppressed within its own window
        firstAppDelivered shouldBe true
        firstBDelivered shouldBe true
        secondADelivered shouldBe false
    }

    @Test
    fun `an unresolvable source package falls back to a consistent scope key`() = runTest {
        // Given: a fresh tracker with no prior deliveries
        val clock = TestClock(0L)
        val tracker = NotificationThrottleTracker(repository(), clock)

        // When: the first match arrives with a blank package name (source app couldn't be
        // resolved), then a second blank-package match arrives 2 minutes later, still inside the
        // window
        val firstDelivered = tracker.shouldDeliver(ACTION_ID, packageName = "", WINDOW_MS, resetAt = 0L)
        clock.epochMillis = 120_000L
        val secondDelivered = tracker.shouldDeliver(ACTION_ID, packageName = "", WINDOW_MS, resetAt = 0L)

        // Then: both blank-package matches share the same fallback scope key, so the second one is
        // throttled just like a resolvable package would be - the fallback doesn't bypass throttling
        firstDelivered shouldBe true
        secondDelivered shouldBe false
    }

    @Test
    fun `concurrent near-simultaneous matches resolve to exactly one delivery`() = runTest {
        // Given: a fresh tracker
        val clock = TestClock(0L)
        val tracker = NotificationThrottleTracker(repository(), clock)

        // When: two matches race to decide at the same instant
        val results = listOf(
            async { tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L) },
            async { tracker.shouldDeliver(ACTION_ID, PACKAGE_NAME, WINDOW_MS, resetAt = 0L) },
        ).awaitAll()

        // Then: the mutex-guarded check-and-set lets exactly one through
        results.count { it } shouldBe 1
    }
}
