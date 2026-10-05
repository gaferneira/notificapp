package dev.gaferneira.notificapp.features.home.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.di.HomeDataSources
import dev.gaferneira.notificapp.core.notification.RecurringNotificationSuggester
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.RecentActivity
import dev.gaferneira.notificapp.domain.model.RuleCoverage
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.domain.repository.SuggestionDismissalRepository
import dev.gaferneira.notificapp.features.home.contract.HomeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEvent
import dev.gaferneira.notificapp.features.home.contract.HomeSection
import dev.gaferneira.notificapp.features.home.contract.MonitoringStatus
import dev.gaferneira.notificapp.features.home.contract.RecentActivityUi
import dev.gaferneira.notificapp.features.home.contract.RecurringSuggestionUi
import dev.gaferneira.notificapp.features.home.contract.WeekStats
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var ruleRepository: RuleRepository
    private lateinit var selectedAppRepository: SelectedAppRepository
    private lateinit var ruleExecutionRepository: RuleExecutionRepository
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var suggestionDismissalRepository: SuggestionDismissalRepository
    private lateinit var dismissalsFlow: MutableStateFlow<Set<dev.gaferneira.notificapp.domain.model.SuggestionDismissalKey>>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        dismissalsFlow = MutableStateFlow(emptySet())
        ruleRepository = mockk {
            every { observeAllRules() } returns flowOf(emptyList())
        }
        selectedAppRepository = mockk {
            every { observeEnabledApps() } returns flowOf(emptyList())
        }
        ruleExecutionRepository = mockk {
            every { observeExecutionCountSince(any()) } returns flowOf(0)
            every { observeRecentActivity(any()) } returns flowOf(emptyList())
        }
        notificationRepository = mockk {
            every { observeActiveAppCountSince(any()) } returns flowOf(0)
            every { observeRecentSince(any(), any()) } returns flowOf(emptyList())
            every { observeCountSince(any()) } returns flowOf(0)
        }
        suggestionDismissalRepository = mockk {
            every { observeDismissals() } returns dismissalsFlow
            coEvery { dismiss(any(), any()) } returns Result.success(Unit)
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createDataSources(): HomeDataSources = HomeDataSources(
        ruleRepository = ruleRepository,
        selectedAppRepository = selectedAppRepository,
        ruleExecutionRepository = ruleExecutionRepository,
        notificationRepository = notificationRepository,
        suggestionDismissalRepository = suggestionDismissalRepository,
    )

    private fun createViewModel(listenerEnabled: Boolean = true): HomeViewModel = HomeViewModel(
        dataSources = createDataSources(),
        recurringNotificationSuggester = RecurringNotificationSuggester(),
        listenerStatus = NotificationListenerStatusProvider { listenerEnabled },
        ioDispatcher = testDispatcher,
    )

    @Nested
    inner class SectionSelectionTests {

        @Test
        fun `zero rules shows StarterRules regardless of suggestions`() = runTest(testDispatcher) {
            every { ruleRepository.observeAllRules() } returns flowOf(emptyList())
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.section.shouldBeInstanceOf<HomeSection.StarterRules>()
        }

        @Test
        fun `rules exist with suggestions shows Recurring`() = runTest(testDispatcher) {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            val day1 = today.minusDays(1).atStartOfDay(zoneId).toInstant()
            val day2 = today.minusDays(2).atStartOfDay(zoneId).toInstant()

            val notifications = listOf(
                createTestNotification(id = "n1", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day1.toEpochMilli()),
                createTestNotification(id = "n2", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day1.toEpochMilli()),
                createTestNotification(id = "n3", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day1.toEpochMilli()),
                createTestNotification(id = "n4", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day2.toEpochMilli()),
                createTestNotification(id = "n5", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day2.toEpochMilli()),
            )

            val rule = createTestRule(
                id = "r1",
                name = "Test Rule",
                isActive = true,
                targetApps = emptyList(),
                conditions = listOf(
                    dev.gaferneira.notificapp.domain.model.RuleCondition.ContentMatchCondition(
                        id = "c1",
                        condition = dev.gaferneira.notificapp.domain.model.MatchingCondition.TITLE,
                        operator = dev.gaferneira.notificapp.domain.model.MatchingOperator.CONTAINS,
                        value = "urgent",
                    ),
                ),
            )

            every { ruleRepository.observeAllRules() } returns flowOf(listOf(rule))
            every { notificationRepository.observeRecentSince(any(), any()) } returns flowOf(notifications)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.section.shouldBeInstanceOf<HomeSection.Recurring>()
        }

        @Test
        fun `rules exist with no suggestions shows None`() = runTest(testDispatcher) {
            every { ruleRepository.observeAllRules() } returns flowOf(listOf(createTestRule(id = "r1")))
            every { notificationRepository.observeRecentSince(any(), any()) } returns flowOf(emptyList())
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.section shouldBe HomeSection.None
        }
    }

    @Nested
    inner class DismissTests {

        @Test
        fun `dismiss event calls the repository and the suggestion disappears from the next emission`() = runTest(testDispatcher) {
            val zoneId = ZoneId.systemDefault()
            val today = LocalDate.now(zoneId)
            val day1 = today.minusDays(1).atStartOfDay(zoneId).toInstant()
            val day2 = today.minusDays(2).atStartOfDay(zoneId).toInstant()

            val notifications = listOf(
                createTestNotification(id = "n1", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day1.toEpochMilli()),
                createTestNotification(id = "n2", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day1.toEpochMilli()),
                createTestNotification(id = "n3", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day1.toEpochMilli()),
                createTestNotification(id = "n4", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day2.toEpochMilli()),
                createTestNotification(id = "n5", packageName = "com.test.app", appName = "Test App", title = "Your order #12345", timestamp = day2.toEpochMilli()),
            )

            val rule = createTestRule(
                id = "r1",
                name = "Test Rule",
                isActive = true,
                targetApps = emptyList(),
                conditions = listOf(
                    dev.gaferneira.notificapp.domain.model.RuleCondition.ContentMatchCondition(
                        id = "c1",
                        condition = dev.gaferneira.notificapp.domain.model.MatchingCondition.TITLE,
                        operator = dev.gaferneira.notificapp.domain.model.MatchingOperator.CONTAINS,
                        value = "urgent",
                    ),
                ),
            )

            val notificationsFlow = MutableStateFlow(emptyList<dev.gaferneira.notificapp.domain.model.Notification>())
            every { notificationRepository.observeRecentSince(any(), any()) } returns notificationsFlow
            every { ruleRepository.observeAllRules() } returns flowOf(listOf(rule))

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            notificationsFlow.value = notifications
            dismissalsFlow.value = emptySet()
            testDispatcher.scheduler.advanceUntilIdle()

            val section = viewModel.uiState.value.section
            section.shouldBeInstanceOf<HomeSection.Recurring>()
            val suggestion = section.suggestions[0]

            viewModel.onEvent(HomeEvent.OnSkipSimilar(suggestion))
            testDispatcher.scheduler.runCurrent()

            coVerify { suggestionDismissalRepository.dismiss(suggestion.packageName, suggestion.normalizedTitleKey) }

            dismissalsFlow.value = setOf(dev.gaferneira.notificapp.domain.model.SuggestionDismissalKey(suggestion.packageName, suggestion.normalizedTitleKey))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.section shouldBe HomeSection.None
        }
    }

    @Nested
    inner class ListenerStatusTests {

        @Test
        fun `listener status refreshes on OnResume`() = runTest(testDispatcher) {
            var enabled = true
            val viewModel = HomeViewModel(
                dataSources = createDataSources(),
                recurringNotificationSuggester = RecurringNotificationSuggester(),
                listenerStatus = NotificationListenerStatusProvider { enabled },
                ioDispatcher = testDispatcher,
            )
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.monitoring.isListenerEnabled shouldBe true

            enabled = false
            viewModel.onEvent(HomeEvent.OnResume)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.monitoring.isListenerEnabled shouldBe false
        }
    }

    @Nested
    inner class TimeWindowTests {

        @Test
        fun `time windows are recomputed on OnResume only after the day changes`() = runTest(testDispatcher) {
            val zone = ZoneId.systemDefault()
            val day1 = LocalDate.parse("2026-01-07").atTime(10, 0).atZone(zone).toInstant()
            val viewModel = createViewModel()
            viewModel.clock = Clock.fixed(day1, zone)
            // Re-observe with the injected clock so the initial windows are anchored to day1.
            viewModel.onEvent(HomeEvent.OnRetry)
            testDispatcher.scheduler.advanceUntilIdle()
            val weekStart1 = LocalDate.parse("2026-01-05").atStartOfDay(zone).toInstant().toEpochMilli()
            verify(exactly = 1) { ruleExecutionRepository.observeExecutionCountSince(weekStart1) }

            viewModel.clock = Clock.fixed(day1.plusSeconds(3_600), zone)
            viewModel.onEvent(HomeEvent.OnResume)
            testDispatcher.scheduler.advanceUntilIdle()
            verify(exactly = 1) { ruleExecutionRepository.observeExecutionCountSince(weekStart1) }

            // Next Monday: the week window rolls over.
            viewModel.clock = Clock.fixed(LocalDate.parse("2026-01-12").atTime(8, 0).atZone(zone).toInstant(), zone)
            viewModel.onEvent(HomeEvent.OnResume)
            testDispatcher.scheduler.advanceUntilIdle()
            val weekStart2 = LocalDate.parse("2026-01-12").atStartOfDay(zone).toInstant().toEpochMilli()
            verify(atLeast = 1) { ruleExecutionRepository.observeExecutionCountSince(weekStart2) }
        }
    }

    @Nested
    inner class ErrorHandlingTests {

        @Test
        fun `upstream failure sets hasError and retry recovers`() = runTest(testDispatcher) {
            var fail = true
            every { ruleRepository.observeAllRules() } answers {
                if (fail) flow { throw IllegalStateException("boom") } else flowOf(emptyList())
            }
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.hasError shouldBe true
            viewModel.uiState.value.isLoading shouldBe false

            fail = false
            viewModel.onEvent(HomeEvent.OnRetry)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.hasError shouldBe false
            viewModel.uiState.value.isLoading shouldBe false
        }
    }

    @Nested
    inner class EffectTests {

        @Test
        fun `OnRecentActivityClick emits NavigateToNotificationDetail`() = runTest(testDispatcher) {
            val viewModel = createViewModel()

            viewModel.effect.test {
                viewModel.onEvent(HomeEvent.OnRecentActivityClick("notif-1"))
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe HomeEffect.NavigateToNotificationDetail("notif-1")
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `OnSeeAllActivity emits NavigateToInbox`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                viewModel.onEvent(HomeEvent.OnSeeAllActivity)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe HomeEffect.NavigateToInbox
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `OnCreateRuleFromScratch emits NavigateToRuleEditor with no ids`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                viewModel.onEvent(HomeEvent.OnCreateRuleFromScratch)
                testDispatcher.scheduler.advanceUntilIdle()

                val effect = awaitItem()
                effect.shouldBeInstanceOf<HomeEffect.NavigateToRuleEditor>()
                effect.ruleId shouldBe null
                effect.notificationId shouldBe null
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class SuggestionEffectTests {

        @Test
        fun `OnCreateRuleFromSuggestion emits NavigateToRuleEditor with the sample notification id`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                viewModel.onEvent(HomeEvent.OnCreateRuleFromSuggestion(createSuggestionUi(sampleNotificationId = "sample-42")))
                testDispatcher.scheduler.advanceUntilIdle()

                val effect = awaitItem()
                effect.shouldBeInstanceOf<HomeEffect.NavigateToRuleEditor>()
                effect.notificationId shouldBe "sample-42"
                effect.ruleId shouldBe null
                cancelAndIgnoreRemainingEvents()
            }
        }

        @Test
        fun `dismiss failure emits ShowError with the dismiss error message`() = runTest(testDispatcher) {
            coEvery { suggestionDismissalRepository.dismiss(any(), any()) } returns Result.failure(IllegalStateException("db"))
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                viewModel.onEvent(HomeEvent.OnSkipSimilar(createSuggestionUi()))
                testDispatcher.scheduler.advanceUntilIdle()

                val effect = awaitItem()
                effect.shouldBeInstanceOf<HomeEffect.ShowError>()
                val message = effect.message
                message.shouldBeInstanceOf<UiText.StringResource>()
                message.id shouldBe R.string.home_error_dismiss_suggestion
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class StatsMappingTests {

        @Test
        fun `week stats map records, rules fired and apps active from their sources`() = runTest(testDispatcher) {
            every { notificationRepository.observeCountSince(any()) } returns flowOf(12)
            every { ruleExecutionRepository.observeExecutionCountSince(any()) } returns flowOf(7)
            every { notificationRepository.observeActiveAppCountSince(any()) } returns flowOf(3)
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.weekStats shouldBe WeekStats(records = 12, rulesFired = 7, appsActive = 3)
        }

        @Test
        fun `recent activity maps notification content to subtitle and keeps order`() = runTest(testDispatcher) {
            every { ruleExecutionRepository.observeRecentActivity(any()) } returns flowOf(
                listOf(
                    createActivity(id = "e1", notificationId = "n1", title = "Hello", content = "World"),
                    createActivity(id = "e2", notificationId = "n2", title = null, content = null),
                ),
            )
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val items = viewModel.uiState.value.recentActivity
            items.map { it.notificationId } shouldBe listOf("n1", "n2")
            items[0] shouldBe RecentActivityUi(
                notificationId = "n1",
                ruleName = "Rule",
                title = "Hello",
                subtitle = "World",
                appName = "Test App",
            )
            items[1].subtitle shouldBe null
        }

        @Test
        fun `recent activity is requested with a limit of 5`() = runTest(testDispatcher) {
            createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            verify { ruleExecutionRepository.observeRecentActivity(5) }
        }
    }

    @Nested
    inner class LoadingTests {

        @Test
        fun `isLoading is true until the first emission then false`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.uiState.value.isLoading shouldBe true

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isLoading shouldBe false
        }

        @Test
        fun `OnRetry sets isLoading true until data re-emits`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(HomeEvent.OnRetry)
            viewModel.uiState.value.isLoading shouldBe true

            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.isLoading shouldBe false
        }
    }

    @Nested
    inner class MonitoringTests {

        @Test
        fun `SecurityException in listener check does not crash and reports listener off`() = runTest(testDispatcher) {
            val viewModel = HomeViewModel(
                dataSources = createDataSources(),
                recurringNotificationSuggester = RecurringNotificationSuggester(),
                listenerStatus = NotificationListenerStatusProvider { throw SecurityException("denied") },
                ioDispatcher = testDispatcher,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.monitoring.isListenerEnabled shouldBe false
            viewModel.uiState.value.hasError shouldBe false
            viewModel.uiState.value.isLoading shouldBe false
        }

        @Test
        fun `listener on with zero enabled apps reports zero monitored apps`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns flowOf(emptyList())
            val viewModel = createViewModel(listenerEnabled = true)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.monitoring shouldBe MonitoringStatus(isListenerEnabled = true, monitoredAppCount = 0, ruleCount = 0)
        }
    }

    private fun createSuggestionUi(sampleNotificationId: String = "n-1") = RecurringSuggestionUi(
        packageName = "com.test.app",
        appName = "Test App",
        normalizedTitleKey = "your order",
        sampleTitle = "Your order #1",
        sampleContent = null,
        sampleNotificationId = sampleNotificationId,
        occurrences = 5,
        coverage = RuleCoverage.UNCOVERED,
    )

    private fun createActivity(id: String, notificationId: String, title: String?, content: String?) = RecentActivity(
        executionId = id,
        ruleId = "r1",
        ruleName = "Rule",
        notificationId = notificationId,
        notificationTitle = title,
        notificationContent = content,
        packageName = "com.test.app",
        appName = "Test App",
        executedAt = 0L,
    )
}
