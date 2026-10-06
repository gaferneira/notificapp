package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import java.util.regex.PatternSyntaxException

/**
 * Pure Kotlin extraction engine for extracting fields from notification text.
 * No Android dependencies - fully unit testable.
 */
object FieldExtractor {

    internal const val CURRENCY_CODES = "USD|EUR|GBP|JPY|SEK|NOK|DKK|CHF|CAD|AUD|MXN|BRL|CLP|COP|ARS|PEN|INR|CNY"

    /** Currency symbol, or an ISO code / "kr" that is not embedded in a longer word. */
    internal const val CURRENCY_MARKER =
        """(?:R\$|[\$€£¥₹₩₽₺₪₱฿]|(?<!\p{L})(?:$CURRENCY_CODES|kr)(?!\p{L}))"""

    /** A number that starts and ends with a digit, with optional thousands separators and decimals. */
    private const val AMOUNT_NUMBER =
        """\d{1,3}(?:[.,   ]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?"""

    /** Group 1 is the prefix currency marker, group 2 the suffix marker (both optional). */
    private val SMART_AMOUNT_REGEX: Regex = (
        """(?:(?<![\p{L}\d])-)?(?:($CURRENCY_MARKER)[  ]*-?)?(?:$AMOUNT_NUMBER)(?!\d)""" +
            """(?:[  ]*($CURRENCY_MARKER))?"""
        ).toRegex()

    private const val MONTH_NAMES = "Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec"
    private const val YEAR = """(\d{4}|\d{2})(?!\d)"""
    private const val ORDINAL = "(?:st|nd|rd|th)?"

    private class DatePattern(val regex: Regex, val isValid: (MatchResult) -> Boolean)

    private val SMART_DATE_PATTERNS: List<DatePattern> = listOf(
        // DD/MM/YYYY or MM/DD/YYYY
        DatePattern("""(?<!\d)(\d{1,2})/(\d{1,2})/$YEAR""".toRegex(), ::isPlausibleDayMonth),
        // DD-MM-YYYY or MM-DD-YYYY
        DatePattern("""(?<!\d)(\d{1,2})-(\d{1,2})-$YEAR""".toRegex(), ::isPlausibleDayMonth),
        // DD.MM.YYYY (European)
        DatePattern("""(?<![\d.])(\d{1,2})\.(\d{1,2})\.(\d{4}|\d{2})(?!\d|\.\d)""".toRegex(), ::isPlausibleDayMonth),
        // YYYY-MM-DD
        DatePattern("""(?<!\d)(\d{4})-(\d{2})-(\d{2})(?!\d)""".toRegex()) { isMonthDay(it, monthGroup = 2, dayGroup = 3) },
        // 12 Jan 2024, 12th January 2024
        DatePattern(
            """(?<!\d)(\d{1,2})$ORDINAL\s+(?:$MONTH_NAMES)[a-z]*\.?\s+(\d{4})(?!\d)""".toRegex(RegexOption.IGNORE_CASE),
        ) { isDay(it.groupValues[1]) },
        // Jan 12, 2024, January 12th 2024
        DatePattern(
            """\b(?:$MONTH_NAMES)[a-z]*\.?\s+(\d{1,2})$ORDINAL,?\s+(\d{4})(?!\d)""".toRegex(RegexOption.IGNORE_CASE),
        ) { isDay(it.groupValues[1]) },
    )

    private val NUMERIC_FIELD_TYPES = setOf(RuleField.FieldType.NUMBER, RuleField.FieldType.CURRENCY)

    private val JSON_SIMPLE_ESCAPES = mapOf(
        '"' to '"',
        '\\' to '\\',
        '/' to '/',
        'n' to '\n',
        't' to '\t',
        'r' to '\r',
        'b' to '\b',
        'f' to '\u000C',
    )

    private val MASK_SEPARATORS = setOf('/', ':', '*')

    private const val UNICODE_ESCAPE_LENGTH = 4
    private const val HEX_RADIX = 16

    /**
     * Extract a field value from the given text using the field's extraction method.
     * A [ExtractionResult.Success] always carries a non-blank value (and, for numeric field
     * types, at least one digit); anything else is reported as a [ExtractionResult.Failure].
     *
     * @param text The source text to extract from
     * @param field The extraction field configuration
     * @return ExtractionResult containing the extracted value or failure reason
     */
    fun extract(text: String, field: RuleField): ExtractionResult = try {
        validate(dispatch(text, field.method), field)
    } catch (e: Exception) {
        ExtractionResult.Failure("Extraction error: ${e.message}")
    }

