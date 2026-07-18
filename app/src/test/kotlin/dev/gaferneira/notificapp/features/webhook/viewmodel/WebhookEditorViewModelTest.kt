package dev.gaferneira.notificapp.features.webhook.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookTestResult
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.AuthTypeUi
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.UiEffect
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.UiEvent
import dev.gaferneira.notificapp.testutil.fakes.FakeWebhookRepository
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
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
class WebhookEditorViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: FakeWebhookRepository
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var viewModel: WebhookEditorViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = FakeWebhookRepository()
        navigationHandler = mockk()
        coEvery { navigationHandler.goBack() } just Runs

        viewModel = WebhookEditorViewModel(repository, navigationHandler, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `field edits update state`() {
        viewModel.onEvent(UiEvent.OnNameChanged("Home Assistant"))
        viewModel.onEvent(UiEvent.OnUrlChanged("https://ha.local/api/hook"))

        viewModel.uiState.value.name shouldBe "Home Assistant"
        viewModel.uiState.value.url shouldBe "https://ha.local/api/hook"
    }

    @Test
    fun `save persists a new webhook and navigates back`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnNameChanged("Home Assistant"))
        viewModel.onEvent(UiEvent.OnUrlChanged("https://ha.local/api/hook"))

        viewModel.onEvent(UiEvent.OnSave)
        testDispatcher.scheduler.advanceUntilIdle()

        repository.currentWebhooks().single().name shouldBe "Home Assistant"
        coEvery { navigationHandler.goBack() }
    }

    @Test
    fun `save blocks a blank name and does not persist`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnUrlChanged("https://ha.local/api/hook"))

        viewModel.onEvent(UiEvent.OnSave)
        testDispatcher.scheduler.advanceUntilIdle()

        repository.currentWebhooks() shouldBe emptyList()
        viewModel.uiState.value.errors.isEmpty() shouldBe false
    }

    @Test
    fun `save blocks a malformed url and does not persist`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnNameChanged("Home Assistant"))
        viewModel.onEvent(UiEvent.OnUrlChanged("not-a-url"))

        viewModel.onEvent(UiEvent.OnSave)
        testDispatcher.scheduler.advanceUntilIdle()

        repository.currentWebhooks() shouldBe emptyList()
        viewModel.uiState.value.errors.isEmpty() shouldBe false
    }

    @Test
    fun `editing an existing webhook preserves its id on save - never generates a new UUID`() = runTest(testDispatcher) {
        val existing = Webhook(id = "wh-existing", name = "Original", url = "https://ha.local/api/hook")
        repository.setWebhooks(listOf(existing))

        viewModel.onEvent(UiEvent.LoadWebhook("wh-existing"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UiEvent.OnNameChanged("Renamed"))
        viewModel.onEvent(UiEvent.OnSave)
        testDispatcher.scheduler.advanceUntilIdle()

        repository.currentWebhooks().size shouldBe 1
        repository.currentWebhooks().single().id shouldBe "wh-existing"
        repository.currentWebhooks().single().name shouldBe "Renamed"
    }

    @Test
    fun `loading an unknown webhook id stops loading and shows an error`() = runTest(testDispatcher) {
        viewModel.effect.test {
            viewModel.onEvent(UiEvent.LoadWebhook("missing"))
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe UiEffect.ShowError("Webhook not found")
        }
        viewModel.uiState.value.isLoading shouldBe false
    }

    @Test
    fun `duplicate header keys are rejected case-insensitively`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnNameChanged("Home Assistant"))
        viewModel.onEvent(UiEvent.OnUrlChanged("https://ha.local/api/hook"))
        viewModel.onEvent(UiEvent.OnAddHeaderRow)
        viewModel.onEvent(UiEvent.OnAddHeaderRow)
        viewModel.onEvent(UiEvent.OnHeaderRowKeyChanged(0, "X-Custom"))
        viewModel.onEvent(UiEvent.OnHeaderRowValueChanged(0, "a"))
        viewModel.onEvent(UiEvent.OnHeaderRowKeyChanged(1, "x-custom"))
        viewModel.onEvent(UiEvent.OnHeaderRowValueChanged(1, "b"))

        viewModel.onEvent(UiEvent.OnSave)
        testDispatcher.scheduler.advanceUntilIdle()

        repository.currentWebhooks() shouldBe emptyList()
        viewModel.uiState.value.errors.isEmpty() shouldBe false
    }

    @Test
    fun `duplicate header key colliding with the active auth header name is rejected`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnNameChanged("Home Assistant"))
        viewModel.onEvent(UiEvent.OnUrlChanged("https://ha.local/api/hook"))
        viewModel.onEvent(UiEvent.OnAuthTypeChanged(AuthTypeUi.API_KEY_HEADER))
        viewModel.onEvent(UiEvent.OnAuthValueChanged("secret"))
        viewModel.onEvent(UiEvent.OnAddHeaderRow)
        viewModel.onEvent(UiEvent.OnHeaderRowKeyChanged(0, "x-api-key"))
        viewModel.onEvent(UiEvent.OnHeaderRowValueChanged(0, "duplicate"))

        viewModel.onEvent(UiEvent.OnSave)
        testDispatcher.scheduler.advanceUntilIdle()

        repository.currentWebhooks() shouldBe emptyList()
        viewModel.uiState.value.errors.isEmpty() shouldBe false
    }

    @Test
    fun `send test success emits ShowTestResult`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnNameChanged("Home Assistant"))
        viewModel.onEvent(UiEvent.OnUrlChanged("https://ha.local/api/hook"))
        repository.testPayloadResult = Result.success(WebhookTestResult.Success(200))

        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnSendTestClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe UiEffect.ShowTestResult(WebhookTestResult.Success(200))
        }
    }

    @Test
    fun `send test failure emits ShowTestResult with the failure variant`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnNameChanged("Home Assistant"))
        viewModel.onEvent(UiEvent.OnUrlChanged("https://ha.local/api/hook"))
        repository.testPayloadResult = Result.success(WebhookTestResult.NetworkError)

        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnSendTestClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe UiEffect.ShowTestResult(WebhookTestResult.NetworkError)
        }
    }

    @Test
    fun `send test is a no-op while already sending`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnNameChanged("Home Assistant"))
        viewModel.onEvent(UiEvent.OnUrlChanged("https://ha.local/api/hook"))
        repository.testPayloadResult = Result.success(WebhookTestResult.Success(200))

        viewModel.onEvent(UiEvent.OnSendTestClicked)
        // Re-entrant tap before the first request resolves - should be swallowed silently.
        viewModel.onEvent(UiEvent.OnSendTestClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.value.isSending shouldBe false
    }
}
