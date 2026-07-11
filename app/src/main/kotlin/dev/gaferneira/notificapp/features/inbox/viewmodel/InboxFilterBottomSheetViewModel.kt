package dev.gaferneira.notificapp.features.inbox.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

/**
 * ViewModel for the InboxFilterBottomSheet.
 *
 * Manages the state for filtering notifications by app and status.
 * Automatically loads available apps from the repository.
 */
@HiltViewModel
class InboxFilterBottomSheetViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : MviViewModel<InboxFilterContract.UiState, InboxFilterContract.UiEvent, InboxFilterContract.UiEffect>(
    InboxFilterContract.UiState(),
) {

    init {
        // Load available apps from repository
        viewModelScope.launch {
            notificationRepository.observeAppsWithNotifications()
                .collectLatest { apps ->
                    setState {
                        copy(availableApps = apps)
                    }
                }
        }
    }

    override fun onEvent(event: InboxFilterContract.UiEvent) {
        when (event) {
            is InboxFilterContract.UiEvent.Init -> initWithFilter(
                event.currentSelectedApps,
                event.currentStatusFilter,
            )
            is InboxFilterContract.UiEvent.OnAppToggle -> toggleApp(event.appPackageName)
            is InboxFilterContract.UiEvent.OnStatusChange -> changeStatus(event.status)
            is InboxFilterContract.UiEvent.OnClearAll -> clearAllFilters()
            is InboxFilterContract.UiEvent.OnApply -> applyFilters()
            is InboxFilterContract.UiEvent.OnDismiss -> dismiss()
        }
    }

    /**
     * Initialize the ViewModel with the current filter state.
     */
    fun initialize(
        currentSelectedApps: List<String>,
        currentStatusFilter: Status = Status.ALL,
    ) {
        onEvent(InboxFilterContract.UiEvent.Init(currentSelectedApps, currentStatusFilter))
    }

    private fun initWithFilter(
        selectedApps: List<String>,
        statusFilter: Status,
    ) {
        setState {
            copy(
                selectedApps = selectedApps.toSet(),
                statusFilter = statusFilter,
                hasActiveFilters = selectedApps.isNotEmpty() || statusFilter != Status.ALL,
            )
        }
    }

    private fun toggleApp(appPackageName: String) {
        setState {
            val newSelection = if (selectedApps.contains(appPackageName)) {
                selectedApps - appPackageName
            } else {
                selectedApps + appPackageName
            }
            copy(
                selectedApps = newSelection,
                hasActiveFilters = newSelection.isNotEmpty() || statusFilter != Status.ALL,
            )
        }
    }

    private fun changeStatus(status: Status) {
        setState {
            copy(
                statusFilter = status,
                hasActiveFilters = status != Status.ALL || selectedApps.isNotEmpty(),
            )
        }
    }

    private fun clearAllFilters() {
        setState {
            copy(
                selectedApps = emptySet(),
                statusFilter = Status.ALL,
                hasActiveFilters = false,
            )
        }
    }

    private fun applyFilters() {
        val currentState = uiState.value
        sendEffect(
            InboxFilterContract.UiEffect.ApplyFilter(
                selectedApps = currentState.selectedApps.toList(),
                statusFilter = currentState.statusFilter,
            ),
        )
    }

    private fun dismiss() {
        sendEffect(InboxFilterContract.UiEffect.Dismiss)
    }
}
