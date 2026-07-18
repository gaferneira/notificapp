package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.WebhookPayloadMode

/**
 * Feature-owned, UI-facing snapshot of a `SEND_WEBHOOK` action's authoring state.
 *
 * @param webhookId the target [dev.gaferneira.notificapp.domain.model.Webhook.id], or null until
 * one is picked/created - gates whether the sheet's confirm button is enabled.
 * @param mode current payload-authoring mode (field checklist vs. custom JSON template).
 * @param selectedBuiltins built-in token names (`title`/`content`/`app_name`/`package_name`/
 * `timestamp`/`raw_content`) checked in FIELDS mode - the un-prefixed UI shape; the
 * `field.<fieldId>` prefixing used by the underlying config map only happens at the
 * ViewModel/Contract boundary.
 * @param selectedFieldIds [dev.gaferneira.notificapp.domain.model.RuleField.id]s checked in FIELDS
 * mode - referenced by id (not name) so a later field rename doesn't silently drop the selection.
 * @param template the TEMPLATE-mode JSON body, containing `{{token}}` placeholders.
 */
data class WebhookConfigUiModel(
    val webhookId: String? = null,
    val mode: WebhookPayloadMode = WebhookPayloadMode.FIELDS,
    val selectedBuiltins: Set<String> = emptySet(),
    val selectedFieldIds: Set<String> = emptySet(),
    val template: String = "",
)
