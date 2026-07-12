package dev.gaferneira.notificapp.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * A composable function to collect one-off UI effects from a Flow in a lifecycle-aware manner.
 * This reduces boilerplate in individual screens
 *
 * @param effectFlow The Flow of UiEffect to collect from the ViewModel.
 * @param lifecycleOwner The LifecycleOwner to observe (defaults to LocalLifecycleOwner.current).
 * @param minActiveState The minimum Lifecycle.State required for collection to be active (defaults to STARTED).
 * @param onEffect A suspend lambda to handle each emitted UiEffect.
 */
@Stable
@Composable
fun <UiEffect> CollectOneOffEffects(
    effectFlow: Flow<UiEffect>,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    onEffect: suspend (UiEffect) -> Unit,
) {
    LaunchedEffect(effectFlow, lifecycleOwner, minActiveState) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(minActiveState) {
            effectFlow.collect { effect ->
                try {
                    onEffect(effect)
                } catch (e: Exception) {
                    Timber.e(e, "Error handling UI effect: $effect")
                }
            }
        }
    }
}