    private fun dispatch(text: String, method: ExtractionMethod): ExtractionResult = when (method) {
        is ExtractionMethod.FixedPosition -> extractFixedPosition(text, method)
        is ExtractionMethod.TextBetweenAnchors -> extractTextBetweenAnchors(text, method)
        is ExtractionMethod.RegexPattern -> extractWithRegex(text, method)
        is ExtractionMethod.TextAfterKeyword -> extractTextAfterKeyword(text, method)
        is ExtractionMethod.TextBeforeKeyword -> extractTextBeforeKeyword(text, method)
        is ExtractionMethod.LineExtraction -> extractLine(text, method)
        is ExtractionMethod.SplitByDelimiter -> extractSplitByDelimiter(text, method)
        is ExtractionMethod.JsonPath -> extractJsonPath(text, method)
        is ExtractionMethod.SmartAmountDetection -> extractSmartAmount(text)
        is ExtractionMethod.SmartDateDetection -> extractSmartDate(text)
        is ExtractionMethod.SmartCodeDetection -> extractSmartCode(text)
    }

    private fun validate(result: ExtractionResult, field: RuleField): ExtractionResult = when {
        result !is ExtractionResult.Success -> result
        result.value.isBlank() -> ExtractionResult.Failure("Extracted value is empty")
        field.fieldType in NUMERIC_FIELD_TYPES && result.value.none { it.isDigit() } ->
            ExtractionResult.Failure("Extracted value is not a number: ${result.value}")
        else -> result
    }

    /** Builds a Success for [text] trimmed in the range [from, to), with positions of the trimmed value. */
    private fun trimmedSuccess(text: String, from: Int, to: Int): ExtractionResult.Success {
        val raw = text.substring(from, to)
        val trimmed = raw.trim()
        val start = from + (raw.length - raw.trimStart().length)
        return ExtractionResult.Success(value = trimmed, startIndex = start, endIndex = start + trimmed.length)
    }

    private fun extractFixedPosition(text: String, method: ExtractionMethod.FixedPosition): ExtractionResult = when {
        method.startIndex < 0 -> ExtractionResult.Failure("Start index must not be negative")
        method.endIndex <= method.startIndex -> ExtractionResult.Failure("End index must be greater than start index")
        method.startIndex >= text.length -> ExtractionResult.Failure("Start index beyond text length")
        else -> {
            val end = method.endIndex.coerceAtMost(text.length)
            ExtractionResult.Success(
                value = text.substring(method.startIndex, end),
                startIndex = method.startIndex,
                endIndex = end,
            )
        }
    }

    private fun extractTextBetweenAnchors(text: String, method: ExtractionMethod.TextBetweenAnchors): ExtractionResult {
        val startIndex = text.indexOf(method.startAnchor)
        val afterStart = startIndex + method.startAnchor.length
        val endIndex = if (startIndex == -1) -1 else text.indexOf(method.endAnchor, afterStart)

        return when {
            method.startAnchor.isEmpty() -> ExtractionResult.Failure("Start anchor must not be empty")
            method.endAnchor.isEmpty() -> ExtractionResult.Failure("End anchor must not be empty")
            startIndex == -1 -> ExtractionResult.Failure("Start anchor not found: ${method.startAnchor}")
            endIndex == -1 -> ExtractionResult.Failure("End anchor not found: ${method.endAnchor}")
            else -> trimmedSuccess(text, afterStart, endIndex)
        }
    }

    private fun extractWithRegex(text: String, method: ExtractionMethod.RegexPattern): ExtractionResult = try {
        matchRegex(RegexCache.compiled(method.pattern), text, method.captureGroup)
    } catch (e: PatternSyntaxException) {
        ExtractionResult.Failure("Invalid regex: ${e.description}")
    }

    /**
     * A pattern without groups always yields the whole match, so the editor's default
     * `captureGroup = 1` works for plain patterns like `\d+`. A pattern that does have groups
     * must reference an existing one — never silently fall back to a different group.
     */
    private fun matchRegex(regex: Regex, text: String, requestedGroup: Int): ExtractionResult {
        val groupCount = regex.toPattern().matcher("").groupCount()
        val captureGroup = if (groupCount == 0) 0 else requestedGroup
        val match = if (captureGroup in 0..groupCount) regex.find(text) else null

        return when {
            captureGroup !in 0..groupCount ->
                ExtractionResult.Failure("Capture group $captureGroup does not exist in pattern")
            match == null -> ExtractionResult.Failure("Pattern did not match")
            else -> match.groups[captureGroup]
                ?.let { ExtractionResult.Success(it.value, it.range.first, it.range.last + 1) }
                ?: ExtractionResult.Failure("Capture group $captureGroup not found")
        }
    }

