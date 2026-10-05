package dev.gaferneira.notificapp.features.ruleeditor.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AdvancedSettingsSummaryTest {

    @Test
    fun `given the defaults when summarising then only test mode off is reported`() {
        advancedStateSummary(isDryRun = false, deleteRawContentVisible = false, deleteRawContentEnabled = false) shouldBe
            listOf(AdvancedStateItem.TEST_MODE_OFF)
    }

    @Test
    fun `given test mode on when summarising then test mode on is reported`() {
        advancedStateSummary(isDryRun = true, deleteRawContentVisible = false, deleteRawContentEnabled = false) shouldBe
            listOf(AdvancedStateItem.TEST_MODE_ON)
    }

    @Test
    fun `given delete raw on and visible when summarising then it follows test mode`() {
        advancedStateSummary(isDryRun = false, deleteRawContentVisible = true, deleteRawContentEnabled = true) shouldBe
            listOf(AdvancedStateItem.TEST_MODE_OFF, AdvancedStateItem.ORIGINAL_TEXT_DELETED)
        advancedStateSummary(isDryRun = true, deleteRawContentVisible = true, deleteRawContentEnabled = true) shouldBe
            listOf(AdvancedStateItem.TEST_MODE_ON, AdvancedStateItem.ORIGINAL_TEXT_DELETED)
    }

    @Test
    fun `given delete raw off or not applicable when summarising then it is not reported`() {
        advancedStateSummary(isDryRun = false, deleteRawContentVisible = true, deleteRawContentEnabled = false) shouldBe
            listOf(AdvancedStateItem.TEST_MODE_OFF)
        advancedStateSummary(isDryRun = false, deleteRawContentVisible = false, deleteRawContentEnabled = true) shouldBe
            listOf(AdvancedStateItem.TEST_MODE_OFF)
    }

    @Test
    fun `given defaults when deciding initial expansion then it stays collapsed`() {
        shouldExpandAdvancedInitially(false, false, false) shouldBe false
        shouldExpandAdvancedInitially(false, true, false) shouldBe false
    }

    @Test
    fun `given test mode on when deciding initial expansion then it opens`() {
        shouldExpandAdvancedInitially(true, false, false) shouldBe true
    }

    @Test
    fun `given delete raw on and applicable when deciding initial expansion then it opens`() {
        shouldExpandAdvancedInitially(false, true, true) shouldBe true
    }

    @Test
    fun `given delete raw on but not applicable when deciding initial expansion then it stays collapsed`() {
        shouldExpandAdvancedInitially(false, false, true) shouldBe false
    }
}
