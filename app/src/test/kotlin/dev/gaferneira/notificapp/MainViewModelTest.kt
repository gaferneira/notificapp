package dev.gaferneira.notificapp

import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: SelectedAppRepository = mockk()
    private lateinit var viewModel: MainViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = MainViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts with null appFlowState, meaning still checking`() {
        viewModel.appFlowState.value shouldBe null
    }

    @Test
    fun `recheckFlowState resolves to ONBOARDING when the listener is disabled`() = runTest(testDispatcher) {
        viewModel.recheckFlowState(isListenerEnabled = false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.ONBOARDING
    }

    @Test
    fun `recheckFlowState resolves to MAIN_APP when the listener is enabled and apps are selected`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns Result.success(listOf(SelectedApp(packageName = "com.a", appName = "A", isEnabled = true)))

        viewModel.recheckFlowState(isListenerEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.MAIN_APP
    }

    @Test
    fun `recheckFlowState resolves to APP_SELECTION when the listener is enabled but no apps are selected`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns Result.success(emptyList())

        viewModel.recheckFlowState(isListenerEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.APP_SELECTION
    }

    @Test
    fun `recheckFlowState resolves to APP_SELECTION when the repository call fails`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns Result.failure(IllegalStateException("db error"))

        viewModel.recheckFlowState(isListenerEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.APP_SELECTION
    }
}
