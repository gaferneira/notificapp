package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod

/**
 * Pure Kotlin extraction engine for extracting fields from notification text.
 * No Android dependencies - fully unit testable.
 */
object FieldExtractor {

    private val SMART_AMOUNT_REGEX: Regex =
        """(?:\$|€|£|¥|USD|EUR|GBP|JPY|SEK|NOK|DKK|kr|USD\s*)?\s*[\d,.]+(?:\s*(?:USD|EUR|GBP|JPY|SEK|NOK|DKK|kr))?""".toRegex()

    private val SMART_DATE_REGEXES: List<Regex> = listOf(
        """\d{1,2}/\d{1,2}/\d{2,4}""".toRegex(), // MM/DD/YYYY or DD/MM/YYYY
        """\d{1,2}-\d{1,2}-\d{2,4}""".toRegex(), // MM-DD-YYYY
        """\d{4}-\d{2}-\d{2}""".toRegex(), // YYYY-MM-DD
        """\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+\d{4}""".toRegex(RegexOption.IGNORE_CASE), // 12 Jan 2024
        """(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+\d{1,2},?\s+\d{4}""".toRegex(RegexOption.IGNORE_CASE), // Jan 12, 2024
    )

    /**
     * Extract a field value from the given text using the field's extraction method.
     *
     * @param text The source text to extract from
     * @param field The extraction field configuration
     * @return ExtractionResult containing the extracted value or failure reason
     */
    fun extract(text: String, field: RuleField): ExtractionResult = try {
        when (val method = field.method) {
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
        }
    } catch (e: Exception) {
        ExtractionResult.Failure("Extraction error: ${e.message}")
    }

    private fun extractFixedPosition(text: String, method: ExtractionMethod.FixedPosition): ExtractionResult {
        val safeStart = method.startIndex.coerceIn(0, text.length)
        val safeEnd = method.endIndex.coerceIn(safeStart, text.length)

        if (safeStart >= text.length) {
            return ExtractionResult.Failure("Start index beyond text length")
        }

        return ExtractionResult.Success(
            value = text.substring(safeStart, safeEnd),
            startIndex = safeStart,
            endIndex = safeEnd,
        )
    }

    private fun extractTextBetweenAnchors(text: String, method: ExtractionMethod.TextBetweenAnchors): ExtractionResult {
        val startIndex = text.indexOf(method.startAnchor)
        if (startIndex == -1) {
            return ExtractionResult.Failure("Start anchor not found: ${method.startAnchor}")
        }

        val afterStart = startIndex + method.startAnchor.length
        val endIndex = text.indexOf(method.endAnchor, afterStart)
        if (endIndex == -1) {
            return ExtractionResult.Failure("End anchor not found: ${method.endAnchor}")
        }

        val extractedValue = text.substring(afterStart, endIndex).trim()
        // Find the actual start of the trimmed value (skip leading whitespace)
        val actualStart = afterStart + (text.substring(afterStart, endIndex).length - extractedValue.length)

        return ExtractionResult.Success(
            value = extractedValue,
            startIndex = actualStart,
            endIndex = actualStart + extractedValue.length,
        )
    }

    private fun extractWithRegex(text: String, method: ExtractionMethod.RegexPattern): ExtractionResult {
        val regex = RegexCache.compiled(method.pattern)
        val match = regex.find(text)

        return if (match != null) {
            val groupIndex = method.captureGroup.coerceIn(0, match.groupValues.size - 1)
            val group = match.groups[groupIndex]
                ?: return ExtractionResult.Failure("Capture group $groupIndex not found")
            val value = group.value
            val range = group.range
            ExtractionResult.Success(
                value = value,
                startIndex = range.first,
                endIndex = range.last + 1,
            )
        } else {
            ExtractionResult.Failure("Pattern did not match")
        }
    }

    private fun extractTextAfterKeyword(text: String, method: ExtractionMethod.TextAfterKeyword): ExtractionResult {
        val index = text.indexOf(method.keyword)
        if (index == -1) {
            return ExtractionResult.Failure("Keyword not found: ${method.keyword}")
        }

        val afterKeyword = index + method.keyword.length
        val maxLength = method.maxLength
        val rawResult = text.substring(afterKeyword)
        val result = if (maxLength != null) rawResult.take(maxLength) else rawResult
        val trimmedResult = result.trim()

        // Calculate actual start after whitespace
        val leadingWhitespace = result.length - result.trimStart().length
        val actualStart = afterKeyword + leadingWhitespace

        return ExtractionResult.Success(
            value = trimmedResult,
            startIndex = actualStart,
            endIndex = actualStart + trimmedResult.length,
        )
    }

