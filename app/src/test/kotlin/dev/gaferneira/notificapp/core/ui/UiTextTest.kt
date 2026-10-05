package dev.gaferneira.notificapp.core.ui

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class UiTextTest {

    @Test
    fun `string resources with the same id and args are equal`() {
        UiText.StringResource(42, arrayOf("a", 1)) shouldBe UiText.StringResource(42, arrayOf("a", 1))
        UiText.StringResource(42, arrayOf("a", 1)).hashCode() shouldBe UiText.StringResource(42, arrayOf("a", 1)).hashCode()
    }

    @Test
    fun `string resources with different ids or args are not equal`() {
        UiText.StringResource(42, arrayOf("a")) shouldNotBe UiText.StringResource(43, arrayOf("a"))
        UiText.StringResource(42, arrayOf("a")) shouldNotBe UiText.StringResource(42, arrayOf("b"))
        UiText.StringResource(42) shouldNotBe UiText.DynamicString("42")
    }
}
