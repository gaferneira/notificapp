package dev.gaferneira.notificapp.domain.model

/**
 * Configuration key for the `SEND_REPLY` (BETA) action's reply-text template. Uses the same
 * `{{token}}` placeholder convention as [READ_ALOUD_TEMPLATE_KEY] (see [WEBHOOK_TOKEN_REGEX] /
 * [WEBHOOK_FIELD_ID_PREFIX]) so a rule author reuses one substitution syntax across action types.
 */
const val SEND_REPLY_TEMPLATE_KEY = "send_reply_template"

/**
 * Get the configured reply-text template, or an empty string if unset.
 */
fun RuleAction.getSendReplyTemplate(): String = config[SEND_REPLY_TEMPLATE_KEY].orEmpty()
