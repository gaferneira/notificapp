package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleSummary.AppScope
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleSummary.ConditionMatch
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test

class RuleSummaryTest {

    @Test
    fun `an empty draft summarises as any app, no conditions and no actions`() {
        val summary = RuleUiModel().toSummary()

        summary.scope shouldBe AppScope.AllApps
        summary.conditionMatch shouldBe ConditionMatch.NONE
        summary.actions shouldBe emptyList()
    }

    @Test
    fun `included apps are listed by display name with a package fallback`() {
        val rule = RuleUiModel(
            targetApps = persistentListOf(AppInfo("com.a", "App A"), AppInfo("com.b", "")),
        )

        rule.toSummary().scope shouldBe AppScope.Only(listOf("App A", "com.b"))
    }

    @Test
    fun `exclude mode produces an except scope`() {
        val rule = RuleUiModel(
            targetApps = persistentListOf(AppInfo("com.a", "App A")),
            isIncludeMode = false,
        )

        rule.toSummary().scope shouldBe AppScope.Except(listOf("App A"))
    }

    @Test
    fun `exclude mode without apps still means any app`() {
        RuleUiModel(isIncludeMode = false).toSummary().scope shouldBe AppScope.AllApps
    }

    @Test
    fun `one condition is described as a single match regardless of the combinator`() {
        val rule = RuleUiModel(
            triggers = persistentListOf(createTestCondition(id = "c1")),
            conditionLogic = ConditionCombinator.ANY,
        )

        rule.toSummary().conditionMatch shouldBe ConditionMatch.SINGLE
    }

    @Test
    fun `several conditions follow the combinator`() {
        val triggers = persistentListOf(createTestCondition(id = "c1"), createTestCondition(id = "c2"))

        RuleUiModel(triggers = triggers, conditionLogic = ConditionCombinator.ALL).toSummary().conditionMatch shouldBe ConditionMatch.ALL
        RuleUiModel(triggers = triggers, conditionLogic = ConditionCombinator.ANY).toSummary().conditionMatch shouldBe ConditionMatch.ANY
    }

    @Test
    fun `only enabled actions appear in the summary, in rule order`() {
        val rule = RuleUiModel(
            actions = persistentListOf(
                createTestAction(id = "a1", type = ActionType.DISMISS_NOTIFICATION),
                createTestAction(id = "a2", type = ActionType.SNOOZE_NOTIFICATION).copy(isEnabled = false),
                createTestAction(id = "a3", type = ActionType.SAVE_DATA),
            ),
        )

        rule.toSummary().actions shouldBe listOf(ActionType.DISMISS_NOTIFICATION, ActionType.SAVE_DATA)
    }
}
