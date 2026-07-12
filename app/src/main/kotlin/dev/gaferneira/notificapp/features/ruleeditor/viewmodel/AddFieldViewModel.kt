package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.extraction.FieldExtractor
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Add Field screen.
 *
 * Manages extraction field configuration with various method types
 * and real-time preview of extraction results.
 */
@HiltViewModel
class AddFieldViewModel @Inject constructor(
    @Dispatcher(DispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

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

    private fun initialize(field: RuleField?, notification: Notification?) {
        val sampleText = notification?.content ?: ""

        if (field != null) {
            // Populate from existing field
            val methodType = mapMethodToType(field.method)
            setState {
                copy(
                    fieldName = field.name,
                    selectedMethodType = methodType,
                    sampleText = sampleText,
                    // Populate method-specific fields based on type
                    fixedStartIndex = when (val m = field.method) {
                        is ExtractionMethod.FixedPosition -> m.startIndex
                        else -> 0
                    },
                    fixedEndIndex = when (val m = field.method) {
                        is ExtractionMethod.FixedPosition -> m.endIndex
                        else -> sampleText.length.coerceAtMost(50)
                    },
                    startAnchor = when (val m = field.method) {
                        is ExtractionMethod.TextBetweenAnchors -> m.startAnchor
                        else -> ""
                    },
                    endAnchor = when (val m = field.method) {
                        is ExtractionMethod.TextBetweenAnchors -> m.endAnchor
                        else -> ""
                    },
                    regexPattern = when (val m = field.method) {
                        is ExtractionMethod.RegexPattern -> m.pattern
                        else -> ""
                    },
                    captureGroup = when (val m = field.method) {
                        is ExtractionMethod.RegexPattern -> m.captureGroup
                        else -> 1
                    },
                    afterKeyword = when (val m = field.method) {
                        is ExtractionMethod.TextAfterKeyword -> m.keyword
                        else -> ""
                    },
                    afterKeywordMaxLength = when (val m = field.method) {
                        is ExtractionMethod.TextAfterKeyword -> m.maxLength
                        else -> null
                    },
                    beforeKeyword = when (val m = field.method) {
                        is ExtractionMethod.TextBeforeKeyword -> m.keyword
                        else -> ""
                    },
                    lineNumber = when (val m = field.method) {
                        is ExtractionMethod.LineExtraction -> m.lineNumber
                        else -> 1
                    },
                    delimiter = when (val m = field.method) {
                        is ExtractionMethod.SplitByDelimiter -> m.delimiter
                        else -> ","
                    },
                    takeIndex = when (val m = field.method) {
                        is ExtractionMethod.SplitByDelimiter -> m.takeIndex
                        else -> 0
                    },
                    jsonPath = when (val m = field.method) {
                        is ExtractionMethod.JsonPath -> m.path
                        else -> ""
                    },
                    // Clear any previous errors and preview
                    validationErrors = emptyMap(),
                    error = null,
                    previewResult = AddFieldContract.PreviewResult.None,
                )
            }
        } else {
            // Reset to default state for new field
            setState {
                copy(
                    fieldName = "",
                    selectedMethodType = AddFieldContract.MethodType.TEXT_BETWEEN_ANCHORS,
                    sampleText = sampleText,
                    fixedStartIndex = 0,
                    fixedEndIndex = sampleText.length.coerceAtMost(50),
                    startAnchor = "",
                    endAnchor = "",
                    regexPattern = "",
                    captureGroup = 1,
                    afterKeyword = "",
                    afterKeywordMaxLength = null,
                    beforeKeyword = "",
                    lineNumber = 1,
                    delimiter = ",",
                    takeIndex = 0,
                    jsonPath = "",
                    validationErrors = emptyMap(),
                    error = null,
                    previewResult = AddFieldContract.PreviewResult.None,
                )
            }
        }

        // Auto-preview if we have sample text and can preview
        if (sampleText.isNotEmpty()) {
            previewExtraction()
        }
    }

    private fun mapMethodToType(method: ExtractionMethod): AddFieldContract.MethodType = when (method) {
        is ExtractionMethod.FixedPosition -> AddFieldContract.MethodType.FIXED_POSITION
        is ExtractionMethod.TextBetweenAnchors -> AddFieldContract.MethodType.TEXT_BETWEEN_ANCHORS
        is ExtractionMethod.RegexPattern -> AddFieldContract.MethodType.REGEX
        is ExtractionMethod.TextAfterKeyword -> AddFieldContract.MethodType.TEXT_AFTER_KEYWORD
        is ExtractionMethod.TextBeforeKeyword -> AddFieldContract.MethodType.TEXT_BEFORE_KEYWORD
        is ExtractionMethod.LineExtraction -> AddFieldContract.MethodType.LINE_EXTRACTION
        is ExtractionMethod.SplitByDelimiter -> AddFieldContract.MethodType.SPLIT_BY_DELIMITER
        is ExtractionMethod.JsonPath -> AddFieldContract.MethodType.JSON_PATH
        is ExtractionMethod.SmartAmountDetection -> AddFieldContract.MethodType.SMART_AMOUNT
        is ExtractionMethod.SmartDateDetection -> AddFieldContract.MethodType.SMART_DATE
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
        if (currentState.sampleText.isEmpty() || !currentState.canPreview) {
            setState { copy(previewResult = AddFieldContract.PreviewResult.None) }
            return
        }

        viewModelScope.launch {
            setState { copy(isTesting = true) }

            try {
                val field = currentState.extractionField
                val result = withContext(defaultDispatcher) { FieldExtractor.extract(currentState.sampleText, field) }

                val previewResult = when (result) {
                    is dev.gaferneira.notificapp.core.extraction.ExtractionResult.Success ->
                        AddFieldContract.PreviewResult.Success(
                            value = result.value,
                            startIndex = result.startIndex,
                            endIndex = result.endIndex,
                        )
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

        // Send effect to return with the created field
        val field = currentState.extractionField
        sendEffect(UiEffect.ReturnWithField(field))

        // Check if extraction actually works - allow saving even if it fails (the user might be
        // testing with different sample data), just log a warning. Off the Main thread and after
        // the effect above, since the check is diagnostic only and must not block returning.
        viewModelScope.launch {
            val testResult = withContext(defaultDispatcher) { FieldExtractor.extract(currentState.sampleText, field) }
            if (testResult is dev.gaferneira.notificapp.core.extraction.ExtractionResult.Failure) {
                Timber.w("Field extraction test failed: ${testResult.reason}")
            }
        }
    }

    private fun dismissError() {
        setState { copy(error = null) }
    }
}
