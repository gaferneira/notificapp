package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AddButton
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AppSelectionPicker
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.DataExtractionSection
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.DoSection
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.WhenSection
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.RuleEditorViewModel

@Composable
fun RuleEditorScreen(
    modifier: Modifier = Modifier,
    ruleId: String? = null,
    notificationId: String? = null,
    viewModel: RuleEditorViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    // Load initial data
    LaunchedEffect(ruleId, notificationId) {
        viewModel.onEvent(UiEvent.LoadRule(ruleId))
        notificationId?.let { viewModel.onEvent(UiEvent.LoadSampleNotification(it)) }
    }

    // Collect effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.ShowSuccess -> {
            }
            is UiEffect.ShowError -> {
            }
        }
    }

    RuleEditorScreenContent(
        uiState = uiState.value,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (uiState.currentStep) {
                            1 -> if (uiState.rule.id == null) "New rule" else "Edit rule"
                            else -> "Save"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.currentStep == 2) {
                            onEvent(UiEvent.OnBackToLogicClicked)
                        } else {
                            onEvent(UiEvent.OnBackClicked)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    // Show delete icon only when editing an existing rule
                    if (uiState.rule.id != null) {
                        IconButton(onClick = { onEvent(UiEvent.OnDeleteClicked) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete rule",
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Main content with step-based animation
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { width -> width } togetherWith
                            slideOutHorizontally { width -> -width }
                    } else {
                        slideInHorizontally { width -> -width } togetherWith
                            slideOutHorizontally { width -> width }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { step ->
                when (step) {
                    1 -> LogicStep(
                        uiState = uiState,
                        onEvent = onEvent,
                        scrollState = scrollState,
                    )
                    2 -> MetadataStep(
                        uiState = uiState,
                        onEvent = onEvent,
                        scrollState = scrollState,
                    )
                }
            }

            RuleEditorBottomSheets(uiState = uiState, onEvent = onEvent)

            // Delete confirmation dialog
            if (uiState.showDeleteConfirmation) {
                AlertDialog(
                    onDismissRequest = { onEvent(UiEvent.OnDeleteDismissed) },
                    title = { Text("Delete Rule") },
                    text = { Text("Are you sure you want to delete this rule? This action cannot be undone.") },
                    confirmButton = {
                        TextButton(
                            onClick = { onEvent(UiEvent.OnDeleteConfirmed) },
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { onEvent(UiEvent.OnDeleteDismissed) },
                        ) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RuleEditorBottomSheets(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
) {
    if (uiState.isMatchingLogicSheetVisible) {
        MatchingLogicBottomSheet(
            initialCondition = uiState.editingCondition,
            onConditionSaved = { condition ->
                onEvent(UiEvent.OnConditionSaved(condition))
            },
            onDismiss = { onEvent(UiEvent.OnDismissSheet) },
        )
    }

    if (uiState.isAppSheetVisible) {
        AppSelectionPicker(
            selectedApps = uiState.rule.targetApps,
            enabledApps = uiState.enabledApps,
            onConfirm = { apps ->
                onEvent(UiEvent.OnAppsSelected(apps))
            },
            onDismiss = { onEvent(UiEvent.OnDismissSheet) },
        )
    }

    if (uiState.isActionSheetVisible) {
        ActionBottomSheet(
            initialAction = uiState.editingAction,
            onActionSaved = { action ->
                onEvent(UiEvent.OnActionSaved(action))
            },
            onDismiss = { onEvent(UiEvent.OnDismissSheet) },
        )
    }

    if (uiState.isFieldSheetVisible) {
        AddFieldBottomSheet(
            fieldToEdit = uiState.editingField,
            notification = uiState.sampleNotification,
            onFieldSaved = { field ->
                onEvent(UiEvent.OnFieldSaved(field))
            },
            onDismiss = { onEvent(UiEvent.OnDismissSheet) },
        )
    }

    uiState.backtestResults?.let { results ->
        BacktestResultsBottomSheet(
            results = results,
            testedCount = uiState.backtestTestedCount,
            fields = uiState.rule.fields,
            onDismiss = { onEvent(UiEvent.OnDismissBacktestResults) },
        )
    }
}

@Composable
private fun LogicStep(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // When Section
        SectionWithHelp(
            title = "When",
            description = "Define when this rule should trigger. Select apps and add conditions.",
        ) {
            WhenSection(
                targetApps = uiState.rule.targetApps,
                conditions = uiState.rule.triggers,
                onAppsClick = { onEvent(UiEvent.OnAppsClicked) },
                onRemoveCondition = { onEvent(UiEvent.OnRemoveConditionClicked(it)) },
                onConditionClick = { onEvent(UiEvent.OnConditionItemClicked(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Add condition button
            AddButton(
                text = "Add condition",
                onClick = { onEvent(UiEvent.OnAddConditionClicked) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Data Extraction Section
        DataExtractionSection(
            fields = uiState.rule.fields,
            onAutoGenerate = { onEvent(UiEvent.OnAutoGenerateClicked) },
            onAddField = { onEvent(UiEvent.OnAddFieldClicked) },
            onEditField = { onEvent(UiEvent.OnEditFieldClicked(it)) },
            onRemoveField = { onEvent(UiEvent.OnRemoveFieldClicked(it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Do Section
        DoSection(
            actions = uiState.rule.actions,
            onToggleAction = { _, _ ->
                // For now, we toggle by removing and re-adding (simplified)
                // In a full implementation, we'd have a specific toggle event
            },
            onRemoveAction = { onEvent(UiEvent.OnRemoveActionClicked(it)) },
            onEditAction = { onEvent(UiEvent.OnEditActionClicked(it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Add action button
        AddButton(
            text = "Add action",
            onClick = { onEvent(UiEvent.OnAddActionClicked) },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Test against history
        OutlinedButton(
            onClick = { onEvent(UiEvent.OnTestAgainstHistoryClicked) },
            enabled = !uiState.isBacktesting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (uiState.isBacktesting) "Testing..." else "Test against history")
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Bottom actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onEvent(UiEvent.OnBackClicked) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Cancel")
            }

            Button(
                onClick = { onEvent(UiEvent.OnContinueClicked) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Continue")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MetadataStep(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Name field (required)
        OutlinedTextField(
            value = uiState.rule.name,
            onValueChange = { onEvent(UiEvent.OnNameChange(it)) },
            label = { Text("Name*") },
            placeholder = { Text("e.g., ICA Banken Purchase") },
            isError = uiState.validationErrors.containsKey("name"),
            supportingText = uiState.validationErrors["name"]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!uiState.showDescription) {
                ActionChip(
                    text = "Add description",
                    onClick = { onEvent(UiEvent.OnAddDescriptionClicked) },
                )
            }
            if (!uiState.showCategory) {
                ActionChip(
                    text = "Add category",
                    onClick = { onEvent(UiEvent.OnAddCategoryClicked) },
                )
            }
        }

        // Description field (shown when showDescription is true)
        if (uiState.showDescription) {
            OutlinedTextField(
                value = uiState.rule.description,
                onValueChange = { onEvent(UiEvent.OnDescriptionChange(it)) },
                label = { Text("Description") },
                placeholder = { Text("What does this rule do?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }

        // Category field (shown when showCategory is true)
        if (uiState.showCategory) {
            OutlinedTextField(
                value = uiState.rule.category,
                onValueChange = { onEvent(UiEvent.OnCategoryChange(it)) },
                label = { Text("Category") },
                placeholder = { Text("e.g., Finance, Shopping") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        DryRunToggle(
            enabled = uiState.rule.isDryRun,
            onToggle = { onEvent(UiEvent.OnDryRunToggle(it)) },
        )

        Spacer(modifier = Modifier.weight(1f))

        // Bottom actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = { onEvent(UiEvent.OnBackToLogicClicked) },
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }

            Button(
                onClick = { onEvent(UiEvent.OnSaveClicked) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = uiState.rule.name.isNotBlank() && !uiState.isLoading,
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Toggle for dry-run mode: when enabled, matches are logged but no actions execute - a safe
 * way to trial a rule before trusting it to act on real notifications.
 */
@Composable
private fun DryRunToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Dry run",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Log matches without dismissing, snoozing, or alerting - review results before trusting this rule",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun SectionWithHelp(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        content()
    }
}

@Composable
private fun ActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text)
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun RuleEditorScreenStep1Preview() {
    NotificappTheme {
        RuleEditorScreenContent(
            uiState = UiState(
                currentStep = 1,
                rule = RuleUiModel(
                    name = "ICA Banken Purchase",
                    targetApps = emptyList(),
                    triggers = listOf(
                        RuleCondition(
                            id = "1",
                            condition = MatchingCondition.TEXT_CONTENT,
                            operator = MatchingOperator.CONTAINS,
                            value = "purchase",
                        ),
                    ),
                    fields = listOf(
                        RuleField(
                            id = "1",
                            name = "Merchant",
                            method = ExtractionMethod.LineExtraction(10),
                        ),
                        RuleField(
                            id = "2",
                            name = "Amount",
                            method = ExtractionMethod.RegexPattern("\\d+(\\.\\d+)?"),
                        ),
                    ),
                    actions = listOf(
                        RuleAction(
                            id = "1",
                            type = ActionType.SAVE_DATA,
                            isEnabled = true,
                        ),
                    ),
                ),
                sampleNotification = Notification(
                    id = "test",
                    packageName = "com.ica.banken",
                    appName = "ICA Banken",
                    title = "Purchase notification",
                    content = "Your purchase of 153.50 kr at ICA Kvantum was successful",
                    rawContent = "Your purchase of 153.50 kr at ICA Kvantum was successful",
                    timestamp = System.currentTimeMillis(),
                ),
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun RuleEditorScreenStep2Preview() {
    NotificappTheme {
        RuleEditorScreenContent(
            uiState = UiState(
                currentStep = 2,
                rule = RuleUiModel(
                    name = "ICA Banken Purchase",
                    description = "Extracts purchase information from ICA Banken notifications",
                    targetApps = emptyList(),
                    fields = listOf(
                        RuleField(
                            id = "1",
                            name = "Merchant",
                            method = ExtractionMethod.LineExtraction(10),
                        ),
                        RuleField(
                            id = "2",
                            name = "Amount",
                            method = ExtractionMethod.RegexPattern("\\d+(\\.\\d+)?"),
                        ),
                    ),
                    actions = listOf(
                        RuleAction(
                            id = "1",
                            type = ActionType.SAVE_DATA,
                            isEnabled = true,
                        ),
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
