package dev.gaferneira.notificapp.features.notificationdetail.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExecutionWithDetails
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExtractedFieldDisplay
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEffect
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Notification Detail screen.
 *
 * Loads notification data and rule executions that matched this notification.
 *
 * @param notificationRepository Repository for notifications
 * @param ruleExecutionRepository Repository for rule executions
 * @param ruleRepository Repository for rules (to get rule names)
 * @param ruleEngine Engine for re-executing rules
 * @param navigationHandler Handler for navigation commands
 * @param ioDispatcher Dispatcher for IO operations
 */
@HiltViewModel
class NotificationDetailViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val ruleExecutionRepository: RuleExecutionRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val navigationHandler: NavigationHandler,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    private var notificationId: String? = null

    /**
     * Set the notification ID to load.
     */
    fun setNotificationId(id: String) {
        if (notificationId == id) return
        notificationId = id
        loadNotificationAndExecutions()
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnBackClicked -> {
                navigateBack()
            }
            is UiEvent.OnCreateRuleClicked -> {
                notificationId?.let { navigateToRuleEditor(it) }
            }
            is UiEvent.OnRefreshClicked -> {
                refreshExecutions()
            }
            is UiEvent.OnDismissError -> {
                setState { copy(error = null) }
            }
        }
    }

    /**
     * Navigate back to previous screen.
     */
    private fun navigateBack() {
        viewModelScope.launch {
            navigationHandler.goBack()
        }
    }

    /**
     * Navigate to rule editor for creating a new rule from this notification.
     */
    private fun navigateToRuleEditor(notificationId: String) {
        viewModelScope.launch {
            navigationHandler.navigate(Routes.ruleEditor(notificationId = notificationId))
        }
    }

    /**
     * Load notification and rule executions.
     */
    private fun loadNotificationAndExecutions() {
        val id = notificationId ?: return

        viewModelScope.launch(ioDispatcher) {
            setState { copy(isLoading = true, error = null) }

            try {
                // Get the notification
                val notificationResult = notificationRepository.getNotification(id)

                if (notificationResult.isFailure) {
                    setState { copy(isLoading = false, error = "Failed to load notification") }
                    return@launch
                }

                val notification = notificationResult.getOrNull()
                if (notification == null) {
                    setState { copy(isLoading = false, error = "Notification not found") }
                    return@launch
                }

                // Load executions for this notification
                observeExecutions(notification)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load notification detail")
                setState { copy(isLoading = false, error = "Failed to load: ${e.message}") }
            }
        }
    }

    /**
     * Load and observe executions for this notification.
     */
    private fun observeExecutions(notification: Notification) {
        viewModelScope.launch(ioDispatcher) {
            try {
                // Get executions from the repository
                val executions = ruleExecutionRepository.observeExecutionsForNotification(notification.id).first()

                // Build execution details
                val executionsWithDetails = executions.map { execution ->
                    buildExecutionDetails(execution)
                }

                setState {
                    copy(
                        notification = notification,
                        executions = executionsWithDetails,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load executions")
                setState {
                    copy(
                        notification = notification,
                        executions = emptyList(),
                        isLoading = false,
                    )
                }
            }
        }
    }

    /**
     * Build execution details with field values and action names.
     */
    private suspend fun buildExecutionDetails(execution: RuleExecution): ExecutionWithDetails {
        // Get rule name
        val ruleResult = ruleRepository.getRule(execution.ruleId)
        val ruleName = ruleResult.getOrNull()?.name ?: "Unknown Rule"

        // Build field displays (we'd need rule fields to get names, but we can use IDs for now)
        val extractedFields = execution.extractedData.map { (fieldId, value) ->
            ExtractedFieldDisplay(
                fieldName = "Field ${fieldId.takeLast(4)}", // Temporary naming
                fieldType = RuleField.FieldType.STRING, // Default type
                value = value,
            )
        }

        // Convert action IDs to display names
        val actionNames = execution.triggeredActions.map { actionId ->
            when (actionId) {
                "SAVE_DATA" -> "Save Data"
                "DELETE_NOTIFICATION" -> "Delete"
                "CREATE_ALARM" -> "Alarm"
                else -> actionId
            }
        }

        return ExecutionWithDetails(
            execution = execution,
            ruleName = ruleName,
            extractedFields = extractedFields,
            triggeredActionNames = actionNames,
        )
    }

    /**
     * Refresh/re-execute rules for this notification.
     * Clears existing executions and re-runs current rules.
     */
    private fun refreshExecutions() {
        val id = notificationId ?: return

        viewModelScope.launch(ioDispatcher) {
            setState { copy(isLoading = true, error = null) }

            try {
                // 1. Get the notification
                val notificationResult = notificationRepository.getNotification(id)
                if (notificationResult.isFailure) {
                    setState { copy(isLoading = false, error = "Failed to load notification") }
                    return@launch
                }
                val notification = notificationResult.getOrNull()
                if (notification == null) {
                    setState { copy(isLoading = false, error = "Notification not found") }
                    return@launch
                }

                // 2. Delete existing executions for this notification and reset its counter
                ruleExecutionRepository.deleteExecutionsForNotification(id)

                // 3. Re-run the rule engine
                ruleEngine.process(notification)

                // 4. Reload executions
                observeExecutions(notification)

                Timber.d("Refreshed rules for notification $id")
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh rules for notification $id")
                setState { copy(isLoading = false, error = "Failed to refresh: ${e.message}") }
            }
        }
    }
}
