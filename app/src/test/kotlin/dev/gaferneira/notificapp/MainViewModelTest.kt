package dev.gaferneira.notificapp

import app.cash.turbine.test
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

    private fun savedApps() = Result.success(listOf(SelectedApp(packageName = "com.a", appName = "A", isEnabled = true)))

    @Test
    fun `first-time user without access resolves to ONBOARDING`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns Result.success(emptyList())

        viewModel.recheckFlowState(isListenerEnabled = false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.ONBOARDING
    }

    @Test
    fun `revoked access with saved apps resolves to MAIN_APP so Home can show its banner`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns savedApps()

        viewModel.recheckFlowState(isListenerEnabled = false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.MAIN_APP
    }

    @Test
    fun `no access and a failed repository read falls back to ONBOARDING`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns Result.failure(IllegalStateException("db error"))

        viewModel.recheckFlowState(isListenerEnabled = false)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.ONBOARDING
    }

    @Test
    fun `access granted with apps resolves to MAIN_APP`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns savedApps()

        viewModel.recheckFlowState(isListenerEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.MAIN_APP
    }

    @Test
    fun `access granted with zero saved apps resolves to APP_SELECTION, never Home`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns Result.success(emptyList())

        viewModel.recheckFlowState(isListenerEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.APP_SELECTION
    }

    @Test
    fun `access granted with a failed repository read resolves to APP_SELECTION`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns Result.failure(IllegalStateException("db error"))

        viewModel.recheckFlowState(isListenerEnabled = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.appFlowState.value shouldBe AppFlowState.APP_SELECTION
    }

    @Test
    fun `granting access moves ONBOARDING to APP_SELECTION exactly once`() = runTest(testDispatcher) {
        coEvery { repository.getAllApps() } returns Result.success(emptyList())

        viewModel.appFlowState.test {
            awaitItem() shouldBe null
            viewModel.recheckFlowState(isListenerEnabled = false)
            awaitItem() shouldBe AppFlowState.ONBOARDING

            viewModel.recheckFlowState(isListenerEnabled = true)
            awaitItem() shouldBe AppFlowState.APP_SELECTION

            // A repeated resume re-check emits no duplicate transition
            viewModel.recheckFlowState(isListenerEnabled = true)
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }
}
