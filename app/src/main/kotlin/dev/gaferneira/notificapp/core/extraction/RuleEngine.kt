package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.entity.RuleExecutionEntity
import dev.gaferneira.notificapp.core.data.local.mapper.ExtractedFieldValueMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Rule execution engine that orchestrates rule matching, field extraction,
 * and recording of execution results.
 *
 * @property ruleRepository Repository for loading rules
 * @property ruleExecutionDao DAO for recording rule executions
 * @property extractedFieldValueDao DAO for recording extracted field values
 * @property notificationDao DAO for updating notification stats
 * @property ioDispatcher Coroutine dispatcher for IO operations
 */
class RuleEngine @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val ruleExecutionDao: RuleExecutionDao,
    private val extractedFieldValueDao: ExtractedFieldValueDao,
    private val notificationDao: NotificationDao,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Process a notification against all active rules for its app.
     *
     * @param notification The notification to process
     * @return Result containing list of rule executions (one per matched rule)
     */
    suspend fun process(notification: Notification): Result<List<RuleExecution>> = withContext(ioDispatcher) {
        try {
            // Load active rules for this app's package
            val rulesResult = ruleRepository.getRulesForApp(notification.packageName)

            if (rulesResult.isFailure) {
                return@withContext Result.failure(rulesResult.exceptionOrNull()!!)
            }

            val rules = rulesResult.getOrNull() ?: emptyList()

            if (rules.isEmpty()) {
                Timber.d("No active rules for ${notification.packageName}")
                return@withContext Result.success(emptyList())
            }

            // Process each rule and collect executions
            val executions = mutableListOf<RuleExecution>()

            for (rule in rules) {
                val execution = executeRule(notification, rule)
                if (execution != null) {
                    executions.add(execution)
                }
            }

            Timber.d("Processed ${executions.size} rule matches for notification ${notification.id}")
            Result.success(executions)
        } catch (e: Exception) {
            Timber.e(e, "Error processing rules for notification ${notification.id}")
            Result.failure(e)
        }
    }

    /**
     * Execute a single rule against a notification.
     *
     * @param notification The notification to check
     * @param rule The rule to execute
     * @return RuleExecution if rule matched, null otherwise
     */
    private suspend fun executeRule(
        notification: Notification,
        rule: Rule,
    ): RuleExecution? {
        // Check if rule conditions match
        if (!RuleMatcher.matches(notification, rule.conditions)) {
            Timber.d("Rule ${rule.id} did not match notification ${notification.id}")
            return null
        }

        Timber.d("Rule ${rule.id} matched notification ${notification.id}")

        // Extract all fields
        val extractedData = extractFields(notification, rule)

        if (extractedData.isEmpty() && rule.fields.isNotEmpty()) {
            Timber.w("Rule ${rule.id} matched but no fields could be extracted")
        }

        // Determine which actions were triggered
        val enabledActions = rule.actions.filter { it.isEnabled }
        val triggeredActions = enabledActions.map { it.id }

        // Create RuleExecution
        val execution = RuleExecution(
            id = UUID.randomUUID().toString(),
            notificationId = notification.id,
            ruleId = rule.id,
            extractedData = extractedData,
            triggeredActions = triggeredActions,
            triggeredRuleActions = enabledActions,
        )

        // Save to database
        return saveExecution(execution, rule)
    }

    /**
     * Extract all fields from the notification using the rule's field definitions.
     */
    private fun extractFields(
        notification: Notification,
        rule: Rule,
    ): Map<String, String> {
        val extractedData = mutableMapOf<String, String>()
        val sourceText = notification.rawContent

        for (field in rule.fields) {
            val result = FieldExtractor.extract(sourceText, field)

            if (result is ExtractionResult.Success) {
                extractedData[field.id] = result.value
                Timber.d("Extracted field ${field.name}: ${result.value}")
            } else if (field.isRequired) {
                // If a required field fails, we might want to mark this execution as partial
                Timber.w("Failed to extract required field ${field.name} for rule ${rule.id}")
            }
        }

        return extractedData
    }

    /**
     * Save the rule execution and its extracted field values to the database.
     */
    private suspend fun saveExecution(
        execution: RuleExecution,
        rule: Rule,
    ): RuleExecution? = try {
        // Save the execution
        val executionEntity = RuleExecutionEntity(
            id = execution.id,
            notificationId = execution.notificationId,
            ruleId = execution.ruleId,
            extractedData = kotlinx.serialization.json.Json.encodeToString(
                execution.extractedData,
            ),
            triggeredActions = kotlinx.serialization.json.Json.encodeToString(
                execution.triggeredActions,
            ),
            createdAt = execution.createdAt,
        )
        ruleExecutionDao.insert(executionEntity)

        // Create and save extracted field values for filtering
        val fieldValues = ExtractedFieldValueMapper.fromExtractedData(
            executionId = execution.id,
            extractedData = execution.extractedData,
            fields = rule.fields,
        )

        if (fieldValues.isNotEmpty()) {
            extractedFieldValueDao.insertAll(fieldValues)
        }

        // Increment the applied rules count on the notification
        notificationDao.incrementAppliedRulesCount(execution.notificationId)

        Timber.d("Saved rule execution ${execution.id} with ${fieldValues.size} field values")
        execution
    } catch (e: Exception) {
        Timber.e(e, "Failed to save rule execution ${execution.id}")
        null
    }
}
