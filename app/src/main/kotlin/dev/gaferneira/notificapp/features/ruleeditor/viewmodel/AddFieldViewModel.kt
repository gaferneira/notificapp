package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.extraction.FieldExtractor
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiState
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Add Field screen.
 *
 * Manages extraction field configuration with various method types
 * and real-time preview of extraction results.
 */
@HiltViewModel
class AddFieldViewModel @Inject constructor() : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Initialize -> initialize(event.field, event.notification)
            is UiEvent.OnFieldNameChange -> updateFieldName(event.name)
            is UiEvent.OnMethodTypeChange -> updateMethodType(event.type)
            is UiEvent.OnStartIndexChange -> updateStartIndex(event.index)
            is UiEvent.OnEndIndexChange -> updateEndIndex(event.index)
            is UiEvent.OnStartAnchorChange -> updateStartAnchor(event.anchor)
            is UiEvent.OnEndAnchorChange -> updateEndAnchor(event.anchor)
            is UiEvent.OnRegexPatternChange -> updateRegexPattern(event.pattern)
            is UiEvent.OnCaptureGroupChange -> updateCaptureGroup(event.group)
            is UiEvent.OnAfterKeywordChange -> updateAfterKeyword(event.keyword)
            is UiEvent.OnAfterKeywordMaxLengthChange -> updateAfterKeywordMaxLength(event.maxLength)
            is UiEvent.OnBeforeKeywordChange -> updateBeforeKeyword(event.keyword)
            is UiEvent.OnLineNumberChange -> updateLineNumber(event.line)
            is UiEvent.OnDelimiterChange -> updateDelimiter(event.delimiter)
            is UiEvent.OnTakeIndexChange -> updateTakeIndex(event.index)
            is UiEvent.OnJsonPathChange -> updateJsonPath(event.path)
            is UiEvent.OnPreviewClicked -> previewExtraction()
            is UiEvent.OnSaveClicked -> saveField()
            is UiEvent.OnCancelClicked -> {
                sendEffect(UiEffect.CancelAndReturn)
            }
            is UiEvent.OnDismissError -> dismissError()
        }
    }

    private fun initialize(fieldId: String?, notification: Notification?) {
        val sampleText = notification?.content ?: ""
        setState {
            copy(
                sampleText = sampleText,
                // Set a reasonable default based on sample
                fixedEndIndex = sampleText.length.coerceAtMost(50),
            )
        }
        // Auto-preview if we have sample text
        if (sampleText.isNotEmpty()) {
            previewExtraction()
        }
    }

    private fun updateFieldName(name: String) {
        setState {
            copy(
                fieldName = name,
                validationErrors = validationErrors - "fieldName",
            )
        }
    }

    private fun updateMethodType(type: AddFieldContract.MethodType) {
        setState { copy(selectedMethodType = type) }
        // Auto-preview with new method
        previewExtraction()
    }

    private fun updateStartIndex(index: Int) {
        setState { copy(fixedStartIndex = index.coerceAtLeast(0)) }
        previewExtraction()
    }

    private fun updateEndIndex(index: Int) {
        setState {
            copy(fixedEndIndex = index.coerceAtLeast(fixedStartIndex))
        }
        previewExtraction()
    }

    private fun updateStartAnchor(anchor: String) {
        setState { copy(startAnchor = anchor) }
        previewExtraction()
    }

    private fun updateEndAnchor(anchor: String) {
        setState { copy(endAnchor = anchor) }
        previewExtraction()
    }

    private fun updateRegexPattern(pattern: String) {
        setState { copy(regexPattern = pattern) }
        previewExtraction()
    }

    private fun updateCaptureGroup(group: Int) {
        setState { copy(captureGroup = group.coerceAtLeast(0)) }
        previewExtraction()
    }

    private fun updateAfterKeyword(keyword: String) {
        setState { copy(afterKeyword = keyword) }
        previewExtraction()
    }

    private fun updateAfterKeywordMaxLength(maxLength: Int?) {
        setState { copy(afterKeywordMaxLength = maxLength) }
        previewExtraction()
    }

    private fun updateBeforeKeyword(keyword: String) {
        setState { copy(beforeKeyword = keyword) }
        previewExtraction()
    }

    private fun updateLineNumber(line: Int) {
        setState { copy(lineNumber = line.coerceAtLeast(1)) }
        previewExtraction()
    }

    private fun updateDelimiter(delimiter: String) {
        setState { copy(delimiter = delimiter) }
        previewExtraction()
    }

    private fun updateTakeIndex(index: Int) {
        setState { copy(takeIndex = index.coerceAtLeast(0)) }
        previewExtraction()
    }

    private fun updateJsonPath(path: String) {
        setState { copy(jsonPath = path) }
        previewExtraction()
    }

    private fun previewExtraction() {
        val currentState = uiState.value
        if (currentState.sampleText.isEmpty()) {
            setState { copy(previewResult = AddFieldContract.PreviewResult.None) }
            return
        }

        viewModelScope.launch {
            setState { copy(isTesting = true) }

            try {
                val field = currentState.extractionField
                val result = FieldExtractor.extract(currentState.sampleText, field)

                val previewResult = when (result) {
                    is dev.gaferneira.notificapp.core.extraction.ExtractionResult.Success ->
                        AddFieldContract.PreviewResult.Success(result.value)
                    is dev.gaferneira.notificapp.core.extraction.ExtractionResult.Failure ->
                        AddFieldContract.PreviewResult.Failure(result.reason)
                }

                setState {
                    copy(
                        previewResult = previewResult,
                        isTesting = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Preview extraction failed")
                setState {
                    copy(
                        previewResult = AddFieldContract.PreviewResult.Failure("Preview failed: ${e.message}"),
                        isTesting = false,
                    )
                }
            }
        }
    }

    private fun saveField() {
        val currentState = uiState.value

        // Validate
        val errors = mutableMapOf<String, String>()
        if (currentState.fieldName.isBlank()) {
            errors["fieldName"] = "Field name is required"
        }

        // Validate method-specific required fields
        when (currentState.selectedMethodType) {
            AddFieldContract.MethodType.TEXT_BETWEEN_ANCHORS -> {
                if (currentState.startAnchor.isBlank()) {
                    errors["startAnchor"] = "Start anchor is required"
                }
                if (currentState.endAnchor.isBlank()) {
                    errors["endAnchor"] = "End anchor is required"
                }
            }
            AddFieldContract.MethodType.REGEX -> {
                if (currentState.regexPattern.isBlank()) {
                    errors["regexPattern"] = "Pattern is required"
                }
            }
            AddFieldContract.MethodType.TEXT_AFTER_KEYWORD -> {
                if (currentState.afterKeyword.isBlank()) {
                    errors["afterKeyword"] = "Keyword is required"
                }
            }
            AddFieldContract.MethodType.TEXT_BEFORE_KEYWORD -> {
                if (currentState.beforeKeyword.isBlank()) {
                    errors["beforeKeyword"] = "Keyword is required"
                }
            }
            AddFieldContract.MethodType.JSON_PATH -> {
                if (currentState.jsonPath.isBlank()) {
                    errors["jsonPath"] = "JSON path is required"
                }
            }
            else -> { /* No additional validation needed */ }
        }

        if (errors.isNotEmpty()) {
            setState { copy(validationErrors = errors) }
            sendEffect(UiEffect.ShowError("Please fix validation errors"))
            return
        }

        // Check if extraction actually works
        val field = currentState.extractionField
        val testResult = FieldExtractor.extract(currentState.sampleText, field)

        // Allow saving even if extraction fails - user might be testing with different data
        // But show a warning
        if (testResult is dev.gaferneira.notificapp.core.extraction.ExtractionResult.Failure) {
            Timber.w("Field extraction test failed: ${testResult.reason}")
        }

        // Send effect to return with the created field
        sendEffect(UiEffect.ReturnWithField(field))
    }

    private fun dismissError() {
        setState { copy(error = null) }
    }
}
