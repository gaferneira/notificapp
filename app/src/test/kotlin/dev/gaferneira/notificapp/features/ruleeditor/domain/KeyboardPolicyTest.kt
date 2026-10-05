package dev.gaferneira.notificapp.features.ruleeditor.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class KeyboardPolicyTest {

    @Test
    fun `bottom bar hides only when the keyboard is open on a short window`() {
        shouldHideBottomBarForIme(isImeVisible = true, windowHeightDp = 360) shouldBe true
        shouldHideBottomBarForIme(isImeVisible = true, windowHeightDp = 800) shouldBe false
        shouldHideBottomBarForIme(isImeVisible = false, windowHeightDp = 360) shouldBe false
    }

    @Test
    fun `window exactly at the compact threshold keeps the bottom bar`() {
        shouldHideBottomBarForIme(isImeVisible = true, windowHeightDp = COMPACT_HEIGHT_DP) shouldBe false
    }

    @Test
    fun `name hands focus to the first shown optional field`() {
        nameNextField(showDescription = true, showCategory = true) shouldBe NameNextField.DESCRIPTION
        nameNextField(showDescription = true, showCategory = false) shouldBe NameNextField.DESCRIPTION
        nameNextField(showDescription = false, showCategory = true) shouldBe NameNextField.CATEGORY
    }

    @Test
    fun `name is the last field when no optional field is shown`() {
        nameNextField(showDescription = false, showCategory = false) shouldBe NameNextField.NONE
    }

    @Test
    fun `auto focus applies to an empty name that was not handled yet`() {
        shouldAutoFocusName(name = "", alreadyHandled = false) shouldBe true
        shouldAutoFocusName(name = "   ", alreadyHandled = false) shouldBe true
    }

    @Test
    fun `auto focus is skipped for a filled name or after being handled`() {
        shouldAutoFocusName(name = "Bank alerts", alreadyHandled = false) shouldBe false
        shouldAutoFocusName(name = "", alreadyHandled = true) shouldBe false
    }
}
