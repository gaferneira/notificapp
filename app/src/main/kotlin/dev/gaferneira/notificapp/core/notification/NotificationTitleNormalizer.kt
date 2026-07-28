package dev.gaferneira.notificapp.core.notification

import java.text.Normalizer
import java.util.Locale

/**
 * Folds a notification title down to a stable grouping key: superficial differences (casing,
 * amounts, timestamps, ids) collapse away so near-identical titles group together, while
 * genuinely unrelated titles do not. Pure, dependency-free, no I/O - mirrors
 * [NotificationDeduplicator]'s "compiled-once" discipline but carries no repository dependency.
 */
object NotificationTitleNormalizer {

    // Order matters: url/email before id (so a URL's domain segment isn't mistaken for an id),
    // currency before residual digits (so "$40.00" becomes <amt> not "$<num>.<num>").
    private val urlPattern = Regex("""https?://\S+""")
    private val emailPattern = Regex("""\S+@\S+\.\S+""")
    private val currencySymbolPattern = Regex("""[€$£¥₹]\s?\d[\d.,]*""")
    private val currencyCodePattern = Regex("""\d[\d.,]*\s?(usd|eur|cop|mxn|ars|gbp)\b""")
    private val timePattern = Regex("""\b\d{1,2}:\d{2}(:\d{2})?\s?(am|pm)?\b""")
    private val datePattern = Regex("""\b\d{1,4}[-/]\d{1,2}[-/]\d{1,4}\b""")
    private val idPattern = Regex("""\b(?=[a-z0-9-]*\d)[a-z0-9-]{6,}\b""")
    private val residualDigitsPattern = Regex("""\d+""")
    private val punctuationAndWhitespacePattern = Regex("""[\s\p{Punct}]+""")

    private const val MIN_ALPHA_CHARS = 3

    /**
     * @return a stable grouping key, or null when the title carries no stable structure (blank,
     *   or collapses to fewer than [MIN_ALPHA_CHARS] alphabetic characters after normalization).
     */
    fun normalize(title: String?): String? {
        if (title.isNullOrBlank()) return null

        val folded = Normalizer.normalize(title, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)

        var result = folded
        result = urlPattern.replace(result, "<url>")
        result = emailPattern.replace(result, "<email>")
        result = currencySymbolPattern.replace(result, "<amt>")
        result = currencyCodePattern.replace(result, "<amt>")
        result = timePattern.replace(result, "<time>")
        result = datePattern.replace(result, "<date>")
        result = idPattern.replace(result, "<id>")
        result = residualDigitsPattern.replace(result, "<num>")

        result = punctuationAndWhitespacePattern.replace(result, " ").trim()

        val alphaCount = result.count { it.isLetter() }
        if (alphaCount < MIN_ALPHA_CHARS) return null

        return result
    }
}
