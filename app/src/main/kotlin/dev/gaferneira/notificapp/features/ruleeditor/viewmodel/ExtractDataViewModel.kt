package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.extraction.FieldExtractor
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiState
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the ExtractDataBottomSheet.
 *
 * Owns a draft copy of the rule's extraction fields plus the nested add/edit-field sheet. The draft
 * is committed to the parent (via [UiEffect.Committed]) only when the user confirms; cancelling
 * discards it. Self-contained with no dependencies on the parent RuleEditorViewModel, mirroring
 * [MatchingLogicViewModel].
 */
@HiltViewModel
class ExtractDataViewModel @Inject constructor() : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    /** Notification text used by auto-generate, captured on init */
    private var sampleText: String? = null

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
        }
    }

    private fun init(event: UiEvent.Init) {
        sampleText = event.sampleText
        setState {
            UiState(
                fields = event.initialFields,
                isEditingAction = event.isEditingAction,
            )
        }
        recomputePreviews()
    }

    /**
     * Recomputes [UiState.previewResults] from the current draft fields against [sampleText].
     * Empty when [sampleText] is null/blank. Called after init and every field-list mutation.
     */
    private fun recomputePreviews() {
        val text = sampleText
        val previews = if (text.isNullOrBlank()) {
            emptyMap()
        } else {
            uiState.value.fields.associate { field -> field.id to FieldExtractor.extract(text, field) }
        }
        setState { copy(previewResults = previews) }
    }

    private fun autoGenerate() {
        val text = sampleText.orEmpty()
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
        setState { copy(fields = newFields) }
        recomputePreviews()
    }

    private fun removeField(fieldId: String) {
        setState { copy(fields = fields.filter { it.id != fieldId }) }
        recomputePreviews()
    }

    private fun onFieldSaved(field: RuleField) {
        setState {
            val updatedFields = if (editingFieldId != null) {
                fields.map { if (it.id == editingFieldId) field else it }
            } else {
                fields + field
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
        sampleText = null
        setState { UiState() }
        sendEffect(UiEffect.Dismiss)
    }
}
