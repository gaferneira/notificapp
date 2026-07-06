package dev.gaferneira.notificapp.core.rulesharing.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire representation of the `RuleField` domain model. [fieldType] crosses the wire as a raw
 * string, same reasoning as [ConditionDto].
 */
@Serializable
data class FieldDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("fieldType") val fieldType: String,
    @SerialName("isRequired") val isRequired: Boolean = false,
    @SerialName("method") val method: ExtractionMethodDto,
)

/**
 * Wire representation of `RuleField.ExtractionMethod`. Each variant declares only its own fields
 * (no redundant `type` property) - the sealed class's `type` discriminator alone tags the variant
 * on the wire. Domain `ExtractionMethod` subtypes redeclare `type` as a real property, which
 * collides with kotlinx's default JSON class discriminator and made `RuleJsonCodec.encode()`
 * throw for every extraction method except the two zero-argument "smart" ones - undetected because
 * no existing test encoded a field using any other method. This DTO layer fixes that.
 */
@Serializable
sealed class ExtractionMethodDto {

    @Serializable
    @SerialName("fixed_position")
    data class FixedPosition(
        @SerialName("startIndex") val startIndex: Int,
        @SerialName("endIndex") val endIndex: Int,
    ) : ExtractionMethodDto()

    @Serializable
    @SerialName("text_between_anchors")
    data class TextBetweenAnchors(
        @SerialName("startAnchor") val startAnchor: String,
        @SerialName("endAnchor") val endAnchor: String,
    ) : ExtractionMethodDto()

    @Serializable
    @SerialName("regex")
    data class RegexPattern(
        @SerialName("pattern") val pattern: String,
        @SerialName("captureGroup") val captureGroup: Int = 1,
    ) : ExtractionMethodDto()

    @Serializable
    @SerialName("text_after_keyword")
    data class TextAfterKeyword(
        @SerialName("keyword") val keyword: String,
        @SerialName("maxLength") val maxLength: Int? = null,
    ) : ExtractionMethodDto()

    @Serializable
    @SerialName("text_before_keyword")
    data class TextBeforeKeyword(
        @SerialName("keyword") val keyword: String,
    ) : ExtractionMethodDto()

    @Serializable
    @SerialName("line_extraction")
    data class LineExtraction(
        @SerialName("lineNumber") val lineNumber: Int,
    ) : ExtractionMethodDto()

    @Serializable
    @SerialName("split_by_delimiter")
    data class SplitByDelimiter(
        @SerialName("delimiter") val delimiter: String,
        @SerialName("takeIndex") val takeIndex: Int,
    ) : ExtractionMethodDto()

    @Serializable
    @SerialName("json_path")
    data class JsonPath(
        @SerialName("path") val path: String,
    ) : ExtractionMethodDto()

    @Serializable
    @SerialName("smart_amount")
    data object SmartAmountDetection : ExtractionMethodDto()

    @Serializable
    @SerialName("smart_date")
    data object SmartDateDetection : ExtractionMethodDto()
}
