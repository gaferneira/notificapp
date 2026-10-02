package dev.gaferneira.notificapp.features.ruletemplates.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleTemplates
import dev.gaferneira.notificapp.core.ui.components.RuleTemplateCard
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.navigation.navOptions
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.ruletemplates.contract.RuleTemplatesEffect
import dev.gaferneira.notificapp.features.ruletemplates.contract.RuleTemplatesEvent
import dev.gaferneira.notificapp.features.ruletemplates.contract.RuleTemplatesUiState
import dev.gaferneira.notificapp.features.ruletemplates.viewmodel.RuleTemplatesViewModel
import kotlinx.collections.immutable.toImmutableList

@Composable
fun RuleTemplatesScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    navigateBack: () -> Unit,
    viewModel: RuleTemplatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            // The gallery is a decision step, not a place to return to: pop it so that saving
            // (or backing out of) the editor lands on the screen the user came from.
            is RuleTemplatesEffect.OpenRuleEditor -> navigateTo(
                Routes.ruleEditor(templateAssetFileName = effect.templateAssetFileName),
                navOptions { popUpTo(Screen.RuleTemplates::class, inclusive = true) },
            )
        }
    }

    RuleTemplatesContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        navigateBack = navigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RuleTemplatesContent(
    uiState: RuleTemplatesUiState,
    onEvent: (RuleTemplatesEvent) -> Unit,
    navigateBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = { StartFromScratchBar(onClick = { onEvent(RuleTemplatesEvent.OnStartFromScratch) }) },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                text = stringResource(R.string.templates_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            CategoryFilterRow(
                categories = uiState.categories,
                selected = uiState.selectedCategory,
                onSelected = { onEvent(RuleTemplatesEvent.OnCategorySelected(it)) },
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = uiState.templates, key = { it.assetFileName }) { template ->
                    RuleTemplateCard(
                        category = template.category,
                        name = template.name,
                        description = template.description,
                        onClick = { onEvent(RuleTemplatesEvent.OnTemplateClick(template)) },
                    )
                }
            }
        }
    }
}

/** Single-select category filter; "All" (null) first. Kept outside the list so it never scrolls away. */
@Composable
private fun CategoryFilterRow(
    categories: List<String>,
    selected: String?,
    onSelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelected(null) },
                label = { Text(stringResource(R.string.templates_filter_all)) },
            )
        }
        items(items = categories, key = { it }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelected(category) },
                label = { Text(category) },
            )
        }
    }
}

/** Always-visible escape hatch for people who know what they want, visually secondary to the templates. */
@Composable
private fun StartFromScratchBar(onClick: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
        ) {
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(R.string.home_create_rule_from_scratch),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RuleTemplatesContentPreview() {
    NotificappTheme(dynamicColor = false) {
        RuleTemplatesContent(
            uiState = RuleTemplatesUiState(
                categories = RuleTemplates.all.map { it.category }.distinct().toImmutableList(),
                templates = RuleTemplates.all.toImmutableList(),
            ),
            onEvent = {},
            navigateBack = {},
        )
    }
}
