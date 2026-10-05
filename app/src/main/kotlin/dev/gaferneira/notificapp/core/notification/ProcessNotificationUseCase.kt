package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.core.notification.action.ActionDispatcher
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleMatch
import dev.gaferneira.notificapp.domain.model.saveDataFields
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Orchestrates the notification processing pipeline: deduplication, persistence,
 * rule evaluation, and recording of rule executions.
 *
 * This is the non-Android core of what [NotificappListenerService] used to do inline.
 * The service still owns Android-specific concerns (SBN normalization, pre-filters,
 * action execution); everything else lives here so it can run on a plain JVM.
 *
 * @property deduplicator Detects duplicate notifications
 * @property notificationRepository Repository for persisting notifications
 * @property ruleRepository Repository for loading rules
 * @property ruleEngine Pure rule evaluation engine
 * @property ruleExecutionRepository Repository for recording rule executions
 * @property actionDispatcher Dispatches enabled rule actions to their registered executors
 * @property timeProvider Seam for "now", threaded into [RuleEngine.evaluate] for day-of-week/time-range conditions
 * @property userPreferencesRepository Source of the global monitoring-paused kill switch, checked
 * at the top of [invoke] so a paused user's notifications are never captured or processed
 * @property ioDispatcher Coroutine dispatcher for IO operations
 */
