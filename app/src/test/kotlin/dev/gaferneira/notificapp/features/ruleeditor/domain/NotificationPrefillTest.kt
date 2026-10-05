package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class NotificationPrefillTest {

    private fun prefill(title: String?, content: String?): RuleCondition.ContentMatchCondition? = prefillConditionFrom(createTestNotification(title = title, content = content), newId = { "id" })
        ?.shouldBeInstanceOf<RuleCondition.ContentMatchCondition>()

    @Test
    fun `a title becomes a title contains condition`() {
        val condition = prefill(title = "  Your order shipped  ", content = "Body")

        condition?.condition shouldBe MatchingCondition.TITLE
        condition?.operator shouldBe MatchingOperator.CONTAINS
        condition?.value shouldBe "Your order shipped"
        condition?.id shouldBe "id"
    }

    @Test
    fun `a blank title falls back to the first non-blank line of the text`() {
        val condition = prefill(title = "   ", content = "\n  \n  Arriving today \nSecond line")

        condition?.condition shouldBe MatchingCondition.TEXT_CONTENT
        condition?.value shouldBe "Arriving today"
    }

    @Test
    fun `a null title falls back to the text`() {
        prefill(title = null, content = "Hello")?.value shouldBe "Hello"
    }

    @Test
    fun `blank title and text produce no condition`() {
        prefill(title = " ", content = " \n ") shouldBe null
        prefill(title = null, content = null) shouldBe null
    }

    @Test
    fun `long values are capped at 60 characters and trimmed`() {
        val long = "a".repeat(59) + " " + "b".repeat(20)

        val value = prefill(title = long, content = null)?.value

        value shouldBe ("a".repeat(59))
        (value?.length ?: 0) shouldBe 59
    }

    @Test
    fun `exactly 60 characters are kept whole`() {
        prefill(title = "x".repeat(60), content = null)?.value shouldBe "x".repeat(60)
    }

    @Test
    fun `a multiline text only uses its first line`() {
        prefill(title = null, content = "first\nsecond\nthird")?.value shouldBe "first"
    }
}
