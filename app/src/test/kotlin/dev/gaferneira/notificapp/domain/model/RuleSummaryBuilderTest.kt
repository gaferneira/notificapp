package dev.gaferneira.notificapp.domain.model

import dev.gaferneira.notificapp.domain.model.RuleSummary.Action
import dev.gaferneira.notificapp.domain.model.RuleSummary.ActionRun
import dev.gaferneira.notificapp.domain.model.RuleSummary.AppScope
import dev.gaferneira.notificapp.domain.model.RuleSummary.ConditionMatch
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleSummaryBuilderTest {

    private val appA = AppInfo("com.a", "App A")
    private val appB = AppInfo("com.b", "")
    private val twoConditions = listOf(createTestCondition(id = "c1"), createTestCondition(id = "c2"))
    private val mixedActions = listOf(
        createTestAction(id = "a1", type = ActionType.DISMISS_NOTIFICATION),
        createTestAction(id = "a2", type = ActionType.SNOOZE_NOTIFICATION, isEnabled = false),
        createTestAction(id = "a3", type = ActionType.SAVE_DATA),
    )

    @Test
    fun `given no target apps when summarising then scope is all apps`() {
        createTestRule(targetApps = null).toSummary().scope shouldBe AppScope.AllApps
        createTestRule(targetApps = emptyList()).toSummary().scope shouldBe AppScope.AllApps
    }

    @Test
    fun `given include mode apps when summarising then scope is only those apps with name fallback`() {
        val scope = createTestRule(targetApps = listOf(appA, appB)).toSummary().scope

        scope shouldBe AppScope.Only(listOf(appA, appB))
        scope.appNames shouldBe listOf("App A", "com.b")
    }

    @Test
    fun `given exclude mode apps when summarising then scope is all apps except those`() {
        val scope = createTestRule(targetApps = listOf(appA), isIncludeMode = false).toSummary().scope

        scope shouldBe AppScope.Except(listOf(appA))
    }

    @Test
    fun `given exclude mode without apps when summarising then scope is all apps`() {
        createTestRule(isIncludeMode = false, targetApps = emptyList()).toSummary().scope shouldBe AppScope.AllApps
    }

    @Test
    fun `given no conditions when summarising then match is none`() {
        createTestRule().toSummary().conditionMatch shouldBe ConditionMatch.NONE
    }

    @Test
    fun `given one condition when summarising then match is single regardless of combinator`() {
        val rule = createTestRule(conditions = listOf(createTestCondition()), conditionLogic = ConditionCombinator.ANY)

        rule.toSummary().conditionMatch shouldBe ConditionMatch.SINGLE
    }

    @Test
    fun `given several conditions when summarising then match follows the combinator`() {
        createTestRule(conditions = twoConditions, conditionLogic = ConditionCombinator.ALL)
            .toSummary().conditionMatch shouldBe ConditionMatch.ALL
        createTestRule(conditions = twoConditions, conditionLogic = ConditionCombinator.ANY)
            .toSummary().conditionMatch shouldBe ConditionMatch.ANY
    }

    @Test
    fun `given conditions when summarising then they are carried over in order`() {
        createTestRule(conditions = twoConditions).toSummary().conditions shouldBe twoConditions
    }

    @Test
    fun `given mixed actions when summarising enabled only then disabled ones are dropped`() {
        val summary = createTestRule(actions = mixedActions).toSummary(includeDisabledActions = false)

        summary.actions shouldBe listOf(
            Action(ActionType.DISMISS_NOTIFICATION, isEnabled = true),
            Action(ActionType.SAVE_DATA, isEnabled = true),
        )
    }

    @Test
    fun `given mixed actions when summarising with disabled then all are listed in order and flagged`() {
        val summary = createTestRule(actions = mixedActions).toSummary(includeDisabledActions = true)

        summary.actions shouldBe listOf(
            Action(ActionType.DISMISS_NOTIFICATION, isEnabled = true),
            Action(ActionType.SNOOZE_NOTIFICATION, isEnabled = false),
            Action(ActionType.SAVE_DATA, isEnabled = true),
        )
    }

    @Test
    fun `given no actions when summarising then the action list is empty either way`() {
        createTestRule().toSummary(includeDisabledActions = false).actions shouldBe emptyList()
        createTestRule().toSummary(includeDisabledActions = true).actions shouldBe emptyList()
    }

    @Test
    fun `given a live rule with enabled actions when summarising then actions run`() {
        createTestRule(actions = mixedActions).toSummary().actionRun shouldBe ActionRun.RUN
    }

    @Test
    fun `given a dry run rule when summarising then matches are only recorded`() {
        createTestRule(isDryRun = true, actions = mixedActions).toSummary().actionRun shouldBe ActionRun.RECORD_ONLY
    }

    @Test
    fun `given only disabled actions when summarising with disabled then nothing runs but they are listed`() {
        val rule = createTestRule(actions = listOf(createTestAction(id = "a1", isEnabled = false)))

        val summary = rule.toSummary(includeDisabledActions = true)

        summary.actionRun shouldBe ActionRun.NONE
        summary.actions.size shouldBe 1
    }
}
