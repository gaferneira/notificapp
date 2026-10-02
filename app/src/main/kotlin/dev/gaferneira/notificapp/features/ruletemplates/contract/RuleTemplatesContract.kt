package dev.gaferneira.notificapp.features.ruletemplates.contract

import androidx.compose.runtime.Immutable
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplateInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class RuleTemplatesUiState(
    /** Distinct template categories, in catalog order. */
    val categories: ImmutableList<String> = persistentListOf(),
    /** The active filter, or null for "All". */
    val selectedCategory: String? = null,
    /** Templates matching [selectedCategory]. */
    val templates: ImmutableList<RuleTemplateInfo> = persistentListOf(),
)

sealed interface RuleTemplatesEvent {
    data class OnCategorySelected(val category: String?) : RuleTemplatesEvent
    data class OnTemplateClick(val template: RuleTemplateInfo) : RuleTemplatesEvent
    data object OnStartFromScratch : RuleTemplatesEvent
}

sealed interface RuleTemplatesEffect {
    /** Open the rule editor; [templateAssetFileName] is null for a blank rule. */
    data class OpenRuleEditor(val templateAssetFileName: String?) : RuleTemplatesEffect
}
