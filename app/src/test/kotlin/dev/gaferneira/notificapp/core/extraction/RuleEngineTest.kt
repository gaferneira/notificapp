package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleMatch
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleEngineTest {

    private val ruleEngine = RuleEngine()

    @Test
    fun `rule with matching conditions produces a match with extracted data`() {
        // Given: a notification and a rule whose condition matches, with an extractable field
        val notification = createTestNotification(
            title = "ICA Kvantum",
            content = "Totalt: 153,50 kr",
            rawContent = "ICA Kvantum\nTotalt: 153,50 kr",
        )
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val field = createTestField(
            id = "amount",
            method = RuleField.ExtractionMethod.TextAfterKeyword(keyword = "Totalt: "),
        )
        val saveDataAction = createTestAction(type = ActionType.SAVE_DATA, fields = listOf(field))
        val rule = createTestRule(conditions = listOf(condition), actions = listOf(saveDataAction))

        // When: evaluating the notification against the rule
        val result = ruleEngine.evaluate(notification, listOf(rule))

        // Then: a single match is produced with the extracted field value
        result shouldBe listOf(RuleMatch(rule, mapOf("amount" to "153,50 kr")))
    }

    @Test
    fun `rule with non-matching conditions produces no match`() {
        // Given: a notification and a rule whose condition does not match
        val notification = createTestNotification(title = "Some other app")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val rule = createTestRule(conditions = listOf(condition))

        // When: evaluating the notification against the rule
        val result = ruleEngine.evaluate(notification, listOf(rule))

        // Then: no match is produced
        result shouldBe emptyList()
    }

    @Test
    fun `required field that fails to extract still produces a match with the field omitted`() {
        // Given: a rule whose condition matches, but whose required field cannot be extracted
        val notification = createTestNotification(
            title = "ICA Kvantum",
            content = "No amount here",
            rawContent = "ICA Kvantum\nNo amount here",
        )
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val requiredField = createTestField(
            id = "amount",
            method = RuleField.ExtractionMethod.TextAfterKeyword(keyword = "Totalt: "),
            isRequired = true,
        )
        val saveDataAction = createTestAction(type = ActionType.SAVE_DATA, fields = listOf(requiredField))
        val rule = createTestRule(conditions = listOf(condition), actions = listOf(saveDataAction))

        // When: evaluating the notification against the rule
        val result = ruleEngine.evaluate(notification, listOf(rule))

        // Then: the rule still matches, with an empty extractedData map (a failed required field
        // only logs a warning, per RuleEngine.extractFields - it does not block the match)
        result.size shouldBe 1
        result[0].rule shouldBe rule
        result[0].extractedData shouldBe emptyMap()
    }

    @Test
    fun `rule with zero fields still matches when conditions pass`() {
        // Given: a rule with matching conditions and no fields defined
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val rule = createTestRule(conditions = listOf(condition))

        // When: evaluating the notification against the rule
        val result = ruleEngine.evaluate(notification, listOf(rule))

        // Then: a single match is produced with no extracted data
        result.size shouldBe 1
        result[0].extractedData shouldBe emptyMap()
    }

    @Test
    fun `rule with no SAVE_DATA action extracts nothing`() {
        // Given: a rule whose conditions match but has no SAVE_DATA action at all
        val notification = createTestNotification(title = "ICA Kvantum", content = "Totalt: 153,50 kr")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val dismiss = createTestAction(type = ActionType.DISMISS_NOTIFICATION)
        val rule = createTestRule(conditions = listOf(condition), actions = listOf(dismiss))

        // When: evaluating the notification against the rule
        val result = ruleEngine.evaluate(notification, listOf(rule))

        // Then: the rule matches but nothing is extracted - fields are sourced from saveDataFields()
        result.size shouldBe 1
        result[0].extractedData shouldBe emptyMap()
    }

    @Test
    fun `evaluate returns one match per matching rule preserving order`() {
        // Given: three rules - matching, non-matching, matching (in that order)
        val notification = createTestNotification(title = "ICA Kvantum", content = "Totalt: 153,50 kr")
        val matchingCondition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val nonMatchingCondition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "Nope",
        )
        val firstRule = createTestRule(id = "rule-1", conditions = listOf(matchingCondition))
        val secondRule = createTestRule(id = "rule-2", conditions = listOf(nonMatchingCondition))
        val thirdRule = createTestRule(id = "rule-3", conditions = listOf(matchingCondition))

        // When: evaluating the notification against all three rules
        val result = ruleEngine.evaluate(notification, listOf(firstRule, secondRule, thirdRule))

        // Then: only the matching rules produce a match, in the original order
        result.map { it.rule.id } shouldBe listOf("rule-1", "rule-3")
    }
}
