package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.testutil.createTestField
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FieldExtractorTest {

    @Nested
    inner class FixedPositionTests {

        @Test
        fun `extracts substring within a normal range`() {
            // Given: a text and a range fully inside its bounds
            val text = "Hello World"
            val field = createTestField(method = ExtractionMethod.FixedPosition(startIndex = 0, endIndex = 5))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the substring is returned with matching position info
            result shouldBe ExtractionResult.Success(value = "Hello", startIndex = 0, endIndex = 5)
        }

        @Test
        fun `fails when the start index is negative`() {
            // Given: a start index below zero
            val text = "Hello World"
            val field = createTestField(method = ExtractionMethod.FixedPosition(startIndex = -5, endIndex = 5))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails instead of clamping
            result shouldBe ExtractionResult.Failure("Start index must not be negative")
        }

        @Test
        fun `coerces an end index beyond text length to the text length`() {
            // Given: an end index past the end of the text
            val text = "Hello World"
            val field = createTestField(method = ExtractionMethod.FixedPosition(startIndex = 6, endIndex = 100))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the end index is coerced to the text length
            result shouldBe ExtractionResult.Success(value = "World", startIndex = 6, endIndex = 11)
        }

        @Test
        fun `fails when start index is beyond text length`() {
            // Given: a start index at or beyond the text length
            val text = "Hello World"
            val field = createTestField(method = ExtractionMethod.FixedPosition(startIndex = 20, endIndex = 25))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Start index beyond text length")
        }

        @Test
        fun `fails when the end index is not greater than the start index`() {
            // Given: an empty range
            val text = "Hello World"
            val field = createTestField(method = ExtractionMethod.FixedPosition(startIndex = 5, endIndex = 5))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("End index must be greater than start index")
        }
    }

    @Nested
    inner class TextBetweenAnchorsTests {

        @Test
        fun `extracts text between anchors with no surrounding whitespace`() {
            // Given: text with a value tightly bound by both anchors
            val text = "Total: 100 USD end"
            val field = createTestField(
                method = ExtractionMethod.TextBetweenAnchors(startAnchor = "Total: ", endAnchor = " end"),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value between anchors is returned with correct positions
            result shouldBe ExtractionResult.Success(value = "100 USD", startIndex = 7, endIndex = 14)
        }

        @Test
        fun `trims leading whitespace and recalculates the start position`() {
            // Given: text where only leading whitespace separates the start anchor from the value
            val text = "X:   ValueZ"
            val field = createTestField(
                method = ExtractionMethod.TextBetweenAnchors(startAnchor = "X:", endAnchor = "Z"),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is trimmed and the position points at the trimmed value
            result shouldBe ExtractionResult.Success(value = "Value", startIndex = 5, endIndex = 10)
        }

        @Test
        fun `fails when the start anchor is not found`() {
            // Given: text that does not contain the start anchor
            val text = "no anchors here"
            val field = createTestField(
                method = ExtractionMethod.TextBetweenAnchors(startAnchor = "START", endAnchor = "END"),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Start anchor not found: START")
        }

        @Test
        fun `fails when the end anchor is not found after the start anchor`() {
            // Given: text that contains the start anchor but not the end anchor after it
            val text = "START but no closing tag"
            val field = createTestField(
                method = ExtractionMethod.TextBetweenAnchors(startAnchor = "START", endAnchor = "END"),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("End anchor not found: END")
        }

        @Test
        fun `reports the position of the trimmed value when whitespace surrounds it`() {
            // Given: text with both leading and trailing whitespace between the anchors
            val text = "A:  100  B"
            val field = createTestField(
                method = ExtractionMethod.TextBetweenAnchors(startAnchor = "A:", endAnchor = "B"),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the position points exactly at the trimmed value
            result shouldBe ExtractionResult.Success(value = "100", startIndex = 4, endIndex = 7)
        }

        @Test
        fun `fails when the start anchor is empty`() {
            // Given: an empty start anchor
            val text = "A: 100 B"
            val field = createTestField(method = ExtractionMethod.TextBetweenAnchors(startAnchor = "", endAnchor = "B"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Start anchor must not be empty")
        }

        @Test
        fun `fails when the end anchor is empty`() {
            // Given: an empty end anchor
            val text = "A: 100 B"
            val field = createTestField(method = ExtractionMethod.TextBetweenAnchors(startAnchor = "A:", endAnchor = ""))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("End anchor must not be empty")
        }
    }

    @Nested
    inner class RegexPatternTests {

        @Test
        fun `extracts the requested capture group on match`() {
            // Given: a pattern with a capture group narrower than the full match
            val text = "xxabc123defyy"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "abc(\\d+)def", captureGroup = 1))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: only the captured group value is returned
            result shouldBe ExtractionResult.Success(value = "123", startIndex = 5, endIndex = 8)
        }

        @Test
        fun `fails when the capture group is negative`() {
            // Given: a negative capture group index
            val text = "xxabc123defyy"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "abc(\\d+)def", captureGroup = -1))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails instead of clamping to the full match
            result shouldBe ExtractionResult.Failure("Capture group -1 does not exist in pattern")
        }

        @Test
        fun `fails when the capture group exceeds the group count`() {
            // Given: a capture group index beyond the number of groups in the pattern
            val text = "xxabc123defyy"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "abc(\\d+)def", captureGroup = 99))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails instead of clamping to the last group
            result shouldBe ExtractionResult.Failure("Capture group 99 does not exist in pattern")
        }

        @Test
        fun `returns the whole match when the pattern has no groups`() {
            // Given: a groupless pattern with the editor's default capture group
            val text = "Order 4521 shipped"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "\\d+"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the full match is returned
            result shouldBe ExtractionResult.Success(value = "4521", startIndex = 6, endIndex = 10)
        }

        @Test
        fun `fails when the resolved group did not participate in the match`() {
            // Given: an alternation pattern where the requested group is non-participating for this match
            val text = "b"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "(a)|(b)", captureGroup = 1))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails because group 1 has no value for this match
            result shouldBe ExtractionResult.Failure("Capture group 1 not found")
        }

        @Test
        fun `fails when the pattern does not match`() {
            // Given: a pattern that has no match in the text
            val text = "no digits here"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "\\d+", captureGroup = 0))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Pattern did not match")
        }

        @Test
        fun `fails with an invalid regex reason when the pattern itself is invalid`() {
            // Given: a syntactically invalid regex pattern
            val text = "anything"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "[unclosed", captureGroup = 0))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the failure names the invalid regex instead of crashing
            (result as ExtractionResult.Failure).reason.startsWith("Invalid regex:") shouldBe true
        }
    }

    @Nested
    inner class TextAfterKeywordTests {

        @Test
        fun `extracts text after the keyword with no max length`() {
            // Given: a keyword followed directly by the target value
            val text = "Total: 100 kr"
            val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "Total: ", maxLength = null))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: everything after the keyword is returned
            result shouldBe ExtractionResult.Success(value = "100 kr", startIndex = 7, endIndex = 13)
        }

        @Test
        fun `truncates the result to maxLength before trimming`() {
            // Given: a maxLength shorter than the remaining text after the keyword
            val text = "Total: 100 kr extra"
            val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "Total: ", maxLength = 3))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: only the first maxLength characters after the keyword are kept
            result shouldBe ExtractionResult.Success(value = "100", startIndex = 7, endIndex = 10)
        }

        @Test
        fun `trims leading whitespace and recalculates the start position`() {
            // Given: whitespace between the keyword and the value
            val text = "Key:   Value"
            val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "Key:", maxLength = null))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is trimmed and the position points at the trimmed value
            result shouldBe ExtractionResult.Success(value = "Value", startIndex = 7, endIndex = 12)
        }

        @Test
        fun `fails when the keyword is not found`() {
            // Given: text that does not contain the keyword
            val text = "nothing relevant here"
            val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "Total:", maxLength = null))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Keyword not found: Total:")
        }

        @Test
        fun `fails when the keyword is empty`() {
            // Given: an empty keyword
            val text = "Total: 100"
            val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "", maxLength = null))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Keyword must not be empty")
        }

        @Test
        fun `fails when maxLength is zero`() {
            // Given: a non-positive maxLength
            val text = "Total: 100"
            val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "Total:", maxLength = 0))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Max length must be greater than zero")
        }

        @Test
        fun `fails when the text after the keyword is blank`() {
            // Given: a keyword followed only by whitespace
            val text = "Total:   "
            val field = createTestField(method = ExtractionMethod.TextAfterKeyword(keyword = "Total:", maxLength = null))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the empty value is rejected
            result shouldBe ExtractionResult.Failure("Extracted value is empty")
        }
    }

    @Nested
    inner class TextBeforeKeywordTests {

        @Test
        fun `extracts and trims text before the keyword`() {
            // Given: text with whitespace padding around the value before the keyword
            val text = "  100  END"
            val field = createTestField(method = ExtractionMethod.TextBeforeKeyword(keyword = "END"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the trimmed value and its correct position are returned
            result shouldBe ExtractionResult.Success(value = "100", startIndex = 2, endIndex = 5)
        }

        @Test
        fun `fails when the keyword is not found`() {
            // Given: text that does not contain the keyword
            val text = "nothing relevant here"
            val field = createTestField(method = ExtractionMethod.TextBeforeKeyword(keyword = "END"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Keyword not found: END")
        }

        @Test
        fun `fails when the keyword is empty`() {
            // Given: an empty keyword
            val text = "100 END"
            val field = createTestField(method = ExtractionMethod.TextBeforeKeyword(keyword = ""))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Keyword must not be empty")
        }
    }

    @Nested
    inner class LineExtractionTests {

        @Test
        fun `extracts the requested line number`() {
            // Given: multi-line text and a 1-indexed line number
            val text = "Line1\nLine2\nLine3"
            val field = createTestField(method = ExtractionMethod.LineExtraction(lineNumber = 2))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the requested line is returned with correct positions
            result shouldBe ExtractionResult.Success(value = "Line2", startIndex = 6, endIndex = 11)
        }

        @Test
        fun `fails when the line number is below one`() {
            // Given: a line number below 1
            val text = "Line1\nLine2\nLine3"
            val field = createTestField(method = ExtractionMethod.LineExtraction(lineNumber = 0))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails instead of clamping
            result shouldBe ExtractionResult.Failure("Line number must be at least 1")
        }

        @Test
        fun `trims whitespace within the target line and recalculates its start position`() {
            // Given: a target line padded with leading and trailing whitespace
            val text = "A\n  B  \nC"
            val field = createTestField(method = ExtractionMethod.LineExtraction(lineNumber = 2))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the line is trimmed and the position points at the trimmed value
            result shouldBe ExtractionResult.Success(value = "B", startIndex = 4, endIndex = 5)
        }

        @Test
        fun `fails when the line number exceeds the number of lines`() {
            // Given: a line number beyond the text's line count
            val text = "Line1\nLine2"
            val field = createTestField(method = ExtractionMethod.LineExtraction(lineNumber = 5))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Line 5 not found (text has 2 lines)")
        }

        @Test
        fun `computes the start offset for CRLF line endings`() {
            // Given: text using Windows line endings
            val text = "Line1\r\nLine2\r\nLine3"
            val field = createTestField(method = ExtractionMethod.LineExtraction(lineNumber = 3))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the position matches the original text
            result shouldBe ExtractionResult.Success(value = "Line3", startIndex = 14, endIndex = 19)
        }

        @Test
        fun `computes the start offset for lone CR line endings`() {
            // Given: text using old Mac line endings
            val text = "Line1\rLine2\rLine3"
            val field = createTestField(method = ExtractionMethod.LineExtraction(lineNumber = 2))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the position matches the original text
            result shouldBe ExtractionResult.Success(value = "Line2", startIndex = 6, endIndex = 11)
        }
    }

    @Nested
    inner class SplitByDelimiterTests {

        @Test
        fun `extracts the requested part by index`() {
            // Given: delimited text and a valid part index
            val text = "a,b,c"
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = ",", takeIndex = 1))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the requested part is returned with correct positions
            result shouldBe ExtractionResult.Success(value = "b", startIndex = 2, endIndex = 3)
        }

        @Test
        fun `fails when the take index is beyond the number of parts`() {
            // Given: a take index beyond the number of parts
            val text = "a,b,c"
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = ",", takeIndex = 10))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails instead of clamping
            result shouldBe ExtractionResult.Failure("Part index 10 out of range (text has 3 parts)")
        }

        @Test
        fun `fails when the take index is negative`() {
            // Given: a negative take index
            val text = "a,b,c"
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = ",", takeIndex = -5))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails instead of clamping
            result shouldBe ExtractionResult.Failure("Part index -5 out of range (text has 3 parts)")
        }

        @Test
        fun `trims whitespace within the target part and recalculates its start position`() {
            // Given: parts padded with whitespace
            val text = "a, b , c"
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = ",", takeIndex = 1))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the part is trimmed and the position points at the trimmed value
            result shouldBe ExtractionResult.Success(value = "b", startIndex = 3, endIndex = 4)
        }

        @Test
        fun `fails when the delimiter is empty`() {
            // Given: an empty delimiter
            val text = "a,b,c"
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = "", takeIndex = 0))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Delimiter must not be empty")
        }

        @Test
        fun `reports the correct position when earlier parts are empty`() {
            // Given: a leading empty part before the requested one
            val text = ",b,c"
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = ",", takeIndex = 1))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the position points at the requested part
            result shouldBe ExtractionResult.Success(value = "b", startIndex = 1, endIndex = 2)
        }
    }

    @Nested
    inner class JsonPathTests {

        @Test
        fun `extracts a value from a dot-notation object path`() {
            // Given: a JSON object with a nested string value
            val text = """{"data":{"amount":"150.00"}}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "data.amount"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the nested value is returned
            result shouldBe ExtractionResult.Success(value = "150.00")
        }

        @Test
        fun `extracts a value from a path containing an array index`() {
            // Given: a JSON object containing an array of objects
            val text = """{"items":[{"name":"Widget"},{"name":"Gadget"}]}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "items.1.name"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value at the given array index is returned
            result shouldBe ExtractionResult.Success(value = "Gadget")
        }

        @Test
        fun `supports bracket notation for array indexes`() {
            // Given: a JSON document and a path
            val text = """{"items":[{"name":"Widget"},{"name":"Gadget"}]}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "items[1].name"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is resolved
            result shouldBe ExtractionResult.Success(value = "Gadget")
        }

        @Test
        fun `supports a root-anchored path with a trailing index`() {
            // Given: a JSON document and a path
            val text = """{"items":["a","b"]}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "$.items[0]"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is resolved
            result shouldBe ExtractionResult.Success(value = "a")
        }

        @Test
        fun `supports single-quoted bracket keys`() {
            // Given: a JSON document and a path
            val text = """{"a.b":"dotted"}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "['a.b']"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is resolved
            result shouldBe ExtractionResult.Success(value = "dotted")
        }

        @Test
        fun `supports double-quoted bracket keys`() {
            // Given: a JSON document and a path
            val text = """{"data":{"my key":"v"}}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "data[\"my key\"]"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is resolved
            result shouldBe ExtractionResult.Success(value = "v")
        }

        @Test
        fun `unescapes quotes and backslashes in string values`() {
            // Given: a JSON document and a path
            val text = """{"msg":"say \"hi\" C:\\x"}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "msg"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is resolved
            result shouldBe ExtractionResult.Success(value = "say \"hi\" C:\\x")
        }

        @Test
        fun `unescapes newline, tab and unicode sequences`() {
            // Given: a JSON document and a path
            val text = """{"msg":"a\nb\t\u00e9\/"}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "msg"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is resolved
            result shouldBe ExtractionResult.Success(value = "a\nb\t\u00e9/")
        }

        @Test
        fun `fails when the bracket is unterminated`() {
            // Given: a JSON document and a path
            val text = """{"a":1}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "a[0"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is resolved
            result shouldBe ExtractionResult.Failure("Invalid JSON path: a[0")
        }

        @Test
        fun `fails when the resolved JSON string is empty`() {
            // Given: a JSON document and a path
            val text = """{"a":""}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "a"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is resolved
            result shouldBe ExtractionResult.Failure("Extracted value is empty")
        }

        @Test
        fun `fails when the path does not resolve to a value`() {
            // Given: a JSON object missing the requested key
            val text = """{"data":{"amount":"150.00"}}"""
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "data.missing"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("Path not found: data.missing")
        }

        @Test
        fun `fails when the source text is not JSON`() {
            // Given: plain text with no JSON structure at all
            val text = "not json at all"
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "data.amount"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails because the path cannot resolve against a plain string
            result shouldBe ExtractionResult.Failure("Path not found: data.amount")
        }

        @Test
        fun `resolves a shallow path against a deeply nested payload without crashing`() {
            // Given: JSON nested far past any previous depth guard, e.g. a hostile notification
            // payload designed to trigger a StackOverflowError in a naive tree-parsing approach
            val text = "{\"a\":".repeat(100000) + "1" + "}".repeat(100000)
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "a.a.a"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the shallow path resolves normally instead of crashing or hitting an
            // artificial depth cap, because only the requested segments are ever scanned -
            // sibling and deeper structure is never materialized
            result.shouldBeInstanceOf<ExtractionResult.Success>()
        }

        @Test
        fun `fails gracefully instead of crashing when a deeply nested payload doesn't contain the path`() {
            // Given: the same hostile deep payload, but querying a key that isn't present
            val text = "{\"a\":".repeat(100000) + "1" + "}".repeat(100000)
            val field = createTestField(method = ExtractionMethod.JsonPath(path = "a.missing"))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails as a normal ExtractionResult, it doesn't crash the process
            result.shouldBeInstanceOf<ExtractionResult.Failure>()
        }
    }

    @Nested
    inner class SmartAmountDetectionTests {

        @Test
        fun `detects a kr-prefixed amount with a decimal comma`() {
            // Given: a Nordic-style currency amount
            val text = "kr 153,50"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the amount is detected
            result shouldBe ExtractionResult.Success(value = "kr 153,50", startIndex = 0, endIndex = 9)
        }

        @Test
        fun `detects a dollar-prefixed amount with a decimal point`() {
            // Given: a USD-style currency amount
            val text = "$42.99"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the amount is detected
            result shouldBe ExtractionResult.Success(value = "$42.99", startIndex = 0, endIndex = 6)
        }

        @Test
        fun `detects a bare numeric amount with no currency marker`() {
            // Given: a plain number with no currency symbol or code
            val text = "123"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the number is still detected, since the currency marker is optional
            result shouldBe ExtractionResult.Success(value = "123", startIndex = 0, endIndex = 3)
        }

        @Test
        fun `fails when the text has no digits or amount-like characters`() {
            // Given: text with no digits, commas, or periods at all
            val text = "no numbers or symbols here"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("No amount detected")
        }

        @Test
        fun `excludes the sentence-ending period after an amount`() {
            // Given: a sentence with an amount followed by a period
            val text = "Hi. You paid \$5.00."
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the trailing period is not part of the amount
            result shouldBe ExtractionResult.Success(value = "\$5.00", startIndex = 13, endIndex = 18)
        }

        @Test
        fun `prefers the currency amount over a masked card number`() {
            // Given: a masked card number before an amount with a suffix code
            val text = "Card ****1234 charged 45,90 EUR"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the currency-marked amount is chosen
            result shouldBe ExtractionResult.Success(value = "45,90 EUR", startIndex = 22, endIndex = 31)
        }

        @Test
        fun `fails when the text only has a trailing period`() {
            // Given: text containing only a period
            val text = "Text with no amount."
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: no amount is detected
            result shouldBe ExtractionResult.Failure("No amount detected")
        }

        @Test
        fun `never returns a lone dot or comma`() {
            // Given: a lone dot
            val text = "."
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: no amount is detected
            result shouldBe ExtractionResult.Failure("No amount detected")
        }

        @Test
        fun `detects an amount with thousands separators`() {
            // Given: an amount with a thousands comma and decimal point
            val text = "Total \$1,234.56 today"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the whole amount is returned
            result shouldBe ExtractionResult.Success(value = "\$1,234.56", startIndex = 6, endIndex = 15)
        }

        @Test
        fun `detects a negative amount`() {
            // Given: a negative amount
            val text = "Refund -\$5.00 issued"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the minus sign is part of the amount
            result shouldBe ExtractionResult.Success(value = "-\$5.00", startIndex = 7, endIndex = 13)
        }

        @Test
        fun `detects a suffix ISO code`() {
            // Given: an amount followed by an ISO code
            val text = "Paid 100 USD"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the code is included
            result shouldBe ExtractionResult.Success(value = "100 USD", startIndex = 5, endIndex = 12)
        }

        @Test
        fun `detects a prefix ISO code`() {
            // Given: an amount preceded by an ISO code
            val text = "Paid CHF 100"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the code is included
            result shouldBe ExtractionResult.Success(value = "CHF 100", startIndex = 5, endIndex = 12)
        }

        @Test
        fun `detects the R$ marker`() {
            // Given: an amount in Brazilian reais
            val text = "Pago R\$ 50,00"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the marker is included
            result shouldBe ExtractionResult.Success(value = "R\$ 50,00", startIndex = 5, endIndex = 13)
        }

        @Test
        fun `does not treat kr inside a word as a currency marker`() {
            // Given: a number next to words containing kr
            val text = "Krona 5 kronor"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: only the bare number is returned
            result shouldBe ExtractionResult.Success(value = "5", startIndex = 6, endIndex = 7)
        }

        @Test
        fun `skips times and dates when falling back to a bare number`() {
            // Given: a time and a date before a bare number
            val text = "At 12:30 on 12/25/2024 you got 50"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the bare number outside them is returned
            result shouldBe ExtractionResult.Success(value = "50", startIndex = 31, endIndex = 33)
        }

        @Test
        fun `skips ISO dates when falling back to a bare number`() {
            // Given: an ISO date before a bare number
            val text = "Due 2024-01-15 ref 77"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the bare number outside the date is returned
            result shouldBe ExtractionResult.Success(value = "77", startIndex = 19, endIndex = 21)
        }

        @Test
        fun `prefers the first currency amount over an earlier bare number`() {
            // Given: a bare number before a currency amount
            val text = "Order 12 total \$30.00"
            val field = createTestField(method = ExtractionMethod.SmartAmountDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the currency amount is chosen
            result shouldBe ExtractionResult.Success(value = "\$30.00", startIndex = 15, endIndex = 21)
        }
    }

    @Nested
    inner class SmartDateDetectionTests {

        @Test
        fun `detects a slash-separated date`() {
            // Given: a slash-separated date
            val text = "Due on 12/25/2024 please"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the date is detected
            result shouldBe ExtractionResult.Success(value = "12/25/2024", startIndex = 7, endIndex = 17)
        }

        @Test
        fun `detects a day-month-name-year date`() {
            // Given: a day-first, named-month date
            val text = "Delivered on 12 Jan 2024 morning"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the date is detected
            result shouldBe ExtractionResult.Success(value = "12 Jan 2024", startIndex = 13, endIndex = 24)
        }

        @Test
        fun `detects a month-name-first date`() {
            // Given: a named-month-first date
            val text = "Posted Jan 12, 2024 today"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the date is detected
            result shouldBe ExtractionResult.Success(value = "Jan 12, 2024", startIndex = 7, endIndex = 19)
        }

        @Test
        fun `fails when no date pattern matches`() {
            // Given: text with no recognizable date
            val text = "no date here"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result shouldBe ExtractionResult.Failure("No date detected")
        }

        @Test
        fun `detects an ISO date without truncating it`() {
            // Given: an ISO date
            val text = "Due 2024-01-15"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the full ISO date is returned
            result shouldBe ExtractionResult.Success(value = "2024-01-15", startIndex = 4, endIndex = 14)
        }

        @Test
        fun `picks the earliest date in the text`() {
            // Given: two dates in different formats
            val text = "Jan 12, 2024 then 12/25/2024"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the earliest one is returned
            result shouldBe ExtractionResult.Success(value = "Jan 12, 2024", startIndex = 0, endIndex = 12)
        }

        @Test
        fun `rejects out-of-range numeric dates`() {
            // Given: a numeric date with impossible month and day
            val text = "Ref 99/99/99"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: no date is detected
            result shouldBe ExtractionResult.Failure("No date detected")
        }

        @Test
        fun `accepts a day-first date when the month-first reading is invalid`() {
            // Given: a date whose first number exceeds 12
            val text = "On 25/12/2024"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: it is accepted as day-first
            result shouldBe ExtractionResult.Success(value = "25/12/2024", startIndex = 3, endIndex = 13)
        }

        @Test
        fun `detects a dotted European date`() {
            // Given: a DD.MM.YYYY date
            val text = "Fällig 25.12.2024"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the date is detected
            result shouldBe ExtractionResult.Success(value = "25.12.2024", startIndex = 7, endIndex = 17)
        }

        @Test
        fun `detects a dotted European date with a two-digit year`() {
            // Given: a DD.MM.YY date
            val text = "Due 05.03.24"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the date is detected
            result shouldBe ExtractionResult.Success(value = "05.03.24", startIndex = 4, endIndex = 12)
        }

        @Test
        fun `detects a month-first date with an ordinal suffix`() {
            // Given: a month-name date with an ordinal day
            val text = "Posted Jan 12th, 2024"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the suffix is included
            result shouldBe ExtractionResult.Success(value = "Jan 12th, 2024", startIndex = 7, endIndex = 21)
        }

        @Test
        fun `detects a day-first date with an ordinal suffix`() {
            // Given: a day-first date with an ordinal day
            val text = "Sent 12th January 2024"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the suffix is included
            result shouldBe ExtractionResult.Success(value = "12th January 2024", startIndex = 5, endIndex = 22)
        }

        @Test
        fun `rejects a month-name date with an impossible day`() {
            // Given: a month-name date with day 45
            val text = "Jan 45, 2024"
            val field = createTestField(method = ExtractionMethod.SmartDateDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: no date is detected
            result shouldBe ExtractionResult.Failure("No date detected")
        }
    }

    @Nested
    inner class SmartCodeDetectionTests {

        @Test
        fun `detects a plain verification code after a keyword`() {
            // Given: a notification text
            val text = "Your verification code is 482913"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "482913", startIndex = 26, endIndex = 32)
        }

        @Test
        fun `detects a letter-prefixed code and returns the prefix`() {
            // Given: a notification text
            val text = "G-123456 is your Google verification code"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "G-123456", startIndex = 0, endIndex = 8)
        }

        @Test
        fun `detects a hyphen-split code as shown`() {
            // Given: a notification text
            val text = "Use 123-456 to sign in. Code expires in 10 minutes"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "123-456", startIndex = 4, endIndex = 11)
        }

        @Test
        fun `detects a space-split code as shown`() {
            // Given: a notification text
            val text = "Your code: 123 456"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "123 456", startIndex = 11, endIndex = 18)
        }

        @Test
        fun `detects an uppercase alphanumeric code`() {
            // Given: a notification text
            val text = "Your login code is A1B2C3"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "A1B2C3", startIndex = 19, endIndex = 25)
        }

        @Test
        fun `detects a code after a Spanish keyword`() {
            // Given: a notification text
            val text = "Tu código es 7781"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "7781", startIndex = 13, endIndex = 17)
        }

        @Test
        fun `accepts a sole digits-only candidate without a keyword`() {
            // Given: a notification text
            val text = "Sign in with 5521 now"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "5521", startIndex = 13, endIndex = 17)
        }

        @Test
        fun `accepts a year-like code when anchored to a keyword`() {
            // Given: a notification text
            val text = "Your code is 2024"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "2024", startIndex = 13, endIndex = 17)
        }

        @Test
        fun `prefers the candidate closest to a keyword`() {
            // Given: a notification text
            val text = "Ref 9988. Your verification code is 482913"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Success(value = "482913", startIndex = 36, endIndex = 42)
        }

        @Test
        fun `rejects an amount`() {
            // Given: a notification text
            val text = "You paid \$1234.50 at Store"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Failure("No code detected")
        }

        @Test
        fun `rejects a masked card number`() {
            // Given: a notification text
            val text = "Card ****4821 charged"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Failure("No code detected")
        }

        @Test
        fun `rejects times and dates`() {
            // Given: a notification text
            val text = "Meeting at 10:30 on 12/05/2024"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Failure("No code detected")
        }

        @Test
        fun `rejects two digits-only candidates without a keyword`() {
            // Given: a notification text
            val text = "Order 4521 shipped, ref 9988"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Failure("No code detected")
        }

        @Test
        fun `rejects a phone-number-like run`() {
            // Given: a notification text
            val text = "Call 555-123-4567 now"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Failure("No code detected")
        }

        @Test
        fun `rejects a year without a keyword`() {
            // Given: a notification text
            val text = "Founded in 1999"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Failure("No code detected")
        }

        @Test
        fun `rejects a slice of a longer number`() {
            // Given: a notification text
            val text = "Your code is 123456789"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Failure("No code detected")
        }

        @Test
        fun `rejects a currency-adjacent number even next to a keyword`() {
            // Given: a notification text
            val text = "Your code is 1234 USD"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the expected outcome is produced
            result shouldBe ExtractionResult.Failure("No code detected")
        }

        @Test
        fun `reported positions slice back to the value`() {
            // Given: a text with a code in the middle
            val text = "Hi! Your OTP is G-123456, valid 5 min"
            val field = createTestField(method = ExtractionMethod.SmartCodeDetection)

            // When: extracting the field
            val result = FieldExtractor.extract(text, field) as ExtractionResult.Success

            // Then: the positions point at the value in the original text
            text.substring(result.startIndex, result.endIndex) shouldBe result.value
            result.value shouldBe "G-123456"
        }
    }

    @Nested
    inner class ResultValidationTests {

        @Test
        fun `rejects a blank extracted value`() {
            // Given: a fixed range that covers only whitespace
            val text = "a    b"
            val field = createTestField(method = ExtractionMethod.FixedPosition(startIndex = 1, endIndex = 4))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the blank value is rejected
            result shouldBe ExtractionResult.Failure("Extracted value is empty")
        }

        @Test
        fun `rejects a number field whose value has no digit`() {
            // Given: a number field extracting plain text
            val text = "Total: abc"
            val field = createTestField(
                fieldType = RuleField.FieldType.NUMBER,
                method = ExtractionMethod.TextAfterKeyword(keyword = "Total:", maxLength = null),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            (result as ExtractionResult.Failure).reason.startsWith("Extracted value is not a number") shouldBe true
        }

        @Test
        fun `rejects a currency field whose value has no digit`() {
            // Given: a currency field extracting a lone symbol
            val text = "Total: $"
            val field = createTestField(
                fieldType = RuleField.FieldType.CURRENCY,
                method = ExtractionMethod.TextAfterKeyword(keyword = "Total:", maxLength = null),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction fails
            result.shouldBeInstanceOf<ExtractionResult.Failure>()
        }

        @Test
        fun `accepts a number field whose value has a digit`() {
            // Given: a number field extracting a numeric value
            val text = "Total: 42"
            val field = createTestField(
                fieldType = RuleField.FieldType.NUMBER,
                method = ExtractionMethod.TextAfterKeyword(keyword = "Total:", maxLength = null),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction succeeds
            result shouldBe ExtractionResult.Success(value = "42", startIndex = 7, endIndex = 9)
        }

        @Test
        fun `does not require digits for string fields`() {
            // Given: a string field extracting plain text
            val text = "Total: abc"
            val field = createTestField(
                method = ExtractionMethod.TextAfterKeyword(keyword = "Total:", maxLength = null),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: extraction succeeds
            result shouldBe ExtractionResult.Success(value = "abc", startIndex = 7, endIndex = 10)
        }
    }
}
