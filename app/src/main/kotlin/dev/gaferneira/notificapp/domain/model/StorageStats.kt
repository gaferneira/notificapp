package dev.gaferneira.notificapp.domain.model

/**
 * Snapshot of on-device storage usage: database file size plus row counts per table.
 * Used by the Settings screen's Storage section; not observed live, just a one-shot read.
 *
 * @property databaseSizeBytes Size of the Room/SQLCipher database file on disk, in bytes
 * @property notificationCount Total captured notifications
 * @property ruleCount Total rules
 * @property ruleExecutionCount Total rule executions
 * @property extractedFieldValueCount Total extracted field values
 * @property selectedAppCount Total monitored (selected) apps
 */
data class StorageStats(
    val databaseSizeBytes: Long,
    val notificationCount: Int,
    val ruleCount: Int,
    val ruleExecutionCount: Int,
    val extractedFieldValueCount: Int,
    val selectedAppCount: Int,
)
