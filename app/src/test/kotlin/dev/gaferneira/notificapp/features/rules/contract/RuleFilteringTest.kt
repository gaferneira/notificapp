package dev.gaferneira.notificapp.features.rules.contract

import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RuleFilteringTest {

    private val appA = AppInfo("com.a", "App A")
    private val appB = AppInfo("com.b", "App B")

    private val global = createTestRule(id = "global", targetApps = null)
    private val emptyGlobal = createTestRule(id = "empty", targetApps = emptyList())
    private val includeA = createTestRule(id = "includeA", targetApps = listOf(appA))
    private val includeB = createTestRule(id = "includeB", targetApps = listOf(appB))
    private val excludeA = createTestRule(id = "excludeA", isIncludeMode = false, targetApps = listOf(appA))
    private val excludeB = createTestRule(id = "excludeB", isIncludeMode = false, targetApps = listOf(appB))
    private val allRules = listOf(global, emptyGlobal, includeA, includeB, excludeA, excludeB)

    private fun RuleFilter.ids(rules: List<Rule> = allRules, query: String = "") = rules.filter { matches(it, query) }.map { it.id }

    @Nested
    inner class AppMentionSemantics {

        @Test
        fun `by default an app matches rules that mention it in include or exclude mode`() {
            RuleFilter(selectedApps = setOf("com.a")).ids() shouldBe listOf("includeA", "excludeA")
        }

        @Test
        fun `by default global rules and non-excluding exclude rules do not match`() {
            // com.a is mentioned by neither global nor excludeB; excludeB would run for it but does not mention it
            val ids = RuleFilter(selectedApps = setOf("com.a")).ids()
            ids.contains("global") shouldBe false
            ids.contains("empty") shouldBe false
            ids.contains("excludeB") shouldBe false
        }

        @Test
        fun `include-global adds rules that would run for the app`() {
            // mentions: includeA, excludeA. would run for com.a: global, empty, includeA, excludeB
            RuleFilter(selectedApps = setOf("com.a"), includeGlobalRules = true).ids() shouldBe
                listOf("global", "empty", "includeA", "excludeA", "excludeB")
        }

        @Test
        fun `include-global never matches a rule that neither mentions nor would run for the app`() {
            RuleFilter(selectedApps = setOf("com.a"), includeGlobalRules = true).ids().contains("includeB") shouldBe false
        }

        @Test
        fun `multiple selected apps combine with OR`() {
            RuleFilter(selectedApps = setOf("com.a", "com.b")).ids() shouldBe
                listOf("includeA", "includeB", "excludeA", "excludeB")
        }

        @Test
        fun `multiple apps with include-global is the union of each app`() {
            RuleFilter(selectedApps = setOf("com.a", "com.b"), includeGlobalRules = true).ids() shouldBe allRules.map { it.id }
        }

        @Test
        fun `include-global without selected apps has no effect`() {
            RuleFilter(includeGlobalRules = true).ids() shouldBe allRules.map { it.id }
        }

        @Test
        fun `an unknown app matches nothing by default`() {
            RuleFilter(selectedApps = setOf("com.zzz")).ids() shouldBe emptyList()
        }
    }

    @Nested
    inner class StatusAndCategory {

        private val enabled = createTestRule(id = "on", isActive = true, category = "Finance")
        private val disabled = createTestRule(id = "off", isActive = false, category = "Deliveries")
        private val uncategorized = createTestRule(id = "none", isActive = true, category = null)
        private val rules = listOf(enabled, disabled, uncategorized)

        @Test
        fun `status filters enabled and disabled`() {
            RuleFilter(status = RuleFilter.Status.ENABLED).ids(rules) shouldBe listOf("on", "none")
            RuleFilter(status = RuleFilter.Status.DISABLED).ids(rules) shouldBe listOf("off")
            RuleFilter(status = RuleFilter.Status.ALL).ids(rules) shouldBe listOf("on", "off", "none")
        }

        @Test
        fun `categories combine with OR`() {
            RuleFilter(selectedCategories = setOf("Finance", "Deliveries")).ids(rules) shouldBe listOf("on", "off")
        }

        @Test
        fun `uncategorized alone matches only rules without a category`() {
            RuleFilter(includeUncategorized = true).ids(rules) shouldBe listOf("none")
        }

        @Test
        fun `uncategorized combines with named categories`() {
            RuleFilter(selectedCategories = setOf("Finance"), includeUncategorized = true).ids(rules) shouldBe
                listOf("on", "none")
        }

        @Test
        fun `a named category never matches a rule without a category`() {
            RuleFilter(selectedCategories = setOf("Finance")).ids(rules).contains("none") shouldBe false
        }

        @Test
        fun `dimensions combine with AND`() {
            RuleFilter(status = RuleFilter.Status.ENABLED, selectedCategories = setOf("Deliveries")).ids(rules) shouldBe
                emptyList()
        }
    }

    @Nested
    inner class Search {

        private val rule = createTestRule(id = "r", name = "Bank Alerts", description = "payments", category = "Finance")

        @Test
        fun `search is case insensitive over name, description and category`() {
            RuleFilter().ids(listOf(rule), "bank") shouldBe listOf("r")
            RuleFilter().ids(listOf(rule), "PAYM") shouldBe listOf("r")
            RuleFilter().ids(listOf(rule), "fin") shouldBe listOf("r")
            RuleFilter().ids(listOf(rule), "zzz") shouldBe emptyList()
        }

        @Test
        fun `blank search matches everything`() {
            RuleFilter().ids(listOf(rule), "   ") shouldBe listOf("r")
        }

        @Test
        fun `search combines with the filters`() {
            val other = createTestRule(id = "o", name = "Bank other", targetApps = listOf(appA))
            RuleFilter(selectedApps = setOf("com.a")).ids(listOf(rule, other), "bank") shouldBe listOf("o")
        }
    }

    @Nested
    inner class Counting {

        @Test
        fun `sort alone is not an active filter`() {
            val filter = RuleFilter(sortBy = RuleFilter.SortBy.NAME_ASC)
            filter.isActive() shouldBe false
            filter.activeFilterCount() shouldBe 0
        }

        @Test
        fun `each dimension counts once`() {
            val filter = RuleFilter(
                status = RuleFilter.Status.ENABLED,
                selectedCategories = setOf("A", "B"),
                includeUncategorized = true,
                selectedApps = setOf("com.a", "com.b"),
                includeGlobalRules = true,
            )
            filter.activeFilterCount() shouldBe 3
        }

        @Test
        fun `uncategorized alone counts as the category dimension`() {
            RuleFilter(includeUncategorized = true).activeFilterCount() shouldBe 1
        }

        @Test
        fun `withoutFilters keeps the sort`() {
            RuleFilter(status = RuleFilter.Status.DISABLED, sortBy = RuleFilter.SortBy.STATUS).withoutFilters() shouldBe
                RuleFilter(sortBy = RuleFilter.SortBy.STATUS)
        }

        @Test
        fun `chips list every active filter and removing them empties the filter`() {
            val filter = RuleFilter(
                status = RuleFilter.Status.ENABLED,
                selectedCategories = setOf("B", "a"),
                includeUncategorized = true,
                selectedApps = setOf("com.a"),
                includeGlobalRules = true,
            )
            val chips = filter.activeChips()
            chips shouldBe listOf(
                RuleFilterChip.Status(RuleFilter.Status.ENABLED),
                RuleFilterChip.Category("a"),
                RuleFilterChip.Category("B"),
                RuleFilterChip.Uncategorized,
                RuleFilterChip.App("com.a"),
                RuleFilterChip.GlobalRules,
            )
            chips.fold(filter) { acc, chip -> acc.without(chip) } shouldBe RuleFilter()
        }

        @Test
        fun `global-rules chip is hidden without apps`() {
            RuleFilter(includeGlobalRules = true).activeChips() shouldBe emptyList()
        }
    }

    @Nested
    inner class Sorting {

        @Test
        fun `category sort is case insensitive and puts uncategorized last`() {
            val rules = listOf(
                createTestRule(id = "1", name = "x", category = "banana"),
                createTestRule(id = "2", name = "x", category = null),
                createTestRule(id = "3", name = "x", category = "Apple"),
            )
            RuleFilter().sort(rules).map { it.id } shouldBe listOf("3", "1", "2")
        }

        @Test
        fun `status sort puts enabled first then by name`() {
            val rules = listOf(
                createTestRule(id = "1", name = "b", isActive = false),
                createTestRule(id = "2", name = "z", isActive = true),
                createTestRule(id = "3", name = "a", isActive = true),
            )
            RuleFilter(sortBy = RuleFilter.SortBy.STATUS).sort(rules).map { it.id } shouldBe listOf("3", "2", "1")
        }
    }
}
