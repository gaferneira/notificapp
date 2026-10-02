package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_APP_NAME
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_CONTENT
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_PACKAGE_NAME
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_RAW_CONTENT
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TIMESTAMP
import dev.gaferneira.notificapp.domain.model.WEBHOOK_BUILTIN_TITLE
import dev.gaferneira.notificapp.domain.model.WEBHOOK_FIELD_ID_PREFIX

/**
 * Shared `{{token}}` placeholder convention (see
 * [dev.gaferneira.notificapp.domain.model.WEBHOOK_TOKEN_REGEX]) used by every template-authored
 * action config: `SEND_WEBHOOK`'s TEMPLATE payload mode ([WebhookPayloadBuilder]) and
 * `READ_ALOUD`'s spoken-text template ([ReadAloudActionExecutor]). Kept here, rather than
 * duplicated per action type, so a rule author learns one substitution syntax across actions.
 */

/**
 * Built-in token values available to any template, keyed by the same names used in
 * [dev.gaferneira.notificapp.domain.model.WEBHOOK_ALL_BUILTINS].
 */
internal fun builtinTokenValues(notification: Notification): Map<String, String> = mapOf(
    WEBHOOK_BUILTIN_TITLE to notification.title.orEmpty(),
    WEBHOOK_BUILTIN_CONTENT to notification.content.orEmpty(),
    WEBHOOK_BUILTIN_APP_NAME to notification.appName,
    WEBHOOK_BUILTIN_PACKAGE_NAME to notification.packageName,
    WEBHOOK_BUILTIN_TIMESTAMP to notification.timestamp.toString(),
    WEBHOOK_BUILTIN_RAW_CONTENT to notification.rawContent,
)

/**
 * Resolves a single `{{token}}` match (the captured group, without braces) to its raw string
 * value: a [builtins] hit, or - for a `field.<fieldId>`-prefixed token - the matching entry in
 * [extractedFields]. An unknown token resolves to an empty string rather than failing the whole
 * template - see design.md's "strict at authoring, lenient at runtime" note on the webhook
 * TEMPLATE mode, which applies equally here.
 */
internal fun resolveTemplateToken(token: String, builtins: Map<String, String>, extractedFields: Map<String, String>): String = builtins[token]
    ?: token.takeIf { it.startsWith(WEBHOOK_FIELD_ID_PREFIX) }
        ?.let { extractedFields[it.removePrefix(WEBHOOK_FIELD_ID_PREFIX)] }
        .orEmpty()
