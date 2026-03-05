package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.AddFieldViewModel

@Composable
fun AddFieldScreen(
    sampleText: String,
    onNavigate: (UiEffect) -> Unit = {},
    viewModel: AddFieldViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Initialize with sample text
    LaunchedEffect(sampleText) {
        viewModel.onEvent(UiEvent.Initialize(sampleText))
    }

    // Collect effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        onNavigate(effect)
    }

    AddFieldScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Add Field") },
                navigationIcon = {
                    IconButton(onClick = { onEvent(UiEvent.OnCancelClicked) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { onEvent(UiEvent.OnSaveClicked) },
                        enabled = uiState.isValid,
                    ) {
                        Text("Add")
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Field Name
                item {
                    OutlinedTextField(
                        value = uiState.fieldName,
                        onValueChange = { onEvent(UiEvent.OnFieldNameChange(it)) },
                        label = { Text("Field Name") },
                        placeholder = { Text("e.g., Amount") },
                        isError = uiState.validationErrors.containsKey("fieldName"),
                        supportingText = uiState.validationErrors["fieldName"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Extraction Method Selection
                item {
                    ExtractionMethodSection(
                        selectedMethod = uiState.selectedMethodType,
                        onMethodSelect = { onEvent(UiEvent.OnMethodTypeChange(it)) },
                    )
                }

                // Dynamic Configuration based on selected method
                item {
                    AnimatedContent(
                        targetState = uiState.selectedMethodType,
                        label = "method_config",
                    ) { method ->
                        MethodConfigurationSection(
                            method = method,
                            uiState = uiState,
                            onEvent = onEvent,
                        )
                    }
                }

                // Sample Text Card
                item {
                    SampleTextCard(sampleText = uiState.sampleText)
                }

                // Preview Result
                item {
                    PreviewResultCard(previewResult = uiState.previewResult)
                }

                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }

            // Bottom Action Buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEvent(UiEvent.OnCancelClicked) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = { onEvent(UiEvent.OnSaveClicked) },
                    enabled = uiState.isValid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save Field")
                }
            }

            // Error Snackbar
            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp, 80.dp, 16.dp, 16.dp),
                    action = {
                        TextButton(onClick = { onEvent(UiEvent.OnDismissError) }) {
                            Text("Dismiss")
                        }
                    },
                ) {
                    Text(error)
                }
            }
        }
    }
}

@Composable
private fun ExtractionMethodSection(
    selectedMethod: AddFieldContract.MethodType,
    onMethodSelect: (AddFieldContract.MethodType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Extraction Method",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )

        AddFieldContract.MethodType.entries.forEach { method ->
            MethodCard(
                method = method,
                isSelected = method == selectedMethod,
                onClick = { onMethodSelect(method) },
            )
        }
    }
}

@Composable
private fun MethodCard(
    method: AddFieldContract.MethodType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            },
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = method.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = method.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isSelected) {
                RadioButton(
                    selected = true,
                    onClick = null,
                )
            }
        }
    }
}

