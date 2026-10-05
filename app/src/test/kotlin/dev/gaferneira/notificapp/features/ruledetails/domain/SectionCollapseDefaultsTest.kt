package dev.gaferneira.notificapp.features.ruledetails.domain

import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SectionCollapseDefaultsTest {

    private fun conditions(count: Int) = (1..count).map { createTestCondition(id = "c$it") }
    private fun actions(count: Int, enabled: Boolean = true) = (1..count).map { createTestAction(id = "a$it", isEnabled = enabled) }

    @Test
    fun `given an empty rule when computing defaults then both sections are expanded`() {
        createTestRule().sectionCollapseDefaults() shouldBe SectionCollapseDefaults(whenCollapsed = false, doCollapsed = false)
    }

    @Test
    fun `given exactly the threshold of entries when computing defaults then sections stay expanded`() {
        val rule = createTestRule(conditions = conditions(LONG_SECTION_THRESHOLD), actions = actions(LONG_SECTION_THRESHOLD))

        rule.sectionCollapseDefaults() shouldBe SectionCollapseDefaults(whenCollapsed = false, doCollapsed = false)
    }

    @Test
    fun `given more conditions than the threshold when computing defaults then only When collapses`() {
        val rule = createTestRule(conditions = conditions(LONG_SECTION_THRESHOLD + 1), actions = actions(1))

        rule.sectionCollapseDefaults() shouldBe SectionCollapseDefaults(whenCollapsed = true, doCollapsed = false)
    }

    @Test
    fun `given more actions than the threshold when computing defaults then only Do collapses`() {
        val rule = createTestRule(conditions = conditions(1), actions = actions(LONG_SECTION_THRESHOLD + 1))

        rule.sectionCollapseDefaults() shouldBe SectionCollapseDefaults(whenCollapsed = false, doCollapsed = true)
    }

    @Test
    fun `given disabled actions beyond the threshold when computing defaults then they still count`() {
        val rule = createTestRule(actions = actions(LONG_SECTION_THRESHOLD + 1, enabled = false))

        rule.sectionCollapseDefaults().doCollapsed shouldBe true
    }
}
