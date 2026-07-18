package dev.gaferneira.notificapp.domain.model

/**
 * Configuration key for the `READ_ALOUD` action's spoken-text template. Uses the same
 * `{{token}}` placeholder convention as [WEBHOOK_TEMPLATE_KEY] (see [WEBHOOK_TOKEN_REGEX] /
 * [WEBHOOK_FIELD_ID_PREFIX]) so a rule author reuses one substitution syntax across action types.
 */
const val READ_ALOUD_TEMPLATE_KEY = "read_aloud_template"

/**
 * Get the configured spoken-text template, or an empty string if unset.
 */
fun RuleAction.getReadAloudTemplate(): String = config[READ_ALOUD_TEMPLATE_KEY].orEmpty()
