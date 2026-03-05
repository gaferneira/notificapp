package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ExtractionField
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.features.ruleeditor.contract.ActionBottomSheetContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.contract.TriggerUiModel
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AddButton
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.DataExtractionSection
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.DoSection
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.WhenSection
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.RuleEditorViewModel

@Composable
fun RuleEditorScreen(
    modifier: Modifier = Modifier,
    ruleId: String? = null,
    notificationId: String? = null,
    pendingField: ExtractionField? = null,
    onNavigate: (UiEffect) -> Unit = {},
    viewModel: RuleEditorViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle()

    // Load initial data
    LaunchedEffect(ruleId, notificationId) {
        viewModel.onEvent(UiEvent.LoadRule(ruleId))
        notificationId?.let { viewModel.onEvent(UiEvent.LoadSampleNotification(it)) }
    }

    // Handle pending field from AddField screen
    LaunchedEffect(pendingField) {
        pendingField?.let {
            viewModel.onEvent(UiEvent.OnFieldAdded(it))
        }
    }

    // Collect effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        onNavigate(effect)
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

    // Helper function to handle effects from the MatchingLogicBottomSheet
    val handleMatchingLogicEffect: (MatchingLogicContract.UiEffect) -> Unit = { effect ->
        when (effect) {
            is MatchingLogicContract.UiEffect.TriggerCreated -> {
                onEvent(UiEvent.OnMatchingLogicEffect(effect))
            }
            is MatchingLogicContract.UiEffect.TriggerUpdated -> {
                onEvent(UiEvent.OnMatchingLogicEffect(effect))
            }
            is MatchingLogicContract.UiEffect.Dismiss -> {
                onEvent(UiEvent.OnDismissTriggerSheet)
            }
            is MatchingLogicContract.UiEffect.ShowError -> {
                // Error is already shown via validation in the bottom sheet
            }
        }
    }

    // Helper function to handle effects from the ActionBottomSheet
    val handleActionSheetEffect: (ActionBottomSheetContract.UiEffect) -> Unit = { effect ->
        when (effect) {
            is ActionBottomSheetContract.UiEffect.ActionCreated -> {
                onEvent(UiEvent.OnActionSheetEffect(effect))
            }
            is ActionBottomSheetContract.UiEffect.ActionUpdated -> {
                onEvent(UiEvent.OnActionSheetEffect(effect))
            }
            is ActionBottomSheetContract.UiEffect.Dismiss -> {
                onEvent(UiEvent.OnDismissActionSheet)
            }
            is ActionBottomSheetContract.UiEffect.ShowError -> {
                // Error is already shown via validation in the bottom sheet
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (uiState.currentStep) {
                            1 -> "New automation"
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

            // Bottom sheets
            if (uiState.isMatchingLogicSheetVisible) {
                MatchingLogicBottomSheet(
                    isVisible = true,
                    editingTriggerId = uiState.editingTriggerId,
                    initialTrigger = uiState.editingTrigger,
                    onEffect = handleMatchingLogicEffect,
                )
            }

            if (uiState.isActionSheetVisible) {
                ActionBottomSheet(
                    isVisible = true,
                    editingActionId = uiState.editingActionId,
                    initialAction = uiState.editingAction?.let { action ->
                        ActionBottomSheetContract.ActionUiModel(
                            id = action.id,
                            type = when (action.type) {
                                RuleEditorContract.ActionType.SAVE_DATA -> ActionBottomSheetContract.ActionType.SAVE_DATA
                                RuleEditorContract.ActionType.DELETE_NOTIFICATION -> ActionBottomSheetContract.ActionType.DELETE_NOTIFICATION
                                RuleEditorContract.ActionType.CREATE_ALARM -> ActionBottomSheetContract.ActionType.CREATE_ALARM
                            },
                            isEnabled = action.isEnabled,
                        )
                    },
                    onEffect = handleActionSheetEffect,
                )
            }
        }
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
            description = "A trigger is a specific notification event. Any trigger added here will start the extraction process.",
        ) {
            WhenSection(
                triggers = uiState.triggers,
                onRemoveTrigger = { onEvent(UiEvent.OnRemoveTriggerClicked(it)) },
                onTriggerClick = { onEvent(UiEvent.OnTriggerItemClicked(it)) },
                modifier = Modifier.fillMaxWidth(),
            )

            // Add trigger button
            AddButton(
                text = "Add trigger",
                onClick = { onEvent(UiEvent.OnAddTriggerClicked) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Data Extraction Section
        DataExtractionSection(
            fields = uiState.extractionFields,
            onAutoGenerate = { onEvent(UiEvent.OnAutoGenerateClicked) },
            onAddField = { onEvent(UiEvent.OnAddFieldClicked) },
            onRemoveField = { onEvent(UiEvent.OnRemoveFieldClicked(it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Do Section
        DoSection(
            actions = uiState.actions,
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
            value = uiState.name,
            onValueChange = { onEvent(UiEvent.OnNameChange(it)) },
            label = { Text("Name*") },
            placeholder = { Text("e.g., ICA Banken Purchase") },
            isError = uiState.validationErrors.containsKey("name"),
            supportingText = uiState.validationErrors["name"]?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        // Action chips row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionChip(
                text = if (uiState.description.isBlank()) "Add description" else "Edit description",
                onClick = { /* Toggle description field visibility */ },
                modifier = Modifier.weight(1f),
            )
            ActionChip(
                text = if (uiState.area.isBlank()) "Add area" else "Edit area",
                onClick = { /* Toggle area field visibility */ },
                modifier = Modifier.weight(1f),
            )
        }

        // Action chips row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionChip(
                text = if (uiState.category.isBlank()) "Add category" else "Edit category",
                onClick = { /* Toggle category field visibility */ },
                modifier = Modifier.weight(1f),
            )
            ActionChip(
                text = "Set app scope",
                onClick = { /* Show app scope selection */ },
                modifier = Modifier.weight(1f),
            )
        }

        // Description field (always visible in this version)
        OutlinedTextField(
            value = uiState.description,
            onValueChange = { onEvent(UiEvent.OnDescriptionChange(it)) },
            label = { Text("Description") },
            placeholder = { Text("What does this rule do?") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
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
                enabled = uiState.name.isNotBlank() && !uiState.isLoading,
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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
                name = "ICA Banken Purchase",
                targetApps = listOf("com.ica.banken"),
                sampleNotification = Notification(
                    id = "test",
                    packageName = "com.ica.banken",
                    appName = "ICA Banken",
                    title = "Purchase notification",
                    content = "Your purchase of 153.50 kr at ICA Kvantum was successful",
                    rawContent = "Your purchase of 153.50 kr at ICA Kvantum was successful",
                    timestamp = System.currentTimeMillis(),
                ),
                triggers = listOf(
                    TriggerUiModel(
                        id = "1",
                        type = MatchingLogicContract.TriggerType.CONDITION,
                        condition = MatchingLogicContract.MatchingCondition.TEXT_CONTENT,
                        operator = MatchingLogicContract.MatchingOperator.CONTAINS,
                        value = "purchase",
                    ),
                ),
                extractionFields = listOf(
                    RuleEditorContract.ExtractionFieldUiModel(
                        id = "1",
                        name = "Merchant",
                        methodType = "text_between_anchors",
                        methodSummary = "Regex: (.*) at .*",
                    ),
                    RuleEditorContract.ExtractionFieldUiModel(
                        id = "2",
                        name = "Amount",
                        methodType = "smart_amount",
                        methodSummary = "Find: currency pattern",
                    ),
                ),
                actions = listOf(
                    RuleEditorContract.ActionUiModel(
                        id = "1",
                        type = RuleEditorContract.ActionType.SAVE_DATA,
                        isEnabled = true,
                    ),
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
                name = "ICA Banken Purchase",
                description = "Extracts purchase information from ICA Banken notifications",
                targetApps = listOf("com.ica.banken"),
                extractionFields = listOf(
                    RuleEditorContract.ExtractionFieldUiModel(
                        id = "1",
                        name = "Merchant",
                        methodType = "text_between_anchors",
                        methodSummary = "Regex: (.*) at .*",
                    ),
                    RuleEditorContract.ExtractionFieldUiModel(
                        id = "2",
                        name = "Amount",
                        methodType = "smart_amount",
                        methodSummary = "Find: currency pattern",
                    ),
                ),
                actions = listOf(
                    RuleEditorContract.ActionUiModel(
                        id = "1",
                        type = RuleEditorContract.ActionType.SAVE_DATA,
                        isEnabled = true,
                    ),
                ),
            ),
            onEvent = {},
        )
    }
}
