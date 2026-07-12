package dev.gaferneira.notificapp.features.onboarding.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.OnboardingStep
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEffect
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEvent
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var listenerStatus: NotificationListenerStatusProvider
    private lateinit var navigationHandler: NavigationHandler

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigationHandler = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(enabled: Boolean): OnboardingViewModel {
        listenerStatus = NotificationListenerStatusProvider { enabled }
        return OnboardingViewModel(listenerStatus, navigationHandler)
    }

    @Nested
    inner class InitialStateTests {

        @Test
        fun `initial step is value statement`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.currentStep shouldBe OnboardingStep.VALUE_STATEMENT
        }
    }

    @Nested
    inner class StepTransitionTests {

        @Test
        fun `get started advances to permission explanation`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnGetStartedClicked)

            viewModel.uiState.value.currentStep shouldBe OnboardingStep.PERMISSION_EXPLANATION
        }

        @Test
        fun `back from permission explanation returns to value statement`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(UiEvent.OnGetStartedClicked)

            viewModel.onEvent(UiEvent.OnBackClicked)

            viewModel.uiState.value.currentStep shouldBe OnboardingStep.VALUE_STATEMENT
        }
    }

    @Nested
    inner class GrantAccessTests {

        @Test
        fun `grant access emits OpenNotificationSettings`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnGrantAccessClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe UiEffect.OpenNotificationSettings
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class PermissionCheckTests {

        @Test
        fun `granted permission navigates to app selection`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = true)
            testDispatcher.scheduler.advanceUntilIdle() // drives the delay(100) + navigate

            viewModel.uiState.value.hasNotificationPermission shouldBe true
            coVerify { navigationHandler.clearAndNavigate(Routes.appSelection(isInitialSetup = true)) }
        }

        @Test
        fun `denied permission does not navigate`() = runTest(testDispatcher) {
            createViewModel(enabled = false)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { navigationHandler.clearAndNavigate(any()) }
        }

        @Test
        fun `denied hint stays hidden before access was ever requested`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.CheckPermission)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.showPermissionDeniedHint shouldBe false
        }

        @Test
        fun `denied hint shows only after access was requested`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnGrantAccessClicked) // sets hasRequestedNotificationAccess
            viewModel.onEvent(UiEvent.CheckPermission)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.showPermissionDeniedHint shouldBe true
        }
    }
}
