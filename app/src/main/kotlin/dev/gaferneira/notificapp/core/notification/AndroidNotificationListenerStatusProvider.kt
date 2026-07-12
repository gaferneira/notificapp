package dev.gaferneira.notificapp.core.notification

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.util.isNotificationListenerEnabled
import javax.inject.Inject

internal class AndroidNotificationListenerStatusProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationListenerStatusProvider {
    override fun isEnabled(): Boolean = isNotificationListenerEnabled(context)
}
