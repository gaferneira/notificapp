package dev.gaferneira.notificapp.domain.model

/**
 * Domain model representing an extraction rule.
 *
 * Rules define patterns to extract structured data from notifications.
 */
data class ExtractionRule(
    val id: String,
    val name: String,
    val description: String?,
    /** Pattern to match (regex or keyword) */
    val pattern: String,
    /** Whether this rule is active */
    val isActive: Boolean = true,
    /** App scope: null means all apps, or list of specific package names */
    val targetApps: List<String>? = null,
    /** Fields to extract from the notification */
    val extractionFields: List<ExtractionField> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Field to extract from a notification.
 */
data class ExtractionField(
    val name: String,
    val description: String?,
    /** The extraction method to use */
    val method: ExtractionMethod,
    /** Whether this field is required */
    val isRequired: Boolean = false,
)

/**
 * Sealed class representing different extraction methods.
 */
sealed class ExtractionMethod {
    abstract val type: String

    /**
     * Extract text from a fixed character position range.
     */
    data class FixedPosition(val startIndex: Int, val endIndex: Int) : ExtractionMethod() {
        override val type = "fixed_position"
    }

    /**
     * Extract text between two anchor strings.
     */
    data class TextBetweenAnchors(val startAnchor: String, val endAnchor: String) : ExtractionMethod() {
        override val type = "text_between_anchors"
    }

    /**
     * Extract using a regex pattern with capture groups.
     */
    data class RegexPattern(val pattern: String, val captureGroup: Int = 1) : ExtractionMethod() {
        override val type = "regex"
    }

    /**
     * Extract text after a specific keyword.
     */
    data class TextAfterKeyword(val keyword: String, val maxLength: Int? = null) : ExtractionMethod() {
        override val type = "text_after_keyword"
    }

    /**
     * Extract text before a specific keyword.
     */
    data class TextBeforeKeyword(val keyword: String) : ExtractionMethod() {
        override val type = "text_before_keyword"
    }

    /**
     * Extract a specific line by number.
     */
    data class LineExtraction(val lineNumber: Int) : ExtractionMethod() {
        override val type = "line_extraction"
    }

    /**
     * Split text by delimiter and take the nth part.
     */
    data class SplitByDelimiter(val delimiter: String, val takeIndex: Int) : ExtractionMethod() {
        override val type = "split_by_delimiter"
    }

    /**
     * Extract using a JSON path (for structured notifications).
     */
    data class JsonPath(val path: String) : ExtractionMethod() {
        override val type = "json_path"
    }

    /**
     * Smart detection for currency amounts.
     */
    data object SmartAmountDetection : ExtractionMethod() {
        override val type = "smart_amount"
    }

    /**
     * Smart detection for dates.
     */
    data object SmartDateDetection : ExtractionMethod() {
        override val type = "smart_date"
    }
}
