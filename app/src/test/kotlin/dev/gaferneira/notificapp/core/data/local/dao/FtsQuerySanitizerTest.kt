package dev.gaferneira.notificapp.core.data.local.dao

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class FtsQuerySanitizerTest {

    @Test
    fun `single word becomes a quoted prefix match`() {
        FtsQuerySanitizer.toMatchExpression("payment") shouldBe "\"payment\"*"
    }

    @Test
    fun `multiple words become space-joined quoted prefix matches`() {
        FtsQuerySanitizer.toMatchExpression("bank payment") shouldBe "\"bank\"* \"payment\"*"
    }

    @Test
    fun `collapses repeated whitespace between words`() {
        FtsQuerySanitizer.toMatchExpression("bank   payment") shouldBe "\"bank\"* \"payment\"*"
    }

    @Test
    fun `strips FTS4 operator characters from tokens`() {
        FtsQuerySanitizer.toMatchExpression("""pay*ment "quote" (paren) dash-word""") shouldBe
            "\"payment\"* \"quote\"* \"paren\"* \"dashword\"*"
    }

    @Test
    fun `blank input produces an empty expression`() {
        FtsQuerySanitizer.toMatchExpression("   ") shouldBe ""
    }
}
