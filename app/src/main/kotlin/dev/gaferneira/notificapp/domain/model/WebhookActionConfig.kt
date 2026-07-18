package dev.gaferneira.notificapp.domain.model

/**
 * Configuration key for the target [dev.gaferneira.notificapp.domain.model.Webhook.id].
 */
const val WEBHOOK_ID_KEY = "webhook_id"

/**
 * Configuration key for the [WebhookPayloadMode] ("fields" | "template").
 */
const val WEBHOOK_PAYLOAD_MODE_KEY = "webhook_payload_mode"

/**
 * Configuration key for the FIELDS-mode checklist: a CSV of selected keys. Built-in tokens
 * (`title`, `content`, `app_name`, `package_name`, `timestamp`, `raw_content`) are stored by
 * their fixed name; extracted fields are stored as `field.<fieldId>` - see the class doc below
 * for why fields reference `RuleField.id` rather than `RuleField.name`.
 */
const val WEBHOOK_SELECTED_FIELDS_KEY = "webhook_selected_fields"

/**
 * Configuration key for the TEMPLATE-mode custom JSON body containing `{{token}}` placeholders.
 */
const val WEBHOOK_TEMPLATE_KEY = "webhook_template"

/**
 * Prefix used to reference an extracted field by id within [WEBHOOK_SELECTED_FIELDS_KEY]'s CSV
 * (e.g. `field.<fieldId>`), distinguishing it from a fixed built-in token name.
 */
const val WEBHOOK_FIELD_ID_PREFIX = "field."

/** Built-in token names available in both FIELDS-mode checklist and TEMPLATE-mode `{{token}}` substitution. */
const val WEBHOOK_BUILTIN_TITLE = "title"
const val WEBHOOK_BUILTIN_CONTENT = "content"
const val WEBHOOK_BUILTIN_APP_NAME = "app_name"
const val WEBHOOK_BUILTIN_PACKAGE_NAME = "package_name"
const val WEBHOOK_BUILTIN_TIMESTAMP = "timestamp"
const val WEBHOOK_BUILTIN_RAW_CONTENT = "raw_content"

/** All built-in token names, in the Rule Editor's default-selection/checklist display order. */
val WEBHOOK_ALL_BUILTINS: List<String> = listOf(
    WEBHOOK_BUILTIN_TITLE,
    WEBHOOK_BUILTIN_CONTENT,
    WEBHOOK_BUILTIN_APP_NAME,
    WEBHOOK_BUILTIN_PACKAGE_NAME,
    WEBHOOK_BUILTIN_TIMESTAMP,
    WEBHOOK_BUILTIN_RAW_CONTENT,
)

/** Matches a `{{token}}` placeholder in a TEMPLATE-mode payload body. */
val WEBHOOK_TOKEN_REGEX: Regex = Regex("\\{\\{(.+?)\\}\\}")

/**
 * How a `SEND_WEBHOOK` action builds its outgoing payload.
 *
 * [FIELDS]: a fixed-shape JSON object built from only the selected keys (see
 * `WebhookPayloadBuilder`). [TEMPLATE]: the author's own JSON string, with `{{token}}`
 * placeholders substituted at build time.
 */
enum class WebhookPayloadMode { FIELDS, TEMPLATE }

/**
 * Groups the FIELDS/TEMPLATE payload-authoring options for
 * [dev.gaferneira.notificapp.domain.model.RuleAction.Companion.createSendWebhook] into a single
 * value object, mirroring `AlarmOptionsConfig`'s pattern of keeping that factory under detekt's
 * `LongParameterList` threshold.
 */
data class WebhookPayloadConfig(
    val mode: WebhookPayloadMode = WebhookPayloadMode.FIELDS,
    val fields: Set<String> = emptySet(),
    val template: String = "",
)

/**
 * Get the target webhook id, or null if unset/misconfigured.
 */
fun RuleAction.getWebhookId(): String? = config[WEBHOOK_ID_KEY]

/**
 * Get the configured payload mode, defaulting to [WebhookPayloadMode.FIELDS] if unset or
 * unrecognized (defense in depth against a malformed or imported rule).
 */
fun RuleAction.getWebhookPayloadMode(): WebhookPayloadMode = config[WEBHOOK_PAYLOAD_MODE_KEY]?.let { runCatching { WebhookPayloadMode.valueOf(it.uppercase()) }.getOrNull() }
    ?: WebhookPayloadMode.FIELDS

/**
 * Get the FIELDS-mode selected keys: built-in token names as-is, extracted fields as
 * `field.<fieldId>` (see [WEBHOOK_FIELD_ID_PREFIX]). Empty if unset.
 */
fun RuleAction.getWebhookSelectedFields(): Set<String> = config[WEBHOOK_SELECTED_FIELDS_KEY]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

/**
 * Get the TEMPLATE-mode custom JSON body, or an empty string if unset.
 */
fun RuleAction.getWebhookTemplate(): String = config[WEBHOOK_TEMPLATE_KEY].orEmpty()
