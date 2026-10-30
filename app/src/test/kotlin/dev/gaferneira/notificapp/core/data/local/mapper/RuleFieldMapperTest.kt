package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test

/**
 * Verifies every [ExtractionMethod] round-trips through the `method_config` column via
 * `ExtractionMethodDto`, stored with the default `type` discriminator.
 */
class RuleFieldMapperTest {

    private val actionId = "action-1"

    private val methods: List<Pair<ExtractionMethod, String>> = listOf(
        ExtractionMethod.FixedPosition(2, 8) to "fixed_position",
        ExtractionMethod.TextBetweenAnchors("from ", " to") to "text_between_anchors",
        ExtractionMethod.RegexPattern("(\\d+)", captureGroup = 2) to "regex",
        ExtractionMethod.TextAfterKeyword("Total:", maxLength = 12) to "text_after_keyword",
        ExtractionMethod.TextAfterKeyword("Total:") to "text_after_keyword",
        ExtractionMethod.TextBeforeKeyword("EUR") to "text_before_keyword",
        ExtractionMethod.LineExtraction(3) to "line_extraction",
        ExtractionMethod.SplitByDelimiter("|", 1) to "split_by_delimiter",
        ExtractionMethod.JsonPath("$.order.id") to "json_path",
        ExtractionMethod.SmartAmountDetection to "smart_amount",
        ExtractionMethod.SmartDateDetection to "smart_date",
        ExtractionMethod.SmartCodeDetection to "smart_code",
    )

    private fun field(method: ExtractionMethod) = RuleField(
        id = "f1",
        name = "Amount",
        fieldType = RuleField.FieldType.CURRENCY,
        method = method,
        isRequired = true,
    )

    @Test
    fun `every extraction method round-trips through storage`() {
        methods.forEach { (method, _) ->
            val field = field(method)

            val loaded = RuleFieldMapper.toDomain(RuleFieldMapper.toEntity(field, actionId))

            loaded shouldBe field
        }
    }

    @Test
    fun `stored payload is discriminated by type only, with no classType`() {
        methods.forEach { (method, discriminator) ->
            val entity = RuleFieldMapper.toEntity(field(method), actionId)

            entity.methodConfig shouldContain "\"type\":\"$discriminator\""
            entity.methodConfig shouldNotContain "classType"
            entity.actionId shouldBe actionId
        }
    }

    @Test
    fun `an unknown method type is skipped from the list instead of failing the rule load`() {
        val known = RuleFieldMapper.toEntity(field(ExtractionMethod.RegexPattern("\\d+")), actionId)
        val unknown = known.copy(id = "f-future", methodConfig = """{"type":"from_the_future","x":1}""")
        val malformed = known.copy(id = "f-broken", methodConfig = "not json")

        val loaded = RuleFieldMapper.toDomainList(listOf(unknown, known, malformed))

        loaded.map { it.id } shouldBe listOf("f1")
    }

    @Test
    fun `payloads written with unknown extra keys still decode`() {
        val entity = RuleFieldEntity(
            id = "f1",
            actionId = actionId,
            name = "Code",
            fieldType = "STRING",
            methodConfig = """{"type":"regex","pattern":"(\\d+)","captureGroup":1,"extra":true}""",
        )

        RuleFieldMapper.toDomain(entity).method shouldBe ExtractionMethod.RegexPattern("(\\d+)")
    }
}
