package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.SuggestionDismissalKey
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and querying dismissed recurring-notification suggestions.
 */
interface SuggestionDismissalRepository {

    /**
     * Observe every dismissed suggestion key as a Flow, for the Home ViewModel's
     * suggestion-filtering combine.
     */
    fun observeDismissals(): Flow<Set<SuggestionDismissalKey>>

    /**
     * Record a dismissal for [packageName] + [normalizedTitleKey]. Idempotent — dismissing an
     * already-dismissed key just refreshes its timestamp.
     */
    suspend fun dismiss(packageName: String, normalizedTitleKey: String): Result<Unit>
}