@Composable
private fun MethodConfigurationSection(
    method: AddFieldContract.MethodType,
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Define ${method.displayName}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )

            when (method) {
                AddFieldContract.MethodType.FIXED_POSITION -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = uiState.fixedStartIndex.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { index ->
                                    onEvent(UiEvent.OnStartIndexChange(index))
                                }
                            },
                            label = { Text("Start Index") },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = uiState.fixedEndIndex.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { index ->
                                    onEvent(UiEvent.OnEndIndexChange(index))
                                }
                            },
                            label = { Text("End Index") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                AddFieldContract.MethodType.TEXT_BETWEEN_ANCHORS -> {
                    OutlinedTextField(
                        value = uiState.startAnchor,
                        onValueChange = { onEvent(UiEvent.OnStartAnchorChange(it)) },
                        label = { Text("Start Anchor") },
                        placeholder = { Text("e.g., of") },
                        isError = uiState.validationErrors.containsKey("startAnchor"),
                        supportingText = uiState.validationErrors["startAnchor"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.endAnchor,
                        onValueChange = { onEvent(UiEvent.OnEndAnchorChange(it)) },
                        label = { Text("End Anchor") },
                        placeholder = { Text("e.g., at") },
                        isError = uiState.validationErrors.containsKey("endAnchor"),
                        supportingText = uiState.validationErrors["endAnchor"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AddFieldContract.MethodType.REGEX -> {
                    OutlinedTextField(
                        value = uiState.regexPattern,
                        onValueChange = { onEvent(UiEvent.OnRegexPatternChange(it)) },
                        label = { Text("Regex Pattern") },
                        placeholder = { Text("e.g., ([\\d,.]+) (?<currency>\\w+)") },
                        isError = uiState.validationErrors.containsKey("regexPattern"),
                        supportingText = uiState.validationErrors["regexPattern"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.captureGroup.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { group ->
                                onEvent(UiEvent.OnCaptureGroupChange(group))
                            }
                        },
                        label = { Text("Capture Group") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AddFieldContract.MethodType.TEXT_AFTER_KEYWORD -> {
                    OutlinedTextField(
                        value = uiState.afterKeyword,
                        onValueChange = { onEvent(UiEvent.OnAfterKeywordChange(it)) },
                        label = { Text("Keyword") },
                        placeholder = { Text("e.g., Total:") },
                        isError = uiState.validationErrors.containsKey("afterKeyword"),
                        supportingText = uiState.validationErrors["afterKeyword"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AddFieldContract.MethodType.TEXT_BEFORE_KEYWORD -> {
                    OutlinedTextField(
                        value = uiState.beforeKeyword,
                        onValueChange = { onEvent(UiEvent.OnBeforeKeywordChange(it)) },
                        label = { Text("Keyword") },
                        placeholder = { Text("e.g., was") },
                        isError = uiState.validationErrors.containsKey("beforeKeyword"),
                        supportingText = uiState.validationErrors["beforeKeyword"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AddFieldContract.MethodType.LINE_EXTRACTION -> {
                    OutlinedTextField(
                        value = uiState.lineNumber.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { line ->
                                onEvent(UiEvent.OnLineNumberChange(line))
                            }
                        },
                        label = { Text("Line Number") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AddFieldContract.MethodType.SPLIT_BY_DELIMITER -> {
                    OutlinedTextField(
                        value = uiState.delimiter,
                        onValueChange = { onEvent(UiEvent.OnDelimiterChange(it)) },
                        label = { Text("Delimiter") },
                        placeholder = { Text("e.g., , or space") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = uiState.takeIndex.toString(),
                        onValueChange = {
                            it.toIntOrNull()?.let { index ->
                                onEvent(UiEvent.OnTakeIndexChange(index))
                            }
                        },
                        label = { Text("Take Index (0-based)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AddFieldContract.MethodType.JSON_PATH -> {
                    OutlinedTextField(
                        value = uiState.jsonPath,
                        onValueChange = { onEvent(UiEvent.OnJsonPathChange(it)) },
                        label = { Text("JSON Path") },
                        placeholder = { Text("e.g., data.amount or items.0.name") },
                        isError = uiState.validationErrors.containsKey("jsonPath"),
                        supportingText = uiState.validationErrors["jsonPath"]?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AddFieldContract.MethodType.SMART_AMOUNT -> {
                    Text(
                        text = "Smart amount detection will automatically find currency amounts in the text.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AddFieldContract.MethodType.SMART_DATE -> {
                    Text(
                        text = "Smart date detection will automatically find dates in various formats (MM/DD/YYYY, YYYY-MM-DD, etc.).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SampleTextCard(
    sampleText: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "NOTIFICATION SAMPLE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = sampleText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PreviewResultCard(
    previewResult: AddFieldContract.PreviewResult,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (previewResult) {
                is AddFieldContract.PreviewResult.Success ->
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                is AddFieldContract.PreviewResult.Failure ->
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Preview Result",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            when (previewResult) {
                is AddFieldContract.PreviewResult.Success -> {
                    Text(
                        text = "EXTRACTED VALUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = previewResult.value,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                is AddFieldContract.PreviewResult.Failure -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = previewResult.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Configure the extraction method to see a preview",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun AddFieldScreenPreview() {
    NotificappTheme {
        AddFieldScreenContent(
            uiState = UiState(
                fieldName = "Amount",
                selectedMethodType = AddFieldContract.MethodType.TEXT_BETWEEN_ANCHORS,
                sampleText = "Your purchase of USD 45.00 at Starbucks was successful",
                startAnchor = "of",
                endAnchor = "at",
                previewResult = AddFieldContract.PreviewResult.Success("USD 45.00"),
            ),
            onEvent = {},
        )
    }
}
