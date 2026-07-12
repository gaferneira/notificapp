package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.runtime.Immutable

/**
 * A resolved extracted-field (name, value) pair, stable for Compose (unlike `Pair<String, String>`,
 * which the compiler can't prove immutable).
 */
@Immutable
data class ExtractedField(val name: String, val value: String)
