package dev.gaferneira.notificapp.domain.model

import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleTest {

    private val field = createTestField(method = RuleField.ExtractionMethod.RegexPattern("\\d+"))

    @Test
    fun `saveDataFields returns empty when there is no SAVE_DATA action`() {
        val rule = createTestRule(actions = listOf(createTestAction(type = ActionType.DISMISS_NOTIFICATION)))

        rule.saveDataFields() shouldBe emptyList()
    }

    @Test
    fun `saveDataFields returns empty when the SAVE_DATA action is disabled`() {
        val rule = createTestRule(
            actions = listOf(createTestAction(type = ActionType.SAVE_DATA, isEnabled = false, fields = listOf(field))),
        )

        rule.saveDataFields() shouldBe emptyList()
    }

    @Test
    fun `saveDataFields returns the enabled SAVE_DATA action's fields`() {
        val rule = createTestRule(
            actions = listOf(createTestAction(type = ActionType.SAVE_DATA, isEnabled = true, fields = listOf(field))),
        )

        rule.saveDataFields() shouldBe listOf(field)
    }

    @Test
    fun `saveDataFields returns empty when there are no actions at all`() {
        val rule = createTestRule(actions = emptyList())

        rule.saveDataFields() shouldBe emptyList()
    }
}
