package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.WEBHOOK_TOKEN_REGEX
import dev.gaferneira.notificapp.domain.model.getReadAloudTemplate
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Executes [dev.gaferneira.notificapp.domain.model.ActionType.READ_ALOUD] by resolving the
 * configured template's `{{token}}` placeholders (same convention as `SEND_WEBHOOK`'s TEMPLATE
 * mode - see `TemplateSubstitution.kt`) against the matched notification and [extractedFields],
 * then speaking the result via [TtsController].
 *
 * Unlike most other executors, a [TtsController] failure is caught here rather than left to
 * `ActionDispatcher`'s catch-all: [ActionOutcome.FAILED] should reflect "the device failed to
 * speak this", a distinction worth preserving even when this executor is exercised in isolation.
 */
class ReadAloudActionExecutor @Inject constructor(
    private val ttsController: TtsController,
) : ActionExecutor {

    override suspend fun execute(notification: Notification, action: RuleAction, extractedFields: Map<String, String>): ActionOutcome {
        val builtins = builtinTokenValues(notification)
        val text = WEBHOOK_TOKEN_REGEX.replace(action.getReadAloudTemplate()) { match ->
            resolveTemplateToken(match.groupValues[1], builtins, extractedFields)
        }

        return if (text.isBlank()) {
            ActionOutcome.SKIPPED
        } else {
            runCatching { ttsController.speak(text) }
                .fold(
                    onSuccess = { ActionOutcome.SUCCESS },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        Timber.e(e, "Failed to speak read-aloud text for action ${action.id}")
                        ActionOutcome.FAILED
                    },
                )
        }
    }
}
