package dev.gaferneira.notificapp.core.di

import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.domain.repository.SuggestionDismissalRepository
import javax.inject.Inject

/**
 * Bundles the repositories the Home dashboard reads from, keeping HomeViewModel's
 * constructor to a single Hilt-managed dependency.
 */
class HomeDataSources @Inject constructor(
    val ruleRepository: RuleRepository,
    val selectedAppRepository: SelectedAppRepository,
    val ruleExecutionRepository: RuleExecutionRepository,
    val notificationRepository: NotificationRepository,
    val suggestionDismissalRepository: SuggestionDismissalRepository,
)
