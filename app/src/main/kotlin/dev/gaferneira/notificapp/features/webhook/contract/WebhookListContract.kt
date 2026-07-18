package dev.gaferneira.notificapp.features.webhook.contract

import dev.gaferneira.notificapp.domain.model.Webhook
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Contract for the Webhook List screen: view, edit, and delete user-defined webhooks.
 */
object WebhookListContract {

    data class UiState(
        val webhooks: ImmutableList<Webhook> = persistentListOf(),
        val isLoading: Boolean = true,
        val error: String? = null,
        /** Id of the webhook pending a delete confirmation, or null if none. */
        val pendingDeleteId: String? = null,
    )

    sealed class UiEvent {
        data object OnAddClicked : UiEvent()
        data class OnEditClicked(val id: String) : UiEvent()
        data class OnDeleteClicked(val id: String) : UiEvent()
        data object OnConfirmDelete : UiEvent()
        data object OnDismissDeleteConfirmation : UiEvent()
        data object OnDismissError : UiEvent()
    }

    sealed class UiEffect {
        data class NavigateToEditor(val webhookId: String?) : UiEffect()
        data class ShowError(val message: String) : UiEffect()
    }
}