    private fun extractTextAfterKeyword(text: String, method: ExtractionMethod.TextAfterKeyword): ExtractionResult {
        val index = text.indexOf(method.keyword)
        val maxLength = method.maxLength

        return when {
            method.keyword.isEmpty() -> ExtractionResult.Failure("Keyword must not be empty")
            maxLength != null && maxLength <= 0 -> ExtractionResult.Failure("Max length must be greater than zero")
            index == -1 -> ExtractionResult.Failure("Keyword not found: ${method.keyword}")
            else -> {
                val afterKeyword = index + method.keyword.length
                val end = if (maxLength != null) (afterKeyword + maxLength).coerceAtMost(text.length) else text.length
                trimmedSuccess(text, afterKeyword, end)
            }
        }
    }

    private fun extractTextBeforeKeyword(text: String, method: ExtractionMethod.TextBeforeKeyword): ExtractionResult {
        val index = text.indexOf(method.keyword)

        return when {
            method.keyword.isEmpty() -> ExtractionResult.Failure("Keyword must not be empty")
            index == -1 -> ExtractionResult.Failure("Keyword not found: ${method.keyword}")
            else -> trimmedSuccess(text, 0, index)
        }
    }

    /** Start (inclusive) and end (exclusive, terminator excluded) offsets of every line, for \n, \r\n and \r. */
    private fun lineBounds(text: String): List<Pair<Int, Int>> {
        val bounds = mutableListOf<Pair<Int, Int>>()
        var lineStart = 0
        var i = 0
        while (i < text.length) {
            val char = text[i]
            if (char == '\n' || char == '\r') {
                bounds.add(lineStart to i)
                if (char == '\r' && text.getOrNull(i + 1) == '\n') i++
                lineStart = i + 1
            }
            i++
        }
        bounds.add(lineStart to text.length)
        return bounds
    }

    private fun extractLine(text: String, method: ExtractionMethod.LineExtraction): ExtractionResult {
        val bounds = lineBounds(text)
        val lineNumber = method.lineNumber

        return when {
            lineNumber < 1 -> ExtractionResult.Failure("Line number must be at least 1")
            lineNumber > bounds.size -> ExtractionResult.Failure("Line $lineNumber not found (text has ${bounds.size} lines)")
            else -> trimmedSuccess(text, bounds[lineNumber - 1].first, bounds[lineNumber - 1].second)
        }
    }

    private fun extractSplitByDelimiter(text: String, method: ExtractionMethod.SplitByDelimiter): ExtractionResult {
        val parts = if (method.delimiter.isEmpty()) emptyList() else text.split(method.delimiter)
        val index = method.takeIndex

        return when {
            method.delimiter.isEmpty() -> ExtractionResult.Failure("Delimiter must not be empty")
            index !in parts.indices ->
                ExtractionResult.Failure("Part index $index out of range (text has ${parts.size} parts)")
            else -> {
                val partStart = parts.take(index).sumOf { it.length + method.delimiter.length }
                trimmedSuccess(text, partStart, partStart + parts[index].length)
            }
        }
    }

    private class PathSegment(val key: String, val allowsIndex: Boolean)

    /**
     * Resolves a JSON path (dot notation `data.amount` / `items.0.name`, bracket notation
     * `items[0].name` / `$.items[0]`, quoted keys `['key']` / `["key"]`) by scanning only the
     * segments the path touches, never materializing sibling keys/values into a full tree. Since
     * each segment is a linear scan (no recursion into unrelated branches), an attacker-controlled
     * notification with a deeply/widely nested JSON payload can't trigger a StackOverflowError.
     */
    private fun extractJsonPath(text: String, method: ExtractionMethod.JsonPath): ExtractionResult {
        val segments = parseJsonPath(method.path)
            ?: return ExtractionResult.Failure("Invalid JSON path: ${method.path}")

        val resolved = segments.fold<PathSegment, String?>(text.trim()) { current, segment ->
            current?.let { resolveSegment(it, segment) }
        }

        return resolved?.let { unwrapJsonScalar(it) }
            ?.let { ExtractionResult.Success(it) }
            ?: ExtractionResult.Failure("Path not found: ${method.path}")
    }

    private fun resolveSegment(current: String, segment: PathSegment): String? = when {
        current.startsWith("{") -> findObjectValue(current, segment.key)
        current.startsWith("[") && segment.allowsIndex -> segment.key.toIntOrNull()?.let { findArrayValue(current, it) }
        else -> null
    }

