package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.ExtractionField
import dev.gaferneira.notificapp.domain.model.ExtractionMethod

/**
 * Pure Kotlin extraction engine for extracting fields from notification text.
 * No Android dependencies - fully unit testable.
 */
object FieldExtractor {

    /**
     * Extract a field value from the given text using the field's extraction method.
     *
     * @param text The source text to extract from
     * @param field The extraction field configuration
     * @return ExtractionResult containing the extracted value or failure reason
     */
    fun extract(text: String, field: ExtractionField): ExtractionResult = try {
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

        return ExtractionResult.Success(text.substring(safeStart, safeEnd))
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

        return ExtractionResult.Success(text.substring(afterStart, endIndex).trim())
    }

    private fun extractWithRegex(text: String, method: ExtractionMethod.RegexPattern): ExtractionResult {
        val regex = method.pattern.toRegex()
        val match = regex.find(text)

        return if (match != null) {
            val groupIndex = method.captureGroup.coerceIn(0, match.groupValues.size - 1)
            val value = match.groupValues.getOrNull(groupIndex)
                ?: return ExtractionResult.Failure("Capture group $groupIndex not found")
            ExtractionResult.Success(value)
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
        var result = text.substring(afterKeyword)

        method.maxLength?.let { max ->
            result = result.take(max)
        }

        return ExtractionResult.Success(result.trim())
    }

    private fun extractTextBeforeKeyword(text: String, method: ExtractionMethod.TextBeforeKeyword): ExtractionResult {
        val index = text.indexOf(method.keyword)
        if (index == -1) {
            return ExtractionResult.Failure("Keyword not found: ${method.keyword}")
        }

        return ExtractionResult.Success(text.substring(0, index).trim())
    }

    private fun extractLine(text: String, method: ExtractionMethod.LineExtraction): ExtractionResult {
        val lines = text.lines()
        val lineNumber = method.lineNumber.coerceAtLeast(1)

        return if (lineNumber <= lines.size) {
            ExtractionResult.Success(lines[lineNumber - 1].trim())
        } else {
            ExtractionResult.Failure("Line $lineNumber not found (text has ${lines.size} lines)")
        }
    }

    private fun extractSplitByDelimiter(text: String, method: ExtractionMethod.SplitByDelimiter): ExtractionResult {
        val parts = text.split(method.delimiter)
        val index = method.takeIndex.coerceIn(0, parts.size - 1)

        return ExtractionResult.Success(parts[index].trim())
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
        // Regex to match currency amounts
        val amountRegex = """(?:\$|€|£|¥|USD|EUR|GBP|JPY|SEK|NOK|DKK|kr|USD\s*)?\s*[\d,.]+(?:\s*(?:USD|EUR|GBP|JPY|SEK|NOK|DKK|kr))?""".toRegex()
        val match = amountRegex.find(text)

        return if (match != null) {
            ExtractionResult.Success(match.value.trim())
        } else {
            ExtractionResult.Failure("No amount detected")
        }
    }

    private fun extractSmartDate(text: String): ExtractionResult {
        // Common date patterns
        val datePatterns = listOf(
            """\d{1,2}/\d{1,2}/\d{2,4}""".toRegex(), // MM/DD/YYYY or DD/MM/YYYY
            """\d{1,2}-\d{1,2}-\d{2,4}""".toRegex(), // MM-DD-YYYY
            """\d{4}-\d{2}-\d{2}""".toRegex(), // YYYY-MM-DD
            """\d{1,2}\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+\d{4}""".toRegex(RegexOption.IGNORE_CASE), // 12 Jan 2024
            """(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\.?\s+\d{1,2},?\s+\d{4}""".toRegex(RegexOption.IGNORE_CASE), // Jan 12, 2024
        )

        for (pattern in datePatterns) {
            val match = pattern.find(text)
            if (match != null) {
                return ExtractionResult.Success(match.value)
            }
        }

        return ExtractionResult.Failure("No date detected")
    }

    /**
     * Simple JSON parser for basic extraction needs.
     * For MVP, handles simple JSON objects and arrays.
     */
    private fun parseJson(json: String): Any? {
        val trimmed = json.trim()

        return when {
            trimmed.startsWith("{") -> parseJsonObject(trimmed)
            trimmed.startsWith("[") -> parseJsonArray(trimmed)
            trimmed.startsWith("\"") -> trimmed.removeSurrounding("\"")
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed == "null" -> null
            trimmed.toDoubleOrNull() != null -> trimmed.toDoubleOrNull()
            else -> trimmed
        }
    }

    private fun parseJsonObject(json: String): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val content = json.removeSurrounding("{", "}").trim()

        if (content.isEmpty()) return result

        var braceCount = 0
        var bracketCount = 0
        var inString = false
        var escapeNext = false
        var currentKey = ""
        var currentValue = StringBuilder()
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
                                currentValue = StringBuilder()
                                isKey = false
                            } else {
                                currentValue.append(char)
                            }
                        }
                        ',' -> {
                            if (braceCount == 0 && bracketCount == 0) {
                                result[currentKey] = parseJsonValue(currentValue.toString().trim())
                                currentValue = StringBuilder()
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
            result[currentKey] = parseJsonValue(currentValue.toString().trim())
        }

        return result
    }

    private fun parseJsonArray(json: String): List<Any?> {
        val result = mutableListOf<Any?>()
        val content = json.removeSurrounding("[", "]").trim()

        if (content.isEmpty()) return result

        var braceCount = 0
        var bracketCount = 0
        var inString = false
        var escapeNext = false
        var currentValue = StringBuilder()

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
                                result.add(parseJsonValue(currentValue.toString().trim()))
                                currentValue = StringBuilder()
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
            result.add(parseJsonValue(currentValue.toString().trim()))
        }

        return result
    }

    private fun parseJsonValue(value: String): Any? {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("{") -> parseJsonObject(trimmed)
            trimmed.startsWith("[") -> parseJsonArray(trimmed)
            trimmed.startsWith("\"") -> trimmed.removeSurrounding("\"")
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed == "null" -> null
            trimmed.toIntOrNull() != null -> trimmed.toInt()
            trimmed.toDoubleOrNull() != null -> trimmed.toDouble()
            else -> trimmed
        }
    }
}

/**
 * Result of a field extraction attempt.
 */
sealed class ExtractionResult {
    /**
     * Successful extraction with the extracted value.
     */
    data class Success(val value: String) : ExtractionResult()

    /**
     * Failed extraction with the reason for failure.
     */
    data class Failure(val reason: String) : ExtractionResult()
}
