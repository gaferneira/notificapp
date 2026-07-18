package dev.gaferneira.notificapp.features.webhook.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.HttpMethod
import dev.gaferneira.notificapp.domain.model.ValidationError
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookAuth
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.AuthTypeUi
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.HttpMethodUi
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.UiEffect
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.UiEvent
import dev.gaferneira.notificapp.features.webhook.contract.WebhookEditorContract.UiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Webhook Editor screen: create/edit a webhook and send a one-shot test
 * payload. The caller (the Compose screen) drives loading via [UiEvent.LoadWebhook] with the
 * `webhookId` navigation argument, mirroring `RuleEditorViewModel`'s `LoadRule` pattern.
 *
 * MUST preserve the loaded [UiState.id] when building the [Webhook] for save in edit mode -
 * never call the [Webhook] constructor without an explicit id, or the default
 * `UUID.randomUUID()` would silently turn an edit into a duplicate insert (design.md).
 */
@HiltViewModel
class WebhookEditorViewModel @Inject constructor(
    private val webhookRepository: WebhookRepository,
    private val navigationHandler: NavigationHandler,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    /**
     * Delivery-status fields are not user-editable, so they live outside [UiState] and are
     * carried forward from the loaded webhook into [buildWebhook] - otherwise saving an edit
     * would silently reset them to their domain-model defaults (UNKNOWN/null).
     */
    private var loadedDeliveryStatus: WebhookDeliveryStatus = WebhookDeliveryStatus.UNKNOWN
    private var loadedDeliveryAt: Long? = null

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.LoadWebhook -> if (event.webhookId != null) loadWebhook(event.webhookId)
            is UiEvent.OnNameChanged,
            is UiEvent.OnUrlChanged,
            is UiEvent.OnMethodChanged,
            is UiEvent.OnAuthTypeChanged,
            is UiEvent.OnAuthHeaderNameChanged,
            is UiEvent.OnAuthValueChanged,
            -> onFieldChangedEvent(event)
            is UiEvent.OnAddHeaderRow,
            is UiEvent.OnHeaderRowKeyChanged,
            is UiEvent.OnHeaderRowValueChanged,
            is UiEvent.OnRemoveHeaderRow,
            -> onHeaderRowEvent(event)
            is UiEvent.OnAddQueryParamRow,
            is UiEvent.OnQueryParamRowKeyChanged,
            is UiEvent.OnQueryParamRowValueChanged,
            is UiEvent.OnRemoveQueryParamRow,
            -> onQueryParamRowEvent(event)
            is UiEvent.OnSave -> save()
            is UiEvent.OnSendTestClicked -> sendTest()
            is UiEvent.OnBackClicked -> navigateBack()
            is UiEvent.OnDismissError -> setState { copy(errors = emptyList<String>().toImmutableList()) }
        }
    }

    /** Simple field-editing events, split out of [onEvent] to keep its complexity within budget. */
    private fun onFieldChangedEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnNameChanged -> setState { copy(name = event.name) }
            is UiEvent.OnUrlChanged -> setState { copy(url = event.url) }
            is UiEvent.OnMethodChanged -> setState { copy(method = event.method) }
            is UiEvent.OnAuthTypeChanged -> setState { copy(authType = event.authType) }
            is UiEvent.OnAuthHeaderNameChanged -> setState { copy(authHeaderName = event.headerName) }
            is UiEvent.OnAuthValueChanged -> setState { copy(authValue = event.value) }
            else -> Unit
        }
    }

    /** Header-row-editing events, split out of [onEvent] to keep its complexity within budget. */
    private fun onHeaderRowEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnAddHeaderRow -> setState { copy(headerRows = (headerRows + ("" to "")).toImmutableList()) }
            is UiEvent.OnHeaderRowKeyChanged -> updateHeaderRow(event.index) { it.copy(first = event.key) }
            is UiEvent.OnHeaderRowValueChanged -> updateHeaderRow(event.index) { it.copy(second = event.value) }
            is UiEvent.OnRemoveHeaderRow -> setState {
                copy(headerRows = headerRows.filterIndexed { index, _ -> index != event.index }.toImmutableList())
            }
            else -> Unit
        }
    }

    private fun updateHeaderRow(index: Int, transform: (Pair<String, String>) -> Pair<String, String>) {
        setState {
            val updated = headerRows.mapIndexed { i, row -> if (i == index) transform(row) else row }
            copy(headerRows = updated.toImmutableList())
        }
    }

    /** Query-param-row-editing events, split out of [onEvent] to keep its complexity within budget. */
    private fun onQueryParamRowEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnAddQueryParamRow -> setState { copy(queryParamRows = (queryParamRows + ("" to "")).toImmutableList()) }
            is UiEvent.OnQueryParamRowKeyChanged -> updateQueryParamRow(event.index) { it.copy(first = event.key) }
            is UiEvent.OnQueryParamRowValueChanged -> updateQueryParamRow(event.index) { it.copy(second = event.value) }
            is UiEvent.OnRemoveQueryParamRow -> setState {
                copy(queryParamRows = queryParamRows.filterIndexed { index, _ -> index != event.index }.toImmutableList())
            }
            else -> Unit
        }
    }

    private fun updateQueryParamRow(index: Int, transform: (Pair<String, String>) -> Pair<String, String>) {
        setState {
            val updated = queryParamRows.mapIndexed { i, row -> if (i == index) transform(row) else row }
            copy(queryParamRows = updated.toImmutableList())
        }
    }

    private fun loadWebhook(id: String) {
        setState { copy(isLoading = true) }
        viewModelScope.launch(ioDispatcher) {
            webhookRepository.getWebhook(id)
                .onSuccess { webhook ->
                    setState { copy(isLoading = false) }
                    if (webhook == null) {
                        Timber.w("Webhook not found: $id")
                        sendEffect(UiEffect.ShowError("Webhook not found"))
                        return@onSuccess
                    }
                    applyWebhookToState(webhook)
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to load webhook: $id")
                    setState { copy(isLoading = false) }
                    sendEffect(UiEffect.ShowError("Failed to load webhook"))
                }
        }
    }

    private fun applyWebhookToState(webhook: Webhook) {
        val (authType, authHeaderName, authValue) = when (val auth = webhook.auth) {
            is WebhookAuth.None -> Triple(AuthTypeUi.NONE, "X-API-Key", "")
            is WebhookAuth.ApiKeyHeader -> Triple(AuthTypeUi.API_KEY_HEADER, auth.headerName, auth.value)
            is WebhookAuth.BearerToken -> Triple(AuthTypeUi.BEARER_TOKEN, "X-API-Key", auth.value)
        }
        loadedDeliveryStatus = webhook.lastDeliveryStatus
        loadedDeliveryAt = webhook.lastDeliveryAt
        setState {
            copy(
                id = webhook.id,
                name = webhook.name,
                url = webhook.url,
                headerRows = webhook.headers.map { it.key to it.value }.toImmutableList(),
                authType = authType,
                authHeaderName = authHeaderName,
                authValue = authValue,
                method = webhook.method.toUi(),
                queryParamRows = webhook.queryParams.map { it.key to it.value }.toImmutableList(),
            )
        }
    }

    /** Case-insensitive duplicate-key check against the pre-collapse [UiState.headerRows]. */
    private fun findDuplicateHeaderKeyErrors(state: UiState): List<String> {
        val errors = mutableListOf<String>()
        val trimmedKeys = state.headerRows.map { it.first.trim() }.filter { it.isNotEmpty() }
        val duplicates = trimmedKeys.groupBy { it.lowercase() }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            errors += "Duplicate header key(s): ${duplicates.values.map { it.first() }.joinToString(", ")}"
        }
        val activeAuthHeaderName = when (state.authType) {
            AuthTypeUi.NONE -> null
            AuthTypeUi.API_KEY_HEADER -> state.authHeaderName
            AuthTypeUi.BEARER_TOKEN -> "Authorization"
        }
        if (activeAuthHeaderName != null && trimmedKeys.any { it.equals(activeAuthHeaderName, ignoreCase = true) }) {
            errors += "Header key collides with the active auth header ($activeAuthHeaderName)"
        }
        return errors
    }

    /** Case-insensitive duplicate-key check against the pre-collapse [UiState.queryParamRows]. */
    private fun findDuplicateQueryParamKeyErrors(state: UiState): List<String> {
        val errors = mutableListOf<String>()
        val trimmedKeys = state.queryParamRows.map { it.first.trim() }.filter { it.isNotEmpty() }
        val duplicates = trimmedKeys.groupBy { it.lowercase() }.filterValues { it.size > 1 }
        if (duplicates.isNotEmpty()) {
            errors += "Duplicate query parameter key(s): ${duplicates.values.map { it.first() }.joinToString(", ")}"
        }
        return errors
    }

    private fun buildWebhook(state: UiState): Webhook {
        val auth = when (state.authType) {
            AuthTypeUi.NONE -> WebhookAuth.None
            AuthTypeUi.API_KEY_HEADER -> WebhookAuth.ApiKeyHeader(headerName = state.authHeaderName, value = state.authValue)
            AuthTypeUi.BEARER_TOKEN -> WebhookAuth.BearerToken(value = state.authValue)
        }
        val headers = state.headerRows
            .filter { it.first.isNotBlank() }
            .associate { it.first.trim() to it.second }
        val queryParams = state.queryParamRows
            .filter { it.first.isNotBlank() }
            .associate { it.first.trim() to it.second }

        return if (state.id != null) {
            Webhook(
                id = state.id,
                name = state.name,
                url = state.url,
                headers = headers,
                auth = auth,
                method = state.method.toDomain(),
                queryParams = queryParams,
                lastDeliveryStatus = loadedDeliveryStatus,
                lastDeliveryAt = loadedDeliveryAt,
            )
        } else {
            Webhook(
                name = state.name,
                url = state.url,
                headers = headers,
                auth = auth,
                method = state.method.toDomain(),
                queryParams = queryParams,
            )
        }
    }

    private fun save() {
        val state = uiState.value
        val duplicateErrors = findDuplicateHeaderKeyErrors(state) + findDuplicateQueryParamKeyErrors(state)
        if (duplicateErrors.isNotEmpty()) {
            setState { copy(errors = duplicateErrors.toImmutableList()) }
            return
        }

        val webhook = buildWebhook(state)
        val validationErrors = webhook.validate()
        if (validationErrors.isNotEmpty()) {
            setState { copy(errors = validationErrors.map { it.toMessage() }.toImmutableList()) }
            return
        }

        setState { copy(isSaving = true, errors = emptyList<String>().toImmutableList()) }
        viewModelScope.launch(ioDispatcher) {
            webhookRepository.saveWebhook(webhook)
                .onSuccess {
                    setState { copy(isSaving = false) }
                    navigateBack()
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to save webhook: ${webhook.id}")
                    setState { copy(isSaving = false, errors = listOf("Failed to save webhook").toImmutableList()) }
                }
        }
    }

    /** No-op while already sending - a debounce/re-entrancy guard against overlapping requests. */
    private fun sendTest() {
        val state = uiState.value
        if (state.isSending) return

        val webhook = buildWebhook(state)
        val validationErrors = webhook.validate()
        if (validationErrors.isNotEmpty()) {
            setState { copy(errors = validationErrors.map { it.toMessage() }.toImmutableList()) }
            return
        }

        setState { copy(isSending = true) }
        viewModelScope.launch(ioDispatcher) {
            webhookRepository.sendTestPayload(webhook)
                .onSuccess { result ->
                    setState { copy(isSending = false) }
                    sendEffect(UiEffect.ShowTestResult(result))
                }
                .onFailure { e ->
                    Timber.e(e, "Unexpected error sending test payload for webhook: ${webhook.id}")
                    setState { copy(isSending = false) }
                    sendEffect(UiEffect.ShowError("Failed to send test payload"))
                }
        }
    }

    private fun navigateBack() {
        viewModelScope.launch {
            navigationHandler.goBack()
        }
    }
}

private fun HttpMethodUi.toDomain(): HttpMethod = when (this) {
    HttpMethodUi.GET -> HttpMethod.GET
    HttpMethodUi.POST -> HttpMethod.POST
    HttpMethodUi.PUT -> HttpMethod.PUT
    HttpMethodUi.PATCH -> HttpMethod.PATCH
    HttpMethodUi.DELETE -> HttpMethod.DELETE
}

private fun HttpMethod.toUi(): HttpMethodUi = when (this) {
    HttpMethod.GET -> HttpMethodUi.GET
    HttpMethod.POST -> HttpMethodUi.POST
    HttpMethod.PUT -> HttpMethodUi.PUT
    HttpMethod.PATCH -> HttpMethodUi.PATCH
    HttpMethod.DELETE -> HttpMethodUi.DELETE
}

private fun ValidationError.toMessage(): String = when (this) {
    is ValidationError.BlankName -> "Name cannot be blank"
    is ValidationError.MalformedUrl -> "URL must be a valid http:// or https:// address"
    is ValidationError.HeaderAuthCollision -> "A custom header collides with the active auth header"
}
