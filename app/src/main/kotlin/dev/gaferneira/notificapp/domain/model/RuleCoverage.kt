package dev.gaferneira.notificapp.domain.model

/**
 * How thoroughly an existing rule already covers a recurring-notification candidate.
 *
 * The ordinal of each entry is used directly as a sort priority by
 * [dev.gaferneira.notificapp.core.notification.RecurringNotificationSuggester] (lower ordinal ranks
 * first) - entries MUST NOT be reordered; append new entries at the end only.
 */
enum class RuleCoverage {
    /** No active rule targets this candidate's app package at all. */
    UNCOVERED,

    /** A rule targets this candidate's app package, but its conditions do not match this pattern. */
    APP_ONLY,

    /** A rule targets this app and its conditions already match this exact pattern. */
    CONDITIONS_MATCH,
}
