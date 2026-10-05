package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec.withFreshIdentityForImport
import dev.gaferneira.notificapp.core.rulesharing.dto.RULE_EXPORT_SCHEMA_VERSION
import dev.gaferneira.notificapp.core.rulesharing.dto.RuleExportDto
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.saveDataFields
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestDayOfWeekCondition
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestRule
import dev.gaferneira.notificapp.testutil.createTestTimeRangeCondition
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime

class RuleJsonCodecTest {

    private val fields = listOf(
        // A method with its own fields, not just a zero-argument "smart" one - regression
        // coverage for the classDiscriminator/property-name collision this DTO layer fixes.
        createTestField(id = "f1", name = "Amount", method = ExtractionMethod.TextAfterKeyword(keyword = "Total: ", maxLength = 20)),
    )

    private val rule = createTestRule(
        id = "rule-1",
        name = "Bank payment received",
        category = "Finance",
        targetApps = listOf(AppInfo("com.bank.example", "Example Bank")),
        conditions = listOf(
            createTestCondition(
                id = "c1",
                condition = MatchingCondition.TEXT_CONTENT,
                operator = MatchingOperator.CONTAINS,
                value = "Payment received",
            ),
        ),
        actions = listOf(createTestAction(id = "a1", type = ActionType.SAVE_DATA, fields = fields)),
    )

    @Test
    fun `encode then decode round-trips the rule's content`() {
        // When: encoding then decoding the same rule
        val encoded = RuleJsonCodec.encode(rule)
        val decoded = RuleJsonCodec.decode(encoded)

        // Then: decoding succeeds and every field survives the round trip
        decoded.isSuccess shouldBe true
        val decodedRule = decoded.getOrThrow().rule
        decodedRule.name shouldBe rule.name
        decodedRule.category shouldBe rule.category
        decodedRule.isIncludeMode shouldBe rule.isIncludeMode
        decodedRule.targetApps shouldBe rule.targetApps
        decodedRule.conditions shouldBe rule.conditions
        decodedRule.saveDataFields() shouldBe rule.saveDataFields()
        decodedRule.actions shouldBe rule.actions
    }

