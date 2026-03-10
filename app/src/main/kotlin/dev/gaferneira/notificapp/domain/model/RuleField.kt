package dev.gaferneira.notificapp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Field to extract from a notification.
 */
@Serializable
data class RuleField(
    val id: String,
    val name: String,
    /** The type of data this field represents */
    val fieldType: FieldType = FieldType.STRING,
    /** The extraction method to use */
    val method: ExtractionMethod,
    /** Whether this field is required */
    val isRequired: Boolean = false,
) {
    /**
     * Enum representing the type of data a field can hold.
     */
    @Serializable
    enum class FieldType {
        @SerialName("string")
        STRING,

        @SerialName("number")
        NUMBER,

        @SerialName("date")
        DATE,

        @SerialName("currency")
        CURRENCY,

        @SerialName("boolean")
        BOOLEAN,
    }

    /**
     * Sealed class representing different extraction methods.
     */
    @Serializable
    sealed class ExtractionMethod {
        abstract val type: String

        /**
         * Extract text from a fixed character position range.
         */
        @Serializable
        @SerialName("fixed_position")
        data class FixedPosition(val startIndex: Int, val endIndex: Int) : ExtractionMethod() {
            override val type = "fixed_position"
        }

        /**
         * Extract text between two anchor strings.
         */
        @Serializable
        @SerialName("text_between_anchors")
        data class TextBetweenAnchors(val startAnchor: String, val endAnchor: String) : ExtractionMethod() {
            override val type = "text_between_anchors"
        }

        /**
         * Extract using a regex pattern with capture groups.
         */
        @Serializable
        @SerialName("regex")
        data class RegexPattern(val pattern: String, val captureGroup: Int = 1) : ExtractionMethod() {
            override val type = "regex"
        }

        /**
         * Extract text after a specific keyword.
         */
        @Serializable
        @SerialName("text_after_keyword")
        data class TextAfterKeyword(val keyword: String, val maxLength: Int? = null) : ExtractionMethod() {
            override val type = "text_after_keyword"
        }

        /**
         * Extract text before a specific keyword.
         */
        @Serializable
        @SerialName("text_before_keyword")
        data class TextBeforeKeyword(val keyword: String) : ExtractionMethod() {
            override val type = "text_before_keyword"
        }

        /**
         * Extract a specific line by number.
         */
        @Serializable
        @SerialName("line_extraction")
        data class LineExtraction(val lineNumber: Int) : ExtractionMethod() {
            override val type = "line_extraction"
        }

        /**
         * Split text by delimiter and take the nth part.
         */
        @Serializable
        @SerialName("split_by_delimiter")
        data class SplitByDelimiter(val delimiter: String, val takeIndex: Int) : ExtractionMethod() {
            override val type = "split_by_delimiter"
        }

        /**
         * Extract using a JSON path (for structured notifications).
         */
        @Serializable
        @SerialName("json_path")
        data class JsonPath(val path: String) : ExtractionMethod() {
            override val type = "json_path"
        }

        /**
         * Smart detection for currency amounts.
         */
        @Serializable
        @SerialName("smart_amount")
        data object SmartAmountDetection : ExtractionMethod() {
            override val type = "smart_amount"
        }

        /**
         * Smart detection for dates.
         */
        @Serializable
        @SerialName("smart_date")
        data object SmartDateDetection : ExtractionMethod() {
            override val type = "smart_date"
        }
    }
}
