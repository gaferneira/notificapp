package dev.gaferneira.notificapp

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import javax.inject.Inject

/**
 * ViewModel for MainActivity to provide repository access.
 *
 * Used by the Notificapp composable to check flow state.
 */
@HiltViewModel
class MainViewModel @Inject constructor(val repository: SelectedAppRepository) : ViewModel()
