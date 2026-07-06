package dev.gaferneira.notificapp.core.notification

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NotificationNormalizerTest {

    private val normalizer = NotificationNormalizer()

    @Suppress("LongParameterList")
    private fun rawData(
        packageName: String = "com.example.app",
        notificationId: Int = 42,
        postTime: Long = 1_000L,
        key: String? = "key-1",
        title: String? = null,
        text: String? = null,
        bigText: String? = null,
        textLines: List<String> = emptyList(),
        subText: String? = null,
        tickerText: String? = null,
    ) = RawNotificationData(
        packageName = packageName,
        notificationId = notificationId,
        postTime = postTime,
        key = key,
        title = title,
        text = text,
        bigText = bigText,
        textLines = textLines,
        subText = subText,
        tickerText = tickerText,
    )

    @Test
    fun `title is passed through unchanged`() {
        // Given: raw data with a title
        val raw = rawData(title = "ICA Kvantum")

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "ICA")

        // Then: the title is preserved
        result.title shouldBe "ICA Kvantum"
    }

    @Test
    fun `content prefers big text over regular text, text lines, and sub text`() {
        // Given: raw data with every content source populated
        val raw = rawData(
            bigText = "big text",
            text = "regular text",
            textLines = listOf("line 1", "line 2"),
            subText = "sub text",
        )

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: big text wins
        result.content shouldBe "big text"
    }

    @Test
    fun `content falls back to regular text when big text is absent`() {
        // Given: raw data with text, text lines, and sub text, but no big text
        val raw = rawData(text = "regular text", textLines = listOf("line 1"), subText = "sub text")

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: regular text is used
        result.content shouldBe "regular text"
    }

    @Test
    fun `content falls back to joined text lines when big text and text are absent`() {
        // Given: raw data with only text lines and sub text
        val raw = rawData(textLines = listOf("line 1", "line 2"), subText = "sub text")

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: text lines are joined with newlines
        result.content shouldBe "line 1\nline 2"
    }

    @Test
    fun `content falls back to sub text when no other source is present`() {
        // Given: raw data with only sub text
        val raw = rawData(subText = "sub text")

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: sub text is used
        result.content shouldBe "sub text"
    }

    @Test
    fun `content is null when no source has any text`() {
        // Given: raw data with no title, text, big text, text lines, or sub text
        val raw = rawData()

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: content is null - there is no bundle-dump fallback
        result.content shouldBe null
    }

    @Test
    fun `blank content sources are treated as absent`() {
        // Given: raw data where the higher-priority sources are blank, not null
        val raw = rawData(bigText = "   ", text = "", subText = "sub text")

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: blank sources are skipped in favor of the next non-blank one
        result.content shouldBe "sub text"
    }

    @Test
    fun `raw content joins title, content, and ticker with newlines`() {
        // Given: raw data with title, content, and a distinct ticker text
        val raw = rawData(title = "Title", text = "Content", tickerText = "Ticker")

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: all three are joined in order, each labeled
        result.rawContent shouldBe "Title: Title\nContent: Content\nTicker: Ticker"
    }

    @Test
    fun `raw content omits missing parts`() {
        // Given: raw data with only content, no title or ticker
        val raw = rawData(text = "Content only")

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: only the content line is present
        result.rawContent shouldBe "Content: Content only"
    }

    @Test
    fun `ticker text is not duplicated when it exactly matches an existing part`() {
        // Given: ticker text that happens to exactly match the formatted title part
        val raw = rawData(title = "Title", tickerText = "Title: Title")

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: the ticker is not appended a second time
        result.rawContent shouldBe "Title: Title"
    }

    @Test
    fun `notification id is package name, notification id, and post time joined by underscores`() {
        // Given: raw data with specific identity fields
        val raw = rawData(packageName = "com.example.app", notificationId = 42, postTime = 123456L)

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: the id format is stable - dedup and DB identity depend on this exact shape
        result.id shouldBe "com.example.app_42_123456"
    }

    @Test
    fun `resolved app name and package name are carried through`() {
        // Given: raw data for a specific package
        val raw = rawData(packageName = "com.example.app")

        // When: normalizing with a resolved app name
        val result = normalizer.normalize(raw, appName = "Example App")

        // Then: both fields are set from their respective inputs
        result.packageName shouldBe "com.example.app"
        result.appName shouldBe "Example App"
    }

    @Test
    fun `sbn key and post time are carried through`() {
        // Given: raw data with a specific key and post time
        val raw = rawData(key = "sbn-key", postTime = 999L)

        // When: normalizing
        val result = normalizer.normalize(raw, appName = "App")

        // Then: both are preserved on the domain model
        result.sbnKey shouldBe "sbn-key"
        result.timestamp shouldBe 999L
    }
}
