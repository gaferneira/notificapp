package dev.gaferneira.notificapp.features.onboarding.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.OnboardingStep
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEffect
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEvent
import io.kotest.matchers.shouldBe
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        enabled: Boolean,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): OnboardingViewModel {
        listenerStatus = NotificationListenerStatusProvider { enabled }
        return OnboardingViewModel(listenerStatus, savedStateHandle)
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
    inner class BackTests {

        @Test
        fun `back event from permission step returns to value statement`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)
            viewModel.onEvent(UiEvent.OnGetStartedClicked)

            viewModel.onEvent(UiEvent.OnBackClicked)

            viewModel.uiState.value.currentStep shouldBe OnboardingStep.VALUE_STATEMENT
        }
    }

    @Nested
    inner class ProcessDeathTests {

        @Test
        fun `restores the permission step from saved state`() = runTest(testDispatcher) {
            val handle = SavedStateHandle()
            createViewModel(enabled = false, savedStateHandle = handle)
                .onEvent(UiEvent.OnGetStartedClicked)

            val restored = createViewModel(enabled = false, savedStateHandle = handle)

            restored.uiState.value.currentStep shouldBe OnboardingStep.PERMISSION_EXPLANATION
        }

        @Test
        fun `restores the value statement step after going back`() = runTest(testDispatcher) {
            val handle = SavedStateHandle()
            val first = createViewModel(enabled = false, savedStateHandle = handle)
            first.onEvent(UiEvent.OnGetStartedClicked)
            first.onEvent(UiEvent.OnBackClicked)

            createViewModel(enabled = false, savedStateHandle = handle)
                .uiState.value.currentStep shouldBe OnboardingStep.VALUE_STATEMENT
        }

        @Test
        fun `falls back to value statement when saved step is unknown`() = runTest(testDispatcher) {
            val handle = SavedStateHandle(mapOf("onboarding_step" to "REMOVED_STEP"))

            createViewModel(enabled = false, savedStateHandle = handle)
                .uiState.value.currentStep shouldBe OnboardingStep.VALUE_STATEMENT
        }

        @Test
        fun `restored settings-requested flag shows the denied hint on next check`() = runTest(testDispatcher) {
            val handle = SavedStateHandle()
            createViewModel(enabled = false, savedStateHandle = handle)
                .onEvent(UiEvent.OnGrantAccessClicked)

            val restored = createViewModel(enabled = false, savedStateHandle = handle)
            restored.onEvent(UiEvent.CheckPermission)

            restored.uiState.value.showPermissionDeniedHint shouldBe true
        }
    }

    @Nested
    inner class PermissionCheckTests {

        @Test
        fun `initialization does not check the permission, only resume does`() = runTest(testDispatcher) {
            var checks = 0
            val viewModel = OnboardingViewModel(
                {
                    checks++
                    false
                },
                SavedStateHandle(),
            )
            testDispatcher.scheduler.advanceUntilIdle()
            checks shouldBe 0

            viewModel.onEvent(UiEvent.CheckPermission)

            checks shouldBe 1
        }

        @Test
        fun `granted permission leaves navigation to MainViewModel and shows no hint`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = true)
            viewModel.onEvent(UiEvent.OnGrantAccessClicked)

            viewModel.onEvent(UiEvent.CheckPermission)

            viewModel.uiState.value.showPermissionDeniedHint shouldBe false
        }

        @Test
        fun `denied hint stays hidden before access was ever requested`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)

            viewModel.onEvent(UiEvent.CheckPermission)

            viewModel.uiState.value.showPermissionDeniedHint shouldBe false
        }

        @Test
        fun `denied hint shows only after access was requested`() = runTest(testDispatcher) {
            val viewModel = createViewModel(enabled = false)

            viewModel.onEvent(UiEvent.OnGrantAccessClicked) // sets hasRequestedNotificationAccess
            viewModel.onEvent(UiEvent.CheckPermission)

            viewModel.uiState.value.showPermissionDeniedHint shouldBe true
        }
    }
}
