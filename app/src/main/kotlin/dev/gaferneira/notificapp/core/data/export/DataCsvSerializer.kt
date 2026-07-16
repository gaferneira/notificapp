package dev.gaferneira.notificapp.core.data.export

import dev.gaferneira.notificapp.domain.model.DataBrowserRow

/**
 * Streams [DataBrowserRow]s as RFC-4180 CSV to an [Appendable] (typically a
 * `Writer`/`BufferedWriter` wrapping the export sink). Stateless and pure Kotlin: callers write the
 * header once via [writeHeader], then call [writeRows] once per streamed batch - never buffering
 * the full row set.
 */
internal object DataCsvSerializer {

    private val HEADER = listOf("Field Name", "Field Type", "Value", "Source App", "Rule Name", "Timestamp")

    /** Write the CSV header row. Call exactly once, before the first [writeRows] call. */
    fun writeHeader(writer: Appendable) {
        writer.append(HEADER.joinToString(",") { escape(it) }).append(CRLF)
    }

    /** Append one CSV row per entry in [rows], in order. Safe to call zero times (empty result). */
    fun writeRows(writer: Appendable, rows: List<DataBrowserRow>) {
        rows.forEach { row -> writer.append(row.toCsvLine()).append(CRLF) }
    }

    private fun DataBrowserRow.toCsvLine(): String = listOf(
        fieldName,
        fieldType.name,
        valueAsString(),
        appName,
        ruleName,
        createdAt.toString(),
    ).joinToString(",") { escape(it) }

    private fun DataBrowserRow.valueAsString(): String = valueText
        ?: valueNumber?.toString()
        ?: valueDate?.toString()
        ?: ""

    /**
     * RFC-4180 escaping: a field containing a comma, double quote, or line break is wrapped in
     * double quotes, with any embedded double quote doubled. Before that, [neutralizeFormula]
     * guards against CSV/DDE formula injection (CWE-1236): [DataBrowserRow] fields ultimately
     * derive from third-party app notification content, which is attacker-influenceable, so a
     * value starting with `=`, `+`, `-`, `@`, tab, or CR would otherwise be interpreted as a
     * formula by Excel/Sheets/LibreOffice when this CSV is opened.
     */
    private fun escape(value: String): String {
        val neutralized = neutralizeFormula(value)
        val needsQuoting = neutralized.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"" + neutralized.replace("\"", "\"\"") + "\"" else neutralized
    }

    /** Prepends a literal single quote if [value] starts with a spreadsheet formula trigger. */
    private fun neutralizeFormula(value: String): String = if (value.isNotEmpty() && value[0] in FORMULA_TRIGGER_CHARS) "'$value" else value

    private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    private const val CRLF = "\r\n"
}
