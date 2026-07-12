package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.core.notification.action.ActionDispatcher
import dev.gaferneira.notificapp.domain.action.RuleReEvaluator
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleMatch
import dev.gaferneira.notificapp.domain.model.saveDataFields
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
 * @property ioDispatcher Coroutine dispatcher for IO operations
 */
class ProcessNotificationUseCase @Inject constructor(
    private val deduplicator: NotificationDeduplicator,
    private val notificationRepository: NotificationRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val ruleExecutionRepository: RuleExecutionRepository,
    private val actionDispatcher: ActionDispatcher,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleReEvaluator {

    override suspend fun reEvaluate(notification: Notification): Result<List<RuleExecution>> = evaluateAndPersist(notification, executeActions = false)

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
     * Evaluate rules against an already-stored notification and persist the matches.
     *
     * Used to re-run rules without re-checking dedup or re-saving the notification
     * (e.g. `NotificationDetailViewModel`'s refresh action).
     *
     * @param notification The notification to evaluate
     * @param executeActions Whether matched, non-dry-run rules should actually dispatch their
     * actions. `NotificationDetailViewModel`'s refresh passes `false`: it recomputes what a rule
     * *would* do without replaying alarms/snoozes/dismisses for a notification that's already
     * been acted on once by the listener service.
     * @return Result containing the list of rule executions
     */
    suspend fun evaluateAndPersist(notification: Notification, executeActions: Boolean = true): Result<List<RuleExecution>> = withContext(ioDispatcher) {
        try {
            val rulesResult = ruleRepository.getRulesForApp(notification.packageName)
            if (rulesResult.isFailure) {
                return@withContext Result.failure(rulesResult.exceptionOrNull()!!)
            }

            val rules = rulesResult.getOrNull() ?: emptyList()
            if (rules.isEmpty()) {
                Timber.d("No active rules for ${notification.packageName}")
                return@withContext Result.success(emptyList())
            }

            val matches = ruleEngine.evaluate(notification, rules)
            val executions = matches.mapNotNull { match ->
                // Dry-run rules log the match but never reach ActionDispatcher - that's the whole
                // point of dry-run mode (trial a rule with zero risk of it acting on anything).
                // Actions execute before the execution record is built/saved (per ADR 010) so the
                // record reflects what actually happened, not just what was "triggered".
                val outcomes = if (match.rule.isDryRun || !executeActions) {
                    emptyMap()
                } else {
                    actionDispatcher.executeAll(notification, match.rule.actions)
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

            Timber.d("Processed ${executions.size} rule matches for notification ${notification.id}")
            Result.success(executions)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Error processing rules for notification ${notification.id}")
            Result.failure(e)
        }
    }

    /**
     * Build a [RuleExecution] from a matched rule, mirroring the rule's enabled actions and the
     * outcomes already recorded for them by [ActionDispatcher.executeAll].
     */
    private fun RuleMatch.toExecution(notificationId: String, actionOutcomes: Map<String, ActionOutcome>): RuleExecution {
        val enabledActions = rule.actions.filter { it.isEnabled }
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
