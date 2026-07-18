package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.WEBHOOK_TOKEN_REGEX
import dev.gaferneira.notificapp.domain.model.getSendReplyTemplate
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.SEND_REPLY] (BETA) by resolving the
 * configured template's `{{token}}` placeholders (same convention as `READ_ALOUD` - see
 * `TemplateSubstitution.kt`) against the matched notification and [extractedFields], then
 * dispatching the result through the source notification's Android direct-reply (`RemoteInput`)
 * action via [SystemNotificationController.reply].
 *
 * This is best-effort by nature: [ActionOutcome.SKIPPED] is the *expected* outcome whenever the
 * source app doesn't expose a reply action, the listener is disconnected, or there's no live
 * notification to reply to (e.g. the re-evaluate path, which has no `sbnKey`) - it is never
 * surfaced as a failure. Only an actual exception from the controller yields
 * [ActionOutcome.FAILED].
 */
class NotificationReplyActionExecutor @Inject constructor(
    private val controllerHolder: SystemNotificationControllerHolder,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): ActionOutcome {
        val sbnKey = notification.sbnKey
        val text = resolveReplyText(notification, action, extractedFields)
        val controller = controllerHolder.get()

        return when {
            sbnKey == null -> {
                Timber.w("Cannot reply to notification ${notification.id}: no SBN key")
                ActionOutcome.SKIPPED
            }
            text.isBlank() -> ActionOutcome.SKIPPED
            controller == null -> {
                Timber.w("Cannot reply to notification ${notification.id}: listener not connected")
                ActionOutcome.SKIPPED
            }
            else -> dispatchReply(controller, notification, sbnKey, text)
        }
    }

    private fun resolveReplyText(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): String {
        val builtins = builtinTokenValues(notification)
        return WEBHOOK_TOKEN_REGEX.replace(action.getSendReplyTemplate()) { match ->
            resolveTemplateToken(match.groupValues[1], builtins, extractedFields)
        }
    }

    private fun dispatchReply(controller: SystemNotificationController, notification: Notification, sbnKey: String, text: String): ActionOutcome = runCatching {
        controller.reply(sbnKey, text)
    }.fold(
        onSuccess = { dispatched ->
            if (dispatched) {
                ActionOutcome.SUCCESS
            } else {
                Timber.d("No reply action available for notification ${notification.id}")
                ActionOutcome.SKIPPED
            }
        },
        onFailure = { e ->
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to reply to notification ${notification.id}")
            ActionOutcome.FAILED
        },
    )
}
