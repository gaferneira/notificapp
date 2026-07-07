package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.core.extraction.ExtractionResult
import dev.gaferneira.notificapp.domain.model.RuleField

/**
 * MVI Contract for the ExtractDataBottomSheet ("Extract data" action).
 *
 * Manages a draft of the rule's extraction fields, edited in isolation from the rule. The draft is
 * committed to the parent only when the user confirms (Add/Update); cancelling discards it. Mirrors
 * the [MatchingLogicContract] sub-sheet pattern.
 */
object ExtractDataContract {

    /**
     * UI State for the Extract-data sheet. [fields] is the working draft, not the rule's fields.
     */
    data class UiState(
        /** Draft extraction fields, seeded on init and edited in place until confirmed */
        val fields: List<RuleField> = emptyList(),
        /** True when editing an existing Extract-data action (drives the Add vs Update label) */
        val isEditingAction: Boolean = false,
        /** Whether the nested add/edit-field sheet is visible */
        val isFieldSheetVisible: Boolean = false,
        /** ID of the field currently being edited in the nested sheet, or null for a new field */
        val editingFieldId: String? = null,
        /**
         * Live extraction preview per committed field, keyed by [RuleField.id]. Recomputed after
         * every draft mutation (init, field saved, removed, auto-generate); empty when the sample
         * text is blank or null.
         */
        val previewResults: Map<String, ExtractionResult> = emptyMap(),
    ) {
        /** Confirm is only allowed once the draft has at least one field */
        val canConfirm: Boolean
            get() = fields.isNotEmpty()

        /** The field being edited in the nested sheet, if any */
        val editingField: RuleField?
            get() = editingFieldId?.let { id -> fields.find { it.id == id } }
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Seed the draft when the sheet opens (empty for new, current fields for edit) */
        data class Init(
            val initialFields: List<RuleField>,
            val isEditingAction: Boolean,
            val sampleText: String?,
        ) : UiEvent()

        /** Auto-generate extraction fields from the sample notification text */
        data object OnAutoGenerate : UiEvent()

        /** Open the nested sheet to add a new field */
        data object OnAddFieldClicked : UiEvent()

        /** Open the nested sheet to edit an existing draft field */
        data class OnEditFieldClicked(val fieldId: String) : UiEvent()

        /** Remove a field from the draft */
        data class OnRemoveFieldClicked(val fieldId: String) : UiEvent()

        /** A field was saved from the nested add/edit-field sheet (add or update) */
        data class OnFieldSaved(val field: RuleField) : UiEvent()

        /** Dismiss the nested add/edit-field sheet only (leaves the Extract-data sheet open) */
        data object OnDismissFieldSheet : UiEvent()

        /** Confirm the sheet, committing the draft to the rule */
        data object OnConfirm : UiEvent()

        /** Dismiss the sheet, discarding the draft */
        data object OnDismiss : UiEvent()
    }

    /**
     * One-time effects to communicate with the parent.
     */
    sealed class UiEffect {
        /** The draft was confirmed; the parent should commit these fields + the SAVE_DATA action */
        data class Committed(val fields: List<RuleField>) : UiEffect()

        /** Dismiss the sheet */
        data object Dismiss : UiEffect()

        /** Non-fatal auto-generate feedback (e.g. no text / no numbers) */
        data class ShowError(val message: String) : UiEffect()
    }
}