    /** Returns the parsed segments, or `null` when the path is malformed (e.g. an unterminated bracket). */
    private fun parseJsonPath(path: String): List<PathSegment>? {
        val segments = mutableListOf<PathSegment>()
        var i = if (path.startsWith("$")) 1 else 0
        while (i < path.length) {
            val (segment, next) = when (path[i]) {
                '[' -> readBracketSegment(path, i)
                '.' -> readNameSegment(path, i + 1)
                else -> readNameSegment(path, i)
            } ?: return null
            if (segment.key.isNotEmpty()) segments.add(segment)
            i = next
        }
        return segments
    }

    private fun readNameSegment(path: String, start: Int): Pair<PathSegment, Int> {
        var end = start
        while (end < path.length && path[end] != '.' && path[end] != '[') end++
        return PathSegment(path.substring(start, end), allowsIndex = true) to end
    }

    /** Reads `[0]`, `['key']` or `["key"]` starting at the opening bracket. */
    private fun readBracketSegment(path: String, open: Int): Pair<PathSegment, Int>? {
        val inner = open + 1
        val quote = path.getOrNull(inner)?.takeIf { it == '\'' || it == '"' }
        return if (quote != null) {
            val close = path.indexOf(quote, inner + 1)
            if (close == -1 || path.getOrNull(close + 1) != ']') {
                null
            } else {
                PathSegment(path.substring(inner + 1, close), allowsIndex = false) to close + 2
            }
        } else {
            val close = path.indexOf(']', inner)
            if (close == -1) null else PathSegment(path.substring(inner, close).trim(), allowsIndex = true) to close + 1
        }
    }

    /**
     * Walks [content] tracking brace/bracket nesting and string state, invoking [onDelimiter] for
     * each top-level `:` or `,`. Stops early once [onDelimiter] returns `true`.
     */
    private fun scanTopLevel(content: String, onDelimiter: (index: Int, char: Char) -> Boolean) {
        var depth = 0
        var inString = false
        var escapeNext = false

        for (i in content.indices) {
            val char = content[i]
            when {
                escapeNext -> escapeNext = false
                char == '\\' -> escapeNext = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' || char == '[' -> depth++
                char == '}' || char == ']' -> depth--
                depth == 0 && (char == ':' || char == ',') -> if (onDelimiter(i, char)) return
            }
        }
    }

    /** Scans a `{...}` span for [key] at its top level, returning the raw unparsed value text. */
    private fun findObjectValue(json: String, key: String): String? {
        val content = json.removeSurrounding("{", "}").trim()
        if (content.isEmpty()) return null

        var segmentStart = 0
        var currentKey: String? = null
        var result: String? = null

        scanTopLevel(content) { i, char ->
            if (char == ':' && currentKey == null) {
                currentKey = unescapeJsonString(content.substring(segmentStart, i).trim().removeSurrounding("\""))
                segmentStart = i + 1
                false
            } else if (char == ',') {
                if (currentKey == key) {
                    result = content.substring(segmentStart, i).trim()
                    true
                } else {
                    currentKey = null
                    segmentStart = i + 1
                    false
                }
            } else {
                false
            }
        }

        return result ?: if (currentKey == key) content.substring(segmentStart).trim() else null
    }

    /** Scans a `[...]` span for the element at [index], returning the raw unparsed value text. */
    private fun findArrayValue(json: String, index: Int): String? {
        val content = json.removeSurrounding("[", "]").trim()
        if (index < 0 || content.isEmpty()) return null

        var segmentStart = 0
        var currentIndex = 0
        var result: String? = null

        scanTopLevel(content) { i, char ->
            if (char == ',') {
                if (currentIndex == index) {
                    result = content.substring(segmentStart, i).trim()
                    true
                } else {
                    currentIndex++
                    segmentStart = i + 1
                    false
                }
            } else {
                false
            }
        }

        return result ?: if (currentIndex == index) content.substring(segmentStart).trim() else null
    }

    /** Unwraps a raw JSON scalar span (string/number/bool/null) into its text form; `null` maps to Kotlin `null`. */
    private fun unwrapJsonScalar(value: String): String? {
        val trimmed = value.trim()
        return when {
            trimmed == "null" -> null
            trimmed.length >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"") ->
                unescapeJsonString(trimmed.substring(1, trimmed.length - 1))
            else -> trimmed
        }
    }

