package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleMatcherTest {

    // region General behavior

    @Test
    fun `empty condition list matches everything`() {
        // Given: a notification and no conditions
        val notification = createTestNotification()

        // When: matching with an empty condition list
        val result = RuleMatcher.matches(notification, emptyList())

        // Then: the notification matches
        result shouldBe true
    }

    @Test
    fun `null condition type never matches`() {
        // Given: a condition with no matching condition type set
        val notification = createTestNotification(title = "Hello World")
        val condition = createTestCondition(condition = null, operator = MatchingOperator.CONTAINS, value = "Hello")

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition))

        // Then: the condition does not match
        result shouldBe false
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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(matchingTitle, matchingContent))

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
        val result = RuleMatcher.matches(notification, listOf(matchingTitle, nonMatchingContent))

        // Then: the notification does not match
        result shouldBe false
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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

        // Then: the rule does not match
        result shouldBe false
    }

    @Test
    fun `EQUALS with a null condition value never matches`() {
        // Given: an EQUALS condition with no value configured, against a non-null field
        val notification = createTestNotification(appName = "Test App")
        val condition = createTestCondition(
            condition = MatchingCondition.APP_NAME,
            operator = MatchingOperator.EQUALS,
            value = null,
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, listOf(condition))

        // Then: the rule never matches, since a non-null field value can't equal null
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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

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
        val result = RuleMatcher.matches(notification, listOf(condition))

        // Then: the rule matches against the rawContent field
        result shouldBe true
    }

    // endregion
}
