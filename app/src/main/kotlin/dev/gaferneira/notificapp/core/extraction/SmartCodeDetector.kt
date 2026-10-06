package dev.gaferneira.notificapp.core.extraction

/**
 * Pure Kotlin detector for one-time / verification codes (OTP, PIN, 2FA).
 *
 * A candidate is 4-8 characters: digits only (`123456`), two digit groups split by one space or
 * hyphen (`123-456`), or an uppercase alphanumeric containing a digit (`A1B2C3`), optionally with a
 * short letter prefix (`G-123456`). Candidates near a keyword win; without any keyword only a sole
 * digits-only candidate is accepted.
 */
internal object SmartCodeDetector {

    private const val KEYWORD_MAX_DISTANCE_BEFORE = 40
    private const val KEYWORD_MAX_DISTANCE_AFTER = 20
    private const val MAX_PHONE_DIGITS = 8
    private const val CURRENCY_CONTEXT_LENGTH = 6
    private const val MIN_YEAR = 1900
    private const val MAX_YEAR = 2099
    private const val YEAR_LENGTH = 4

    private const val BOUNDARY_CLASS = """[\p{L}\p{N}]"""
    private const val SPACES = """[   ]"""

    private val KEYWORD_REGEX = (
        """(?<!$BOUNDARY_CLASS)""" +
            """(?:code|otp|pin|passcode|verification|verify|one[- ]time|2fa|token|código|codigo|clave|contraseña)""" +
            """(?!$BOUNDARY_CLASS)"""
        ).toRegex(RegexOption.IGNORE_CASE)

    private val CANDIDATE_REGEX = (
        """(?<!$BOUNDARY_CLASS)(?:[A-Z]{1,3}-)?(?:\d{2,4}[ -]\d{2,4}|[A-Z0-9]{4,8})(?!$BOUNDARY_CLASS)"""
        ).toRegex()

    /** A run of digits joined by single separators; long runs look like phone numbers. */
    private val DIGIT_RUN_REGEX = """\d(?:[ .\-]?\d)*""".toRegex()

    private val CURRENCY_BEFORE_REGEX = """${FieldExtractor.CURRENCY_MARKER}$SPACES*$""".toRegex()
    private val CURRENCY_AFTER_REGEX = """^$SPACES*${FieldExtractor.CURRENCY_MARKER}""".toRegex()

    private val MASK_CHARS = setOf('*', '•', 'x', 'X')
    private val JOIN_SEPARATORS = setOf('.', ',', '-', '/', ':')

    private class Candidate(val range: IntRange, val value: String) {
        val isDigitsOnly: Boolean get() = value.none { it.isLetter() }
        val isYear: Boolean
            get() = value.length == YEAR_LENGTH && value.all { it.isDigit() } && value.toInt() in MIN_YEAR..MAX_YEAR
    }

    fun detect(text: String): ExtractionResult {
        val dateRanges = FieldExtractor.smartDateRanges(text)
        val phoneRuns = DIGIT_RUN_REGEX.findAll(text)
            .filter { run -> run.value.count { it.isDigit() } > MAX_PHONE_DIGITS }
            .map { it.range }
            .toList()
        val keywords = KEYWORD_REGEX.findAll(text).map { it.range }.toList()

        val candidates = CANDIDATE_REGEX.findAll(text)
            .filter { match -> match.value.any { it.isDigit() } }
            .map { Candidate(it.range, it.value) }
            .filterNot { isExcluded(text, it.range, dateRanges, phoneRuns) }
            .toList()

        val chosen = nearestToKeyword(candidates, keywords)
            ?: candidates.filter { it.isDigitsOnly && !it.isYear }.singleOrNull()

        return if (chosen != null) {
            ExtractionResult.Success(
                value = chosen.value,
                startIndex = chosen.range.first,
                endIndex = chosen.range.last + 1,
            )
        } else {
            ExtractionResult.Failure("No code detected")
        }
    }

    private fun isExcluded(
        text: String,
        range: IntRange,
        dateRanges: List<IntRange>,
        phoneRuns: List<IntRange>,
    ): Boolean = (dateRanges + phoneRuns).any { it.overlaps(range) } ||
        isMasked(text, range) ||
        isJoinedToNumber(text, range) ||
        isAmount(text, range)

    private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

    private fun isMasked(text: String, range: IntRange): Boolean = text.getOrNull(range.first - 1) in MASK_CHARS

    /** True when a separator links the candidate to another digit (decimals, times, dates, phone groups). */
    private fun isJoinedToNumber(text: String, range: IntRange): Boolean = isJoined(text, range.first - 1, -1) || isJoined(text, range.last + 1, 1)

    private fun isJoined(text: String, separatorIndex: Int, direction: Int): Boolean = text.getOrNull(separatorIndex) in JOIN_SEPARATORS &&
        text.getOrNull(separatorIndex + direction)?.isDigit() == true

    private fun isAmount(text: String, range: IntRange): Boolean {
        val before = text.substring((range.first - CURRENCY_CONTEXT_LENGTH).coerceAtLeast(0), range.first)
        val after = text.substring(range.last + 1, (range.last + 1 + CURRENCY_CONTEXT_LENGTH).coerceAtMost(text.length))
        return CURRENCY_BEFORE_REGEX.containsMatchIn(before) || CURRENCY_AFTER_REGEX.containsMatchIn(after)
    }

    private fun nearestToKeyword(candidates: List<Candidate>, keywords: List<IntRange>): Candidate? = candidates
        .mapNotNull { candidate -> keywordDistance(candidate.range, keywords)?.let { candidate to it } }
        .minWithOrNull(compareBy({ it.second }, { it.first.range.first }))
        ?.first

    /** Gap in characters to the closest keyword within range, or `null` when none is close enough. */
    private fun keywordDistance(range: IntRange, keywords: List<IntRange>): Int? = keywords
        .mapNotNull { keyword ->
            when {
                keyword.last < range.first -> (range.first - keyword.last - 1).takeIf { it <= KEYWORD_MAX_DISTANCE_BEFORE }
                range.last < keyword.first -> (keyword.first - range.last - 1).takeIf { it <= KEYWORD_MAX_DISTANCE_AFTER }
                else -> null
            }
        }
        .minOrNull()
}
