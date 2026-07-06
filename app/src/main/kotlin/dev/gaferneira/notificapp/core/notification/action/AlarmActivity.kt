package dev.gaferneira.notificapp.core.notification.action

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import javax.inject.Inject

/**
 * Full-screen, phone-call-style UI for a ringing alarm. Raised by [AlarmService]'s full-screen
 * intent over the lock screen. Displays the triggering notification's content with large Dismiss
 * and Snooze controls that drive the same [AlarmService] stop path; the ring itself is owned by the
 * service (ADR 013), never by this Activity.
 */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {

    @Inject
    lateinit var alarmStateHolder: AlarmStateHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty()

        setContent {
            NotificappTheme {
                // When the alarm stops from anywhere (notification action, or a superseding ring),
                // don't linger over a dead alarm.
                val ringing by alarmStateHolder.isRinging.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) {
                    if (!ringing) finish()
                }

                AlarmCallScreen(
                    ui = AlarmUi(
                        title = title.ifBlank { stringResource(R.string.alarm_notification_title) },
                        text = text,
                        appName = appName,
                    ),
                    onDismiss = {
                        startService(AlarmService.dismissIntent(this@AlarmActivity))
                        finish()
                    },
                    onSnooze = {
                        startService(AlarmService.snoozeIntent(this@AlarmActivity))
                        finish()
                    },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
    }

    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TEXT = "extra_text"
        private const val EXTRA_APP_NAME = "extra_app_name"

        /** Launch intent for the full-screen alarm, carrying the display content of [request]. */
        fun intent(context: Context, request: AlarmRequest): Intent = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_TITLE, request.title)
            .putExtra(EXTRA_TEXT, request.text)
            .putExtra(EXTRA_APP_NAME, request.appName)
    }
}

/** Display content for the full-screen alarm, mirrored from the triggering notification. */
private data class AlarmUi(val title: String, val text: String, val appName: String)

@Composable
private fun AlarmCallScreen(
    ui: AlarmUi,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            AlarmCallContent(ui = ui)
            AlarmCallActions(onDismiss = onDismiss, onSnooze = onSnooze)
        }
    }
}

@Composable
private fun AlarmCallContent(
    ui: AlarmUi,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(72.dp),
        )
        Spacer(Modifier.height(24.dp))
        if (ui.appName.isNotBlank()) {
            Text(
                text = ui.appName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = ui.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (ui.text.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = ui.text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlarmCallActions(
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(stringResource(R.string.alarm_action_snooze), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(stringResource(R.string.alarm_action_dismiss), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AlarmCallScreenPreview() {
    NotificappTheme {
        AlarmCallScreen(
            ui = AlarmUi(title = "Payment received", text = "You got \$50 from Alex", appName = "Bank"),
            onDismiss = {},
            onSnooze = {},
        )
    }
}
