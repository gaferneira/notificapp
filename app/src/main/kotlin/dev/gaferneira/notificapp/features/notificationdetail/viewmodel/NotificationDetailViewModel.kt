package dev.gaferneira.notificapp.features.notificationdetail.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.extraction.ExtractionPreviewer
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.LoadStatus
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.Message
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.PreviewFailure
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.RuleRef
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEffect
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Notification Detail screen.
 *
 * Observes the notification and the rule executions that matched it (so a deletion while the
 * screen is open shows a Deleted state), resolves rule/field/action names with one batched rule
 * lookup per emission, and offers a read-only "test current rules" preview that can optionally
 * refresh the stored extracted data without touching executions, outcomes or actions.
 */
@HiltViewModel
class NotificationDetailViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val ruleExecutionRepository: RuleExecutionRepository,
    private val ruleRepository: RuleRepository,
    private val extractionPreviewer: ExtractionPreviewer,
    private val navigationHandler: NavigationHandler,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    private var notificationId: String? = null
    private var observeJob: Job? = null
    private var previewJob: Job? = null
    private var applyJob: Job? = null

    /** Set once the user's own delete is in flight, so the resulting "gone" emission is not shown as Deleted. */
    private var isDeletingByUser = false

    /**
     * Set the notification ID to show. Switching to another id cancels everything in flight and
     * resets the state.
     */
    fun setNotificationId(id: String) {
        if (notificationId == id) return
        notificationId = id
        startObserving(id)
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnBackClicked -> viewModelScope.launch { navigationHandler.goBack() }
            is UiEvent.OnCreateRuleClicked -> notificationId?.let { id ->
                viewModelScope.launch { navigationHandler.navigate(Routes.ruleEditor(notificationId = id)) }
            }
            is UiEvent.OnRetryClicked -> retry()
            is UiEvent.OnTestRulesClicked -> testCurrentRules()
            is UiEvent.OnDismissPreview -> setState { copy(preview = null, previewFailure = null) }
            is UiEvent.OnUpdateExtractedDataClicked -> applyPreview()
            is UiEvent.OnDeleteNotificationClicked -> deleteNotification()
            is UiEvent.OnOpenRuleClicked -> viewModelScope.launch { navigationHandler.navigate(Routes.ruleDetails(event.ruleId)) }
            is UiEvent.OnOpenSourceAppClicked -> uiState.value.sourcePackageName?.let { sendEffect(UiEffect.OpenApp(it)) }
        }
    }

    private fun retry() {
        if (uiState.value.loadStatus != LoadStatus.ERROR) return
        notificationId?.let { startObserving(it) }
    }

    private fun startObserving(id: String) {
        observeJob?.cancel()
        previewJob?.cancel()
        applyJob?.cancel()
        isDeletingByUser = false
        setState { UiState() }

        observeJob = viewModelScope.launch(ioDispatcher) {
            var hasLoaded = false
            combine(
                notificationRepository.observeNotification(id),
                ruleExecutionRepository.observeExecutionsForNotification(id),
            ) { notification, executions -> notification to executions }
                .catch { e ->
                    Timber.e(e, "Failed to observe notification $id")
                    setState { copy(loadStatus = LoadStatus.ERROR) }
                }
                .collect { (notification, executions) ->
                    if (notification == null) {
                        if (!isDeletingByUser) onGone(hasLoaded)
                    } else {
                        hasLoaded = true
                        onEmission(notification, executions)
                    }
                }
        }
    }

    private fun onGone(wasLoaded: Boolean) {
        setState {
            UiState(loadStatus = if (wasLoaded) LoadStatus.DELETED else LoadStatus.NOT_FOUND)
        }
    }

    private suspend fun onEmission(notification: Notification, executions: List<RuleExecution>) {
        // One batched lookup per emission instead of one query per execution.
        val ruleIds = executions.map { it.ruleId }.distinct()
        val rules: Map<String, Rule> = if (ruleIds.isEmpty()) {
            emptyMap()
        } else {
            ruleRepository.getRules(ruleIds).getOrElse { e ->
                Timber.e(e, "Failed to load rules for notification ${notification.id}")
                setState { copy(loadStatus = LoadStatus.ERROR) }
                return
            }.associateBy { it.id }
        }

        val redactingExecution = if (notification.isContentRedacted) {
            executions.firstOrNull { it.redactedContent(rules[it.ruleId]) }
        } else {
            null
        }
        val details = executions.map { execution ->
            buildExecutionDetails(execution, rules[execution.ruleId], isRedactionSource = execution === redactingExecution)
        }

        setState {
            copy(
                loadStatus = LoadStatus.LOADED,
                notification = notification,
                executions = details,
                redactedByRule = redactingExecution?.let { e -> rules[e.ruleId]?.let { RuleRef(it.id, it.name) } },
                // Once the content is gone there is nothing to test against.
                preview = if (notification.isContentRedacted) null else preview,
            )
        }
    }

    /** Mirrors `ProcessNotificationUseCase.qualifiesForRedaction`: what makes a rule scrub content. */
    private fun RuleExecution.redactedContent(rule: Rule?): Boolean = rule != null &&
        rule.deleteRawContentAfterExtraction &&
        !wasDryRun &&
        extractedData.isNotEmpty()

    /**
     * Read-only preview: evaluate the current active rules against this notification with the
     * pure engine (same as the rule backtest). Persists nothing and dispatches no action.
     */
    private fun testCurrentRules() {
        val state = uiState.value
        val notification = state.notification ?: return
        if (state.isContentRedacted) {
            setState { copy(preview = null, previewFailure = PreviewFailure.CONTENT_REDACTED) }
        } else if (state.canTestRules) {
            runPreview(notification)
        }
    }

    private fun runPreview(notification: Notification) {
        previewJob?.cancel()
        setState { copy(isPreviewLoading = true, previewFailure = null) }
        previewJob = viewModelScope.launch(ioDispatcher) {
            try {
                val rules = ruleRepository.getRulesForApp(notification.packageName).getOrThrow().filter { it.isActive }
                val executions = uiState.value.executions
                val preview = extractionPreviewer.preview(
                    notification = notification,
                    rules = rules,
                    executions = executions.map { it.execution },
                    ruleNames = executions.associate { it.ruleId to it.ruleName },
                )
                setState { copy(isPreviewLoading = false, preview = preview) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Timber.e(e, "Failed to test current rules for notification ${notification.id}")
                setState { copy(isPreviewLoading = false, preview = null, previewFailure = PreviewFailure.EVALUATION_FAILED) }
            }
        }
    }

    /** Write the previewed values into existing executions in one transaction. */
    private fun applyPreview() {
        val state = uiState.value
        val preview = state.preview ?: return
        if (!state.canApplyPreview) return

        setState { copy(isApplyingUpdate = true) }
        applyJob = viewModelScope.launch(ioDispatcher) {
            ruleExecutionRepository.updateExtractedData(preview.updates)
                .onSuccess {
                    setState { copy(isApplyingUpdate = false, preview = null) }
                    sendEffect(UiEffect.ShowMessage(Message.EXTRACTED_DATA_UPDATED))
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to update extracted data")
                    setState { copy(isApplyingUpdate = false) }
                    sendEffect(UiEffect.ShowMessage(Message.UPDATE_FAILED))
                }
        }
    }

    private fun deleteNotification() {
        val id = notificationId ?: return
        isDeletingByUser = true
        viewModelScope.launch(ioDispatcher) {
            notificationRepository.deleteNotification(id)
                .onSuccess { sendEffect(UiEffect.NavigateBack) }
                .onFailure { e ->
                    Timber.e(e, "Failed to delete notification $id")
                    isDeletingByUser = false
                    sendEffect(UiEffect.ShowMessage(Message.DELETE_FAILED))
                }
        }
    }
}