    /** Decodes JSON string escapes (`\"`, `\\`, `\/`, `\n`, `\t`, `\r`, `\b`, `\f`, `\uXXXX`). */
    private fun unescapeJsonString(raw: String): String {
        if (!raw.contains('\\')) return raw

        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val char = raw[i]
            val next = raw.getOrNull(i + 1)
            val simple = if (char == '\\' && next != null) JSON_SIMPLE_ESCAPES[next] else null
            val unicode = if (char == '\\' && next == 'u') parseUnicodeEscape(raw, i + 2) else null
            out.append(simple ?: unicode ?: char)
            i += when {
                simple != null -> 2
                unicode != null -> 2 + UNICODE_ESCAPE_LENGTH
                else -> 1
            }
        }
        return out.toString()
    }

    private fun parseUnicodeEscape(raw: String, start: Int): Char? = raw
        .takeIf { start + UNICODE_ESCAPE_LENGTH <= it.length }
        ?.substring(start, start + UNICODE_ESCAPE_LENGTH)
        ?.toIntOrNull(HEX_RADIX)
        ?.toChar()

    private fun extractSmartAmount(text: String): ExtractionResult {
        val matches = SMART_AMOUNT_REGEX.findAll(text).toList()
        val chosen = matches.firstOrNull { it.groups[1] != null || it.groups[2] != null }
            ?: matches.firstOrNull { !isDateTimeOrMaskFragment(text, it.range) }

        return if (chosen != null) {
            ExtractionResult.Success(
                value = chosen.value,
                startIndex = chosen.range.first,
                endIndex = chosen.range.last + 1,
            )
        } else {
            ExtractionResult.Failure("No amount detected")
        }
    }

    /** True when a bare number is really part of a time, date or masked card number. */
    private fun isDateTimeOrMaskFragment(text: String, range: IntRange): Boolean {
        val before = text.getOrNull(range.first - 1)
        val after = text.getOrNull(range.last + 1)
        val touchesSeparator = before in MASK_SEPARATORS || after in MASK_SEPARATORS
        val afterMask = before == 'x' || before == 'X'
        return touchesSeparator || afterMask ||
            isDashedOrDottedDate(text, range.first - 1, before, -1) ||
            isDashedOrDottedDate(text, range.last + 1, after, 1)
    }

    /** True when the separator at [index] is `-` or `.` joined to another digit (e.g. 2024-01-15, 25.12.2024). */
    private fun isDashedOrDottedDate(text: String, index: Int, separator: Char?, direction: Int): Boolean = (separator == '-' || separator == '.') && text.getOrNull(index + direction)?.isDigit() == true

    private fun extractSmartDate(text: String): ExtractionResult {
        val best = SMART_DATE_PATTERNS
            .mapNotNull { pattern -> pattern.regex.findAll(text).firstOrNull(pattern.isValid) }
            .minWithOrNull(compareBy<MatchResult> { it.range.first }.thenByDescending { it.value.length })

        return if (best != null) {
            ExtractionResult.Success(
                value = best.value,
                startIndex = best.range.first,
                endIndex = best.range.last + 1,
            )
        } else {
            ExtractionResult.Failure("No date detected")
        }
    }

    private fun extractSmartCode(text: String): ExtractionResult = SmartCodeDetector.detect(text)

    /** Ranges of every plausible date in [text]; lets other smart detectors skip date fragments. */
    internal fun smartDateRanges(text: String): List<IntRange> = SMART_DATE_PATTERNS.flatMap { pattern ->
        pattern.regex.findAll(text).filter(pattern.isValid).map { it.range }.toList()
    }

    private fun isDay(value: String): Boolean = value.toIntOrNull() in 1..31

    private fun isMonth(value: String): Boolean = value.toIntOrNull() in 1..12

    private fun isMonthDay(match: MatchResult, monthGroup: Int, dayGroup: Int): Boolean = isMonth(match.groupValues[monthGroup]) && isDay(match.groupValues[dayGroup])

    /** Numeric D/M/Y is accepted when either day-first or month-first ordering is valid. */
    private fun isPlausibleDayMonth(match: MatchResult): Boolean = isMonthDay(match, monthGroup = 2, dayGroup = 1) || isMonthDay(match, monthGroup = 1, dayGroup = 2)
}

/**
 * Result of a field extraction attempt.
 */
sealed class ExtractionResult {
    /**
     * Successful extraction with the extracted value and position info.
     * @param value The extracted text
     * @param startIndex The start index of the extracted text in the original text (inclusive)
     * @param endIndex The end index of the extracted text in the original text (exclusive)
     */
    data class Success(
        val value: String,
        val startIndex: Int = -1,
        val endIndex: Int = -1,
    ) : ExtractionResult() {
        /**
         * Whether position information is available for highlighting.
         */
        val hasPosition: Boolean get() = startIndex >= 0 && endIndex > startIndex
    }

    /**
     * Failed extraction with the reason for failure.
     */
    data class Failure(val reason: String) : ExtractionResult()
}
