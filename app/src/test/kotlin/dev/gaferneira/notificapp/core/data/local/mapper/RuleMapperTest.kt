package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestField
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleMapperTest {

    private val field = createTestField(method = RuleField.ExtractionMethod.RegexPattern("\\d+"))

    @Test
    fun `synthesizes an enabled SAVE_DATA action when fields exist and none is present`() {
        // Given: a legacy rule shape - fields but only a non-SAVE_DATA action
        val dismiss = createTestAction(id = "dismiss-1", type = ActionType.DISMISS_NOTIFICATION)

        // When: normalizing
        val result = RuleMapper.normalizeActions(listOf(dismiss), listOf(field))

        // Then: an enabled SAVE_DATA action is added alongside the original
        result shouldHaveSize 2
        val saveData = result.single { it.type == ActionType.SAVE_DATA }
        saveData.isEnabled shouldBe true
    }

    @Test
    fun `does not add SAVE_DATA action when there are no fields`() {
        val dismiss = createTestAction(id = "dismiss-1", type = ActionType.DISMISS_NOTIFICATION)

        val result = RuleMapper.normalizeActions(listOf(dismiss), emptyList())

        result shouldBe listOf(dismiss)
    }

    @Test
    fun `leaves a disabled SAVE_DATA action untouched even with fields`() {
        // A disabled SAVE_DATA action is a deliberate post-change state, not legacy decoupling
        val disabledSaveData = createTestAction(id = "save-1", type = ActionType.SAVE_DATA, isEnabled = false)

        val result = RuleMapper.normalizeActions(listOf(disabledSaveData), listOf(field))

        result shouldBe listOf(disabledSaveData)
    }
}
