package dev.gaferneira.notificapp.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Field to extract from a notification.
 */
@Immutable
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
    sealed class ExtractionMethod {
        /**
         * Extract text from a fixed character position range.
         */
        data class FixedPosition(val startIndex: Int, val endIndex: Int) : ExtractionMethod()

        /**
         * Extract text between two anchor strings.
         */
        data class TextBetweenAnchors(val startAnchor: String, val endAnchor: String) : ExtractionMethod()

        /**
         * Extract using a regex pattern with capture groups.
         */
        data class RegexPattern(val pattern: String, val captureGroup: Int = 1) : ExtractionMethod()

        /**
         * Extract text after a specific keyword.
         */
        data class TextAfterKeyword(val keyword: String, val maxLength: Int? = null) : ExtractionMethod()

        /**
         * Extract text before a specific keyword.
         */
        data class TextBeforeKeyword(val keyword: String) : ExtractionMethod()

        /**
         * Extract a specific line by number.
         */
        data class LineExtraction(val lineNumber: Int) : ExtractionMethod()

        /**
         * Split text by delimiter and take the nth part.
         */
        data class SplitByDelimiter(val delimiter: String, val takeIndex: Int) : ExtractionMethod()

        /**
         * Extract using a JSON path (for structured notifications).
         */
        data class JsonPath(val path: String) : ExtractionMethod()

        /**
         * Smart detection for currency amounts.
         */
        data object SmartAmountDetection : ExtractionMethod()

        /**
         * Smart detection for dates.
         */
        data object SmartDateDetection : ExtractionMethod()

        /**
         * Smart detection for one-time / verification codes (OTP, PIN, 2FA codes).
         */
        data object SmartCodeDetection : ExtractionMethod()
    }
}