    private fun extractTextBeforeKeyword(text: String, method: ExtractionMethod.TextBeforeKeyword): ExtractionResult {
        val index = text.indexOf(method.keyword)
        if (index == -1) {
            return ExtractionResult.Failure("Keyword not found: ${method.keyword}")
        }

        val rawResult = text.substring(0, index)
        val trimmedResult = rawResult.trim()
        val trailingWhitespace = rawResult.length - rawResult.trimEnd().length

        return ExtractionResult.Success(
            value = trimmedResult,
            startIndex = index - trimmedResult.length - trailingWhitespace,
            endIndex = index - trailingWhitespace,
        )
    }

    private fun extractLine(text: String, method: ExtractionMethod.LineExtraction): ExtractionResult {
        val lines = text.lines()
        val lineNumber = method.lineNumber.coerceAtLeast(1)

        return if (lineNumber <= lines.size) {
            val targetLine = lines[lineNumber - 1]
            val startIndex = lines.take(lineNumber - 1).joinToString("\n").let { if (it.isEmpty()) 0 else it.length + 1 }
            val trimmedLine = targetLine.trim()
            val leadingWhitespace = targetLine.length - targetLine.trimStart().length
            val actualStart = startIndex + leadingWhitespace

            ExtractionResult.Success(
                value = trimmedLine,
                startIndex = actualStart,
                endIndex = actualStart + trimmedLine.length,
            )
        } else {
            ExtractionResult.Failure("Line $lineNumber not found (text has ${lines.size} lines)")
        }
    }

    private fun extractSplitByDelimiter(text: String, method: ExtractionMethod.SplitByDelimiter): ExtractionResult {
        val parts = text.split(method.delimiter)
        val index = method.takeIndex.coerceIn(0, parts.size - 1)
        val targetPart = parts[index]
        val trimmedPart = targetPart.trim()

        // Calculate position
        val beforeParts = parts.take(index)
        val startIndex = beforeParts.joinToString(method.delimiter).let { if (it.isEmpty()) 0 else it.length + method.delimiter.length }
        val leadingWhitespace = targetPart.length - targetPart.trimStart().length
        val actualStart = startIndex + leadingWhitespace

        return ExtractionResult.Success(
            value = trimmedPart,
            startIndex = actualStart,
            endIndex = actualStart + trimmedPart.length,
        )
    }

    private fun extractJsonPath(text: String, method: ExtractionMethod.JsonPath): ExtractionResult {
        // Simple JSON path implementation for MVP
        // Supports dot notation like "data.amount" or "items.0.name"
        return try {
            var current: Any? = parseJson(text)
            val pathParts = method.path.trimStart('$').trimStart('.').split(".")

            for (part in pathParts) {
                current = when (current) {
                    is Map<*, *> -> (current as Map<String, *>)[part]
                    is List<*> -> part.toIntOrNull()?.let { index ->
                        if (index >= 0 && index < current.size) current[index] else null
                    }
                    else -> null
                }
                if (current == null) break
            }

            current?.toString()?.let { ExtractionResult.Success(it) }
                ?: ExtractionResult.Failure("Path not found: ${method.path}")
        } catch (e: Exception) {
            ExtractionResult.Failure("JSON parsing error: ${e.message}")
        }
    }

    private fun extractSmartAmount(text: String): ExtractionResult {
        val match = SMART_AMOUNT_REGEX.find(text)

        return if (match != null) {
            ExtractionResult.Success(
                value = match.value.trim(),
                startIndex = match.range.first,
                endIndex = match.range.last + 1,
            )
        } else {
            ExtractionResult.Failure("No amount detected")
        }
    }

    private fun extractSmartDate(text: String): ExtractionResult {
        for (pattern in SMART_DATE_REGEXES) {
            val match = pattern.find(text)
            if (match != null) {
                return ExtractionResult.Success(
                    value = match.value,
                    startIndex = match.range.first,
                    endIndex = match.range.last + 1,
                )
            }
        }

        return ExtractionResult.Failure("No date detected")
    }

    /**
     * Simple JSON parser for basic extraction needs.
     * For MVP, handles simple JSON objects and arrays.
     */
    private fun parseJson(json: String, depth: Int = 0): Any? {
        val trimmed = json.trim()

        return when {
            trimmed.startsWith("{") -> parseJsonObject(trimmed, depth)
            trimmed.startsWith("[") -> parseJsonArray(trimmed, depth)
            trimmed.startsWith("\"") -> trimmed.removeSurrounding("\"")
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed == "null" -> null
            else -> trimmed
        }
    }

