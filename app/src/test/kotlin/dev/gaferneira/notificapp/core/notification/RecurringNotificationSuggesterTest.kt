package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.RuleCoverage
import dev.gaferneira.notificapp.domain.model.SuggestionDismissalKey
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import dev.gaferneira.notificapp.testutil.createTestTimeRangeCondition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val UTC: ZoneId = ZoneOffset.UTC
private val suggester = RecurringNotificationSuggester()

private fun epochMillisAt(date: LocalDate, hour: Int = 9): Long = date.atTime(hour, 0).atZone(UTC).toInstant().toEpochMilli()

class RecurringNotificationSuggesterTest {

    @Test
    fun `below minOccurrences is excluded`() {
        val day1 = LocalDate.of(2026, 7, 1)
        val notifications = listOf(
            createTestNotification(packageName = "com.test.app", title = "Your order is on the way", timestamp = epochMillisAt(day1)),
            createTestNotification(packageName = "com.test.app", title = "Your order is on the way", timestamp = epochMillisAt(day1.plusDays(1L))),
        )

        val result = suggester.suggest(notifications, activeRules = emptyList(), dismissals = emptySet(), zoneId = UTC)

        result shouldBe emptyList()
    }

    @Test
    fun `same-day burst is excluded by minDistinctDays even above minOccurrences`() {
        val day1 = LocalDate.of(2026, 7, 1)
        val notifications = (0 until 5).map { hourOffset ->
            createTestNotification(
                packageName = "com.test.app",
                title = "Your order is on the way",
                timestamp = epochMillisAt(day1, hour = hourOffset),
            )
        }

        val result = suggester.suggest(notifications, activeRules = emptyList(), dismissals = emptySet(), zoneId = UTC)

        result shouldBe emptyList()
    }

