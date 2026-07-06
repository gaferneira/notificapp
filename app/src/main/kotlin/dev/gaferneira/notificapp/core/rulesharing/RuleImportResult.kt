package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.domain.model.Rule

/**
 * Result of decoding an imported rule: the mapped [Rule], plus the wire names of any actions this
 * app version didn't recognize and therefore dropped (the rest of the rule still imports).
 */
data class RuleImportResult(
    val rule: Rule,
    val skippedActions: List<String>,
)
