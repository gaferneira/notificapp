package dev.gaferneira.notificapp.core.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class MviViewModel<UiState, UiEvent, UiEffect>(
    initialState: UiState,
    private val effectDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState by lazy { _uiState.asStateFlow() }

    private val _effect = Channel<UiEffect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    abstract fun onEvent(event: UiEvent)

    protected fun setState(reducer: UiState.() -> UiState) {
        _uiState.update { it.reducer() }
    }

    /**
     * Sends a one-off UI effect to be consumed by the UI layer.
     * Effects are queued and delivered sequentially, ensuring multiple simultaneous effects are handled.
     * Dispatched on [effectDispatcher] (defaults to `Dispatchers.Main.immediate` in production,
     * per ADR 008) to ensure the effect reaches the channel as quickly as possible on the main thread.
     * @param effect The UiEffect to be sent.
     */
    protected fun sendEffect(effect: UiEffect) {
        viewModelScope.launch(effectDispatcher) { _effect.send(effect) }
    }

    override fun onCleared() {
        super.onCleared()
        _effect.close()
    }
}
