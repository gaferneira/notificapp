package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
}
