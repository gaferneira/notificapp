package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.notification.action.WebhookPayloadBuilder
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.WEBHOOK_ALL_BUILTINS
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_APP_NAME
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_CONTENT
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TIMESTAMP
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TITLE
import dev.gaferneira.notificapp.domain.model.WEBHOOK_FIELD_ID_PREFIX
import dev.gaferneira.notificapp.domain.model.WEBHOOK_TOKEN_REGEX
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookPayloadMode
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.WebhookConfigContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.WebhookConfigContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.WebhookConfigContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.contract.toWebhookConfigUiModel
import dev.gaferneira.notificapp.features.ruleeditor.domain.WebhookConfigUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/** Default FIELDS-mode built-in selection for a brand-new `SEND_WEBHOOK` action (task 5.6). */
private val DEFAULT_CHECKED_BUILTINS = setOf(
    WEBHOOK_BUILTIN_TITLE,
    WEBHOOK_BUILTIN_CONTENT,
    WEBHOOK_BUILTIN_APP_NAME,
    WEBHOOK_BUILTIN_TIMESTAMP,
)

/** Synthetic sample notification for "Preview payload" - lean synthetic to avoid a DB read in the editor. */
private val PREVIEW_SAMPLE_NOTIFICATION = Notification(
    id = "preview",
    packageName = "com.example.app",
    appName = "Sample App",
    title = "Sample notification title",
    content = "Sample notification content",
    rawContent = "Sample notification content",
    timestamp = System.currentTimeMillis(),
)

/**
 * ViewModel for [dev.gaferneira.notificapp.features.ruleeditor.ui.WebhookConfigBottomSheet].
 *
 * Observes [WebhookRepository.observeWebhooks] continuously so the picker refreshes live after an
 * inline "New webhook" round-trip through `WebhookEditorScreen`, auto-selecting the newest webhook
 * the first time a not-yet-seen id appears while nothing is selected yet (design.md's Rule Editor
 * UI section).
 */
