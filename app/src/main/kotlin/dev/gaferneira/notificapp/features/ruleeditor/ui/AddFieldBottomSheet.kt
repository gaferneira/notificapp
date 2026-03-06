package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
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
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.AddFieldContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.AddFieldViewModel
import kotlinx.coroutines.launch

/**
 * BottomSheet for adding a new extraction field.
 *
 * This is shown as a modal bottom sheet from the RuleEditor screen
 * instead of being a separate navigation destination.
 *
 * @param fieldId The ID of the field being edited, or null for new field
 * @param notification The notification to extract fields from
 * @param onFieldAdded Called when a field is successfully added
 * @param onDismiss Called when the sheet should be dismissed
 * @param viewModel ViewModel for state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFieldBottomSheet(
    fieldId: String?,
    notification: Notification?,
    onFieldAdded: (ExtractionField) -> Unit,
    onDismiss: () -> Unit,
    viewModel: AddFieldViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val scope = rememberCoroutineScope()

    // Initialize with sample text when shown
    LaunchedEffect(fieldId, notification) {
        viewModel.onEvent(UiEvent.Initialize(fieldId, notification))
    }

    // Collect effects and handle them internally, calling appropriate callbacks
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is AddFieldContract.UiEffect.ReturnWithField -> {
                onFieldAdded(effect.field)
            }
            is AddFieldContract.UiEffect.CancelAndReturn -> {
                onDismiss()
            }
            is AddFieldContract.UiEffect.ShowError -> {
                // Error is already shown via validation in the bottom sheet
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        AddFieldBottomSheetContent(
            uiState = uiState,
            onEvent = viewModel::onEvent,
            onClose = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldBottomSheetContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        // Header
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
                TextButton(
                    onClick = { onEvent(UiEvent.OnSaveClicked) },
                    enabled = uiState.isValid,
                ) {
                    Text("Save")
                }
            },
        )

        // Error message
        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sample Text Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = "Sample Text",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.sampleText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            // Field Name Input
            OutlinedTextField(
                value = uiState.fieldName,
                onValueChange = { onEvent(UiEvent.OnFieldNameChange(it)) },
                label = { Text("Field Name") },
                placeholder = { Text("e.g., Amount, Date, Order ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.validationErrors.contains("fieldName"),
                supportingText = uiState.validationErrors["fieldName"]?.let { { Text(it) } },
            )

            // Method Type Selection
            Text(
                text = "Extraction Method",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )

            // Show all method types as radio buttons
            AddFieldContract.MethodType.entries.forEach { methodType ->
                MethodTypeOption(
                    methodType = methodType,
                    isSelected = uiState.selectedMethodType == methodType,
                    onClick = { onEvent(UiEvent.OnMethodTypeChange(methodType)) },
                )
            }

            // Method-specific configuration based on selected type
            when (uiState.selectedMethodType) {
                AddFieldContract.MethodType.FIXED_POSITION -> {
                    FixedPositionConfig(
                        startIndex = uiState.fixedStartIndex,
                        endIndex = uiState.fixedEndIndex,
                        onStartIndexChange = { onEvent(UiEvent.OnStartIndexChange(it)) },
                        onEndIndexChange = { onEvent(UiEvent.OnEndIndexChange(it)) },
                    )
                }
                AddFieldContract.MethodType.TEXT_BETWEEN_ANCHORS -> {
                    TextBetweenAnchorsConfig(
                        startAnchor = uiState.startAnchor,
                        endAnchor = uiState.endAnchor,
                        onStartAnchorChange = { onEvent(UiEvent.OnStartAnchorChange(it)) },
                        onEndAnchorChange = { onEvent(UiEvent.OnEndAnchorChange(it)) },
                        errors = uiState.validationErrors,
                    )
                }
                AddFieldContract.MethodType.REGEX -> {
                    RegexConfig(
                        pattern = uiState.regexPattern,
                        captureGroup = uiState.captureGroup,
                        onPatternChange = { onEvent(UiEvent.OnRegexPatternChange(it)) },
                        onCaptureGroupChange = { onEvent(UiEvent.OnCaptureGroupChange(it)) },
                        error = uiState.validationErrors["regexPattern"],
                    )
                }
                AddFieldContract.MethodType.TEXT_AFTER_KEYWORD -> {
                    TextAfterKeywordConfig(
                        keyword = uiState.afterKeyword,
                        maxLength = uiState.afterKeywordMaxLength,
                        onKeywordChange = { onEvent(UiEvent.OnAfterKeywordChange(it)) },
                        onMaxLengthChange = { onEvent(UiEvent.OnAfterKeywordMaxLengthChange(it)) },
                        error = uiState.validationErrors["afterKeyword"],
                    )
                }
                AddFieldContract.MethodType.TEXT_BEFORE_KEYWORD -> {
                    TextBeforeKeywordConfig(
                        keyword = uiState.beforeKeyword,
                        onKeywordChange = { onEvent(UiEvent.OnBeforeKeywordChange(it)) },
                        error = uiState.validationErrors["beforeKeyword"],
                    )
                }
                AddFieldContract.MethodType.LINE_EXTRACTION -> {
                    LineExtractionConfig(
                        lineNumber = uiState.lineNumber,
                        onLineNumberChange = { onEvent(UiEvent.OnLineNumberChange(it)) },
                    )
                }
                AddFieldContract.MethodType.SPLIT_BY_DELIMITER -> {
                    SplitByDelimiterConfig(
                        delimiter = uiState.delimiter,
                        takeIndex = uiState.takeIndex,
                        onDelimiterChange = { onEvent(UiEvent.OnDelimiterChange(it)) },
                        onTakeIndexChange = { onEvent(UiEvent.OnTakeIndexChange(it)) },
                    )
                }
                AddFieldContract.MethodType.JSON_PATH -> {
                    JsonPathConfig(
                        path = uiState.jsonPath,
                        onPathChange = { onEvent(UiEvent.OnJsonPathChange(it)) },
                        error = uiState.validationErrors["jsonPath"],
                    )
                }
                AddFieldContract.MethodType.SMART_AMOUNT,
                AddFieldContract.MethodType.SMART_DATE,
                -> {
                    // No additional configuration needed for smart detection
                    Text(
                        text = "This method will automatically detect the ${if (uiState.selectedMethodType == AddFieldContract.MethodType.SMART_AMOUNT) "amount" else "date"} in the text.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Preview Section
            if (uiState.previewResult != AddFieldContract.PreviewResult.None) {
                Spacer(modifier = Modifier.height(8.dp))
                PreviewResultCard(result = uiState.previewResult)
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { onEvent(UiEvent.OnPreviewClicked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isTesting,
            ) {
                Text(if (uiState.isTesting) "Testing..." else "Preview Extraction")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onEvent(UiEvent.OnSaveClicked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isValid,
            ) {
                Text("Add Field")
            }
        }
    }
}

@Composable
private fun MethodTypeOption(
    methodType: AddFieldContract.MethodType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = methodType.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = methodType.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FixedPositionConfig(
    startIndex: Int,
    endIndex: Int,
    onStartIndexChange: (Int) -> Unit,
    onEndIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = startIndex.toString(),
            onValueChange = { onStartIndexChange(it.toIntOrNull() ?: 0) },
            label = { Text("Start Index") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = endIndex.toString(),
            onValueChange = { onEndIndexChange(it.toIntOrNull() ?: 0) },
            label = { Text("End Index") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
}

@Composable
private fun TextBetweenAnchorsConfig(
    startAnchor: String,
    endAnchor: String,
    onStartAnchorChange: (String) -> Unit,
    onEndAnchorChange: (String) -> Unit,
    errors: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = startAnchor,
            onValueChange = onStartAnchorChange,
            label = { Text("Start Anchor") },
            placeholder = { Text("Text before the value") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errors.contains("startAnchor"),
            supportingText = errors["startAnchor"]?.let { { Text(it) } },
        )
        OutlinedTextField(
            value = endAnchor,
            onValueChange = onEndAnchorChange,
            label = { Text("End Anchor") },
            placeholder = { Text("Text after the value") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = errors.contains("endAnchor"),
            supportingText = errors["endAnchor"]?.let { { Text(it) } },
        )
    }
}

@Composable
private fun RegexConfig(
    pattern: String,
    captureGroup: Int,
    onPatternChange: (String) -> Unit,
    onCaptureGroupChange: (Int) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = pattern,
            onValueChange = onPatternChange,
            label = { Text("Regex Pattern") },
            placeholder = { Text("e.g., (\\d+[.,]?\\d*)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
        OutlinedTextField(
            value = captureGroup.toString(),
            onValueChange = { onCaptureGroupChange(it.toIntOrNull()?.coerceAtLeast(0) ?: 0) },
            label = { Text("Capture Group") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun TextAfterKeywordConfig(
    keyword: String,
    maxLength: Int?,
    onKeywordChange: (String) -> Unit,
    onMaxLengthChange: (Int?) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            label = { Text("Keyword") },
            placeholder = { Text("Text to search for") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
        OutlinedTextField(
            value = maxLength?.toString() ?: "",
            onValueChange = { onMaxLengthChange(it.toIntOrNull()) },
            label = { Text("Max Length (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun TextBeforeKeywordConfig(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        label = { Text("Keyword") },
        placeholder = { Text("Text to search for") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
    )
}

@Composable
private fun LineExtractionConfig(
    lineNumber: Int,
    onLineNumberChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = lineNumber.toString(),
        onValueChange = { onLineNumberChange(it.toIntOrNull()?.coerceAtLeast(1) ?: 1) },
        label = { Text("Line Number") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun SplitByDelimiterConfig(
    delimiter: String,
    takeIndex: Int,
    onDelimiterChange: (String) -> Unit,
    onTakeIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = delimiter,
            onValueChange = onDelimiterChange,
            label = { Text("Delimiter") },
            placeholder = { Text(",") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        OutlinedTextField(
            value = takeIndex.toString(),
            onValueChange = { onTakeIndexChange(it.toIntOrNull()?.coerceAtLeast(0) ?: 0) },
            label = { Text("Take Index") },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
    }
}

@Composable
private fun JsonPathConfig(
    path: String,
    onPathChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = path,
        onValueChange = onPathChange,
        label = { Text("JSON Path") },
        placeholder = { Text("e.g., $.amount") },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
    )
}

@Composable
private fun PreviewResultCard(
    result: AddFieldContract.PreviewResult,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = result,
        label = "PreviewResult",
        modifier = modifier.fillMaxWidth(),
    ) { targetResult ->
        when (targetResult) {
            is AddFieldContract.PreviewResult.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Preview Result",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = targetResult.value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            is AddFieldContract.PreviewResult.Failure -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Extraction Failed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = targetResult.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> {}
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AddFieldBottomSheetContentPreview() {
    NotificappTheme {
        AddFieldBottomSheetContent(
            uiState = UiState(
                sampleText = "Your order total is $123.45",
                fieldName = "Amount",
                selectedMethodType = AddFieldContract.MethodType.REGEX,
                regexPattern = "\\$([0-9]+\\.?[0-9]*)",
                previewResult = AddFieldContract.PreviewResult.Success("123.45"),
            ),
            onEvent = {},
            onClose = {},
        )
    }
}
