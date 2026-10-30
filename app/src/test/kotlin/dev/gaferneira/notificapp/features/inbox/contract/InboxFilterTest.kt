package dev.gaferneira.notificapp.features.inbox.contract

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

class InboxFilterTest {

    @Test
    fun `given the default filter, when asked, then nothing is active and there are no chips`() {
        val filter = InboxFilter()

        filter.isActive() shouldBe false
        filter.activeFilterCount() shouldBe 0
        filter.activeChips() shouldBe emptyList()
    }

    @Test
    fun `given status and several apps, when counted, then each dimension counts once`() {
        InboxFilter(setOf("b", "a"), Status.PROCESSED).activeFilterCount() shouldBe 2
        InboxFilter(setOf("b", "a")).activeFilterCount() shouldBe 1
        InboxFilter(status = Status.UNPROCESSED).activeFilterCount() shouldBe 1
    }

    @Test
    fun `given status and apps, when chips are built, then status comes first and apps are sorted`() {
        val chips = InboxFilter(setOf("com.b", "com.a"), Status.UNPROCESSED).activeChips()

        chips shouldBe listOf(
            InboxFilterChip.Status(Status.UNPROCESSED),
            InboxFilterChip.App("com.a"),
            InboxFilterChip.App("com.b"),
        )
    }

    @Test
    fun `given a filter, when a status chip is removed, then status resets and apps stay`() {
        val filter = InboxFilter(setOf("com.a"), Status.PROCESSED)

        filter.without(InboxFilterChip.Status(Status.PROCESSED)) shouldBe InboxFilter(setOf("com.a"), Status.ALL)
    }

    @Test
    fun `given a filter, when an app chip is removed, then only that app is dropped`() {
        val filter = InboxFilter(setOf("com.a", "com.b"), Status.PROCESSED)

        filter.without(InboxFilterChip.App("com.a")) shouldBe InboxFilter(setOf("com.b"), Status.PROCESSED)
    }

    @Test
    fun `given a date window, when counted, then it counts as one dimension`() {
        InboxFilter(since = 1_000L).activeFilterCount() shouldBe 1
        InboxFilter(setOf("a"), Status.PROCESSED, since = 1_000L).activeFilterCount() shouldBe 3
        InboxFilter(since = 1_000L).isActive() shouldBe true
    }

    @Test
    fun `given a date window with status and apps, when chips are built, then the window chip comes first`() {
        val chips = InboxFilter(setOf("com.a"), Status.PROCESSED, since = 1_000L).activeChips()

        chips shouldBe listOf(
            InboxFilterChip.Since(1_000L),
            InboxFilterChip.Status(Status.PROCESSED),
            InboxFilterChip.App("com.a"),
        )
    }

    @Test
    fun `given a date window, when its chip is removed, then only the window is cleared`() {
        val filter = InboxFilter(setOf("com.a"), Status.PROCESSED, since = 1_000L)

        filter.without(InboxFilterChip.Since(1_000L)) shouldBe InboxFilter(setOf("com.a"), Status.PROCESSED)
    }
}
