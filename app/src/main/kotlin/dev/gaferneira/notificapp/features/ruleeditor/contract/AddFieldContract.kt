package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.ExtractionField
import dev.gaferneira.notificapp.domain.model.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.Notification

/**
 * MVI Contract for the Add Field screen.
 *
 * Screen for configuring a single extraction field with various methods.
 */
object AddFieldContract {

    /**
     * UI State for the add field screen.
     */
    data class UiState(
        /** Field name */
        val fieldName: String = "",
        /** Selected extraction method type */
        val selectedMethodType: MethodType = MethodType.TEXT_BETWEEN_ANCHORS,
        /** Sample text to test against */
        val sampleText: String = "",
        /** Preview extraction result */
        val previewResult: PreviewResult = PreviewResult.None,
        /** Whether currently loading/testing */
        val isTesting: Boolean = false,
        /** Error message */
        val error: String? = null,
        /** Validation errors by field */
        val validationErrors: Map<String, String> = emptyMap(),

        // Method-specific parameters
        /** Fixed position: start index */
        val fixedStartIndex: Int = 0,
        /** Fixed position: end index */
        val fixedEndIndex: Int = 10,

        /** Text between anchors: start anchor */
        val startAnchor: String = "",
        /** Text between anchors: end anchor */
        val endAnchor: String = "",

        /** Regex: pattern string */
        val regexPattern: String = "",
        /** Regex: capture group index */
        val captureGroup: Int = 1,

        /** Text after keyword: keyword */
        val afterKeyword: String = "",
        /** Text after keyword: max length */
        val afterKeywordMaxLength: Int? = null,

        /** Text before keyword: keyword */
        val beforeKeyword: String = "",

        /** Line extraction: line number */
        val lineNumber: Int = 1,

        /** Split by delimiter: delimiter */
        val delimiter: String = ",",
        /** Split by delimiter: take index */
        val takeIndex: Int = 0,

        /** JSON path: path string */
        val jsonPath: String = "",
    ) {
        /** Whether the form is valid */
        val isValid: Boolean
            get() = fieldName.isNotBlank() && validationErrors.isEmpty()

        /** Build ExtractionMethod from current parameters */
        val extractionMethod: ExtractionMethod
            get() = when (selectedMethodType) {
                MethodType.FIXED_POSITION -> ExtractionMethod.FixedPosition(
                    startIndex = fixedStartIndex.coerceAtLeast(0),
                    endIndex = fixedEndIndex.coerceAtLeast(fixedStartIndex),
                )
                MethodType.TEXT_BETWEEN_ANCHORS -> ExtractionMethod.TextBetweenAnchors(
                    startAnchor = startAnchor,
                    endAnchor = endAnchor,
                )
                MethodType.REGEX -> ExtractionMethod.RegexPattern(
                    pattern = regexPattern,
                    captureGroup = captureGroup.coerceAtLeast(0),
                )
                MethodType.TEXT_AFTER_KEYWORD -> ExtractionMethod.TextAfterKeyword(
                    keyword = afterKeyword,
                    maxLength = afterKeywordMaxLength,
                )
                MethodType.TEXT_BEFORE_KEYWORD -> ExtractionMethod.TextBeforeKeyword(
                    keyword = beforeKeyword,
                )
                MethodType.LINE_EXTRACTION -> ExtractionMethod.LineExtraction(
                    lineNumber = lineNumber.coerceAtLeast(1),
                )
                MethodType.SPLIT_BY_DELIMITER -> ExtractionMethod.SplitByDelimiter(
                    delimiter = delimiter,
                    takeIndex = takeIndex.coerceAtLeast(0),
                )
                MethodType.JSON_PATH -> ExtractionMethod.JsonPath(
                    path = jsonPath,
                )
                MethodType.SMART_AMOUNT -> ExtractionMethod.SmartAmountDetection
                MethodType.SMART_DATE -> ExtractionMethod.SmartDateDetection
            }

        /** Build ExtractionField from current state */
        val extractionField: ExtractionField
            get() = ExtractionField(
                name = fieldName.trim(),
                description = null,
                method = extractionMethod,
                isRequired = false,
            )
    }

    /**
     * Available extraction method types.
     */
    enum class MethodType(val displayName: String, val description: String) {
        FIXED_POSITION(
            "Fixed position",
            "Extract characters from position X to Y",
        ),
        TEXT_BETWEEN_ANCHORS(
            "Text between anchors",
            "Extract text between two marker strings",
        ),
        REGEX(
            "Regex pattern",
            "Use regular expression with capture groups",
        ),
        TEXT_AFTER_KEYWORD(
            "Text after keyword",
            "Extract everything after a specific word",
        ),
        TEXT_BEFORE_KEYWORD(
            "Text before keyword",
            "Extract everything before a specific word",
        ),
        LINE_EXTRACTION(
            "Line extraction",
            "Extract a specific line by number",
        ),
        SPLIT_BY_DELIMITER(
            "Split by delimiter",
            "Split by comma/space and take Nth part",
        ),
        JSON_PATH(
            "JSON path",
            "Extract from JSON using dot notation",
        ),
        SMART_AMOUNT(
            "Smart amount detection",
            "Auto-detect currency amounts",
        ),
        SMART_DATE(
            "Smart date detection",
            "Auto-detect dates",
        ),
    }

    /**
     * Preview extraction result.
     */
    sealed class PreviewResult {
        data object None : PreviewResult()
        data class Success(val value: String) : PreviewResult()
        data class Failure(val reason: String) : PreviewResult()
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Initialize with sample text */
        data class Initialize(val field: String?, val notification: Notification?) : UiEvent()

        /** Update field name */
        data class OnFieldNameChange(val name: String) : UiEvent()

        /** Select extraction method type */
        data class OnMethodTypeChange(val type: MethodType) : UiEvent()

        // Fixed position events
        data class OnStartIndexChange(val index: Int) : UiEvent()
        data class OnEndIndexChange(val index: Int) : UiEvent()

        // Text between anchors events
        data class OnStartAnchorChange(val anchor: String) : UiEvent()
        data class OnEndAnchorChange(val anchor: String) : UiEvent()

        // Regex events
        data class OnRegexPatternChange(val pattern: String) : UiEvent()
        data class OnCaptureGroupChange(val group: Int) : UiEvent()

        // Text after keyword events
        data class OnAfterKeywordChange(val keyword: String) : UiEvent()
        data class OnAfterKeywordMaxLengthChange(val maxLength: Int?) : UiEvent()

        // Text before keyword events
        data class OnBeforeKeywordChange(val keyword: String) : UiEvent()

        // Line extraction events
        data class OnLineNumberChange(val line: Int) : UiEvent()

        // Split by delimiter events
        data class OnDelimiterChange(val delimiter: String) : UiEvent()
        data class OnTakeIndexChange(val index: Int) : UiEvent()

        // JSON path events
        data class OnJsonPathChange(val path: String) : UiEvent()

        /** Preview extraction with current settings */
        data object OnPreviewClicked : UiEvent()

        /** Save the field and return */
        data object OnSaveClicked : UiEvent()

        /** Cancel and return */
        data object OnCancelClicked : UiEvent()

        /** Dismiss error */
        data object OnDismissError : UiEvent()
    }

    /**
     * One-time effects for navigation and actions.
     */
    sealed class UiEffect {
        /** Return with the created field */
        data class ReturnWithField(val field: ExtractionField) : UiEffect()

        /** Cancel and return without field */
        data object CancelAndReturn : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}