    @Test
    fun `export re-import round-trips a rule with mixed condition families`() {
        // Given: a rule with a content-match, a day-of-week, and a time-range condition
        val mixedRule = rule.copy(
            conditions = persistentListOf(
                createTestCondition(id = "c1", condition = MatchingCondition.TEXT_CONTENT, operator = MatchingOperator.CONTAINS, value = "Payment"),
                createTestDayOfWeekCondition(id = "c2", days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
                createTestTimeRangeCondition(id = "c3", start = LocalTime.of(22, 0), end = LocalTime.of(6, 0)),
            ),
        )

        // When: exporting then re-importing
        val encoded = RuleJsonCodec.encode(mixedRule)
        val decoded = RuleJsonCodec.decode(encoded)

        // Then: the re-imported rule's conditions match the originals in type and value
        decoded.isSuccess shouldBe true
        decoded.getOrThrow().rule.conditions shouldBe mixedRule.conditions
    }

    @Test
    fun `encoded JSON embeds the current schema version`() {
        // When: encoding a rule
        val encoded = RuleJsonCodec.encode(rule)

        // Then: the envelope carries the current schema version
        encoded shouldContain "\"schemaVersion\": $RULE_EXPORT_SCHEMA_VERSION"
    }

    @Test
    fun `exclude-mode rule round-trips isIncludeMode and targetApps`() {
        // Given: an exclude-mode rule with a target app
        val excludeRule = rule.copy(
            isIncludeMode = false,
            targetApps = persistentListOf(AppInfo("com.spam.app", "Spam App")),
        )

        // When: exporting then re-importing
        val encoded = RuleJsonCodec.encode(excludeRule)
        val decoded = RuleJsonCodec.decode(encoded)

        // Then: the mode and target apps survive the round trip
        decoded.isSuccess shouldBe true
        val decodedRule = decoded.getOrThrow().rule
        decodedRule.isIncludeMode shouldBe false
        decodedRule.targetApps shouldBe excludeRule.targetApps
    }

    @Test
    fun `deleteRawContentAfterExtraction round-trips through encode-decode`() {
        // Given: a rule with the privacy flag enabled
        val flaggedRule = rule.copy(deleteRawContentAfterExtraction = true)

        // When: exporting then re-importing
        val encoded = RuleJsonCodec.encode(flaggedRule)
        val decoded = RuleJsonCodec.decode(encoded)

        // Then: the flag survives the round trip
        decoded.isSuccess shouldBe true
        decoded.getOrThrow().rule.deleteRawContentAfterExtraction shouldBe true
    }

    @Test
    fun `decoding JSON without deleteRawContentAfterExtraction defaults it to false`() {
        // Given: a hand-crafted export JSON from before this field existed
        val encoded = RuleJsonCodec.encode(rule)
        val legacyJson = encoded.replace(Regex(""",?\s*"deleteRawContentAfterExtraction"\s*:\s*(true|false)"""), "")

        // When: decoding it
        val result = RuleJsonCodec.decode(legacyJson)

        // Then: decoding still succeeds and the field defaults to false (backward-tolerant)
        result.isSuccess shouldBe true
        result.getOrThrow().rule.deleteRawContentAfterExtraction shouldBe false
    }

    @Test
    fun `decode rejects a schema version newer than this app understands`() {
        // Given: an envelope claiming a future schema version
        val futureExport = RuleExportDto(schemaVersion = RULE_EXPORT_SCHEMA_VERSION + 1, rule = rule.toDto().rule)
        val json = Json.encodeToString(futureExport)

        // When: decoding it
        val result = RuleJsonCodec.decode(json)

        // Then: decoding fails
        result.exceptionOrNull().shouldBeInstanceOf<RuleImportFailure.UnsupportedSchemaVersion>()
    }

    @Test
    fun `decode rejects a rule with a blank name`() {
        // Given: a rule exported with a blank name
        val blankNameRule = rule.copy(name = "   ")
        val encoded = RuleJsonCodec.encode(blankNameRule)

        // When: decoding it
        val result = RuleJsonCodec.decode(encoded)

        // Then: decoding fails
        result.exceptionOrNull().shouldBeInstanceOf<RuleImportFailure.MissingName>()
    }

    @Test
    fun `decode rejects malformed JSON`() {
        // When: decoding garbage input
        val result = RuleJsonCodec.decode("not json at all")

        // Then: decoding fails rather than throwing
        result.exceptionOrNull().shouldBeInstanceOf<RuleImportFailure.InvalidFile>()
    }

    @Test
    fun `decode drops an unrecognized action but keeps the rest of the rule`() {
        // Given: a rule whose JSON has an action type this app version doesn't recognize (a
        // placeholder token, not a real `ActionType` - see webhook-delivery's addition of
        // `send_webhook` as a genuinely recognized type)
        val encoded = RuleJsonCodec.encode(rule)
        val tampered = encoded.replace("\"save_data\"", "\"some_future_action_type\"")

        // When: decoding it
        val result = RuleJsonCodec.decode(tampered)

        // Then: decoding succeeds, the unrecognized action is dropped and reported
        result.isSuccess shouldBe true
        val importResult = result.getOrThrow()
        importResult.rule.actions shouldBe emptyList()
        importResult.skippedActions shouldBe listOf("some_future_action_type")
    }

    @Test
    fun `export re-import round-trips a rule with a READ_ALOUD action`() {
        // Given: a rule whose only action is a READ_ALOUD action with a placeholder template
        val readAloudRule = rule.copy(
            actions = persistentListOf(
                createTestAction(
                    id = "a1",
                    type = ActionType.READ_ALOUD,
                    config = mapOf("read_aloud_template" to "Received {{field.amount}} from {{field.sender}}"),
                ),
            ),
        )

        // When: exporting then re-importing
        val encoded = RuleJsonCodec.encode(readAloudRule)
        val decoded = RuleJsonCodec.decode(encoded)

        // Then: the action, its type, and its config survive the round trip unchanged
        decoded.isSuccess shouldBe true
        val decodedRule = decoded.getOrThrow().rule
        decodedRule.actions shouldBe readAloudRule.actions
        encoded shouldContain "\"read_aloud\""
    }

    @Test
    fun `export re-import round-trips a rule with a SEND_REPLY action`() {
        // Given: a rule whose only action is a SEND_REPLY (BETA) action with a placeholder template
        val sendReplyRule = rule.copy(
            actions = persistentListOf(
                createTestAction(
                    id = "a1",
                    type = ActionType.SEND_REPLY,
                    config = mapOf("send_reply_template" to "Got it, thanks {{field.sender}}"),
                ),
            ),
        )

        // When: exporting then re-importing
        val encoded = RuleJsonCodec.encode(sendReplyRule)
        val decoded = RuleJsonCodec.decode(encoded)

        // Then: the action, its type, and its config survive the round trip unchanged
        decoded.isSuccess shouldBe true
        val decodedRule = decoded.getOrThrow().rule
        decodedRule.actions shouldBe sendReplyRule.actions
        encoded shouldContain "\"send_reply\""
    }

    @Test
    fun `decode fails on an unrecognized condition operator`() {
        // Given: a rule whose JSON has an operator this app version doesn't recognize
        val encoded = RuleJsonCodec.encode(rule)
        val tampered = encoded.replace("\"contains\"", "\"fuzzy_match\"")

        // When: decoding it
        val result = RuleJsonCodec.decode(tampered)

        // Then: decoding fails rather than silently dropping or misinterpreting the condition
        result.exceptionOrNull().shouldBeInstanceOf<RuleImportFailure.UnknownValue>()
    }

    @Test
    fun `export re-import round-trips a rule with a nested condition group`() {
        // Given: a rule with one top-level Group whose children are a leaf condition and another,
        // nested Group - exercising the recursive shape end to end
        val innerGroup = RuleCondition.Group(
            id = "g2",
            combinator = ConditionCombinator.ANY,
            children = persistentListOf(
                createTestCondition(id = "c2", condition = MatchingCondition.TEXT_CONTENT, operator = MatchingOperator.CONTAINS, value = "Payment"),
                createTestDayOfWeekCondition(id = "c3", days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            ),
        )
        val groupedRule = rule.copy(
            conditions = persistentListOf(
                RuleCondition.Group(
                    id = "g1",
                    combinator = ConditionCombinator.ALL,
                    children = persistentListOf(
                        createTestCondition(id = "c1", condition = MatchingCondition.TITLE, operator = MatchingOperator.STARTS_WITH, value = "Payment"),
                        innerGroup,
                    ),
                ),
            ),
        )

        // When: exporting then re-importing
        val encoded = RuleJsonCodec.encode(groupedRule)
        val decoded = RuleJsonCodec.decode(encoded)

        // Then: the group tree survives the round trip exactly, at every nesting level
        decoded.isSuccess shouldBe true
        decoded.getOrThrow().rule.conditions shouldBe groupedRule.conditions
    }

    @Test
    fun `decode accepts a condition tree exactly at MAX_CONDITION_DEPTH`() {
        // Given: nested groups whose deepest node (the leaf) sits exactly at MAX_CONDITION_DEPTH
        // (the boundary is inclusive) - one fewer group level than the leaf's depth
        val tree = nestedGroupTree(depth = MAX_CONDITION_DEPTH - 1)
        val encoded = RuleJsonCodec.encode(rule.copy(conditions = persistentListOf(tree)))

        // When: decoding it
        val result = RuleJsonCodec.decode(encoded)

        // Then: decoding succeeds - the boundary itself is not rejected
        result.isSuccess shouldBe true
    }

    @Test
    fun `decode rejects a condition tree nested deeper than MAX_CONDITION_DEPTH`() {
        // Given: nested groups whose deepest node (the leaf) sits one level past MAX_CONDITION_DEPTH
        val tooDeepTree = nestedGroupTree(depth = MAX_CONDITION_DEPTH)
        val encoded = RuleJsonCodec.encode(rule.copy(conditions = persistentListOf(tooDeepTree)))

        // When: decoding it
        val result = RuleJsonCodec.decode(encoded)

        // Then: decoding fails cleanly instead of risking a stack overflow evaluating the rule
        result.exceptionOrNull().shouldBeInstanceOf<RuleImportFailure.NestedTooDeeply>()
    }

    /**
     * Builds a chain of [depth] nested [RuleCondition.Group]s around a single leaf condition, e.g.
     * `depth = 2` produces `Group(children = [Group(children = [leaf])])`. The outermost group sits
     * at nesting level 1 (same convention as [MAX_CONDITION_DEPTH]); the leaf sits at level `depth + 1`.
     */
    private fun nestedGroupTree(depth: Int): RuleCondition {
        var current: RuleCondition = createTestCondition(id = "leaf")
        for (level in depth downTo 1) {
            current = RuleCondition.Group(id = "g$level", combinator = ConditionCombinator.ALL, children = persistentListOf(current))
        }
        return current
    }

    @Test
    fun `withFreshIdentityForImport regenerates every id and forces dry-run`() {
        // Given: a rule as it would arrive from decode(), potentially active and not dry-run
        val decodedRule = rule.copy(isActive = false, isDryRun = false)

        // When: preparing it for import
        val imported = decodedRule.withFreshIdentityForImport()

        // Then: the rule and every nested id changed, and it starts active + dry-run
        imported.id shouldNotBe decodedRule.id
        imported.conditions.single().id shouldNotBe decodedRule.conditions.single().id
        imported.saveDataFields().single().id shouldNotBe decodedRule.saveDataFields().single().id
        imported.actions.single().id shouldNotBe decodedRule.actions.single().id
        imported.isActive shouldBe true
        imported.isDryRun shouldBe true
        // Content is otherwise preserved
        imported.name shouldBe decodedRule.name
        (imported.conditions.single() as RuleCondition.ContentMatchCondition).value shouldBe
            (decodedRule.conditions.single() as RuleCondition.ContentMatchCondition).value
    }

    @Test
    fun `importing the same file twice never collides with itself`() {
        // Given: the same decoded rule "imported" twice
        val firstImport = rule.withFreshIdentityForImport()
        val secondImport = rule.withFreshIdentityForImport()

        // Then: every id differs between the two imports
        firstImport.id shouldNotBe secondImport.id
        firstImport.conditions.single().id shouldNotBe secondImport.conditions.single().id
        firstImport.saveDataFields().single().id shouldNotBe secondImport.saveDataFields().single().id
        firstImport.actions.single().id shouldNotBe secondImport.actions.single().id
    }
}
