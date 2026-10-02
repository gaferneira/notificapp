package dev.gaferneira.notificapp.features.onboarding.contract

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gaferneira.notificapp.R

/** A single capability highlighted to new users in the onboarding carousel. */
internal data class OnboardingHighlight(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val triggerLabelRes: Int,
    @StringRes val actionLabelRes: Int,
)

internal val onboardingHighlights = listOf(
    OnboardingHighlight(
        icon = Icons.Default.AttachMoney,
        titleRes = R.string.onboarding_highlight_1_title,
        descriptionRes = R.string.onboarding_highlight_1_description,
        triggerLabelRes = R.string.onboarding_highlight_1_trigger_label,
        actionLabelRes = R.string.onboarding_highlight_1_action_label,
    ),
    OnboardingHighlight(
        icon = Icons.Default.Bolt,
        titleRes = R.string.onboarding_highlight_2_title,
        descriptionRes = R.string.onboarding_highlight_2_description,
        triggerLabelRes = R.string.onboarding_highlight_2_trigger_label,
        actionLabelRes = R.string.onboarding_highlight_2_action_label,
    ),
    OnboardingHighlight(
        icon = Icons.Default.FilterAlt,
        titleRes = R.string.onboarding_highlight_3_title,
        descriptionRes = R.string.onboarding_highlight_3_description,
        triggerLabelRes = R.string.onboarding_highlight_3_trigger_label,
        actionLabelRes = R.string.onboarding_highlight_3_action_label,
    ),
    OnboardingHighlight(
        icon = Icons.AutoMirrored.Filled.Send,
        titleRes = R.string.onboarding_highlight_4_title,
        descriptionRes = R.string.onboarding_highlight_4_description,
        triggerLabelRes = R.string.onboarding_highlight_4_trigger_label,
        actionLabelRes = R.string.onboarding_highlight_4_action_label,
    ),
    OnboardingHighlight(
        icon = Icons.Default.Notifications,
        titleRes = R.string.onboarding_highlight_5_title,
        descriptionRes = R.string.onboarding_highlight_5_description,
        triggerLabelRes = R.string.onboarding_highlight_5_trigger_label,
        actionLabelRes = R.string.onboarding_highlight_5_action_label,
    ),
)
