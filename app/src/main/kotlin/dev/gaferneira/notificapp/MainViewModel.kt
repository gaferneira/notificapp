package dev.gaferneira.notificapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for MainActivity, owning the top-level app-flow state (onboarding vs. app selection
 * vs. main app).
 *
 * The repository is private - composables never touch it directly (per CLAUDE.md's "NEVER access
 * repositories directly from Composables"); they observe [appFlowState] and call
 * [recheckFlowState] instead.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: SelectedAppRepository,
) : ViewModel() {

    private val _appFlowState = MutableStateFlow<AppFlowState?>(null) // null = still checking
    val appFlowState: StateFlow<AppFlowState?> = _appFlowState.asStateFlow()

    /**
     * Re-derive [appFlowState]. [isListenerEnabled] is the notification-listener permission
     * state, resolved by the caller (a `Context`-dependent check) so this ViewModel stays
     * context-free.
     */
    fun recheckFlowState(isListenerEnabled: Boolean) {
        viewModelScope.launch {
            _appFlowState.value = determineAppFlowState(isListenerEnabled)
        }
    }

    private suspend fun determineAppFlowState(isListenerEnabled: Boolean): AppFlowState {
        if (!isListenerEnabled) {
            return AppFlowState.ONBOARDING
        }

        // getAllApps() already wraps failures in Result.failure (ADR 006) rather than throwing,
        // so a failed/empty read and "no apps selected" fall to the same default here.
        val hasApps = repository.getAllApps().getOrNull()?.isNotEmpty() == true
        return if (hasApps) AppFlowState.MAIN_APP else AppFlowState.APP_SELECTION
    }
}