@Suppress("LongParameterList")
class ProcessNotificationUseCase @Inject constructor(
    private val deduplicator: NotificationDeduplicator,
    private val notificationRepository: NotificationRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val ruleExecutionRepository: RuleExecutionRepository,
    private val actionDispatcher: ActionDispatcher,
    private val timeProvider: CurrentTimeProvider,
    private val userPreferencesRepository: UserPreferencesRepository,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Full pipeline: dedup, save, evaluate, persist.
     *
     * Used by [NotificappListenerService] for freshly captured notifications.
     *
     * @param notification The normalized notification to process
     * @return Result containing the list of rule executions (empty if duplicate or no rule matched)
     */
    suspend operator fun invoke(notification: Notification): Result<List<RuleExecution>> = withContext(ioDispatcher) {
        try {
            if (userPreferencesRepository.observeMonitoringPaused().first()) {
                Timber.d("Monitoring paused; skipping notification from ${notification.packageName}")
                return@withContext Result.success(emptyList())
            }

            if (deduplicator.isDuplicate(notification)) {
                Timber.d("Duplicate notification skipped from ${notification.packageName}")
                return@withContext Result.success(emptyList())
            }

            val saveResult = notificationRepository.saveNotification(notification)
            if (saveResult.isFailure) {
                return@withContext Result.failure(saveResult.exceptionOrNull()!!)
            }

            Timber.d("Saved notification from ${notification.packageName}: ${notification.id}")

            evaluateAndPersist(notification)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Error processing notification ${notification.id}")
            Result.failure(e)
        }
    }

    /**
     * Evaluate rules against an already-stored notification, dispatch their actions and persist
     * the matches. Does not re-check dedup or re-save the notification.
     *
     * @param notification The notification to evaluate
     * @return Result containing the list of rule executions
     */
    suspend fun evaluateAndPersist(notification: Notification): Result<List<RuleExecution>> = withContext(ioDispatcher) {
        try {
            val rulesResult = ruleRepository.getRulesForApp(notification.packageName)
            if (rulesResult.isFailure) {
                return@withContext Result.failure(rulesResult.exceptionOrNull()!!)
            }

            // getRulesForApp returns rules regardless of their enabled state; disabled rules must never run.
            val rules = rulesResult.getOrNull().orEmpty().filter { it.isActive }
            if (rules.isEmpty()) {
                Timber.d("No active rules for ${notification.packageName}")
                return@withContext Result.success(emptyList())
            }

            val matches = ruleEngine.evaluate(notification, rules, timeProvider.now())
            val executions = matches.mapNotNull { match ->
                // Dry-run rules log the match but never reach ActionDispatcher - that's the whole
                // point of dry-run mode (trial a rule with zero risk of it acting on anything).
                // Actions execute before the execution record is built/saved (per ADR 010) so the
                // record reflects what actually happened, not just what was "triggered".
                val outcomes = if (match.rule.isDryRun) {
                    emptyMap()
                } else {
                    actionDispatcher.executeAll(notification, match.rule.actions, match.extractedFieldsByName())
                }
                val execution = match.toExecution(notification.id, outcomes)
                // Extraction persistence is gated by the Extract-data (SAVE_DATA) action: without an
                // enabled one, field values are not saved, even though the execution record (and any
                // other action outcomes) still are. saveDataFields() is naturally empty in that case.
                val fieldsToPersist = match.rule.saveDataFields()
                ruleExecutionRepository.saveExecution(execution, fieldsToPersist)
                    .fold(
                        onSuccess = { execution },
                        onFailure = { e ->
                            Timber.e(e, "Failed to save rule execution ${execution.id}")
                            null
                        },
                    )
            }

            if (matches.qualifiesForRedaction()) {
                redactNotificationContent(notification.id)
            }

            Timber.d("Processed ${executions.size} rule matches for notification ${notification.id}")
            Result.success(executions)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Error processing rules for notification ${notification.id}")
            Result.failure(e)
        }
    }

    /**
     * Whether any of these matches earns a raw-content scrub: the rule must not be dry-run, must
     * have `deleteRawContentAfterExtraction` enabled, and must have actually extracted data - a
     * rule with the flag on but nothing extracted never qualifies, since there'd be nothing to
     * replace the raw text with.
     */
    private fun List<RuleMatch>.qualifiesForRedaction(): Boolean = any { match ->
        !match.rule.isDryRun && match.rule.deleteRawContentAfterExtraction && match.extractedData.isNotEmpty()
    }

    /**
     * Scrubs the notification's raw text via [NotificationRepository.redactContent]. Best-effort:
     * the rule executions and extracted fields are already saved, so a failure here is logged but
     * must not fail the whole pipeline.
     */
    private suspend fun redactNotificationContent(notificationId: String) {
        notificationRepository.redactContent(notificationId)
            .onSuccess { Timber.d("Redacted raw content for notification $notificationId") }
            .onFailure { e -> Timber.e(e, "Failed to redact content for notification $notificationId") }
    }

    /**
     * Resolves this match's id-keyed [RuleMatch.extractedData] to a **name**-keyed map, per
     * [ActionDispatcher.executeAll]'s `extractedFields` contract - `RuleEngine` keys extraction
     * results by [dev.gaferneira.notificapp.domain.model.RuleField.id], but an executor building a
     * payload (`SendWebhookActionExecutor`) needs the user-facing field **name**. A field id with
     * no matching definition in `rule.saveDataFields()` (deleted since extraction ran) is dropped.
     */
    private fun RuleMatch.extractedFieldsByName(): Map<String, String> {
        val fieldsById = rule.saveDataFields().associateBy { it.id }
        return extractedData.mapNotNull { (fieldId, value) ->
            fieldsById[fieldId]?.name?.let { name -> name to value }
        }.toMap()
    }

    /**
     * Build a [RuleExecution] from a matched rule, mirroring the rule's enabled actions and the
     * outcomes already recorded for them by [ActionDispatcher.executeAll].
     */
    private fun RuleMatch.toExecution(notificationId: String, actionOutcomes: Map<String, ActionOutcome>): RuleExecution {
        val enabledActions = rule.actions.filter { it.isEnabled }.toImmutableList()
        return RuleExecution(
            id = UUID.randomUUID().toString(),
            notificationId = notificationId,
            ruleId = rule.id,
            extractedData = extractedData,
            triggeredActions = enabledActions.map { it.id },
            triggeredRuleActions = enabledActions,
            actionOutcomes = actionOutcomes,
            wasDryRun = rule.isDryRun,
        )
    }
}
