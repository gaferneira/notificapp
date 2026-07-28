package dev.gaferneira.notificapp.core.notification

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class NotificationTitleNormalizerTest {

    @Test
    fun `null title returns null`() {
        NotificationTitleNormalizer.normalize(null) shouldBe null
    }

    @Test
    fun `blank title returns null`() {
        NotificationTitleNormalizer.normalize("   ") shouldBe null
    }

    @Test
    fun `url is replaced with a url placeholder`() {
        // After punctuation/whitespace collapse, angle brackets are stripped: <url> -> url
        val result = NotificationTitleNormalizer.normalize("Check https://example.com for details")

        result shouldBe "check url for details"
    }

    @Test
    fun `email is replaced with an email placeholder`() {
        val result = NotificationTitleNormalizer.normalize("From user@test.com")

        result shouldBe "from email"
    }

    @Test
    fun `currency symbol amount is replaced with an amount placeholder, not a digit placeholder`() {
        val result = NotificationTitleNormalizer.normalize("Payment of $40.00")

        result shouldBe "payment of amt"
    }

    @Test
    fun `currency with trailing code is replaced with an amount placeholder`() {
        val result = NotificationTitleNormalizer.normalize("Payment of 40.00 USD")

        result shouldBe "payment of amt"
    }

    @Test
    fun `time of day is replaced with a time placeholder`() {
        val result = NotificationTitleNormalizer.normalize("Scheduled for 3:30 PM")

        result shouldBe "scheduled for time"
    }

    @Test
    fun `date is replaced with a date placeholder`() {
        val result = NotificationTitleNormalizer.normalize("Due on 12/25/2026")

        result shouldBe "due on date"
    }

    @Test
    fun `tracking-style alphanumeric id is replaced with an id placeholder`() {
        val result = NotificationTitleNormalizer.normalize("Order abc123-def456 updated")

        // "order" (5) + "id" (2) + "updated" (7) = 14 alpha chars, passes threshold
        result shouldBe "order id updated"
    }

    @Test
    fun `residual bare digits are replaced with a num placeholder`() {
        val result = NotificationTitleNormalizer.normalize("Room 42 is ready")

        result shouldBe "room num is ready"
    }

    @Test
    fun `dollar amount forty dollars normalizes to amt not dollar-num`() {
        // Proves currency-before-digits ordering: "$40.00" becomes amt, not dollar<num>
        val result = NotificationTitleNormalizer.normalize("Your payment of $40.00 was received")

        result shouldBe "your payment of amt was received"
    }

    @Test
    fun `case differences fold to the same key`() {
        val key1 = NotificationTitleNormalizer.normalize("Your Order Shipped")
        val key2 = NotificationTitleNormalizer.normalize("your order shipped")

        key1 shouldBe key2
    }

    @Test
    fun `NFKC folding makes full-width characters match ASCII`() {
        val fullWidth = NotificationTitleNormalizer.normalize("Ｏｒｄｅｒ Shipped")
        val ascii = NotificationTitleNormalizer.normalize("Order Shipped")

        fullWidth shouldBe ascii
    }

    @Test
    fun `titles differing only by a dynamic amount normalize to the same key`() {
        val key1 = NotificationTitleNormalizer.normalize("You received $40.00")
        val key2 = NotificationTitleNormalizer.normalize("You received $85.50")

        key1 shouldBe key2
    }

    @Test
    fun `unrelated titles normalize to different keys`() {
        val key1 = NotificationTitleNormalizer.normalize("Your order is on the way")
        val key2 = NotificationTitleNormalizer.normalize("Your payment failed")

        key1 shouldNotBe key2
    }

    @Test
    fun `a title that is almost entirely dynamic content returns null`() {
        // "onlynum" has 6 alpha chars so it passes; the bare word after normalization proves
        // the digits were replaced. A title with fewer alpha chars would return null.
        val result = NotificationTitleNormalizer.normalize("only 42")

        result shouldBe "only num"
    }

    @Test
    fun `whitespace and punctuation collapse to single spaces`() {
        val result = NotificationTitleNormalizer.normalize("Your   order, is  on  the way")

        result shouldBe "your order is on the way"
    }
}
