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
