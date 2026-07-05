package dev.gaferneira.notificapp.domain.model

/**
 * Result of evaluating a [Rule] against a [Notification], before persistence.
 *
 * Produced by the pure `RuleEngine.evaluate()`; converted into a [RuleExecution]
 * by the pipeline that owns persistence (see `ProcessNotificationUseCase`).
 *
 * @property rule The rule that matched
 * @property extractedData Field values extracted from the notification, keyed by field id
 */
data class RuleMatch(
    val rule: Rule,
    val extractedData: Map<String, String>,
)
