package dev.gaferneira.notificapp.domain.model

/**
 * Identifies one dismissed recurring-notification suggestion: a specific app package paired with
 * the normalized title key the user chose to stop seeing suggestions for.
 */
data class SuggestionDismissalKey(
    val packageName: String,
    val normalizedTitleKey: String,
)
