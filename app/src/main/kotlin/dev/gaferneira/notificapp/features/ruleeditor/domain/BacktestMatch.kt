package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.Notification

/**
 * A single match produced by testing a draft (not-yet-saved) rule against previously captured
 * notification history ("Test against history" in the Rule Editor).
 *
 * @param notification The historical notification that matched
 * @param extractedData Extracted field values, keyed by [dev.gaferneira.notificapp.domain.model.RuleField.id]
 */
data class BacktestMatch(
    val notification: Notification,
    val extractedData: Map<String, String>,
)
