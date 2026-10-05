package dev.gaferneira.notificapp.features.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.AppLinks

/**
 * Progress bars for the initial-setup flow, drawn the same way as App Selection's indicator so
 * the three steps (value statement, permission, app selection) read as one flow.
 *
 * @param currentStep Zero-based index of the active step.
 * @param totalSteps Total number of steps in the flow.
 */
@Composable
internal fun StepProgressIndicator(currentStep: Int, totalSteps: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_step_indicator_cd, currentStep + 1, totalSteps)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val color = if (index == currentStep) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            }
            val width = if (index == currentStep) 24.dp else 8.dp
            Box(
                modifier = Modifier
                    .clearAndSetSemantics {}
                    .padding(horizontal = 4.dp)
                    .size(width = width, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** Privacy Policy link: a [TextButton] so it has button semantics and a 48dp touch target. */
@Composable
internal fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    TextButton(
        onClick = { uriHandler.openUri(AppLinks.PRIVACY_POLICY_URL) },
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.onboarding_read_privacy_policy),
            style = MaterialTheme.typography.labelMedium,
            textDecoration = TextDecoration.Underline,
        )
    }
}
