package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestDayOfWeekCondition
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestTimeRangeCondition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private val FIXED_NOW: LocalDateTime = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(12, 0)) // a Monday

class RuleMatcherTest {

    // region General behavior

    @Test
    fun `empty condition list matches everything`() {
        // Given: a notification and no conditions
        val notification = createTestNotification()

        // When: matching with an empty condition list
        val result = RuleMatcher.matches(notification, emptyList(), FIXED_NOW)

        // Then: the notification matches
        result shouldBe true
    }

    @Test
    fun `null notification field never matches`() {
        // Given: a condition targeting TITLE while the notification title is null
        val notification = createTestNotification(title = null)
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "anything",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the condition does not match
        result shouldBe false
    }

    @Test
    fun `multiple conditions are AND-ed together`() {
        // Given: two conditions that both match
        val notification = createTestNotification(title = "Hello World", content = "Some content")
        val matchingTitle = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "Hello",
        )
        val matchingContent = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Some",
        )

        // When: matching with both conditions
        val result = RuleMatcher.matches(notification, listOf(matchingTitle, matchingContent), FIXED_NOW)

        // Then: the notification matches
        result shouldBe true
    }

    @Test
    fun `multiple conditions fail if any single condition fails`() {
        // Given: one matching condition and one non-matching condition
        val notification = createTestNotification(title = "Hello World", content = "Some content")
        val matchingTitle = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "Hello",
        )
        val nonMatchingContent = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Nope",
        )

        // When: matching with both conditions
        val result = RuleMatcher.matches(notification, listOf(matchingTitle, nonMatchingContent), FIXED_NOW)

        // Then: the notification does not match
        result shouldBe false
    }

    // endregion

    // region ConditionCombinator

    @Test
    fun `ALL matches only when every condition matches`() {
        // Given: three conditions and a notification that satisfies all of them
        val notification = createTestNotification(title = "Hello World", content = "Some content", appName = "Test App")
        val titleCondition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "Hello",
        )
        val contentCondition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Some",
        )
        val appCondition = createTestCondition(
            condition = MatchingCondition.APP_NAME,
            operator = MatchingOperator.EQUALS,
            value = "Test App",
        )

        // When/Then: ALL matches when every condition matches
        RuleMatcher.matches(
            notification,
            listOf(titleCondition, contentCondition, appCondition),
            FIXED_NOW,
            ConditionCombinator.ALL,
        ) shouldBe true

        // And: ALL fails as soon as one condition fails
        val failingContent = contentCondition.copy(value = "Nope")
        RuleMatcher.matches(
            notification,
            listOf(titleCondition, failingContent, appCondition),
            FIXED_NOW,
            ConditionCombinator.ALL,
        ) shouldBe false
    }

    @Test
    fun `ANY matches when at least one condition matches`() {
        // Given: three content conditions, only one of which matches the notification
        val notification = createTestNotification(content = "Payment received")
        val conditionOne = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Invoice",
        )
        val conditionTwo = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Payment",
        )
        val conditionThree = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Reminder",
        )

        // When: matching with ANY combinator
        val result = RuleMatcher.matches(
            notification,
            listOf(conditionOne, conditionTwo, conditionThree),
            FIXED_NOW,
            ConditionCombinator.ANY,
        )

        // Then: the rule matches because conditionTwo matches
        result shouldBe true
    }

    @Test
    fun `empty conditions list matches under both combinators`() {
        // Given: a notification and no conditions
        val notification = createTestNotification()

        // When/Then: an empty list matches under ALL and ANY
        RuleMatcher.matches(notification, emptyList(), FIXED_NOW, ConditionCombinator.ALL) shouldBe true
        RuleMatcher.matches(notification, emptyList(), FIXED_NOW, ConditionCombinator.ANY) shouldBe true
    }

    @Test
    fun `ANY with every condition failing does not match`() {
        // Given: three conditions, none of which match the notification
        val notification = createTestNotification(content = "Totalt: 153,50 kr")
        val conditionOne = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Invoice",
        )
        val conditionTwo = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Payment",
        )
        val conditionThree = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Reminder",
        )

        // When: matching with ANY combinator
        val result = RuleMatcher.matches(
            notification,
            listOf(conditionOne, conditionTwo, conditionThree),
            FIXED_NOW,
            ConditionCombinator.ANY,
        )

        // Then: the rule does not match
        result shouldBe false
    }

    @Test
    fun `ANY short-circuits correctly regardless of match order`() {
        // Given: three conditions where the first fails and a later one matches
        val notification = createTestNotification(content = "Payment received")
        val failingFirst = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Invoice",
        )
        val matchingSecond = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Payment",
        )
        val failingThird = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Reminder",
        )

        // When: matching with ANY combinator
        val result = RuleMatcher.matches(
            notification,
            listOf(failingFirst, matchingSecond, failingThird),
            FIXED_NOW,
            ConditionCombinator.ANY,
        )

        // Then: the rule matches despite the first condition failing
        result shouldBe true
    }

    // endregion

    // region MatchingOperator

    @Test
    fun `CONTAINS matches when value is a substring`() {
        // Given: a notification whose content contains the target substring
        val notification = createTestNotification(content = "Totalt: 153,50 kr")
        val condition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Totalt",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches
        result shouldBe true
    }

    @Test
    fun `CONTAINS does not match when value is not a substring`() {
        // Given: a notification whose content does not contain the target substring
        val notification = createTestNotification(content = "Totalt: 153,50 kr")
        val condition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "Missing",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule does not match
        result shouldBe false
    }

    @Test
    fun `STARTS_WITH matches when value is a prefix`() {
        // Given: a notification whose title starts with the target value
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.STARTS_WITH,
            value = "ICA",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches
        result shouldBe true
    }

    @Test
    fun `STARTS_WITH does not match when value is not a prefix`() {
        // Given: a notification whose title does not start with the target value
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.STARTS_WITH,
            value = "Kvantum",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule does not match
        result shouldBe false
    }

    @Test
    fun `ENDS_WITH matches when value is a suffix`() {
        // Given: a notification whose title ends with the target value
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.ENDS_WITH,
            value = "Kvantum",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches
        result shouldBe true
    }

    @Test
    fun `ENDS_WITH does not match when value is not a suffix`() {
        // Given: a notification whose title does not end with the target value
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.ENDS_WITH,
            value = "ICA",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule does not match
        result shouldBe false
    }

    @Test
    fun `EQUALS matches when value is exactly equal`() {
        // Given: a notification whose app name exactly equals the target value
        val notification = createTestNotification(appName = "Test App")
        val condition = createTestCondition(
            condition = MatchingCondition.APP_NAME,
            operator = MatchingOperator.EQUALS,
            value = "Test App",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches
        result shouldBe true
    }

    @Test
    fun `EQUALS does not match when value differs`() {
        // Given: a notification whose app name does not equal the target value
        val notification = createTestNotification(appName = "Test App")
        val condition = createTestCondition(
            condition = MatchingCondition.APP_NAME,
            operator = MatchingOperator.EQUALS,
            value = "Other App",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule does not match
        result shouldBe false
    }

    @Test
    fun `REGEX_MATCH matches when pattern is found`() {
        // Given: a notification whose content matches the regex pattern
        val notification = createTestNotification(content = "Totalt: 153,50 kr")
        val condition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.REGEX_MATCH,
            value = """\d+,\d{2}""",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches
        result shouldBe true
    }

    @Test
    fun `REGEX_MATCH does not match when pattern is not found`() {
        // Given: a notification whose content does not match the regex pattern
        val notification = createTestNotification(content = "Totalt: 153,50 kr")
        val condition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.REGEX_MATCH,
            value = """^\d+$""",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule does not match
        result shouldBe false
    }

    @Test
    fun `REGEX_MATCH with an invalid pattern returns false instead of crashing`() {
        // Given: a condition with a syntactically invalid regex pattern
        val notification = createTestNotification(content = "Totalt: 153,50 kr")
        val condition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.REGEX_MATCH,
            value = "[unclosed",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule does not match, and no exception is thrown
        result shouldBe false
    }

    @Test
    fun `NOT_CONTAINS matches when value is absent`() {
        // Given: a notification whose content does not contain the target substring
        val notification = createTestNotification(content = "Totalt: 153,50 kr")
        val condition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.NOT_CONTAINS,
            value = "Missing",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches
        result shouldBe true
    }

    @Test
    fun `NOT_CONTAINS does not match when value is present`() {
        // Given: a notification whose content contains the target substring
        val notification = createTestNotification(content = "Totalt: 153,50 kr")
        val condition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.NOT_CONTAINS,
            value = "Totalt",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule does not match
        result shouldBe false
    }

    // endregion

    // region MatchingCondition field resolution

    @Test
    fun `TEXT_CONTENT condition resolves against notification content`() {
        // Given: a notification with distinct content
        val notification = createTestNotification(content = "unique-content-marker")
        val condition = createTestCondition(
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.EQUALS,
            value = "unique-content-marker",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches against the content field
        result shouldBe true
    }

    @Test
    fun `TITLE condition resolves against notification title`() {
        // Given: a notification with distinct title
        val notification = createTestNotification(title = "unique-title-marker")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.EQUALS,
            value = "unique-title-marker",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches against the title field
        result shouldBe true
    }

    @Test
    fun `APP_NAME condition resolves against notification appName`() {
        // Given: a notification with distinct app name
        val notification = createTestNotification(appName = "unique-app-marker")
        val condition = createTestCondition(
            condition = MatchingCondition.APP_NAME,
            operator = MatchingOperator.EQUALS,
            value = "unique-app-marker",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches against the appName field
        result shouldBe true
    }

    @Test
    fun `PACKAGE_NAME condition resolves against notification packageName`() {
        // Given: a notification with distinct package name
        val notification = createTestNotification(packageName = "com.unique.marker")
        val condition = createTestCondition(
            condition = MatchingCondition.PACKAGE_NAME,
            operator = MatchingOperator.EQUALS,
            value = "com.unique.marker",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches against the packageName field
        result shouldBe true
    }

    @Test
    fun `RAW_CONTENT condition resolves against notification rawContent`() {
        // Given: a notification with distinct raw content
        val notification = createTestNotification(rawContent = "unique-raw-marker")
        val condition = createTestCondition(
            condition = MatchingCondition.RAW_CONTENT,
            operator = MatchingOperator.EQUALS,
            value = "unique-raw-marker",
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the rule matches against the rawContent field
        result shouldBe true
    }

    // endregion

    // region DayOfWeekCondition

    @Test
    fun `single day matches when the current day equals it`() {
        // Given: a DayOfWeekCondition containing only MONDAY, evaluated on a Monday
        val notification = createTestNotification()
        val condition = createTestDayOfWeekCondition(days = setOf(DayOfWeek.MONDAY))

        // When: matching at a fixed Monday instant
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the condition matches
        result shouldBe true
    }

    @Test
    fun `multi-day set matches any listed day`() {
        // Given: a DayOfWeekCondition containing SATURDAY and SUNDAY, evaluated on a Sunday
        val notification = createTestNotification()
        val condition = createTestDayOfWeekCondition(days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val sunday = LocalDateTime.of(LocalDate.of(2026, 7, 12), LocalTime.of(9, 0))

        // When: matching at a fixed Sunday instant
        val result = RuleMatcher.matches(notification, listOf(condition), sunday)

        // Then: the condition matches
        result shouldBe true
    }

    @Test
    fun `non-listed day does not match`() {
        // Given: a DayOfWeekCondition containing only weekday values, evaluated on a Saturday
        val notification = createTestNotification()
        val condition = createTestDayOfWeekCondition(
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        val saturday = LocalDateTime.of(LocalDate.of(2026, 7, 11), LocalTime.of(9, 0))

        // When: matching at a fixed Saturday instant
        val result = RuleMatcher.matches(notification, listOf(condition), saturday)

        // Then: the condition does not match
        result shouldBe false
    }

    @Test
    fun `empty day set never matches, regardless of the current day`() {
        // Given: a DayOfWeekCondition with an empty days set
        val notification = createTestNotification()
        val condition = createTestDayOfWeekCondition(days = emptySet())

        // When: matching at any instant
        val result = RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)

        // Then: the condition never matches (fail-closed, not "match every day")
        result shouldBe false
    }

    @Test
    fun `every individual day of week is a valid selector`() {
        // Given: one DayOfWeekCondition per single day
        DayOfWeek.entries.forEach { day ->
            val notification = createTestNotification()
            val condition = createTestDayOfWeekCondition(days = setOf(day))
            val instantOnThatDay = FIXED_NOW.with(java.time.temporal.TemporalAdjusters.nextOrSame(day))

            // When: matching on that exact day
            val result = RuleMatcher.matches(notification, listOf(condition), instantOnThatDay)

            // Then: it matches only that day
            result shouldBe true
        }
    }

    // endregion

    // region TimeRangeCondition

    @Test
    fun `same-day range matches within bounds`() {
        // Given: a 09:00-17:00 range and the current time is 12:00
        val notification = createTestNotification()
        val condition = createTestTimeRangeCondition(start = LocalTime.of(9, 0), end = LocalTime.of(17, 0))
        val noon = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(12, 0))

        // When: matching
        val result = RuleMatcher.matches(notification, listOf(condition), noon)

        // Then: the condition matches
        result shouldBe true
    }

    @Test
    fun `boundaries are inclusive on both ends`() {
        // Given: a 09:00-17:00 range
        val notification = createTestNotification()
        val condition = createTestTimeRangeCondition(start = LocalTime.of(9, 0), end = LocalTime.of(17, 0))
        val atStart = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(9, 0))
        val atEnd = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(17, 0))

        // When/Then: matching exactly at start and exactly at end both match
        RuleMatcher.matches(notification, listOf(condition), atStart) shouldBe true
        RuleMatcher.matches(notification, listOf(condition), atEnd) shouldBe true
    }

    @Test
    fun `time outside a same-day range does not match`() {
        // Given: a 09:00-17:00 range and the current time is 08:59
        val notification = createTestNotification()
        val condition = createTestTimeRangeCondition(start = LocalTime.of(9, 0), end = LocalTime.of(17, 0))
        val beforeStart = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(8, 59))

        // When: matching
        val result = RuleMatcher.matches(notification, listOf(condition), beforeStart)

        // Then: the condition does not match
        result shouldBe false
    }

    @Test
    fun `overnight range matches both sides of midnight`() {
        // Given: a 22:00-06:00 overnight range
        val notification = createTestNotification()
        val condition = createTestTimeRangeCondition(start = LocalTime.of(22, 0), end = LocalTime.of(6, 0))
        val lateNight = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(23, 30))
        val earlyMorning = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(5, 0))

        // When/Then: both sides of midnight match
        RuleMatcher.matches(notification, listOf(condition), lateNight) shouldBe true
        RuleMatcher.matches(notification, listOf(condition), earlyMorning) shouldBe true
    }

    @Test
    fun `overnight range excludes the daytime gap`() {
        // Given: a 22:00-06:00 overnight range and the current time is 12:00
        val notification = createTestNotification()
        val condition = createTestTimeRangeCondition(start = LocalTime.of(22, 0), end = LocalTime.of(6, 0))
        val noon = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(12, 0))

        // When: matching
        val result = RuleMatcher.matches(notification, listOf(condition), noon)

        // Then: the condition does not match
        result shouldBe false
    }

    @Test
    fun `degenerate zero-duration range matches only its exact instant`() {
        // Given: a TimeRangeCondition with start == end == 08:00
        val notification = createTestNotification()
        val condition = createTestTimeRangeCondition(start = LocalTime.of(8, 0), end = LocalTime.of(8, 0))
        val exactInstant = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(8, 0))
        val oneMinuteLater = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(8, 1))

        // When/Then: only the exact instant matches
        RuleMatcher.matches(notification, listOf(condition), exactInstant) shouldBe true
        RuleMatcher.matches(notification, listOf(condition), oneMinuteLater) shouldBe false
    }

    // endregion

    // region Mixed-family combination and total evaluation

    @Test
    fun `rule with day, time, and content conditions all satisfied matches`() {
        // Given: a matching DayOfWeekCondition, TimeRangeCondition, and ContentMatchCondition
        val notification = createTestNotification(title = "Hello World")
        val dayCondition = createTestDayOfWeekCondition(days = setOf(DayOfWeek.MONDAY))
        val timeCondition = createTestTimeRangeCondition(start = LocalTime.of(9, 0), end = LocalTime.of(17, 0))
        val contentCondition = createTestCondition(condition = MatchingCondition.TITLE, operator = MatchingOperator.CONTAINS, value = "Hello")

        // When: matching at a fixed Monday-noon instant
        val result = RuleMatcher.matches(notification, listOf(dayCondition, timeCondition, contentCondition), FIXED_NOW)

        // Then: the rule matches
        result shouldBe true
    }

    @Test
    fun `rule fails to match if any one condition fails, even mixed-family`() {
        // Given: the same three conditions, but the current time falls outside the TimeRangeCondition
        val notification = createTestNotification(title = "Hello World")
        val dayCondition = createTestDayOfWeekCondition(days = setOf(DayOfWeek.MONDAY))
        val timeCondition = createTestTimeRangeCondition(start = LocalTime.of(9, 0), end = LocalTime.of(17, 0))
        val contentCondition = createTestCondition(condition = MatchingCondition.TITLE, operator = MatchingOperator.CONTAINS, value = "Hello")
        val outsideTimeRange = LocalDateTime.of(LocalDate.of(2026, 7, 6), LocalTime.of(20, 0))

        // When: matching outside the time range
        val result = RuleMatcher.matches(notification, listOf(dayCondition, timeCondition, contentCondition), outsideTimeRange)

        // Then: the rule does not match, even though day and content conditions are satisfied
        result shouldBe false
    }

    @Test
    fun `evaluation never throws for any sealed member`() {
        // Given: one condition of each sealed subtype
        val notification = createTestNotification()
        val conditions: List<RuleCondition> = listOf(
            createTestCondition(),
            createTestDayOfWeekCondition(),
            createTestTimeRangeCondition(),
        )

        // When/Then: evaluating each individually never throws and always resolves to a boolean
        conditions.forEach { condition ->
            RuleMatcher.matches(notification, listOf(condition), FIXED_NOW)
        }
    }

    // endregion
}
