package dev.gaferneira.notificapp.core.notification.action.alarm

import android.content.Context
import android.content.Intent

/**
 * Builds the full-screen intent for the ringing alarm UI. Implemented in `features/alarm` so
 * `core/notification` never depends on `core/ui` or Compose.
 */
interface AlarmUiIntentFactory {
    fun createFullScreenIntent(context: Context, request: AlarmRequest): Intent
}
