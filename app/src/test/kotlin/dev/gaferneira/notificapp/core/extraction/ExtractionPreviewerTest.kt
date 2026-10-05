package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.FieldChange
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.ZoneOffset

class ExtractionPreviewerTest {

    private val previewer = ExtractionPreviewer(RuleEngine())
    private val notification = createTestNotification(id = "n1", title = "ICA Kvantum", rawContent = "ICA Kvantum 123")

    private val amountField = createTestField(
        id = "f-amount",
        name = "Amount",
        fieldType = RuleField.FieldType.NUMBER,
        method = RuleField.ExtractionMethod.RegexPattern("(\\d+)"),
    )

    private fun rule(
        id: String = "r1",
        fields: List<RuleField> = listOf(amountField),
        value: String = "ICA",
    ): Rule = createTestRule(
        id = id,
        name = "Rule $id",
        conditions = listOf(createTestCondition(condition = MatchingCondition.TITLE, operator = MatchingOperator.CONTAINS, value = value)),
        actions = listOf(
            createTestAction(id = "save-$id", type = ActionType.SAVE_DATA, fields = fields),
            createTestAction(id = "dismiss-$id", type = ActionType.DISMISS_NOTIFICATION),
            createTestAction(id = "off-$id", type = ActionType.CREATE_ALARM, isEnabled = false),
        ),
    )

    private fun execution(ruleId: String = "r1", data: Map<String, String>) = RuleExecution(
        id = "exec-$ruleId",
        notificationId = "n1",
        ruleId = ruleId,
        extractedData = data,
        triggeredActions = emptyList(),
        createdAt = 1L,
    )

    private fun preview(rules: List<Rule>, executions: List<RuleExecution>) = previewer.preview(notification, rules, executions, zone = ZoneOffset.UTC)

    @Test
    fun `identical stored and previewed values are unchanged and produce no update`() {
        val result = preview(listOf(rule()), listOf(execution(data = mapOf("f-amount" to "123"))))

        val match = result.matches.single()
        match.fieldDiffs.single().change shouldBe FieldChange.UNCHANGED
        match.update shouldBe null
        result.hasUpdatableChanges shouldBe false
    }

    @Test
    fun `a different stored value is reported as changed with old and new`() {
        val result = preview(listOf(rule()), listOf(execution(data = mapOf("f-amount" to "99"))))

        val diff = result.matches.single().fieldDiffs.single()
        diff.change shouldBe FieldChange.CHANGED
        diff.oldValue shouldBe "99"
        diff.newValue shouldBe "123"
        diff.fieldName shouldBe "Amount"
        diff.fieldType shouldBe RuleField.FieldType.NUMBER
        result.hasUpdatableChanges shouldBe true
    }

    @Test
    fun `a value that was never stored is reported as new`() {
        val result = preview(listOf(rule()), listOf(execution(data = emptyMap())))

        result.matches.single().fieldDiffs.single().change shouldBe FieldChange.NEW
        result.updates.single().extractedData shouldBe mapOf("f-amount" to "123")
    }

    @Test
    fun `a stored value the rule no longer extracts is reported as removed`() {
        // Given: the regex no longer finds digits
        val noDigits = createTestNotification(id = "n1", title = "ICA Kvantum", rawContent = "ICA Kvantum")
        val result = previewer.preview(noDigits, listOf(rule()), listOf(execution(data = mapOf("f-amount" to "123"))), zone = ZoneOffset.UTC)

        result.matches.single().fieldDiffs.single().change shouldBe FieldChange.REMOVED
        result.updates.single().extractedData shouldBe emptyMap()
    }

    @Test
    fun `a rule that matches but never ran is shown without any update`() {
        val result = preview(listOf(rule()), executions = emptyList())

        val match = result.matches.single()
        match.executionId shouldBe null
        match.fieldDiffs.single().change shouldBe FieldChange.NEW
        match.update shouldBe null
        result.hasUpdatableChanges shouldBe false
    }

    @Test
    fun `an execution whose rule no longer matches is listed and never updated`() {
        val result = preview(listOf(rule(value = "Coop")), listOf(execution(data = mapOf("f-amount" to "123"))))

        result.matches shouldBe emptyList()
        result.noLongerMatching.single().executionId shouldBe "exec-r1"
        result.hasUpdatableChanges shouldBe false
    }

    @Test
    fun `stored values of fields deleted from the rule are carried over, not dropped`() {
        val result = preview(
            listOf(rule()),
            listOf(execution(data = mapOf("f-amount" to "99", "f-deleted" to "old"))),
        )

        result.updates.single().extractedData shouldBe mapOf("f-deleted" to "old", "f-amount" to "123")
    }

    @Test
    fun `preview lists only enabled action types`() {
        val result = preview(listOf(rule()), emptyList())

        result.matches.single().actionTypes shouldBe listOf(ActionType.SAVE_DATA, ActionType.DISMISS_NOTIFICATION)
    }
}
