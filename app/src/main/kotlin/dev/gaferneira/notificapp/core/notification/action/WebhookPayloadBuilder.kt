package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_APP_NAME
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_CONTENT
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_PACKAGE_NAME
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_RAW_CONTENT
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TIMESTAMP
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TITLE
import dev.gaferneira.notificapp.domain.model.WEBHOOK_FIELD_ID_PREFIX
import dev.gaferneira.notificapp.domain.model.WEBHOOK_TOKEN_REGEX
import dev.gaferneira.notificapp.domain.model.WebhookPayloadMode
import dev.gaferneira.notificapp.domain.model.getWebhookPayloadMode
import dev.gaferneira.notificapp.domain.model.getWebhookSelectedFields
import dev.gaferneira.notificapp.domain.model.getWebhookTemplate
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/**
 * Builds the outgoing JSON payload for a `SEND_WEBHOOK` action, pure Kotlin (no I/O), per
 * design.md's Payload Builder section.
 *
 * [extractedFields] is already **name**-keyed and already limited to fields whose id still
 * exists on the rule ([dev.gaferneira.notificapp.core.notification.ProcessNotificationUseCase]
 * resolves `RuleMatch.extractedData` - id-keyed - via `rule.saveDataFields()` before this class
 * ever sees it), since this pure builder has no `Rule` access to do that resolution itself.
 *
 * **Deviation from design.md**: the FIELDS-mode selection stored under
 * [WEBHOOK_SELECTED_FIELDS_KEY] references extracted fields as `field.<fieldId>`
 * (see [WebhookActionConfig]'s doc), to survive a field rename. Resolving that id to the
 * *current* field name requires `rule.fields`, which this builder doesn't have (only
 * `(notification, action, extractedFields)`, matching [ActionExecutor.execute]'s contract).
 * Until the Rule Editor UI (Phase 5) and its ViewModel exist to actually author these selections,
 * this builder matches the suffix after `field.` directly against [extractedFields]'s **name**
 * keys - forward-compatible once Phase 5 resolves id-\>name upstream and passes the resolved name
 * through the existing `field.<name>` shape.
 */
class WebhookPayloadBuilder @Inject constructor() {

    fun build(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): String = when (action.getWebhookPayloadMode()) {
        WebhookPayloadMode.FIELDS -> buildFieldsPayload(notification, action, extractedFields)
        WebhookPayloadMode.TEMPLATE -> buildTemplatePayload(notification, action, extractedFields)
    }

    private fun buildFieldsPayload(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): String {
        val selected = action.getWebhookSelectedFields()
        val selectedFieldNames = selected
            .filter { it.startsWith(WEBHOOK_FIELD_ID_PREFIX) }
            .map { it.removePrefix(WEBHOOK_FIELD_ID_PREFIX) }

        return buildJsonObject {
            if (WEBHOOK_BUILTIN_TITLE in selected) put(WEBHOOK_BUILTIN_TITLE, notification.title.orEmpty())
            if (WEBHOOK_BUILTIN_CONTENT in selected) put(WEBHOOK_BUILTIN_CONTENT, notification.content.orEmpty())
            if (WEBHOOK_BUILTIN_APP_NAME in selected) put(WEBHOOK_BUILTIN_APP_NAME, notification.appName)
            if (WEBHOOK_BUILTIN_PACKAGE_NAME in selected) put(WEBHOOK_BUILTIN_PACKAGE_NAME, notification.packageName)
            if (WEBHOOK_BUILTIN_TIMESTAMP in selected) put(WEBHOOK_BUILTIN_TIMESTAMP, notification.timestamp)
            if (WEBHOOK_BUILTIN_RAW_CONTENT in selected) put(WEBHOOK_BUILTIN_RAW_CONTENT, notification.rawContent)

            if (selectedFieldNames.isNotEmpty()) {
                putJsonObject("fields") {
                    // Drops any selected name absent from extractedFields - either its field id no
                    // longer exists on the rule, or extraction produced no value for this notification.
                    selectedFieldNames.forEach { name -> extractedFields[name]?.let { value -> put(name, value) } }
                }
            }
        }.toString()
    }

    private fun buildTemplatePayload(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): String {
        val builtins = builtinTokenValues(notification)
        val template = action.getWebhookTemplate()
        return WEBHOOK_TOKEN_REGEX.replace(template) { match ->
            val token = match.groupValues[1]
            val rawValue = builtins[token]
                ?: token.takeIf { it.startsWith(WEBHOOK_FIELD_ID_PREFIX) }
                    ?.let { extractedFields[it.removePrefix(WEBHOOK_FIELD_ID_PREFIX)] }
                    // Unknown token: substitutes to empty rather than dropping the notification - see
                    // design.md ("strict at authoring, lenient at runtime").
                    .orEmpty()
            escapeJsonStringValue(rawValue)
        }
    }

    private fun builtinTokenValues(notification: Notification): Map<String, String> = mapOf(
        WEBHOOK_BUILTIN_TITLE to notification.title.orEmpty(),
        WEBHOOK_BUILTIN_CONTENT to notification.content.orEmpty(),
        WEBHOOK_BUILTIN_APP_NAME to notification.appName,
        WEBHOOK_BUILTIN_PACKAGE_NAME to notification.packageName,
        WEBHOOK_BUILTIN_TIMESTAMP to notification.timestamp.toString(),
        WEBHOOK_BUILTIN_RAW_CONTENT to notification.rawContent,
    )

    /**
     * JSON-string-escapes [value] then strips the outer quotes added by
     * [kotlinx.serialization.json.Json.encodeToString], so it can be injected inside the author's
     * existing `"..."` in the template - guarantees a value containing `"`/`\`/newline can't break
     * the document (design.md's JSON-safety note).
     */
    private fun escapeJsonStringValue(value: String): String {
        val quoted = Json.encodeToString(String.serializer(), value)
        return quoted.substring(1, quoted.length - 1)
    }
}
