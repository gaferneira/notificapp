package dev.gaferneira.notificapp.features.onboarding.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.IconBadge
import dev.gaferneira.notificapp.core.ui.components.PagerDot
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.theme.LocalAppTokens
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEvent
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingHighlight
import dev.gaferneira.notificapp.features.onboarding.contract.onboardingHighlights

/**
 * Value Statement screen - First step of onboarding.
 * Shows the app value proposition with animated notification cards.
 */
@Composable
internal fun FirstStepContent(
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // BoxWithConstraints exposes the viewport height so the scrollable Column below can be
    // given `heightIn(min = viewport height)`. That preserves the original top-bar / hero+
    // carousel / footer `SpaceBetween` layout when the content fits on screen, while still
    // allowing scrolling (instead of clipping) on small screens or at large font scales.
    // A `Modifier.weight(1f)` child cannot be combined with `verticalScroll` because a
    // scrollable Column is measured with an infinite max height, so the inner content column
    // below no longer uses `weight` — its vertical position now comes purely from the outer
    // `SpaceBetween` arrangement, which matches the previous visual result at normal font size.
    BoxWithConstraints(modifier = modifier) {
        val minHeight = this.maxHeight
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = minHeight)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                FirstStepTopBar()
                Spacer(modifier = Modifier.height(8.dp))
                StepProgressIndicator(currentStep = 0, totalSteps = OnboardingContract.TOTAL_SETUP_STEPS)
            }

            Column(
                verticalArrangement = Arrangement.Center,
            ) {
                FirstStepHero()

                Spacer(modifier = Modifier.height(16.dp))

                // Swipeable carousel of the app's key capabilities
                HighlightCarousel()
            }

            FirstStepFooter(onEvent = onEvent)
        }
    }
}

/** Top section with the app logo and the "privacy first" badge. */
@Composable
private fun FirstStepTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Logo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.onboarding_brand_name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        // Privacy First badge
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = LocalAppTokens.current.badgeContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.onboarding_privacy_first),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Headline + description shown above the highlight carousel. */
@Composable
private fun FirstStepHero() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_headline),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.headlineLarge.fontSize * 1.2,
            modifier = Modifier.semantics { heading() },
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboarding_description),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalAppTokens.current.mutedOnBackground,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4,
        )
    }
}

/** Bottom section: CTA button, security footer, and privacy policy link. */
@Composable
private fun FirstStepFooter(onEvent: (UiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Button(
            onClick = { onEvent(UiEvent.OnGetStartedClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Text(
                text = stringResource(R.string.onboarding_get_started),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Security footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.onboarding_processed_locally),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        PrivacyPolicyLink()

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Swipeable carousel walking new users through the app's key capabilities.
 */
@Composable
private fun HighlightCarousel() {
    val pagerState = rememberPagerState(pageCount = { onboardingHighlights.size })
    val pageState = stringResource(
        R.string.onboarding_carousel_page_state,
        pagerState.currentPage + 1,
        onboardingHighlights.size,
    )

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth(),
    ) { page ->
        HighlightCard(
            highlight = onboardingHighlights[page],
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }

    // The dots are decorative: expose one "Page X of Y" state instead of N unlabeled boxes.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { stateDescription = pageState },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(onboardingHighlights.size) { page ->
            PagerDot(selected = page == pagerState.currentPage)
        }
    }
}

/**
 * A single carousel page: icon, title, description, and a mock trigger/action row.
 */
@Composable
private fun HighlightCard(
    highlight: OnboardingHighlight,
    modifier: Modifier = Modifier,
) {
    TonalCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(icon = highlight.icon)

            Text(
                text = stringResource(highlight.titleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(highlight.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4,
            )

            val styleState = remember { MutableStyleState(null) }
            Column(
                modifier = Modifier.styleable(styleState, NotificappStyles.innerPanelStyle),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HighlightInfoRow(
                    label = stringResource(R.string.onboarding_highlight_trigger_label),
                    value = stringResource(highlight.triggerLabelRes),
                )
                HighlightInfoRow(
                    label = stringResource(R.string.onboarding_highlight_action_label),
                    value = stringResource(highlight.actionLabelRes),
                )
            }
        }
    }
}

/** A `LABEL   value` row used inside [HighlightCard]'s trigger/action mock box. */
@Composable
private fun HighlightInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun FirstStepContentPreview() {
    NotificappTheme(dynamicColor = false) {
        FirstStepContent(onEvent = {})
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FirstStepContentPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        FirstStepContent(onEvent = {})
    }
}
