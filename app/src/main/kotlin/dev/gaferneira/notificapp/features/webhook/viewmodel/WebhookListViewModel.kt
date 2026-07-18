package dev.gaferneira.notificapp.features.webhook.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import dev.gaferneira.notificapp.features.webhook.contract.WebhookListContract.UiEffect
import dev.gaferneira.notificapp.features.webhook.contract.WebhookListContract.UiEvent
import dev.gaferneira.notificapp.features.webhook.contract.WebhookListContract.UiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Webhook List screen. Observes all webhooks continuously and delegates
 * add/edit navigation to [NavigationHandler].
 */
@HiltViewModel
class WebhookListViewModel @Inject constructor(
    private val webhookRepository: WebhookRepository,
    private val navigationHandler: NavigationHandler,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    init {
        observeWebhooks()
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnAddClicked -> navigateToEditor(webhookId = null)
            is UiEvent.OnEditClicked -> navigateToEditor(webhookId = event.id)
            is UiEvent.OnDeleteClicked -> setState { copy(pendingDeleteId = event.id) }
            is UiEvent.OnConfirmDelete -> confirmDelete()
            is UiEvent.OnDismissDeleteConfirmation -> setState { copy(pendingDeleteId = null) }
            is UiEvent.OnDismissError -> setState { copy(error = null) }
        }
    }

    private fun navigateToEditor(webhookId: String?) {
        viewModelScope.launch {
            navigationHandler.navigate(Routes.webhookEditor(webhookId))
        }
    }

    private fun confirmDelete() {
        val id = uiState.value.pendingDeleteId ?: return
        setState { copy(pendingDeleteId = null) }
        viewModelScope.launch(ioDispatcher) {
            webhookRepository.deleteWebhook(id)
                .onFailure { e ->
                    Timber.e(e, "Failed to delete webhook: $id")
                    sendEffect(UiEffect.ShowError("Failed to delete webhook"))
                }
        }
    }

    private fun observeWebhooks() {
        viewModelScope.launch {
            webhookRepository.observeWebhooks()
                .flowOn(ioDispatcher)
                .catch { e ->
                    Timber.e(e, "Error observing webhooks")
                    setState { copy(isLoading = false, error = "Failed to load webhooks") }
                }
                .collect { webhooks ->
                    setState {
                        copy(webhooks = webhooks.toImmutableList(), isLoading = false)
                    }
                }
        }
    }
}
