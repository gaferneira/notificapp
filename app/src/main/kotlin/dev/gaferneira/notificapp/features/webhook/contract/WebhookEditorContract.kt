package dev.gaferneira.notificapp.features.webhook.contract

import dev.gaferneira.notificapp.domain.model.WebhookTestResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Contract for the Webhook Editor screen: create or edit a webhook, and send a test payload.
 */
object WebhookEditorContract {

    /** Auth mechanism choice surfaced in the UI, mirroring [dev.gaferneira.notificapp.domain.model.WebhookAuth]. */
    enum class AuthTypeUi { NONE, API_KEY_HEADER, BEARER_TOKEN }

    data class UiState(
        /** Loaded webhook id in edit mode, null when creating a new webhook. MUST be preserved
         * on save - see design.md's editor ViewModel notes. */
        val id: String? = null,
        val name: String = "",
        val url: String = "",
        /** Ordered header key/value rows, kept pre-collapse so duplicate-key detection can run
         * against them before they become a `Map`. */
        val headerRows: ImmutableList<Pair<String, String>> = persistentListOf(),
        val authType: AuthTypeUi = AuthTypeUi.NONE,
        val authHeaderName: String = "X-API-Key",
        val authValue: String = "",
        val isLoading: Boolean = false,
        val isSending: Boolean = false,
        val isSaving: Boolean = false,
        val errors: ImmutableList<String> = persistentListOf(),
    )

    sealed class UiEvent {
        /** Triggers loading an existing webhook by id; a null id is a no-op (create mode). */
        data class LoadWebhook(val webhookId: String?) : UiEvent()
        data class OnNameChanged(val name: String) : UiEvent()
        data class OnUrlChanged(val url: String) : UiEvent()
        data class OnAuthTypeChanged(val authType: AuthTypeUi) : UiEvent()
        data class OnAuthHeaderNameChanged(val headerName: String) : UiEvent()
        data class OnAuthValueChanged(val value: String) : UiEvent()
        data object OnAddHeaderRow : UiEvent()
        data class OnHeaderRowKeyChanged(val index: Int, val key: String) : UiEvent()
        data class OnHeaderRowValueChanged(val index: Int, val value: String) : UiEvent()
        data class OnRemoveHeaderRow(val index: Int) : UiEvent()
        data object OnSave : UiEvent()
        data object OnSendTestClicked : UiEvent()
        data object OnBackClicked : UiEvent()
        data object OnDismissError : UiEvent()
    }

    sealed class UiEffect {
        data class ShowTestResult(val result: WebhookTestResult) : UiEffect()
        data class ShowError(val message: String) : UiEffect()
        data object NavigateBack : UiEffect()
    }
}
