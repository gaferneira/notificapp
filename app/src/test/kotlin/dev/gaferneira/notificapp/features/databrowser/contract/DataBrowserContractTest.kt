package dev.gaferneira.notificapp.features.databrowser.contract

import androidx.paging.LoadState
import dev.gaferneira.notificapp.domain.model.DataBrowserFilter
import dev.gaferneira.notificapp.domain.model.DataSort
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class DataBrowserContractTest {

    @Nested
    inner class IsNarrowedTests {

        @Test
        fun `a default filter is not narrowed`() {
            DataBrowserFilter().isNarrowed() shouldBe false
        }

        @Test
        fun `sort alone does not narrow`() {
            DataBrowserFilter(sort = DataSort.RULE_ASC).isNarrowed() shouldBe false
        }

        @Test
        fun `a blank search does not narrow`() {
            DataBrowserFilter(searchQuery = "   ").isNarrowed() shouldBe false
        }

        @Test
        fun `a non-blank search narrows`() {
            DataBrowserFilter(searchQuery = "invoice").isNarrowed() shouldBe true
        }

        @Test
        fun `a rule, app or date bound each narrow`() {
            DataBrowserFilter(ruleIds = listOf("r1")).isNarrowed() shouldBe true
            DataBrowserFilter(packageNames = listOf("com.a")).isNarrowed() shouldBe true
            DataBrowserFilter(dateFrom = 1L).isNarrowed() shouldBe true
            DataBrowserFilter(dateTo = 2L).isNarrowed() shouldBe true
        }
    }

    @Nested
    inner class ActiveChipsTests {

        private val full = DataBrowserFilter(
            ruleIds = listOf("r1", "r2"),
            packageNames = listOf("com.a"),
            dateFrom = 10L,
            dateTo = 20L,
        )

        @Test
        fun `a default filter has no chips`() {
            DataBrowserFilter().activeChips() shouldBe emptyList()
        }

        @Test
        fun `one chip per rule, per app and one for the date range, in display order`() {
            full.activeChips() shouldBe listOf(
                DataFilterChip.Rule("r1"),
                DataFilterChip.Rule("r2"),
                DataFilterChip.App("com.a"),
                DataFilterChip.DateRange,
            )
        }

        @Test
        fun `a single date bound still yields the date range chip`() {
            DataBrowserFilter(dateTo = 5L).activeChips() shouldBe listOf(DataFilterChip.DateRange)
        }

        @Test
        fun `removing a rule chip removes only that rule`() {
            full.without(DataFilterChip.Rule("r1")) shouldBe full.copy(ruleIds = listOf("r2"))
        }

        @Test
        fun `removing an app chip removes only that app`() {
            full.without(DataFilterChip.App("com.a")) shouldBe full.copy(packageNames = emptyList())
        }

        @Test
        fun `removing the date chip clears both bounds and keeps the rest`() {
            full.without(DataFilterChip.DateRange) shouldBe full.copy(dateFrom = null, dateTo = null)
        }

        @Test
        fun `removing the last chip leaves an unnarrowed filter that keeps its sort`() {
            val only = DataBrowserFilter(ruleIds = listOf("r1"), sort = DataSort.APP_DESC)

            only.without(DataFilterChip.Rule("r1")) shouldBe DataBrowserFilter(sort = DataSort.APP_DESC)
        }
    }

    @Nested
    inner class ActiveFilterCountTests {

        @Test
        fun `counts rule, app and date dimensions once each regardless of how many values`() {
            DataBrowserFilter().activeFilterCount() shouldBe 0
            DataBrowserFilter(ruleIds = listOf("a", "b")).activeFilterCount() shouldBe 1
            DataBrowserFilter(ruleIds = listOf("a"), packageNames = listOf("p"), dateFrom = 1L).activeFilterCount() shouldBe 3
        }

        @Test
        fun `search and sort are not filter dimensions`() {
            DataBrowserFilter(searchQuery = "x", sort = DataSort.RULE_DESC).activeFilterCount() shouldBe 0
        }
    }

    @Nested
    inner class DataListContentTests {

        private val narrowed = DataBrowserFilter(ruleIds = listOf("r1"))
        private val notNarrowed = DataBrowserFilter()
        private val notLoading = LoadState.NotLoading(endOfPaginationReached = false)

        @Test
        fun `loading shows the spinner even when the filter is narrowed and the list is empty`() {
            dataListContent(LoadState.Loading, 0, narrowed) shouldBe DataListContent.Loading
        }

        @Test
        fun `error shows the error branch`() {
            dataListContent(LoadState.Error(IllegalStateException("x")), 0, narrowed) shouldBe DataListContent.Error
        }

        @Test
        fun `zero rows with a narrowed filter shows the filtered empty state`() {
            dataListContent(notLoading, 0, narrowed) shouldBe DataListContent.FilterEmpty
        }

        @Test
        fun `zero rows with a search only shows the filtered empty state`() {
            dataListContent(notLoading, 0, DataBrowserFilter(searchQuery = "x")) shouldBe DataListContent.FilterEmpty
        }

        @Test
        fun `zero rows without any filter shows the no data state`() {
            dataListContent(notLoading, 0, notNarrowed) shouldBe DataListContent.NoData
        }

        @Test
        fun `rows present shows the list regardless of the filter`() {
            dataListContent(notLoading, 3, narrowed) shouldBe DataListContent.Rows
            dataListContent(notLoading, 3, notNarrowed) shouldBe DataListContent.Rows
        }
    }
}
