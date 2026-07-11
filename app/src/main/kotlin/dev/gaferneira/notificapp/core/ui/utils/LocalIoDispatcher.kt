package dev.gaferneira.notificapp.core.ui.utils

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers

/**
 * IO dispatcher for the rare UI-layer suspend helpers that do their own file/ContentResolver I/O
 * (e.g. sharing a rule as a file, reading a picked file) instead of going through a ViewModel.
 * Defaults to [Dispatchers.IO] in production; override with [androidx.compose.runtime.CompositionLocalProvider]
 * in tests to inject a deterministic test dispatcher (per ADR 008's testability intent, applied at
 * the UI boundary since ADR 008 itself only covers ViewModels/repositories).
 */
val LocalIoDispatcher = staticCompositionLocalOf { Dispatchers.IO }
