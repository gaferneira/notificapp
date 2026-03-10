package dev.gaferneira.notificapp.domain.model.preferences

import kotlinx.serialization.Serializable

/**
 * Domain model representing rules filter settings.
 *
 * @property selectedCategories List of selected rule categories for filtering
 * @property showOnlyActive Whether to show only active rules
 * @property searchQuery Optional search query for rule names
 */
@Serializable
data class RulesFilterSettings(
    val selectedCategories: List<String> = emptyList(),
    val showOnlyActive: Boolean = false,
    val searchQuery: String = "",
)
