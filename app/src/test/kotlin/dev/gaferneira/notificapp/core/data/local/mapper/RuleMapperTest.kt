package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private val appA = AppInfo("com.a", "App A")

class RuleMapperTest {

    @Test
    fun `fields round-trip under the SAVE_DATA action`() {
        // Given: a rule with an enabled SAVE_DATA action carrying two fields, plus another action
        val fieldOne = createTestField(id = "f1", method = RuleField.ExtractionMethod.RegexPattern("\\d+"))
        val fieldTwo = createTestField(id = "f2", method = RuleField.ExtractionMethod.SmartAmountDetection)
        val saveDataAction = createTestAction(id = "save-1", type = ActionType.SAVE_DATA, fields = listOf(fieldOne, fieldTwo))
        val dismissAction = createTestAction(id = "dismiss-1", type = ActionType.DISMISS_NOTIFICATION)
        val rule = createTestRule(id = "rule-1", actions = listOf(saveDataAction, dismissAction))

        // When: mapping to entities and back (round-tripping through entity mapping)
        val entity = RuleMapper.toEntity(rule)
        val actionEntities = RuleMapper.actionsToEntityList(rule.actions, rule.id)
        val fieldEntities = RuleMapper.fieldsToEntityList(rule.actions)
        val domain = RuleMapper.toDomain(entity, fieldEntities, emptyList(), actionEntities)

        // Then: the loaded rule's SAVE_DATA action carries the same two fields
        val loadedSaveData = domain.actions.single { it.type == ActionType.SAVE_DATA }
        loadedSaveData.fields shouldBe listOf(fieldOne, fieldTwo)

        // And: no other action type carries any fields
        val loadedDismiss = domain.actions.single { it.type == ActionType.DISMISS_NOTIFICATION }
        loadedDismiss.fields.shouldBeEmpty()
    }

    @Test
    fun `isIncludeMode round-trips through entity mapping`() {
        // Given: an exclude-mode rule with target apps
        val rule = createTestRule(
            id = "rule-1",
            isIncludeMode = false,
            targetApps = listOf(appA),
        )

        // When: mapping to entity and back
        val entity = RuleMapper.toEntity(rule)
        val domain = RuleMapper.toDomain(entity, emptyList(), emptyList(), emptyList(), listOf(appA))

        // Then: the mode and target apps survive
        domain.isIncludeMode shouldBe false
        domain.targetApps shouldBe listOf(appA)
    }

    @Test
    fun `empty targetApps collapses to global rule`() {
        // Given: no rows in rule_target_apps for this rule
        val rule = createTestRule(id = "rule-1", isIncludeMode = false, targetApps = emptyList())
        val entity = RuleMapper.toEntity(rule)

        // When: mapping back to domain with an empty target-apps list
        val domain = RuleMapper.toDomain(entity, emptyList(), emptyList(), emptyList(), emptyList())

        // Then: it collapses to global (null targetApps, mode ignored)
        domain.targetApps shouldBe null
    }

    @Test
    fun `conditionLogic round-trips through entity mapping`() {
        // Given: a rule with ANY combinator
        val rule = createTestRule(id = "rule-1", conditionLogic = ConditionCombinator.ANY)

        // When: mapping to entity and back
        val entity = RuleMapper.toEntity(rule)
        val domain = RuleMapper.toDomain(entity, emptyList(), emptyList(), emptyList())

        // Then: the combinator survives the round trip
        domain.conditionLogic shouldBe ConditionCombinator.ANY
    }

    @Test
    fun `pre-existing entity rows default conditionLogic to ALL`() {
        // Given: an entity with no condition_logic value (simulating a pre-existing row)
        val rule = createTestRule(id = "rule-1")
        val entity = RuleMapper.toEntity(rule).copy(conditionLogic = "")

        // When: mapping back to domain
        val domain = RuleMapper.toDomain(entity, emptyList(), emptyList(), emptyList())

        // Then: it defaults to ALL
        domain.conditionLogic shouldBe ConditionCombinator.ALL
    }

    @Test
    fun `unknown conditionLogic string maps to ALL`() {
        // Given: an entity with a garbage condition_logic value
        val rule = createTestRule(id = "rule-1")
        val entity = RuleMapper.toEntity(rule).copy(conditionLogic = "GARBAGE")

        // When: mapping back to domain
        val domain = RuleMapper.toDomain(entity, emptyList(), emptyList(), emptyList())

        // Then: it safely defaults to ALL instead of throwing
        domain.conditionLogic shouldBe ConditionCombinator.ALL
    }
}
