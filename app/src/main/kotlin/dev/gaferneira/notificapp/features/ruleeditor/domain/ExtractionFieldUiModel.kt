package dev.gaferneira.notificapp.features.ruleeditor.domain

/**
 * UI model for an extraction field in the list.
 */
data class ExtractionFieldUiModel(
    val id: String,
    val name: String,
    val methodType: String,
    val methodSummary: String,
)
