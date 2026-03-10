package dev.gaferneira.notificapp.domain.model.preferences

import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract.Status
import kotlinx.serialization.Serializable

/**
 * Domain model representing inbox filter settings.
 *
 * @property selectedApps List of selected app package names for filtering
 * @property statusFilter The processed/unprocessed status filter
 */
@Serializable
data class InboxFilterSettings(
    val selectedApps: List<String> = emptyList(),
    val statusFilter: Status = Status.ALL,
)
