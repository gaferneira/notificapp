package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.extraction.ExtractionResult
import dev.gaferneira.notificapp.core.extraction.FieldExtractor
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.PreviewResult
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the ExtractDataBottomSheet.
 *
 * Owns a draft copy of the rule's extraction fields plus the nested add/edit-field sheet. The draft
 * is committed to the parent (via [UiEffect.Committed]) only when the user confirms; cancelling
 * discards it. Self-contained with no dependencies on the parent RuleEditorViewModel, mirroring
 * [MatchingLogicViewModel].
 *
 * Also owns a preview-only "browse history" affordance: when opened with no entry-flow sample, the
 * user can pick a recent notification from a bounded history list as an override sample. The
 * override never reaches the committed fields and is discarded on confirm/dismiss.
 */
@HiltViewModel
class ExtractDataViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    @Dispatcher(DispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    /** Sample text derived from the entry-flow notification on Init; null when opened to edit a rule. */
    private var entrySampleText: String? = null

    /** Effective text driving preview + auto-generate: entry sample always wins over the override. */
    private fun effectiveSampleText(): String? = entrySampleText ?: uiState.value.overrideNotification?.let { it.content ?: it.title ?: it.rawContent }

    private var previewJob: Job? = null

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Init -> init(event)
            is UiEvent.OnAutoGenerate -> autoGenerate()
            is UiEvent.OnAddFieldClicked -> setState { copy(isFieldSheetVisible = true, editingFieldId = null) }
            is UiEvent.OnEditFieldClicked -> setState {
                copy(isFieldSheetVisible = true, editingFieldId = event.fieldId)
            }
            is UiEvent.OnRemoveFieldClicked -> removeField(event.fieldId)
            is UiEvent.OnFieldSaved -> onFieldSaved(event.field)
            is UiEvent.OnDismissFieldSheet -> setState { copy(isFieldSheetVisible = false, editingFieldId = null) }
            is UiEvent.OnConfirm -> confirm()
            is UiEvent.OnDismiss -> dismiss()
            is UiEvent.OnBrowseHistoryOpened -> onBrowseHistoryOpened(event.targetPackages)
            is UiEvent.OnHistoryNotificationSelected -> onHistoryNotificationSelected(event.notification)
            is UiEvent.OnOverrideCleared -> onOverrideCleared()
        }
    }

    private fun init(event: UiEvent.Init) {
        entrySampleText = event.sampleText
        setState {
            UiState(
                fields = event.initialFields,
                isEditingAction = event.isEditingAction,
            )
        }
        recomputePreviews()
    }

    /**
     * Recomputes [UiState.previewResults] from the current draft fields against [effectiveSampleText].
     * Empty when the effective text is null/blank. Called after init and every field-list mutation.
     */
    private fun recomputePreviews() {
        val text = effectiveSampleText()
        if (text.isNullOrBlank()) {
            previewJob?.cancel()
            setState { copy(previewResults = emptyMap()) }
            return
        }

        val fields = uiState.value.fields
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val previews = withContext(defaultDispatcher) {
                fields.associate { field -> field.id to FieldExtractor.extract(text, field).toPreviewResult() }
            }
            setState { copy(previewResults = previews) }
        }
    }

    private fun ExtractionResult.toPreviewResult(): PreviewResult = when (this) {
        is ExtractionResult.Success -> PreviewResult.Success(value)
        is ExtractionResult.Failure -> PreviewResult.Failure(reason)
    }

    private fun onBrowseHistoryOpened(targetPackages: List<String>?) {
        setState { copy(isBrowsingHistory = true, isLoadingHistory = true) }
        viewModelScope.launch(defaultDispatcher) {
            notificationRepository.getNotificationsForBacktest(targetPackages = targetPackages, limit = HISTORY_LIMIT)
                .onSuccess { results ->
                    setState { copy(historyResults = results, isLoadingHistory = false) }
                }
                .onFailure {
                    Timber.w(it, "Failed to load notification history")
                    setState { copy(historyResults = emptyList(), isLoadingHistory = false) }
                }
        }
    }

    private fun onHistoryNotificationSelected(notification: Notification) {
        setState {
            copy(
                overrideNotification = notification,
                isBrowsingHistory = false,
                historyResults = emptyList(),
                isLoadingHistory = false,
            )
        }
        recomputePreviews()
    }

    private fun onOverrideCleared() {
        setState { copy(overrideNotification = null) }
        recomputePreviews()
    }

    private fun autoGenerate() {
        val text = effectiveSampleText().orEmpty()
        if (text.isEmpty()) {
            sendEffect(UiEffect.ShowError("No notification text available to analyze"))
            return
        }

        val matches = Regex("""\d+[.,]?\d*""").findAll(text).toList()
        if (matches.isEmpty()) {
            sendEffect(UiEffect.ShowError("No numbers found in the notification"))
            return
        }

        val newFields = matches.mapIndexed { index, _ ->
            RuleField(
                id = UUID.randomUUID().toString(),
                name = if (matches.size == 1) "Amount" else "Amount ${index + 1}",
                method = ExtractionMethod.LineExtraction(10),
            )
        }
        setState { copy(fields = newFields.toImmutableList()) }
        recomputePreviews()
    }

    private fun removeField(fieldId: String) {
        setState { copy(fields = fields.filter { it.id != fieldId }.toImmutableList()) }
        recomputePreviews()
    }

    private fun onFieldSaved(field: RuleField) {
        setState {
            val updatedFields = if (editingFieldId != null) {
                fields.map { if (it.id == editingFieldId) field else it }.toImmutableList()
            } else {
                (fields + field).toImmutableList()
            }
            copy(
                fields = updatedFields,
                isFieldSheetVisible = false,
                editingFieldId = null,
            )
        }
        recomputePreviews()
    }

    private fun confirm() {
        val current = uiState.value
        // Guard: a SAVE_DATA action must never be committed without at least one field.
        if (current.fields.isEmpty()) return
        sendEffect(UiEffect.Committed(current.fields))
        sendEffect(UiEffect.Dismiss)
    }

    private fun dismiss() {
        // Reset for next open and discard the draft.
        entrySampleText = null
        setState { UiState() }
        sendEffect(UiEffect.Dismiss)
    }

    private companion object {
        private const val HISTORY_LIMIT = 25
    }
}
