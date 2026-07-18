package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_APP_NAME
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TITLE
import dev.gaferneira.notificapp.domain.model.WEBHOOK_FIELD_ID_PREFIX
import dev.gaferneira.notificapp.domain.model.WEBHOOK_ID_KEY
import dev.gaferneira.notificapp.domain.model.WEBHOOK_PAYLOAD_MODE_KEY
import dev.gaferneira.notificapp.domain.model.WEBHOOK_SELECTED_FIELDS_KEY
import dev.gaferneira.notificapp.domain.model.WEBHOOK_TEMPLATE_KEY
import dev.gaferneira.notificapp.domain.model.WebhookPayloadMode
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/**
 * Unit tests for [WebhookPayloadBuilder] per design.md's Testing Strategy: FIELDS shape, TEMPLATE
 * substitution, JSON-escaping, and unknown-token handling. Pure JVM, no I/O.
 */
class WebhookPayloadBuilderTest {

    private val builder = WebhookPayloadBuilder()

    private fun webhookAction(
        selectedFields: Set<String> = emptySet(),
        mode: WebhookPayloadMode = WebhookPayloadMode.FIELDS,
        template: String = "",
    ) = createTestAction(
        type = ActionType.SEND_WEBHOOK,
        config = buildMap {
            put(WEBHOOK_ID_KEY, "wh-1")
            put(WEBHOOK_PAYLOAD_MODE_KEY, mode.name)
            put(WEBHOOK_SELECTED_FIELDS_KEY, selectedFields.joinToString(","))
            put(WEBHOOK_TEMPLATE_KEY, template)
        },
    )

    // ---- FIELDS mode ----

    @Test
    fun `FIELDS mode includes only selected built-in keys`() {
        val action = webhookAction(selectedFields = setOf(WEBHOOK_BUILTIN_TITLE, WEBHOOK_BUILTIN_APP_NAME))
        val notification = createTestNotification(title = "Hello", appName = "Test App")

        val json = builder.build(notification, action, emptyMap())
        val obj = Json.parseToJsonElement(json).jsonObject

        obj.keys shouldBe setOf(WEBHOOK_BUILTIN_TITLE, WEBHOOK_BUILTIN_APP_NAME)
        obj[WEBHOOK_BUILTIN_TITLE]?.jsonPrimitive?.content shouldBe "Hello"
        obj[WEBHOOK_BUILTIN_APP_NAME]?.jsonPrimitive?.content shouldBe "Test App"
    }

    @Test
    fun `FIELDS mode drops a selected field id no longer present in extractedFields`() {
        val action = webhookAction(selectedFields = setOf("${WEBHOOK_FIELD_ID_PREFIX}Amount"))
        val notification = createTestNotification()

        // extractedFields is name-keyed; "Amount" was resolved upstream but isn't present here,
        // simulating a deleted field / no extraction for this notification - the "fields" object
        // is still emitted (a field was selected) but ends up empty.
        val json = builder.build(notification, action, emptyMap())
        val obj = Json.parseToJsonElement(json).jsonObject

        obj.keys shouldBe setOf("fields")
        obj["fields"]?.jsonObject?.keys shouldBe emptySet()
    }

    @Test
    fun `FIELDS mode nests a resolvable extracted field under fields`() {
        val action = webhookAction(selectedFields = setOf("${WEBHOOK_FIELD_ID_PREFIX}Amount"))
        val notification = createTestNotification()

        val json = builder.build(notification, action, mapOf("Amount" to "42"))
        val obj = Json.parseToJsonElement(json).jsonObject

        obj.keys shouldBe setOf("fields")
        obj["fields"]?.jsonObject?.get("Amount")?.jsonPrimitive?.content shouldBe "42"
    }

    // ---- TEMPLATE mode ----

    @Test
    fun `TEMPLATE mode substitutes built-in tokens`() {
        val action = webhookAction(mode = WebhookPayloadMode.TEMPLATE, template = """{"t":"{{title}}","a":"{{app_name}}"}""")
        val notification = createTestNotification(title = "Hello", appName = "Test App")

        val json = builder.build(notification, action, emptyMap())

        json shouldBe """{"t":"Hello","a":"Test App"}"""
    }

    @Test
    fun `TEMPLATE mode substitutes extracted field tokens by name`() {
        val action = webhookAction(mode = WebhookPayloadMode.TEMPLATE, template = """{"amount":"{{field.Amount}}"}""")
        val notification = createTestNotification()

        val json = builder.build(notification, action, mapOf("Amount" to "42"))

        json shouldBe """{"amount":"42"}"""
    }

    @Test
    fun `TEMPLATE mode JSON-escapes a value containing a quote`() {
        val action = webhookAction(mode = WebhookPayloadMode.TEMPLATE, template = """{"t":"{{title}}"}""")
        val notification = createTestNotification(title = """He said "hi"""")

        val json = builder.build(notification, action, emptyMap())

        json shouldBe """{"t":"He said \"hi\""}"""
        // Round-trips as valid JSON.
        Json.parseToJsonElement(json).jsonObject["t"]?.jsonPrimitive?.content shouldBe """He said "hi""""
    }

    @Test
    fun `TEMPLATE mode JSON-escapes a value containing a newline`() {
        val action = webhookAction(mode = WebhookPayloadMode.TEMPLATE, template = """{"t":"{{title}}"}""")
        val notification = createTestNotification(title = "line1\nline2")

        val json = builder.build(notification, action, emptyMap())

        json shouldBe """{"t":"line1\nline2"}"""
        Json.parseToJsonElement(json).jsonObject["t"]?.jsonPrimitive?.content shouldBe "line1\nline2"
    }

    @Test
    fun `TEMPLATE mode substitutes an unknown token to an empty string`() {
        val action = webhookAction(mode = WebhookPayloadMode.TEMPLATE, template = """{"x":"{{totally_unknown}}"}""")
        val notification = createTestNotification()

        val json = builder.build(notification, action, emptyMap())

        json shouldBe """{"x":""}"""
    }
}
