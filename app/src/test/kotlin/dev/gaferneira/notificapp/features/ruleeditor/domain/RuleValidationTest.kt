package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestField
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RuleValidationTest {

    private val app = AppInfo("com.a", "App A")
    private val condition = createTestCondition(value = "x")
    private val enabledAction = createTestAction(type = ActionType.DISMISS_NOTIFICATION)

    private fun rule(
        name: String = "Named",
        apps: List<AppInfo> = emptyList(),
        include: Boolean = true,
        withCondition: Boolean = true,
        actions: List<dev.gaferneira.notificapp.domain.model.RuleAction> = listOf(enabledAction),
    ) = RuleUiModel(
        name = name,
        targetApps = persistentListOf(*apps.toTypedArray()),
        isIncludeMode = include,
        triggers = if (withCondition) persistentListOf(condition) else persistentListOf(),
        actions = persistentListOf(*actions.toTypedArray()),
    )

    @Nested
    inner class Blocking {

        @Test
        fun `a complete rule has no issues and no warnings`() {
            val result = RuleValidation.evaluate(rule())

            result.blocking shouldBe emptyList()
            result.warnings shouldBe emptyList()
        }

        @Test
        fun `a blank name is required`() {
            RuleValidation.evaluate(rule(name = "   ")).blocking shouldBe listOf(EditorIssue.NAME_REQUIRED)
        }

        @Test
        fun `no enabled action blocks, whether there are none or all are disabled`() {
            RuleValidation.evaluate(rule(actions = emptyList())).blocking shouldBe listOf(EditorIssue.ACTION_REQUIRED)
            RuleValidation.evaluate(rule(actions = listOf(enabledAction.copy(isEnabled = false)))).blocking shouldBe
                listOf(EditorIssue.ACTION_REQUIRED)
        }

        @Test
        fun `one enabled action among disabled ones is enough`() {
            val actions = listOf(enabledAction.copy(id = "off", isEnabled = false), enabledAction)

            RuleValidation.evaluate(rule(actions = actions)).blocking shouldBe emptyList()
        }

        @Test
        fun `draft fields without an extract action count as an enabled action`() {
            val withFields = rule(actions = emptyList()).copy(
                fields = persistentListOf(createTestField(method = ExtractionMethod.SmartAmountDetection)),
            )

            RuleValidation.evaluate(withFields).blocking shouldBe emptyList()
        }

        @Test
        fun `fields next to a disabled extract action do not count`() {
            val disabledExtract = createTestAction(type = ActionType.SAVE_DATA, isEnabled = false)
            val withFields = rule(actions = listOf(disabledExtract)).copy(
                fields = persistentListOf(createTestField(method = ExtractionMethod.SmartAmountDetection)),
            )

            RuleValidation.evaluate(withFields).blocking shouldBe listOf(EditorIssue.ACTION_REQUIRED)
        }

        @Test
        fun `all apps with no conditions is too broad`() {
            RuleValidation.evaluate(rule(withCondition = false)).blocking shouldBe listOf(EditorIssue.RULE_TOO_BROAD)
        }

        @Test
        fun `all apps except some with no conditions is too broad`() {
            RuleValidation.evaluate(rule(apps = listOf(app), include = false, withCondition = false)).blocking shouldBe
                listOf(EditorIssue.RULE_TOO_BROAD)
        }

        @Test
        fun `a condition makes any app scope acceptable`() {
            RuleValidation.evaluate(rule()).blocking shouldBe emptyList()
            RuleValidation.evaluate(rule(apps = listOf(app), include = false)).blocking shouldBe emptyList()
            RuleValidation.evaluate(rule(apps = listOf(app))).blocking shouldBe emptyList()
        }

        @Test
        fun `specific included apps without conditions are allowed`() {
            RuleValidation.evaluate(rule(apps = listOf(app), withCondition = false)).blocking shouldBe emptyList()
        }

        @Test
        fun `every problem is reported in single page order`() {
            val result = RuleValidation.evaluate(rule(name = "", withCondition = false, actions = emptyList()))

            result.blocking shouldBe listOf(EditorIssue.NAME_REQUIRED, EditorIssue.RULE_TOO_BROAD, EditorIssue.ACTION_REQUIRED)
        }
    }

    @Nested
    inner class Warnings {

        @Test
        fun `specific included apps without conditions warn without blocking`() {
            val result = RuleValidation.evaluate(rule(apps = listOf(app), withCondition = false))

            result.warnings shouldBe listOf(EditorWarning.MATCHES_EVERY_NOTIFICATION_OF_APPS)
            result.blocking shouldBe emptyList()
        }

        @Test
        fun `no warning once a condition exists`() {
            RuleValidation.evaluate(rule(apps = listOf(app))).warnings shouldBe emptyList()
        }

        @Test
        fun `no warning for the blocked all-apps and exclude cases`() {
            RuleValidation.evaluate(rule(withCondition = false)).warnings shouldBe emptyList()
            RuleValidation.evaluate(rule(apps = listOf(app), include = false, withCondition = false)).warnings shouldBe emptyList()
        }

        @Test
        fun `a destructive action or extract with delete raw produce no warning`() {
            val dismiss = rule(actions = listOf(createTestAction(type = ActionType.DISMISS_NOTIFICATION)))
            val extract = rule(actions = listOf(createTestAction(type = ActionType.SAVE_DATA))).copy(deleteRawContentAfterExtraction = true)

            RuleValidation.evaluate(dismiss).warnings shouldBe emptyList()
            RuleValidation.evaluate(extract).warnings shouldBe emptyList()
        }
    }

    @Test
    fun `each issue belongs to the step where it is fixed`() {
        EditorIssue.RULE_TOO_BROAD.step shouldBe EditorStep.WHEN
        EditorIssue.ACTION_REQUIRED.step shouldBe EditorStep.DO
        EditorIssue.NAME_REQUIRED.step shouldBe EditorStep.REVIEW
    }
}
