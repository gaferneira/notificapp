package dev.gaferneira.notificapp.features.onboarding.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEffect
import dev.gaferneira.notificapp.features.onboarding.contract.OnboardingContract.UiEvent
import dev.gaferneira.notificapp.features.onboarding.viewmodel.OnboardingViewModel

/**
 * Onboarding screen with two steps (App Selection, the third setup step, is its own screen):
 * 1. Value Statement - Shows app value proposition
 * 2. Permission Explanation - Explains and requests notification access
 *
 * @param onOpenNotificationSettings Callback to open system notification settings
 * @param modifier Modifier for the screen
 * @param viewModel ViewModel for state management
 */
@Composable
fun OnboardingScreen(
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Handle effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.OpenNotificationSettings -> onOpenNotificationSettings()
        }
    }

    // Check permission when screen resumes (user returning from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(UiEvent.CheckPermission)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Step 2 is a sub-state of this single nav entry, so system/predictive back would otherwise
    // pop the entry and exit the app. Route it to the in-screen "previous step" instead.
    BackHandler(enabled = uiState.currentStep == OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION) {
        viewModel.onEvent(UiEvent.OnBackClicked)
    }

    OnboardingScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingScreenContent(
    uiState: OnboardingContract.UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize().systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Animated content between steps
            AnimatedContent(
                targetState = uiState.currentStep,
                transitionSpec = {
                    if (targetState == OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION) {
                        // Going forward: slide in from right, slide out to left
                        (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> -width } + fadeOut())
                    } else {
                        // Going back: slide in from left, slide out to right
                        (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                            (slideOutHorizontally { width -> width } + fadeOut())
                    }
                },
                label = "onboarding_transition",
            ) { step ->
                when (step) {
                    OnboardingContract.OnboardingStep.VALUE_STATEMENT -> {
                        FirstStepContent(
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION -> {
                        PermissionExplanationContent(
                            showPermissionDeniedHint = uiState.showPermissionDeniedHint,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

// Preview
@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun OnboardingScreenValueStatementPreview() {
    NotificappTheme {
        OnboardingScreenContent(
            uiState = OnboardingContract.UiState(
                currentStep = OnboardingContract.OnboardingStep.VALUE_STATEMENT,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun OnboardingScreenPermissionExplanationPreview() {
    NotificappTheme {
        OnboardingScreenContent(
            uiState = OnboardingContract.UiState(
                currentStep = OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun OnboardingScreenPermissionDeniedPreview() {
    NotificappTheme {
        OnboardingScreenContent(
            uiState = OnboardingContract.UiState(
                currentStep = OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION,
                showPermissionDeniedHint = true,
            ),
            onEvent = {},
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OnboardingScreenPermissionDeniedPreviewDark() {
    NotificappTheme {
        OnboardingScreenContent(
            uiState = OnboardingContract.UiState(
                currentStep = OnboardingContract.OnboardingStep.PERMISSION_EXPLANATION,
                showPermissionDeniedHint = true,
            ),
            onEvent = {},
        )
    }
}
