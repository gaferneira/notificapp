package dev.gaferneira.notificapp.features.alarm

import android.content.Context
import android.content.Intent
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmRequest
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmUiIntentFactory
import dev.gaferneira.notificapp.features.alarm.ui.AlarmActivity
import javax.inject.Inject

class AlarmUiIntentFactoryImpl @Inject constructor() : AlarmUiIntentFactory {
    override fun createFullScreenIntent(context: Context, request: AlarmRequest): Intent = AlarmActivity.intent(context, request)
}
