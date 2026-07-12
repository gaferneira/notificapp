package dev.gaferneira.notificapp.core.extraction

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
        fun `coerces a negative start index to zero`() {
            // Given: a start index below the text bounds
            val text = "Hello World"
            val field = createTestField(method = ExtractionMethod.FixedPosition(startIndex = -5, endIndex = 5))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the start index is coerced to zero
            result shouldBe ExtractionResult.Success(value = "Hello", startIndex = 0, endIndex = 5)
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
        fun `startIndex calculation is skewed when both leading and trailing whitespace surround the value`() {
            // Given: text with both leading and trailing whitespace between the anchors
            // Known quirk (not fixed here, out of scope for TD-6): actualStart is computed as
            // afterStart + (untrimmed.length - trimmed.length), which adds the COMBINED leading+trailing
            // whitespace to the start offset instead of only the leading whitespace. When trailing
            // whitespace is present, the reported startIndex/endIndex therefore point past the real
            // location of the trimmed value in the source text.
            val text = "A:  100  B"
            val field = createTestField(
                method = ExtractionMethod.TextBetweenAnchors(startAnchor = "A:", endAnchor = "B"),
            )

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the value is correctly trimmed, but the position is off by the trailing whitespace length
            result shouldBe ExtractionResult.Success(value = "100", startIndex = 6, endIndex = 9)
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
        fun `coerces a negative capture group index to zero (full match)`() {
            // Given: a negative capture group index
            val text = "xxabc123defyy"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "abc(\\d+)def", captureGroup = -1))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the index is coerced to zero and the full match is returned
            result shouldBe ExtractionResult.Success(value = "abc123def", startIndex = 2, endIndex = 11)
        }

        @Test
        fun `coerces a too-large capture group index to the last available group`() {
            // Given: a capture group index beyond the number of groups in the pattern
            val text = "xxabc123defyy"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "abc(\\d+)def", captureGroup = 99))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the index is coerced down to the last group (group 1)
            result shouldBe ExtractionResult.Success(value = "123", startIndex = 5, endIndex = 8)
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
        fun `fails gracefully when the pattern itself is invalid`() {
            // Given: a syntactically invalid regex pattern
            val text = "anything"
            val field = createTestField(method = ExtractionMethod.RegexPattern(pattern = "[unclosed", captureGroup = 0))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the top-level catch turns it into a generic extraction error, not a crash
            (result as ExtractionResult.Failure).reason.startsWith("Extraction error:") shouldBe true
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
        fun `coerces a non-positive line number to the first line`() {
            // Given: a line number below 1
            val text = "Line1\nLine2\nLine3"
            val field = createTestField(method = ExtractionMethod.LineExtraction(lineNumber = -5))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the line number is coerced to 1
            result shouldBe ExtractionResult.Success(value = "Line1", startIndex = 0, endIndex = 5)
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
        fun `coerces a too-large take index to the last part`() {
            // Given: a take index beyond the number of parts
            val text = "a,b,c"
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = ",", takeIndex = 10))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the index is coerced down to the last part
            result shouldBe ExtractionResult.Success(value = "c", startIndex = 4, endIndex = 5)
        }

        @Test
        fun `coerces a negative take index to the first part`() {
            // Given: a negative take index
            val text = "a,b,c"
            val field = createTestField(method = ExtractionMethod.SplitByDelimiter(delimiter = ",", takeIndex = -5))

            // When: extracting the field
            val result = FieldExtractor.extract(text, field)

            // Then: the index is coerced up to the first part
            result shouldBe ExtractionResult.Success(value = "a", startIndex = 0, endIndex = 1)
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

            // Then: the number is still detected, since the currency markers are fully optional
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
    }
}
