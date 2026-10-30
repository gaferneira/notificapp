package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.converter.RuleTypeConverters
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Stored values that no longer map to a known enum/sealed entry must degrade, never throw. */
class TolerantDecodingTest {

    @Test
    fun `unknown action type is skipped and known actions still load`() {
        val entities = listOf(
            RuleActionEntity(id = "a1", ruleId = "r", type = "REMOVED_ACTION"),
            RuleActionEntity(id = "a2", ruleId = "r", type = ActionType.SAVE_DATA.name),
        )

        RuleActionMapper.toDomainList(entities).map { it.id } shouldBe listOf("a2")
    }

    @Test
    fun `unknown condition type decodes to a never-matching placeholder keeping its id`() {
        val entity = RuleConditionEntity(id = "c1", ruleId = "r", payload = """{"type":"battery_level","id":"c1"}""")

        RuleConditionMapper.toDomain(entity) shouldBe RuleCondition.DayOfWeekCondition(id = "c1", days = emptySet())
    }

    @Test
    fun `unknown enum name inside a known condition type decodes to the placeholder`() {
        val payload = """{"type":"content_match","id":"c1","condition":"GONE","operator":"CONTAINS","value":"x"}"""

        RuleConditionMapper.toDomain(RuleConditionEntity(id = "c1", ruleId = "r", payload = payload)) shouldBe
            RuleCondition.DayOfWeekCondition(id = "c1", days = emptySet())
    }

    @Test
    fun `malformed condition payload decodes to the placeholder`() {
        RuleConditionMapper.toDomain(RuleConditionEntity(id = "c1", ruleId = "r", payload = "not json")) shouldBe
            RuleCondition.DayOfWeekCondition(id = "c1", days = emptySet())
    }

    @Test
    fun `unknown field type on a rule field falls back to STRING`() {
        val entity = RuleFieldEntity(
            id = "f1",
            actionId = "a1",
            name = "n",
            fieldType = "DURATION",
            methodConfig = """{"type":"smart_code"}""",
        )

        RuleFieldMapper.toDomain(entity).fieldType shouldBe RuleField.FieldType.STRING
    }

    @Test
    fun `unknown field type snapshot on an extracted value falls back to STRING`() {
        val entity = ExtractedFieldValueEntity(
            id = "v1",
            ruleExecutionId = "e1",
            ruleFieldId = null,
            fieldName = "n",
            fieldType = "DURATION",
            valueText = "x",
            valueNumber = null,
            valueEpochDay = null,
        )

        ExtractedFieldValueMapper.toDomain(entity).fieldType shouldBe RuleField.FieldType.STRING
    }

    @Test
    fun `unknown field type in the raw-query converter falls back to STRING`() {
        RuleTypeConverters().toFieldType("DURATION") shouldBe RuleField.FieldType.STRING
    }

    @Test
    fun `unknown action outcome drops only that entry`() {
        val raw = """{"a1":"SUCCESS","a2":"EXPLODED","a3":"FAILED"}"""

        RuleExecutionMapper.decodeActionOutcomes(raw) shouldBe mapOf("a1" to ActionOutcome.SUCCESS, "a3" to ActionOutcome.FAILED)
    }

    @Test
    fun `execution with an unknown outcome still loads`() {
        val entity = RuleExecutionEntity(
            id = "e1",
            notificationId = "n1",
            ruleId = null,
            ruleName = "r",
            triggeredActions = "[]",
            actionOutcomes = """{"a1":"EXPLODED","a2":"SUPPRESSED"}""",
            createdAt = 1L,
        )

        RuleExecutionMapper.toDomain(entity).actionOutcomes shouldBe mapOf("a2" to ActionOutcome.SUPPRESSED)
    }
}
