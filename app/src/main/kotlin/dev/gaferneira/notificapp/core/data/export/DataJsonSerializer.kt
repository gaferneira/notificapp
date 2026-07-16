package dev.gaferneira.notificapp.core.data.export

import dev.gaferneira.notificapp.domain.model.DataBrowserRow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Streams [DataBrowserRow]s as a single well-formed JSON array to an [Appendable] (typically a
 * `Writer`/`BufferedWriter` wrapping the export sink). Stateless and pure Kotlin: callers write
 * [writeArrayStart], then [writeRows] once per streamed batch, then [writeArrayEnd] - never
 * buffering the full array in memory. Every batch after the first is assumed non-empty (the
 * repository slices a non-empty ID snapshot into fixed-size batches), so inter-batch commas need
 * only the batch index, not per-batch emptiness tracking.
 */
internal object DataJsonSerializer {

    private val json = Json { encodeDefaults = true }

    fun writeArrayStart(writer: Appendable) {
        writer.append("[")
    }

    fun writeArrayEnd(writer: Appendable) {
        writer.append("]")
    }

    /**
     * Append the JSON-object encoding of every row in [rows]. [isFirstBatch] must be true only for
     * the very first call after [writeArrayStart], so the leading element of the array isn't
     * preceded by a comma.
     */
    fun writeRows(writer: Appendable, rows: List<DataBrowserRow>, isFirstBatch: Boolean) {
        rows.forEachIndexed { index, row ->
            if (!isFirstBatch || index != 0) writer.append(",")
            writer.append(json.encodeToString(row.toDto()))
        }
    }

    private fun DataBrowserRow.toDto() = DataBrowserRowDto(
        valueId = valueId,
        executionId = executionId,
        ruleName = ruleName,
        packageName = packageName,
        appName = appName,
        fieldName = fieldName,
        fieldType = fieldType.name,
        valueText = valueText,
        valueNumber = valueNumber,
        valueDate = valueDate,
        notificationTitle = notificationTitle,
        notificationContent = notificationContent,
        createdAt = createdAt,
    )
}

/**
 * JSON wire shape for one exported row. Deliberately separate from the domain [DataBrowserRow] (a
 * plain `String` `fieldType` rather than the enum) so the export file format doesn't silently
 * change if the domain model's shape or enum `@SerialName`s ever change.
 */
@Serializable
private data class DataBrowserRowDto(
    val valueId: String,
    val executionId: String,
    val ruleName: String,
    val packageName: String,
    val appName: String,
    val fieldName: String,
    val fieldType: String,
    val valueText: String?,
    val valueNumber: Double?,
    val valueDate: Long?,
    val notificationTitle: String?,
    val notificationContent: String?,
    val createdAt: Long,
)
