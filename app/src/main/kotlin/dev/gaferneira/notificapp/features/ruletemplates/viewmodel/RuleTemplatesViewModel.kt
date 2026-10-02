package dev.gaferneira.notificapp.features.ruletemplates.viewmodel

import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplates
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.features.ruletemplates.contract.RuleTemplatesEffect
import dev.gaferneira.notificapp.features.ruletemplates.contract.RuleTemplatesEvent
import dev.gaferneira.notificapp.features.ruletemplates.contract.RuleTemplatesUiState
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@HiltViewModel
class RuleTemplatesViewModel @Inject constructor() : MviViewModel<RuleTemplatesUiState, RuleTemplatesEvent, RuleTemplatesEffect>(initialState()) {

    override fun onEvent(event: RuleTemplatesEvent) {
        when (event) {
            is RuleTemplatesEvent.OnCategorySelected -> selectCategory(event.category)
            is RuleTemplatesEvent.OnTemplateClick ->
                sendEffect(RuleTemplatesEffect.OpenRuleEditor(event.template.assetFileName))
            RuleTemplatesEvent.OnStartFromScratch -> sendEffect(RuleTemplatesEffect.OpenRuleEditor(null))
        }
    }

    private fun selectCategory(category: String?) {
        setState {
            copy(
                selectedCategory = category,
                templates = templatesFor(category),
            )
        }
    }

    private companion object {
        fun initialState() = RuleTemplatesUiState(
            categories = RuleTemplates.all.map { it.category }.distinct().toImmutableList(),
            templates = templatesFor(null),
        )

        fun templatesFor(category: String?) = RuleTemplates.all
            .filter { category == null || it.category == category }
            .toImmutableList()
    }
}
