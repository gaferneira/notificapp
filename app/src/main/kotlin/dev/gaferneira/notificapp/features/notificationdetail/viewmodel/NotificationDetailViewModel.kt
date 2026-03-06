package dev.gaferneira.notificapp.features.notificationdetail.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.ExtractionRule
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ApplicableRule
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEffect
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Notification Detail screen.
 *
 * Loads notification data and determines which rules apply to it.
 *
 * @param notificationRepository Repository for notifications
 * @param ruleRepository Repository for rules
 * @param navigationHandler Handler for navigation commands
 * @param ioDispatcher Dispatcher for IO operations
 */
@HiltViewModel
class NotificationDetailViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val ruleRepository: RuleRepository,
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
        loadNotificationAndRules()
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnBackClicked -> {
                navigateBack()
            }
            is UiEvent.OnCreateRuleClicked -> {
                notificationId?.let { navigateToRuleEditor(it) }
            }
            is UiEvent.OnEditRuleClicked -> {
                navigateToEditRule(event.ruleId)
            }
            is UiEvent.OnRuleToggleClicked -> {
                toggleRule(event.ruleId)
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
     * Navigate to rule editor for editing an existing rule.
     */
    private fun navigateToEditRule(ruleId: String) {
        viewModelScope.launch {
            navigationHandler.navigate(Routes.ruleEditor(ruleId = ruleId))
        }
    }

    /**
     * Load notification and applicable rules.
     */
    private fun loadNotificationAndRules() {
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

                // Observe rules that apply to this notification
                observeApplicableRules(notification.packageName, notification)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load notification detail")
                setState { copy(isLoading = false, error = "Failed to load: ${e.message}") }
            }
        }
    }

    /**
     * Observe rules and determine which apply to this notification.
     */
    private fun observeApplicableRules(
        packageName: String,
        notification: dev.gaferneira.notificapp.domain.model.Notification,
    ) {
        viewModelScope.launch(ioDispatcher) {
            ruleRepository.observeAllRules()
                .collect { rules ->
                    val applicableRules = rules.map { rule ->
                        ApplicableRule(
                            rule = rule,
                            isApplicable = isRuleApplicable(rule, packageName, notification),
                            isActive = rule.isActive,
                        )
                    }.sortedByDescending { it.isApplicable }

                    setState {
                        copy(
                            notification = notification,
                            applicableRules = applicableRules,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    /**
     * Check if a rule applies to this notification.
     */
    private fun isRuleApplicable(
        rule: ExtractionRule,
        packageName: String,
        notification: dev.gaferneira.notificapp.domain.model.Notification,
    ): Boolean {
        // Check if rule targets this app
        val appMatches = when {
            rule.targetApps == null -> true // Rule applies to all apps
            rule.targetApps.isEmpty() -> true
            rule.targetApps.contains(packageName) -> true
            else -> false
        }

        if (!appMatches) return false

        // Check if notification content matches the rule pattern
        val contentToCheck = listOfNotNull(
            notification.title,
            notification.content,
            notification.rawContent,
        ).joinToString(" ")

        return try {
            val regex = Regex(rule.pattern, RegexOption.IGNORE_CASE)
            regex.containsMatchIn(contentToCheck)
        } catch (e: Exception) {
            // Invalid regex, treat as non-matching
            false
        }
    }

    /**
     * Toggle a rule's active state.
     */
    private fun toggleRule(ruleId: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                ruleRepository.toggleRuleActive(ruleId)
                    .onSuccess {
                        Timber.d("Toggled rule: $ruleId")
                    }
                    .onFailure { e ->
                        Timber.e(e, "Failed to toggle rule: $ruleId")
                        sendEffect(UiEffect.ShowError("Failed to toggle rule"))
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling rule: $ruleId")
                sendEffect(UiEffect.ShowError("Failed to toggle rule: ${e.message}"))
            }
        }
    }
}
