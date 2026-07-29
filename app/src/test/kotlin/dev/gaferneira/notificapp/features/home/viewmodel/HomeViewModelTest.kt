package dev.gaferneira.notificapp.features.home.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.di.HomeDataSources
import dev.gaferneira.notificapp.core.notification.RecurringNotificationSuggester
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.domain.repository.SuggestionDismissalRepository
import dev.gaferneira.notificapp.features.home.contract.HomeEffect
import dev.gaferneira.notificapp.features.home.contract.HomeEvent
import dev.gaferneira.notificapp.features.home.contract.HomeSection
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
}
