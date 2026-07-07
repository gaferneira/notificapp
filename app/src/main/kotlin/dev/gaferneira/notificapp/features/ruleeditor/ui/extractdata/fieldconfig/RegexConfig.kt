package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RegexConfig(
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
