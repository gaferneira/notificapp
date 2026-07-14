package dev.gaferneira.notificapp.domain.model

import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleAppliesToPackageTest {

    private val appA = AppInfo("com.a", "App A")
    private val appB = AppInfo("com.b", "App B")

    @Test
    fun `null targetApps applies to any package regardless of mode`() {
        val rule = createTestRule(targetApps = null, isIncludeMode = false)

        rule.appliesToPackage("com.anything") shouldBe true
    }

    @Test
    fun `empty targetApps applies to any package regardless of mode`() {
        val rule = createTestRule(targetApps = emptyList(), isIncludeMode = false)

        rule.appliesToPackage("com.anything") shouldBe true
    }

    @Test
    fun `include mode applies to listed package`() {
        val rule = createTestRule(targetApps = listOf(appA, appB), isIncludeMode = true)

        rule.appliesToPackage("com.a") shouldBe true
    }

    @Test
    fun `include mode does not apply to unlisted package`() {
        val rule = createTestRule(targetApps = listOf(appA, appB), isIncludeMode = true)

        rule.appliesToPackage("com.c") shouldBe false
    }

    @Test
    fun `exclude mode does not apply to listed package`() {
        val rule = createTestRule(targetApps = listOf(appA), isIncludeMode = false)

        rule.appliesToPackage("com.a") shouldBe false
    }

    @Test
    fun `exclude mode applies to unlisted package`() {
        val rule = createTestRule(targetApps = listOf(appA), isIncludeMode = false)

        rule.appliesToPackage("com.b") shouldBe true
    }
}
