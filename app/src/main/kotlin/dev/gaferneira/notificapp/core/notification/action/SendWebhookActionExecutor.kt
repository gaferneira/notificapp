package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import dev.gaferneira.notificapp.domain.model.getWebhookId
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.SEND_WEBHOOK] by building the
 * configured payload and handing it off to [WebhookDeliveryEnqueuer] for reliable, async
 * delivery.
 *
 * A returned [ActionOutcome.SUCCESS] means "accepted for delivery", not "delivered" - the actual
 * send happens later on a WorkManager worker; the webhook's tri-state delivery indicator (Rule
 * Editor UI, Phase 5) reports the real outcome. See design.md's executor contract.
 */
class SendWebhookActionExecutor @Inject constructor(
    private val payloadBuilder: WebhookPayloadBuilder,
    private val deliveryEnqueuer: WebhookDeliveryEnqueuer,
) : ActionExecutor {

    override suspend fun execute(
        notification: Notification,
        action: RuleAction,
        extractedFields: Map<String, String>,
    ): ActionOutcome {
        val webhookId = action.getWebhookId() ?: return ActionOutcome.SKIPPED

        val payload = payloadBuilder.build(notification, action, extractedFields)
        deliveryEnqueuer.enqueue(WebhookDelivery(webhookId = webhookId, payload = payload))
        return ActionOutcome.SUCCESS
    }
}
