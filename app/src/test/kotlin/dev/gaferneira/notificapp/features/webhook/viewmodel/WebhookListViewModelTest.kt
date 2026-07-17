package dev.gaferneira.notificapp.features.webhook.viewmodel

import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.features.webhook.contract.WebhookListContract.UiEvent
import dev.gaferneira.notificapp.testutil.fakes.FakeWebhookRepository
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
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
class WebhookListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: FakeWebhookRepository
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var viewModel: WebhookListViewModel

    private fun webhook(id: String = "wh-1") = Webhook(id = id, name = "Home Assistant", url = "https://ha.local/api/hook")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        repository = FakeWebhookRepository()
        navigationHandler = mockk()
        coEvery { navigationHandler.navigate(any()) } just Runs

        viewModel = WebhookListViewModel(repository, navigationHandler, testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observe emits the mapped webhook list`() = runTest(testDispatcher) {
        repository.setWebhooks(listOf(webhook()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.value.webhooks.map { it.id } shouldBe listOf("wh-1")
        viewModel.uiState.value.isLoading shouldBe false
    }

    @Test
    fun `OnAddClicked navigates to the editor with a null id`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnAddClicked)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { navigationHandler.navigate(Routes.webhookEditor(null)) }
    }

    @Test
    fun `OnEditClicked navigates to the editor with the webhook id`() = runTest(testDispatcher) {
        viewModel.onEvent(UiEvent.OnEditClicked("wh-1"))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { navigationHandler.navigate(Routes.webhookEditor("wh-1")) }
    }

    @Test
    fun `OnConfirmDelete calls the repository and clears the pending id`() = runTest(testDispatcher) {
        repository.setWebhooks(listOf(webhook()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UiEvent.OnDeleteClicked("wh-1"))
        viewModel.uiState.value.pendingDeleteId shouldBe "wh-1"

        viewModel.onEvent(UiEvent.OnConfirmDelete)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.value.pendingDeleteId shouldBe null
        repository.currentWebhooks() shouldBe emptyList()
    }

    @Test
    fun `OnDismissDeleteConfirmation clears the pending id without deleting`() = runTest(testDispatcher) {
        repository.setWebhooks(listOf(webhook()))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UiEvent.OnDeleteClicked("wh-1"))
        viewModel.onEvent(UiEvent.OnDismissDeleteConfirmation)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.value.pendingDeleteId shouldBe null
        repository.currentWebhooks().map { it.id } shouldBe listOf("wh-1")
    }
}
