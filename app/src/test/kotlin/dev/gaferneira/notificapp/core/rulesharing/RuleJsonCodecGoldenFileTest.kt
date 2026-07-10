package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.RuleField.FieldType
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Locks the rule export wire format to a checked-in golden file. Any change to
 * [RuleJsonCodec.encode]'s output - a rename, a default change, a new required field - fails this
 * test instead of silently breaking every rule file already exported by users.
 *
 * The fixture below deliberately exercises every action type, every extraction method, and every
 * condition operator, so a wire-format regression in any one of them shows up here.
 */
class RuleJsonCodecGoldenFileTest {

    private val goldenFileResourceV1 = "/rule-export-v1.json"

    private val fixtureFields = listOf(
        createTestField(id = "field-fixed", name = "Fixed Position", fieldType = FieldType.STRING, method = ExtractionMethod.FixedPosition(startIndex = 0, endIndex = 10)),
        createTestField(id = "field-anchors", name = "Between Anchors", fieldType = FieldType.STRING, method = ExtractionMethod.TextBetweenAnchors(startAnchor = "Total: ", endAnchor = " kr")),
        createTestField(id = "field-regex", name = "Regex", fieldType = FieldType.STRING, method = ExtractionMethod.RegexPattern(pattern = "(\\d+)", captureGroup = 1)),
        createTestField(id = "field-after", name = "After Keyword", fieldType = FieldType.CURRENCY, method = ExtractionMethod.TextAfterKeyword(keyword = "Total: ", maxLength = 20)),
        createTestField(id = "field-before", name = "Before Keyword", fieldType = FieldType.STRING, method = ExtractionMethod.TextBeforeKeyword(keyword = " kr")),
        createTestField(id = "field-line", name = "Line", fieldType = FieldType.STRING, method = ExtractionMethod.LineExtraction(lineNumber = 2)),
        createTestField(id = "field-split", name = "Split", fieldType = FieldType.NUMBER, method = ExtractionMethod.SplitByDelimiter(delimiter = ",", takeIndex = 0)),
        createTestField(id = "field-json", name = "Json Path", fieldType = FieldType.STRING, method = ExtractionMethod.JsonPath(path = "$.amount")),
        createTestField(id = "field-smart-amount", name = "Smart Amount", fieldType = FieldType.CURRENCY, method = ExtractionMethod.SmartAmountDetection),
        createTestField(id = "field-smart-date", name = "Smart Date", fieldType = FieldType.DATE, method = ExtractionMethod.SmartDateDetection),
    )

    private val fixtureRule = createTestRule(
        id = "fixture-rule-id",
        name = "Golden Fixture Rule",
        description = "Exercises every action type, extraction method, and operator",
        category = "Testing",
        isActive = true,
        isDryRun = false,
        targetApps = listOf(
            AppInfo(packageName = "com.bank.example", name = "Example Bank", category = "Finance"),
            AppInfo(packageName = "com.example.other", name = "Other App"),
        ),
        conditions = listOf(
            createTestCondition(id = "cond-contains", condition = MatchingCondition.TEXT_CONTENT, operator = MatchingOperator.CONTAINS, value = "Total"),
            createTestCondition(id = "cond-starts", condition = MatchingCondition.TITLE, operator = MatchingOperator.STARTS_WITH, value = "Payment"),
            createTestCondition(id = "cond-ends", condition = MatchingCondition.APP_NAME, operator = MatchingOperator.ENDS_WITH, value = "Bank"),
            createTestCondition(id = "cond-equals", condition = MatchingCondition.PACKAGE_NAME, operator = MatchingOperator.EQUALS, value = "com.bank.example"),
            createTestCondition(id = "cond-regex", condition = MatchingCondition.RAW_CONTENT, operator = MatchingOperator.REGEX_MATCH, value = "\\d+"),
            createTestCondition(id = "cond-not-contains", condition = MatchingCondition.TEXT_CONTENT, operator = MatchingOperator.NOT_CONTAINS, value = "spam"),
        ),
        actions = listOf(
            createTestAction(id = "action-save", type = ActionType.SAVE_DATA, fields = fixtureFields),
            createTestAction(id = "action-dismiss", type = ActionType.DISMISS_NOTIFICATION),
            createTestAction(id = "action-snooze", type = ActionType.SNOOZE_NOTIFICATION, config = mapOf("snooze_duration_minutes" to "30")),
            createTestAction(
                id = "action-alarm",
                type = ActionType.CREATE_ALARM,
                config = mapOf("alarm_sound_uri" to "content://media/alarm/1", "alarm_vibration_enabled" to "true"),
            ),
            createTestAction(
                id = "action-flash",
                type = ActionType.FLASH_ALERT,
                config = mapOf("flash_count" to "5", "flash_duration_ms" to "300"),
            ),
        ),
        createdAt = 1_751_500_000_000L,
        updatedAt = 1_751_500_000_000L,
    )

    private fun readResource(name: String): String = checkNotNull(javaClass.getResource(name)) {
        "Golden file resource not found: $name"
    }.readText()

    @Test
    fun `encoding the fixture rule matches the checked-in golden file`() {
        // When: encoding the fixture rule
        val encoded = RuleJsonCodec.encode(fixtureRule)

        // Then: it matches the golden file byte-for-byte
        encoded shouldBe readResource(goldenFileResourceV1)
    }

    @Test
    fun `decoding the golden file round-trips back to the fixture rule`() {
        // When: decoding the golden file
        val decoded = RuleJsonCodec.decode(readResource(goldenFileResourceV1))

        // Then: it reconstructs the fixture rule exactly, with no actions skipped
        decoded.isSuccess shouldBe true
        val result = decoded.getOrThrow()
        result.skippedActions shouldBe emptyList()
        result.rule shouldBe fixtureRule
    }
}
