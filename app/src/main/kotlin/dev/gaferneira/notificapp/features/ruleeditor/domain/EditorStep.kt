package dev.gaferneira.notificapp.features.ruleeditor.domain

/** Steps of the guided create flow, in order. */
enum class EditorStep {
    WHEN,
    DO,
    REVIEW,
    ;

    fun next(): EditorStep? = entries.getOrNull(ordinal + 1)

    fun previous(): EditorStep? = entries.getOrNull(ordinal - 1)
}
