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
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleField
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
 * @param fieldToEdit The field being edited, or null for a new field
 * @param notification The notification to extract fields from
 * @param onFieldSaved Called when a field is successfully saved
 * @param onDismiss Called when the sheet should be dismissed
 * @param viewModel ViewModel for state management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFieldBottomSheet(
    fieldToEdit: RuleField?,
    notification: Notification?,
    onFieldSaved: (RuleField) -> Unit,
    onDismiss: () -> Unit,
    viewModel: AddFieldViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val scope = rememberCoroutineScope()

    // Initialize with sample text when shown
    LaunchedEffect(fieldToEdit, notification) {
        viewModel.onEvent(UiEvent.Initialize(fieldToEdit, notification))
    }

    // Collect effects and handle them internally, calling appropriate callbacks
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is AddFieldContract.UiEffect.ReturnWithField -> {
                onFieldSaved(effect.field)
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
            title = { Text(if (uiState.fieldName.isBlank()) "Add Field" else "Edit Field") },
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
                text = uiState.error,
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
            MethodTypeDropdown(
                selectedMethodType = uiState.selectedMethodType,
                onMethodTypeChange = { onEvent(UiEvent.OnMethodTypeChange(it)) },
            )

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
                AddFieldContract.MethodType.SMART_AMOUNT -> {
                    SectionHeader(
                        icon = Icons.Default.AttachMoney,
                        title = "SMART DETECTION",
                    )
                    Text(
                        text = "This method will automatically detect currency amounts in the text.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AddFieldContract.MethodType.SMART_DATE -> {
                    SectionHeader(
                        icon = Icons.Default.CalendarToday,
                        title = "SMART DETECTION",
                    )
                    Text(
                        text = "This method will automatically detect dates in the text.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Preview Result Section (Notification Sample + Extracted Value)
            if (uiState.previewResult != AddFieldContract.PreviewResult.None) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Notification Sample with Highlighting
                    val highlightStart = (uiState.previewResult as? AddFieldContract.PreviewResult.Success)?.startIndex ?: -1
                    val highlightEnd = (uiState.previewResult as? AddFieldContract.PreviewResult.Success)?.endIndex ?: -1

                    Text(
                        text = "PREVIEW RESULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Notification Sample Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        ) {
                            Text(
                                text = "Notification Sample:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (highlightStart >= 0 && highlightEnd > 0) {
                                HighlightedText(
                                    text = uiState.sampleText,
                                    highlightStart = highlightStart,
                                    highlightEnd = highlightEnd,
                                )
                            } else {
                                Text(
                                    text = uiState.sampleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }

                    // Preview Result Card
                    PreviewResultCard(result = uiState.previewResult)
                }
            }

            // Add Field Button
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onEvent(UiEvent.OnSaveClicked) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isValid,
            ) {
                Text("Add Field")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodTypeDropdown(
    selectedMethodType: AddFieldContract.MethodType,
    onMethodTypeChange: (AddFieldContract.MethodType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedMethodType.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Extraction Method") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AddFieldContract.MethodType.entries.forEach { methodType ->
                DropdownMenuItem(
                    text = {
                        Column {
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
                    },
                    onClick = {
                        onMethodTypeChange(methodType)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HighlightedText(
    text: String,
    highlightStart: Int,
    highlightEnd: Int,
    modifier: Modifier = Modifier,
) {
    val annotatedText = buildAnnotatedString {
        if (highlightStart >= 0 && highlightEnd > highlightStart && highlightStart < text.length) {
            // Text before highlight
            if (highlightStart > 0) {
                append(text.substring(0, highlightStart))
            }
            // Highlighted portion
            withStyle(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    background = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                val end = highlightEnd.coerceAtMost(text.length)
                append(text.substring(highlightStart, end))
            }
            // Text after highlight
            if (highlightEnd < text.length) {
                append(text.substring(highlightEnd.coerceAtMost(text.length)))
            }
        } else {
            // No valid highlight, show plain text
            append(text)
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier,
    )
}

@Composable
private fun FixedPositionConfig(
    startIndex: Int,
    endIndex: Int,
    onStartIndexChange: (Int) -> Unit,
    onEndIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.Straighten,
            title = "POSITION",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
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
        SectionHeader(
            icon = Icons.Default.HorizontalRule,
            title = "ANCHORS",
        )
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
        SectionHeader(
            icon = Icons.Default.Code,
            title = "REGEX PATTERN",
        )
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
        SectionHeader(
            icon = Icons.Default.TextFields,
            title = "KEYWORD",
        )
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.TextFields,
            title = "KEYWORD",
        )
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
    }
}

@Composable
private fun LineExtractionConfig(
    lineNumber: Int,
    onLineNumberChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.AutoMirrored.Default.List,
            title = "LINE",
        )
        OutlinedTextField(
            value = lineNumber.toString(),
            onValueChange = { onLineNumberChange(it.toIntOrNull()?.coerceAtLeast(1) ?: 1) },
            label = { Text("Line Number") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
private fun SplitByDelimiterConfig(
    delimiter: String,
    takeIndex: Int,
    onDelimiterChange: (String) -> Unit,
    onTakeIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.AutoMirrored.Default.CallSplit,
            title = "DELIMITER",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
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
}

@Composable
private fun JsonPathConfig(
    path: String,
    onPathChange: (String) -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            icon = Icons.Default.DataObject,
            title = "JSON PATH",
        )
        OutlinedTextField(
            value = path,
            onValueChange = onPathChange,
            label = { Text("JSON Path") },
            placeholder = { Text("e.g., $.amount") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
        )
    }
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "EXTRACTED VALUE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = targetResult.value,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            is AddFieldContract.PreviewResult.Failure -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(
                            text = "EXTRACTION FAILED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = targetResult.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
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
                sampleText = "Your purchase of 153,50 kr at ICA Supermarket was successful.",
                fieldName = "Amount",
                selectedMethodType = AddFieldContract.MethodType.TEXT_BETWEEN_ANCHORS,
                startAnchor = "purchase of ",
                endAnchor = " kr",
                previewResult = AddFieldContract.PreviewResult.Success(
                    value = "153,50",
                    startIndex = 16,
                    endIndex = 22,
                ),
            ),
            onEvent = {},
            onClose = {},
        )
    }
}