    private fun parseJsonObject(json: String, depth: Int = 0): Map<String, Any?> {
        checkJsonDepth(depth)
        val result = mutableMapOf<String, Any?>()
        val content = json.removeSurrounding("{", "}").trim()

        if (content.isEmpty()) return result

        var braceCount = 0
        var bracketCount = 0
        var inString = false
        var escapeNext = false
        var currentKey = ""
        val currentValue = StringBuilder()
        var isKey = true

        for (char in content) {
            when {
                escapeNext -> {
                    currentValue.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    currentValue.append(char)
                    escapeNext = true
                }
                char == '"' && !escapeNext -> {
                    inString = !inString
                    currentValue.append(char)
                }
                !inString -> {
                    when (char) {
                        '{' -> {
                            braceCount++
                            currentValue.append(char)
                        }
                        '}' -> {
                            braceCount--
                            currentValue.append(char)
                        }
                        '[' -> {
                            bracketCount++
                            currentValue.append(char)
                        }
                        ']' -> {
                            bracketCount--
                            currentValue.append(char)
                        }
                        ':' -> {
                            if (braceCount == 0 && bracketCount == 0 && isKey) {
                                currentKey = currentValue.toString().trim().removeSurrounding("\"")
                                currentValue.setLength(0)
                                isKey = false
                            } else {
                                currentValue.append(char)
                            }
                        }
                        ',' -> {
                            if (braceCount == 0 && bracketCount == 0) {
                                result[currentKey] = parseJsonValue(currentValue.toString().trim(), depth + 1)
                                currentValue.setLength(0)
                                isKey = true
                            } else {
                                currentValue.append(char)
                            }
                        }
                        else -> currentValue.append(char)
                    }
                }
                else -> currentValue.append(char)
            }
        }

        if (!isKey && currentKey.isNotEmpty()) {
            result[currentKey] = parseJsonValue(currentValue.toString().trim(), depth + 1)
        }

        return result
    }

    private fun parseJsonArray(json: String, depth: Int = 0): List<Any?> {
        checkJsonDepth(depth)
        val result = mutableListOf<Any?>()
        val content = json.removeSurrounding("[", "]").trim()

        if (content.isEmpty()) return result

        var braceCount = 0
        var bracketCount = 0
        var inString = false
        var escapeNext = false
        val currentValue = StringBuilder()

        for (char in content) {
            when {
                escapeNext -> {
                    currentValue.append(char)
                    escapeNext = false
                }
                char == '\\' -> {
                    currentValue.append(char)
                    escapeNext = true
                }
                char == '"' && !escapeNext -> {
                    inString = !inString
                    currentValue.append(char)
                }
                !inString -> {
                    when (char) {
                        '{' -> {
                            braceCount++
                            currentValue.append(char)
                        }
                        '}' -> {
                            braceCount--
                            currentValue.append(char)
                        }
                        '[' -> {
                            bracketCount++
                            currentValue.append(char)
                        }
                        ']' -> {
                            bracketCount--
                            currentValue.append(char)
                        }
                        ',' -> {
                            if (braceCount == 0 && bracketCount == 0) {
                                result.add(parseJsonValue(currentValue.toString().trim(), depth + 1))
                                currentValue.setLength(0)
                            } else {
                                currentValue.append(char)
                            }
                        }
                        else -> currentValue.append(char)
                    }
                }
                else -> currentValue.append(char)
            }
        }

        if (currentValue.isNotEmpty()) {
            result.add(parseJsonValue(currentValue.toString().trim(), depth + 1))
        }

        return result
    }

    private fun parseJsonValue(value: String, depth: Int = 0): Any? {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("{") -> parseJsonObject(trimmed, depth)
            trimmed.startsWith("[") -> parseJsonArray(trimmed, depth)
            trimmed.startsWith("\"") -> trimmed.removeSurrounding("\"")
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed == "null" -> null
            // Numbers are kept as their raw trimmed text rather than boxed into Int/Double:
            // extractJsonPath only ever calls toString() on the result, and boxing a value like
            // "150.50" into a Double would silently drop the trailing zero.
            else -> trimmed
        }
    }

    /**
     * Guards the hand-rolled JSON parser against unbounded recursion: a deeply nested or
     * malformed payload would otherwise trigger a StackOverflowError, which (unlike a regular
     * exception) escapes the `catch (Exception)` wrapping in [extract] and [extractJsonPath].
     */
    private fun checkJsonDepth(depth: Int) {
        if (depth > MAX_JSON_DEPTH) {
            throw JsonDepthExceededException(depth)
        }
    }

    private const val MAX_JSON_DEPTH = 32
}

private class JsonDepthExceededException(depth: Int) : Exception("JSON nesting depth $depth exceeds maximum")

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
