package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.saveDataFields
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Test

class RuleUiModelTest {

    private val field = createTestField(method = RuleField.ExtractionMethod.RegexPattern("\\d+"))

    @Test
    fun `toEntity attaches draft fields to a newly created SAVE_DATA action`() {
        // Given: a draft with fields but no SAVE_DATA action yet
        val uiModel = RuleUiModel(
            name = "Test Rule",
            actions = persistentListOf(),
            fields = persistentListOf(field),
        )

        // When: mapping to the domain entity
        val rule = uiModel.toEntity()

        // Then: a SAVE_DATA action was created carrying the draft fields
        val saveDataAction = rule.actions.single { it.type == ActionType.SAVE_DATA }
        saveDataAction.fields shouldBe listOf(field)
        rule.saveDataFields() shouldBe listOf(field)
    }

    @Test
    fun `toEntity attaches draft fields to an existing SAVE_DATA action without duplicating it`() {
        // Given: a draft with an existing SAVE_DATA action and updated fields
        val existingSaveData = createTestAction(id = "save-1", type = ActionType.SAVE_DATA)
        val uiModel = RuleUiModel(
            name = "Test Rule",
            actions = persistentListOf(existingSaveData),
            fields = persistentListOf(field),
        )

        // When: mapping to the domain entity
        val rule = uiModel.toEntity()

        // Then: the same action carries the fields, no duplicate SAVE_DATA action is created
        rule.actions shouldHaveSize 1
        rule.actions.single().id shouldBe "save-1"
        rule.actions.single().fields shouldBe listOf(field)
    }

    @Test
    fun `toEntity does not create a SAVE_DATA action when there are no fields`() {
        // Given: a draft with no fields and no actions
        val uiModel = RuleUiModel(name = "Test Rule", actions = persistentListOf(), fields = persistentListOf())

        // When: mapping to the domain entity
        val rule = uiModel.toEntity()

        // Then: no SAVE_DATA action is created
        rule.actions shouldBe emptyList()
    }

    @Test
    fun `fromDomain seeds the draft fields from the rule's SAVE_DATA action`() {
        // Given: a domain rule whose enabled SAVE_DATA action carries fields
        val saveDataAction = createTestAction(id = "save-1", type = ActionType.SAVE_DATA, fields = listOf(field))
        val rule = createTestRule(actions = listOf(saveDataAction))

        // When: creating the editor draft from the domain rule
        val uiModel = RuleUiModel.fromDomain(rule)

        // Then: the draft's flat fields list matches the action's fields
        uiModel.fields shouldBe listOf(field)
    }
}
