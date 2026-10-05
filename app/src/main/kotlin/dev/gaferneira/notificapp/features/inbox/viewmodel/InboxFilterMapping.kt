package dev.gaferneira.notificapp.features.inbox.viewmodel

import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilter

/** Maps the persisted settings to the feature-level [InboxFilter]. */
internal fun InboxFilterSettings.toInboxFilter(): InboxFilter = InboxFilter(
    selectedApps = selectedApps.toSet(),
    status = statusFilter,
)

/** Maps the feature-level [InboxFilter] to its persisted form. */
internal fun InboxFilter.toSettings(): InboxFilterSettings = InboxFilterSettings(
    selectedApps = selectedApps.sorted(),
    statusFilter = status,
)

/** The repository's tri-state processed flag: null = no status filter. */
internal fun NotificationStatusFilter.toIsProcessed(): Boolean? = when (this) {
    NotificationStatusFilter.PROCESSED -> true
    NotificationStatusFilter.UNPROCESSED -> false
    NotificationStatusFilter.ALL -> null
}
