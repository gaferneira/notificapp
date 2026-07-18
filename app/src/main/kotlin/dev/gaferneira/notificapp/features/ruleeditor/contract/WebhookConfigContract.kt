package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.HttpMethod
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.WEBHOOK_FIELD_ID_PREFIX
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookPayloadConfig
import dev.gaferneira.notificapp.domain.model.WebhookPayloadMode
import dev.gaferneira.notificapp.domain.model.getWebhookId
import dev.gaferneira.notificapp.domain.model.getWebhookPayloadMode
import dev.gaferneira.notificapp.domain.model.getWebhookSelectedFields
import dev.gaferneira.notificapp.domain.model.getWebhookTemplate
import dev.gaferneira.notificapp.features.ruleeditor.domain.WebhookConfigUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

/**
 * MVI Contract for [dev.gaferneira.notificapp.features.ruleeditor.ui.WebhookConfigBottomSheet].
 *
 * The `WebhookConfigUiModel` <-> `RuleAction` mapping lives here (private-to-file extensions on
 * [UiState]/[RuleAction]), mirroring [AlarmContract]'s `toRuleAction()`/`initialize` pattern - the
 * one place the flat `config: Map<String, String>` shape ([WebhookPayloadMode], `field.<fieldId>`
 * prefixing) is translated to/from the sheet's feature-owned [WebhookConfigUiModel].
 */
object WebhookConfigContract {

    /** State for [dev.gaferneira.notificapp.features.ruleeditor.ui.WebhookConfigBottomSheet]. */
    data class UiState(
        val actionId: String = UUID.randomUUID().toString(),
        val initialIsEnabled: Boolean = true,
        val isEditing: Boolean = false,
        val config: WebhookConfigUiModel = WebhookConfigUiModel(),
        /** Webhooks observed continuously so the picker updates live after an inline create. */
        val webhooks: ImmutableList<Webhook> = persistentListOf(),
        /** The rule's currently-defined extraction fields, for the checklist/insert-field chips. */
        val ruleFields: ImmutableList<RuleField> = persistentListOf(),
        val previewJson: String? = null,
        val previewWarning: String? = null,
    ) {
        /** The currently-selected webhook's HTTP method, or POST if none selected. */
        val selectedWebhookMethod: HttpMethod
            get() = webhooks.find { it.id == config.webhookId }?.method ?: HttpMethod.POST

        /**
         * Confirm requires a target webhook. If the webhook uses GET (no body), payload is not
         * required. Otherwise, requires non-empty payload: at least one selected builtin/field in
         * FIELDS mode, or a non-blank template in TEMPLATE mode.
         */
        val canConfirm: Boolean
            get() = config.webhookId != null &&
                (
                    selectedWebhookMethod == HttpMethod.GET ||
                        when (config.mode) {
                            WebhookPayloadMode.FIELDS -> config.selectedBuiltins.isNotEmpty() || config.selectedFieldIds.isNotEmpty()
                            WebhookPayloadMode.TEMPLATE -> config.template.isNotBlank()
                        }
                    )

        fun toRuleAction(): RuleAction = config.toRuleAction(id = actionId, isEnabled = initialIsEnabled)
    }

    sealed class UiEvent {
        /** Seed state from an existing [RuleAction] when editing, or defaults for a new action. */
        data class Initialize(val initial: RuleAction?, val ruleFields: ImmutableList<RuleField>) : UiEvent()

        data class OnWebhookSelected(val webhookId: String) : UiEvent()

        /** "New webhook" tapped in the picker - navigates to `WebhookEditorScreen`. */
        data object OnCreateWebhookClicked : UiEvent()
        data class OnModeChanged(val mode: WebhookPayloadMode) : UiEvent()
        data class OnBuiltinToggled(val token: String, val checked: Boolean) : UiEvent()
        data class OnFieldToggled(val fieldId: String, val checked: Boolean) : UiEvent()
        data class OnTemplateChanged(val template: String) : UiEvent()
        data object OnPreviewClicked : UiEvent()
        data object OnDismissPreview : UiEvent()

        /** The sheet's confirm ("Add"/"Update") action was tapped. */
        data object OnConfirmClicked : UiEvent()
    }

    sealed class UiEffect {
        data class ConfirmSave(val action: RuleAction) : UiEffect()
    }
}

/** Builds the `SEND_WEBHOOK` [RuleAction] this UI model currently represents. */
private fun WebhookConfigUiModel.toRuleAction(id: String, isEnabled: Boolean): RuleAction {
    val selectedKeys = selectedBuiltins + selectedFieldIds.map { "$WEBHOOK_FIELD_ID_PREFIX$it" }
    return RuleAction.createSendWebhook(
        id = id,
        webhookId = webhookId.orEmpty(),
        payload = WebhookPayloadConfig(
            mode = mode,
            fields = selectedKeys,
            template = template,
        ),
        isEnabled = isEnabled,
    )
}

/** Reconstructs the sheet's [WebhookConfigUiModel] from a persisted `SEND_WEBHOOK` [RuleAction]. */
internal fun RuleAction.toWebhookConfigUiModel(): WebhookConfigUiModel {
    val selected = getWebhookSelectedFields()
    val (fieldKeys, builtinKeys) = selected.partition { it.startsWith(WEBHOOK_FIELD_ID_PREFIX) }
    return WebhookConfigUiModel(
        webhookId = getWebhookId(),
        mode = getWebhookPayloadMode(),
        selectedBuiltins = builtinKeys.toSet(),
        selectedFieldIds = fieldKeys.map { it.removePrefix(WEBHOOK_FIELD_ID_PREFIX) }.toSet(),
        template = getWebhookTemplate(),
    )
}
