package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * Labeled step indicator of the guided rule editor. Each step shows its number (a check once
 * completed) and its label, and is announced as "Step N of M: label". Completed steps are
 * clickable and jump back to that step; the current and upcoming steps are not.
 *
 * @param currentStep 1-based index of the current step
 * @param stepLabels One already-localized label per step, in order
 * @param onStepClick Called with the 1-based index of a completed step that was tapped
 */
@Composable
fun StepIndicator(
    currentStep: Int,
    stepLabels: List<String>,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        stepLabels.forEachIndexed { index, label ->
            val step = index + 1
            StepItem(
                step = step,
                totalSteps = stepLabels.size,
                label = label,
                currentStep = currentStep,
                onClick = { onStepClick(step) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StepItem(
    step: Int,
    totalSteps: Int,
    label: String,
    currentStep: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isActive = step == currentStep
    val isCompleted = step < currentStep
    val description = stringResource(R.string.rule_editor_step_description, step, totalSteps, label)
    val completedState = stringResource(R.string.rule_editor_step_completed_state)
    val goBackLabel = stringResource(R.string.rule_editor_step_completed_action)

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = isCompleted, onClickLabel = goBackLabel, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isActive
                if (isCompleted) stateDescription = completedState
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepConnector(visible = step > 1, highlighted = isActive || isCompleted, modifier = Modifier.weight(1f))
            StepCircle(step = step, isActive = isActive, isCompleted = isCompleted)
            StepConnector(visible = step < totalSteps, highlighted = isCompleted, modifier = Modifier.weight(1f))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StepCircle(step: Int, isActive: Boolean, isCompleted: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                when {
                    isActive -> MaterialTheme.colorScheme.primary
                    isCompleted -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isCompleted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StepConnector(visible: Boolean, highlighted: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(2.dp)
            .background(
                when {
                    !visible -> Color.Transparent
                    highlighted -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            ),
    )
}

@Preview(showBackground = true)
@Composable
private fun StepIndicatorStep2Preview() {
    NotificappTheme {
        StepIndicator(currentStep = 2, stepLabels = previewLabels, onStepClick = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.8f)
@Composable
private fun StepIndicatorStep3LargeFontDarkPreview() {
    NotificappTheme {
        StepIndicator(currentStep = 3, stepLabels = previewLabels, onStepClick = {})
    }
}

private val previewLabels = listOf("When", "Do", "Review")
