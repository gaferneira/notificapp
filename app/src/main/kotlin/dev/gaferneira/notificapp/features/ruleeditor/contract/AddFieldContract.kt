package dev.gaferneira.notificapp.features.ruleeditor.contract

import androidx.annotation.StringRes
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import java.util.UUID

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
        val error: UiText? = null,
        /** Validation errors by field */
        val validationErrors: Map<String, UiText> = emptyMap(),

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

        /**
         * Whether all required fields for the current method are filled,
         * allowing preview extraction and highlighting.
         */
        val canPreview: Boolean
            get() = when (selectedMethodType) {
                MethodType.FIXED_POSITION -> true // Always has valid defaults
                MethodType.TEXT_BETWEEN_ANCHORS ->
                    startAnchor.isNotBlank() && endAnchor.isNotBlank()
                MethodType.REGEX ->
                    regexPattern.isNotBlank()
                MethodType.TEXT_AFTER_KEYWORD ->
                    afterKeyword.isNotBlank()
                MethodType.TEXT_BEFORE_KEYWORD ->
                    beforeKeyword.isNotBlank()
                MethodType.LINE_EXTRACTION -> true // Always has valid default
                MethodType.SPLIT_BY_DELIMITER -> true // Always has valid defaults
                MethodType.JSON_PATH ->
                    jsonPath.isNotBlank()
                MethodType.SMART_AMOUNT,
                MethodType.SMART_DATE,
                -> true // No required input fields
            }

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

        /** Build RuleField from current state */
        val extractionField: RuleField
            get() = RuleField(
                id = UUID.randomUUID().toString(),
                name = fieldName.trim(),
                method = extractionMethod,
                isRequired = false,
            )
    }

    /**
     * Available extraction method types.
     */
    enum class MethodType(@StringRes val displayNameRes: Int, @StringRes val descriptionRes: Int) {
        FIXED_POSITION(R.string.method_fixed_position, R.string.method_fixed_position_desc),
        TEXT_BETWEEN_ANCHORS(R.string.method_text_between_anchors, R.string.method_text_between_anchors_desc),
        REGEX(R.string.method_regex, R.string.method_regex_desc),
        TEXT_AFTER_KEYWORD(R.string.method_text_after_keyword, R.string.method_text_after_keyword_desc),
        TEXT_BEFORE_KEYWORD(R.string.method_text_before_keyword, R.string.method_text_before_keyword_desc),
        LINE_EXTRACTION(R.string.method_line_extraction, R.string.method_line_extraction_desc),
        SPLIT_BY_DELIMITER(R.string.method_split_by_delimiter, R.string.method_split_by_delimiter_desc),
        JSON_PATH(R.string.method_json_path, R.string.method_json_path_desc),
        SMART_AMOUNT(R.string.method_smart_amount, R.string.method_smart_amount_desc),
        SMART_DATE(R.string.method_smart_date, R.string.method_smart_date_desc),
    }

    /**
     * Preview extraction result.
     */
    sealed class PreviewResult {
        data object None : PreviewResult()
        data class Success(
            val value: String,
            val startIndex: Int = -1,
            val endIndex: Int = -1,
        ) : PreviewResult() {
            val hasPosition: Boolean get() = startIndex >= 0 && endIndex > startIndex
        }
        data class Failure(val reason: UiText) : PreviewResult()
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Initialize with sample text */
        data class Initialize(val field: RuleField?, val notification: Notification?) : UiEvent()

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
        data class ReturnWithField(val field: RuleField) : UiEffect()

        /** Cancel and return without field */
        data object CancelAndReturn : UiEffect()

        /** Show error message */
        data class ShowError(val message: UiText) : UiEffect()
    }
}