    @Test
    fun `dismissed key is excluded even when it otherwise qualifies`() {
        val day1 = LocalDate.of(2026, 7, 1)
        val notifications = (1..4).flatMap { dayOffset ->
            listOf(
                createTestNotification(packageName = "com.test.app", title = "Your order is on the way", timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong()))),
            )
        }
        val normalizedKey = NotificationTitleNormalizer.normalize("Your order is on the way")!!
        val dismissals = setOf(SuggestionDismissalKey("com.test.app", normalizedKey))

        val result = suggester.suggest(notifications, activeRules = emptyList(), dismissals = dismissals, zoneId = UTC)

        result shouldBe emptyList()
    }

    @Test
    fun `UNCOVERED outranks APP_ONLY`() {
        val day1 = LocalDate.of(2026, 7, 1)

        val uncoveredNots = (1..4).map { dayOffset ->
            createTestNotification(packageName = "com.uncovered.app", title = "Uncovered notification", timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong())))
        }

        val coveredNots = (1..4).map { dayOffset ->
            createTestNotification(
                packageName = "com.covered.app",
                title = "Covered notification",
                content = "some content",
                timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong())),
            )
        }

        val ruleForCoveredApp = createTestRule(
            targetApps = listOf(AppInfo("com.covered.app", "Covered App")),
            conditions = listOf(createTestCondition(condition = MatchingCondition.TEXT_CONTENT, value = "nonexistent")),
        )

        val result = suggester.suggest(
            notifications = uncoveredNots + coveredNots,
            activeRules = listOf(ruleForCoveredApp),
            dismissals = emptySet(),
            zoneId = UTC,
        )

        result.size shouldBe 2
        result[0].coverage shouldBe RuleCoverage.UNCOVERED
        result[1].coverage shouldBe RuleCoverage.APP_ONLY
    }

    @Test
    fun `CONDITIONS_MATCH is suppressed entirely, not just deprioritized`() {
        val day1 = LocalDate.of(2026, 7, 1)
        val notifications = (1..4).map { dayOffset ->
            createTestNotification(
                packageName = "com.test.app",
                title = "Your payment was processed",
                timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong())),
            )
        }

        val matchingRule = createTestRule(
            targetApps = listOf(AppInfo("com.test.app", "Test App")),
            conditions = listOf(createTestCondition(condition = MatchingCondition.TITLE, value = "payment")),
        )

        val result = suggester.suggest(
            notifications = notifications,
            activeRules = listOf(matchingRule),
            dismissals = emptySet(),
            zoneId = UTC,
        )

        result shouldBe emptyList()
    }

    @Test
    fun `equal coverage breaks tie by higher occurrence count`() {
        val day1 = LocalDate.of(2026, 7, 1)

        val groupA = (1..4).map { dayOffset ->
            createTestNotification(packageName = "com.app.a", title = "Alert from A", timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong())))
        }

        val groupB = (1..6).map { dayOffset ->
            createTestNotification(packageName = "com.app.b", title = "Alert from B", timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong())))
        }

        val result = suggester.suggest(
            notifications = groupA + groupB,
            activeRules = emptyList(),
            dismissals = emptySet(),
            zoneId = UTC,
        )

        result.size shouldBe 2
        result[0].packageName shouldBe "com.app.b"
        result[0].occurrences shouldBe 6
        result[1].packageName shouldBe "com.app.a"
        result[1].occurrences shouldBe 4
    }

    @Test
    fun `maxSuggestions caps the result count`() {
        val day1 = LocalDate.of(2026, 7, 1)

        val notifications = (1..5).flatMap { appNum ->
            (1..4).map { dayOffset ->
                createTestNotification(
                    packageName = "com.app.$appNum",
                    title = "Notification from app $appNum",
                    timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong())),
                )
            }
        }

        val result = suggester.suggest(
            notifications = notifications,
            activeRules = emptyList(),
            dismissals = emptySet(),
            zoneId = UTC,
        )

        result.size shouldBe 3
    }

    @Test
    fun `time-range coverage is judged at the representative notification's own timestamp, not wall-clock now`() {
        val historicalDay = LocalDate.of(2026, 6, 15)
        val notifications = (1..4).map { dayOffset ->
            createTestNotification(
                packageName = "com.test.app",
                title = "Night alert",
                timestamp = epochMillisAt(historicalDay.plusDays(dayOffset.toLong()), hour = 23),
            )
        }

        val nightRule = createTestRule(
            targetApps = listOf(AppInfo("com.test.app", "Test App")),
            conditions = listOf(createTestTimeRangeCondition(start = java.time.LocalTime.of(22, 0), end = java.time.LocalTime.of(7, 0))),
        )

        val result = suggester.suggest(
            notifications = notifications,
            activeRules = listOf(nightRule),
            dismissals = emptySet(),
            zoneId = UTC,
        )

        result shouldBe emptyList()
    }

    @Test
    fun `identical titles group into one candidate`() {
        val day1 = LocalDate.of(2026, 7, 1)
        val notifications = (1..4).map { dayOffset ->
            createTestNotification(
                packageName = "com.test.app",
                title = "Your order is on the way",
                timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong())),
            )
        }

        val result = suggester.suggest(notifications, activeRules = emptyList(), dismissals = emptySet(), zoneId = UTC)

        result.size shouldBe 1
        result[0].occurrences shouldBe 4
    }

    @Test
    fun `titles differing only by a dynamic value group into one candidate`() {
        val day1 = LocalDate.of(2026, 7, 1)
        val notifications = listOf(
            createTestNotification(packageName = "com.test.app", title = "You received $40.00", timestamp = epochMillisAt(day1)),
            createTestNotification(packageName = "com.test.app", title = "You received $85.50", timestamp = epochMillisAt(day1.plusDays(1L))),
            createTestNotification(packageName = "com.test.app", title = "You received $12.00", timestamp = epochMillisAt(day1.plusDays(2L))),
            createTestNotification(packageName = "com.test.app", title = "You received $99.99", timestamp = epochMillisAt(day1.plusDays(3L))),
        )

        val result = suggester.suggest(notifications, activeRules = emptyList(), dismissals = emptySet(), zoneId = UTC)

        result.size shouldBe 1
        result[0].occurrences shouldBe 4
    }

    @Test
    fun `unrelated titles do not group together`() {
        val day1 = LocalDate.of(2026, 7, 1)
        val notifications = (1..4).flatMap { dayOffset ->
            listOf(
                createTestNotification(packageName = "com.test.app", title = "Your order is on the way", timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong()), hour = 9)),
                createTestNotification(packageName = "com.test.app", title = "Your payment failed", timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong()), hour = 10)),
            )
        }

        val result = suggester.suggest(notifications, activeRules = emptyList(), dismissals = emptySet(), zoneId = UTC)

        result.size shouldBe 2
    }

    @Test
    fun `a distinct different title key for the same app is not excluded by an unrelated key's dismissal`() {
        val day1 = LocalDate.of(2026, 7, 1)

        val dismissedKey = NotificationTitleNormalizer.normalize("Your order is on the way")!!
        val dismissals = setOf(SuggestionDismissalKey("com.test.app", dismissedKey))

        val notifications = (1..4).map { dayOffset ->
            createTestNotification(
                packageName = "com.test.app",
                title = "Your payment was processed",
                timestamp = epochMillisAt(day1.plusDays(dayOffset.toLong())),
            )
        }

        val result = suggester.suggest(notifications, activeRules = emptyList(), dismissals = dismissals, zoneId = UTC)

        result.size shouldBe 1
        result[0].sampleTitle shouldBe "Your payment was processed"
    }
}
