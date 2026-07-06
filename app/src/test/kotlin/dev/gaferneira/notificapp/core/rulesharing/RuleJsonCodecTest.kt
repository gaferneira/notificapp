package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec.withFreshIdentityForImport
import dev.gaferneira.notificapp.core.rulesharing.dto.RULE_EXPORT_SCHEMA_VERSION
import dev.gaferneira.notificapp.core.rulesharing.dto.RuleExportDto
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class RuleJsonCodecTest {

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
        fields = listOf(
            // A method with its own fields, not just a zero-argument "smart" one - regression
            // coverage for the classDiscriminator/property-name collision this DTO layer fixes.
            createTestField(id = "f1", name = "Amount", method = ExtractionMethod.TextAfterKeyword(keyword = "Total: ", maxLength = 20)),
        ),
        actions = listOf(createTestAction(id = "a1", type = ActionType.SAVE_DATA)),
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
        decodedRule.targetApps shouldBe rule.targetApps
        decodedRule.conditions shouldBe rule.conditions
        decodedRule.fields shouldBe rule.fields
        decodedRule.actions shouldBe rule.actions
    }

    @Test
    fun `encoded JSON embeds the current schema version`() {
        // When: encoding a rule
        val encoded = RuleJsonCodec.encode(rule)

        // Then: the envelope carries the current schema version
        encoded shouldContain "\"schemaVersion\": $RULE_EXPORT_SCHEMA_VERSION"
    }

    @Test
    fun `decode rejects a schema version newer than this app understands`() {
        // Given: an envelope claiming a future schema version
        val futureExport = RuleExportDto(schemaVersion = RULE_EXPORT_SCHEMA_VERSION + 1, rule = rule.toDto().rule)
        val json = Json.encodeToString(futureExport)

        // When: decoding it
        val result = RuleJsonCodec.decode(json)

        // Then: decoding fails
        result.isFailure shouldBe true
    }

    @Test
    fun `decode rejects a rule with a blank name`() {
        // Given: a rule exported with a blank name
        val blankNameRule = rule.copy(name = "   ")
        val encoded = RuleJsonCodec.encode(blankNameRule)

        // When: decoding it
        val result = RuleJsonCodec.decode(encoded)

        // Then: decoding fails
        result.isFailure shouldBe true
    }

    @Test
    fun `decode rejects malformed JSON`() {
        // When: decoding garbage input
        val result = RuleJsonCodec.decode("not json at all")

        // Then: decoding fails rather than throwing
        result.isFailure shouldBe true
    }

    @Test
    fun `decode drops an unrecognized action but keeps the rest of the rule`() {
        // Given: a rule whose JSON has an action type this app version doesn't recognize
        val encoded = RuleJsonCodec.encode(rule)
        val tampered = encoded.replace("\"save_data\"", "\"send_webhook\"")

        // When: decoding it
        val result = RuleJsonCodec.decode(tampered)

        // Then: decoding succeeds, the unrecognized action is dropped and reported
        result.isSuccess shouldBe true
        val importResult = result.getOrThrow()
        importResult.rule.actions shouldBe emptyList()
        importResult.skippedActions shouldBe listOf("send_webhook")
    }

    @Test
    fun `decode fails on an unrecognized condition operator`() {
        // Given: a rule whose JSON has an operator this app version doesn't recognize
        val encoded = RuleJsonCodec.encode(rule)
        val tampered = encoded.replace("\"contains\"", "\"fuzzy_match\"")

        // When: decoding it
        val result = RuleJsonCodec.decode(tampered)

        // Then: decoding fails rather than silently dropping or misinterpreting the condition
        result.isFailure shouldBe true
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
        imported.fields.single().id shouldNotBe decodedRule.fields.single().id
        imported.actions.single().id shouldNotBe decodedRule.actions.single().id
        imported.isActive shouldBe true
        imported.isDryRun shouldBe true
        // Content is otherwise preserved
        imported.name shouldBe decodedRule.name
        imported.conditions.single().value shouldBe decodedRule.conditions.single().value
    }

    @Test
    fun `importing the same file twice never collides with itself`() {
        // Given: the same decoded rule "imported" twice
        val firstImport = rule.withFreshIdentityForImport()
        val secondImport = rule.withFreshIdentityForImport()

        // Then: every id differs between the two imports
        firstImport.id shouldNotBe secondImport.id
        firstImport.conditions.single().id shouldNotBe secondImport.conditions.single().id
        firstImport.fields.single().id shouldNotBe secondImport.fields.single().id
        firstImport.actions.single().id shouldNotBe secondImport.actions.single().id
    }
}