@HiltViewModel
class WebhookConfigViewModel @Inject constructor(
    private val webhookRepository: WebhookRepository,
    private val webhookPayloadBuilder: WebhookPayloadBuilder,
    private val navigationHandler: NavigationHandler,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    private var knownWebhookIds: Set<String>? = null

    /**
     * Set while navigating to `WebhookEditorScreen` for an inline "New webhook" create. The sheet's
     * `LaunchedEffect(Unit)` re-fires [UiEvent.Initialize] when [RuleEditorScreen] re-enters
     * composition on return - without this guard that would blow away the user's already-authored
     * mode/fields/template back to the initial/default snapshot, not just refresh the webhook list.
     */
    private var resumingFromWebhookCreation = false

    init {
        observeWebhooks()
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Initialize -> initialize(event.initial, event.ruleFields)
            is UiEvent.OnWebhookSelected -> setState { copy(config = config.copy(webhookId = event.webhookId)) }
            is UiEvent.OnCreateWebhookClicked -> navigateToWebhookEditor()
            is UiEvent.OnModeChanged -> setState { copy(config = config.copy(mode = event.mode)) }
            is UiEvent.OnBuiltinToggled -> toggleBuiltin(event.token, event.checked)
            is UiEvent.OnFieldToggled -> toggleField(event.fieldId, event.checked)
            is UiEvent.OnTemplateChanged -> setState { copy(config = config.copy(template = event.template)) }
            is UiEvent.OnPreviewClicked -> updatePreview()
            is UiEvent.OnDismissPreview -> setState { copy(previewJson = null, previewWarning = null) }
            is UiEvent.OnConfirmClicked -> sendEffect(UiEffect.ConfirmSave(uiState.value.toRuleAction()))
        }
    }

    private fun initialize(initial: RuleAction?, ruleFields: ImmutableList<RuleField>) {
        if (resumingFromWebhookCreation) {
            resumingFromWebhookCreation = false
            return
        }
        setState {
            if (initial != null) {
                copy(
                    actionId = initial.id,
                    initialIsEnabled = initial.isEnabled,
                    isEditing = true,
                    config = initial.toWebhookConfigUiModel(),
                    ruleFields = ruleFields,
                )
            } else {
                copy(
                    isEditing = false,
                    config = WebhookConfigUiModel(
                        selectedBuiltins = DEFAULT_CHECKED_BUILTINS,
                        selectedFieldIds = ruleFields.map { it.id }.toSet(),
                    ),
                    ruleFields = ruleFields,
                )
            }
        }
    }

    private fun navigateToWebhookEditor() {
        resumingFromWebhookCreation = true
        viewModelScope.launch {
            navigationHandler.navigate(Routes.webhookEditor(webhookId = null))
        }
    }

    private fun toggleBuiltin(token: String, checked: Boolean) {
        setState {
            val updated = if (checked) config.selectedBuiltins + token else config.selectedBuiltins - token
            copy(config = config.copy(selectedBuiltins = updated))
        }
    }

    private fun toggleField(fieldId: String, checked: Boolean) {
        setState {
            val updated = if (checked) config.selectedFieldIds + fieldId else config.selectedFieldIds - fieldId
            copy(config = config.copy(selectedFieldIds = updated))
        }
    }

    private fun updatePreview() {
        val state = uiState.value
        val action = state.toRuleAction()
        val sampleExtractedFields = state.ruleFields.associate { it.name to "Sample ${it.name}" }
        val json = webhookPayloadBuilder.build(PREVIEW_SAMPLE_NOTIFICATION, action, sampleExtractedFields)
        setState { copy(previewJson = json, previewWarning = previewWarningFor(state, json)) }
    }

    private fun previewWarningFor(state: UiState, json: String): String? = when {
        runCatching { Json.parseToJsonElement(json) }.isFailure ->
            "Invalid JSON — check your template for unescaped quotes or a malformed structure."
        state.config.mode != WebhookPayloadMode.TEMPLATE -> null
        else -> unknownTokenWarning(state)
    }

    private fun unknownTokenWarning(state: UiState): String? {
        val knownTokens = WEBHOOK_ALL_BUILTINS.toSet() +
            state.ruleFields.map { "$WEBHOOK_FIELD_ID_PREFIX${it.id}" }
        val usedTokens = WEBHOOK_TOKEN_REGEX.findAll(state.config.template).map { it.groupValues[1] }.toSet()
        val unknown = usedTokens - knownTokens
        return "Unknown token(s): ${unknown.joinToString(", ") { "{{$it}}" }}".takeIf { unknown.isNotEmpty() }
    }

    private fun observeWebhooks() {
        viewModelScope.launch {
            webhookRepository.observeWebhooks()
                .flowOn(ioDispatcher)
                .catch { e -> Timber.e(e, "Error observing webhooks for Send Webhook config sheet") }
                .collect { webhooks ->
                    autoSelectNewestIfNeeded(webhooks.map { it.id }.toSet(), webhooks)
                    setState { copy(webhooks = webhooks.toImmutableList()) }
                }
        }
    }

    /**
     * The first emission just seeds [knownWebhookIds] as the baseline (nothing to "auto-select"
     * yet - that would clobber an existing selection when editing). On every later emission, if a
     * not-yet-seen id appears and nothing is currently selected, pick the newest of those new ids
     * by `createdAt` - the "New webhook" round-trip through `WebhookEditorScreen` this satisfies.
     */
    private fun autoSelectNewestIfNeeded(currentIds: Set<String>, webhooks: List<Webhook>) {
        val previouslyKnown = knownWebhookIds
        knownWebhookIds = currentIds

        val newIds = previouslyKnown?.let { currentIds - it }.orEmpty()
        val canAutoSelect = previouslyKnown != null && newIds.isNotEmpty() && uiState.value.config.webhookId == null
        val newest = if (canAutoSelect) webhooks.filter { it.id in newIds }.maxByOrNull { it.createdAt } else null

        if (newest != null) {
            setState { copy(config = config.copy(webhookId = newest.id)) }
        }
    }
}
