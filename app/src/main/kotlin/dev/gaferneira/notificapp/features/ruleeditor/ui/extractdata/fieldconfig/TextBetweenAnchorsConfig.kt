package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata.fieldconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TextBetweenAnchorsConfig(
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
