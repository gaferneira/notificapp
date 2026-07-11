package dev.gaferneira.notificapp.features.alarm.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmRequest
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmService
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmStateHolder
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.toBrush
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundPreset
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_MAX_COUNT
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
        val snoozeEnabled = intent.getBooleanExtra(EXTRA_SNOOZE_ENABLED, DEFAULT_ALARM_SNOOZE_ENABLED)
        val snoozeDurationMinutes = intent.getIntExtra(
            EXTRA_SNOOZE_DURATION_MINUTES,
            DEFAULT_ALARM_SNOOZE_DURATION_MINUTES,
        )
        val snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0)
        val snoozeMaxCount = intent.getIntExtra(EXTRA_SNOOZE_MAX_COUNT, DEFAULT_ALARM_SNOOZE_MAX_COUNT)
        val backgroundType = AlarmBackgroundType.fromName(intent.getStringExtra(EXTRA_BACKGROUND_TYPE))
        val backgroundPresetId = intent.getStringExtra(EXTRA_BACKGROUND_PRESET_ID)
        val backgroundImageUri = intent.getStringExtra(EXTRA_BACKGROUND_IMAGE_URI)
        val backgroundImageIsDark = intent.getBooleanExtra(EXTRA_BACKGROUND_IMAGE_IS_DARK, DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK)

        setContent {
            NotificappTheme {
                // When the alarm stops from anywhere (notification action, or a superseding ring),
                // don't linger over a dead alarm.
                val ringing by alarmStateHolder.isRinging.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) {
                    if (!ringing) finish()
                }

                AlarmCallScreen(
                    ui = AlarmCallUi(
                        content = AlarmUi(
                            title = title.ifBlank { stringResource(R.string.alarm_notification_title) },
                            text = text,
                            appName = appName,
                        ),
                        snooze = AlarmSnoozeUi(
                            enabled = snoozeEnabled,
                            durationMinutes = snoozeDurationMinutes,
                            count = snoozeCount,
                            maxCount = snoozeMaxCount,
                        ),
                        background = AlarmBackgroundUi(
                            type = backgroundType,
                            presetId = backgroundPresetId,
                            imageUri = backgroundImageUri,
                            imageIsDark = backgroundImageIsDark,
                        ),
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
        private const val EXTRA_SNOOZE_ENABLED = "extra_snooze_enabled"
        private const val EXTRA_SNOOZE_DURATION_MINUTES = "extra_snooze_duration_minutes"
        private const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
        private const val EXTRA_SNOOZE_MAX_COUNT = "extra_snooze_max_count"
        private const val EXTRA_BACKGROUND_TYPE = "extra_background_type"
        private const val EXTRA_BACKGROUND_PRESET_ID = "extra_background_preset_id"
        private const val EXTRA_BACKGROUND_IMAGE_URI = "extra_background_image_uri"
        private const val EXTRA_BACKGROUND_IMAGE_IS_DARK = "extra_background_image_is_dark"

        /** Launch intent for the full-screen alarm, carrying the display content of [request]. */
        fun intent(context: Context, request: AlarmRequest): Intent = Intent(context, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_TITLE, request.title)
            .putExtra(EXTRA_TEXT, request.text)
            .putExtra(EXTRA_APP_NAME, request.appName)
            .putExtra(EXTRA_SNOOZE_ENABLED, request.snoozeEnabled)
            .putExtra(EXTRA_SNOOZE_DURATION_MINUTES, request.snoozeDurationMinutes)
            .putExtra(EXTRA_SNOOZE_COUNT, request.snoozeCount)
            .putExtra(EXTRA_SNOOZE_MAX_COUNT, request.snoozeMaxCount)
            .putExtra(EXTRA_BACKGROUND_TYPE, request.backgroundType.name)
            .putExtra(EXTRA_BACKGROUND_PRESET_ID, request.backgroundPresetId)
            .putExtra(EXTRA_BACKGROUND_IMAGE_URI, request.backgroundImageUri)
            .putExtra(EXTRA_BACKGROUND_IMAGE_IS_DARK, request.backgroundImageIsDark)
    }
}

/** Display content for the full-screen alarm, mirrored from the triggering notification. */
private data class AlarmUi(val title: String, val text: String, val appName: String)

/** Snooze button state for the full-screen alarm, mirrored from the ringing [AlarmRequest]. */
private data class AlarmSnoozeUi(
    val enabled: Boolean,
    val durationMinutes: Int,
    val count: Int,
    val maxCount: Int,
) {
    val isAvailable: Boolean get() = enabled && count < maxCount
}

/** Full-screen background source, mirrored from the ringing [AlarmRequest]. */
private data class AlarmBackgroundUi(
    val type: AlarmBackgroundType,
    val presetId: String?,
    val imageUri: String?,
    val imageIsDark: Boolean,
)

/** Ring-content bundle for [AlarmCallScreen], grouped to stay under detekt's `LongParameterList`. */
private data class AlarmCallUi(
    val content: AlarmUi,
    val snooze: AlarmSnoozeUi,
    val background: AlarmBackgroundUi,
)

@Composable
private fun AlarmCallScreen(
    ui: AlarmCallUi,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = ui.content
    val snooze = ui.snooze
    val background = ui.background
    // Failed image loads (revoked permission, deleted file) fall back to the same theme background
    // as AlarmBackgroundType.NONE, so the alarm stays legible and dismissible/snoozable regardless.
    var imageLoadFailed by remember(background.imageUri) { mutableStateOf(false) }
    val useThemeBackground = background.type == AlarmBackgroundType.NONE ||
        (background.type == AlarmBackgroundType.IMAGE && (background.imageUri == null || imageLoadFailed))

    val contentColors = rememberAlarmContentColors(background, imageLoadFailed)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = if (useThemeBackground) MaterialTheme.colorScheme.background else Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AlarmCallBackgroundLayer(background = background, imageLoadFailed = imageLoadFailed, onImageError = { imageLoadFailed = true })

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 32.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AlarmCallContent(ui = content, titleColor = contentColors.title, secondaryTextColor = contentColors.secondary)
                AlarmCallActions(snooze = snooze, onDismiss = onDismiss, onSnooze = onSnooze)
            }
        }
    }
}

/** Background-layer visuals for [AlarmCallScreen], split out to keep it under detekt's `LongMethod`/`CyclomaticComplexMethod` thresholds. */
@Composable
private fun AlarmCallBackgroundLayer(
    background: AlarmBackgroundUi,
    imageLoadFailed: Boolean,
    onImageError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (background.type == AlarmBackgroundType.PRESET) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AlarmBackgroundPreset.fromId(background.presetId).toBrush()),
        )
    } else if (background.type == AlarmBackgroundType.IMAGE && background.imageUri != null && !imageLoadFailed) {
        SubcomposeAsyncImage(
            model = background.imageUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { onImageError() },
            modifier = modifier.fillMaxSize(),
        )
        // Scrim for text contrast over an arbitrary user-picked image.
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
        )
    }
}

/** Title/secondary text colors resolved for [AlarmCallContent], grouped to stay under detekt's `LongParameterList`. */
private data class AlarmContentColors(val title: Color, val secondary: Color)

/**
 * Resolves the title/secondary text colors for the ringing alarm's content, based on whether the
 * rendered background reads as visually dark (see [AlarmBackgroundPreset.isDark] KDoc for why: a
 * dark preset like MIDNIGHT would otherwise get dark `onSurface` text from a light system theme).
 * `null` ("no opinion") falls back to following the system theme via `onSurface`/`onSurfaceVariant`.
 */
@Composable
private fun rememberAlarmContentColors(background: AlarmBackgroundUi, imageLoadFailed: Boolean): AlarmContentColors {
    val contentIsDark: Boolean? = when {
        background.type == AlarmBackgroundType.PRESET -> AlarmBackgroundPreset.fromId(background.presetId).isDark
        background.type == AlarmBackgroundType.IMAGE && background.imageUri != null && !imageLoadFailed ->
            background.imageIsDark
        else -> null
    }
    return when (contentIsDark) {
        true -> AlarmContentColors(title = Color.White, secondary = Color.White.copy(alpha = 0.75f))
        false -> AlarmContentColors(title = Color.Black, secondary = Color.Black.copy(alpha = 0.75f))
        null -> AlarmContentColors(
            title = MaterialTheme.colorScheme.onSurface,
            secondary = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val SCRIM_ALPHA = 0.35f

@Composable
private fun AlarmCallContent(
    ui: AlarmUi,
    titleColor: Color,
    secondaryTextColor: Color,
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
                color = secondaryTextColor,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = ui.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = titleColor,
        )
        if (ui.text.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = ui.text,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = secondaryTextColor,
            )
        }
    }
}

@Composable
private fun AlarmCallActions(
    snooze: AlarmSnoozeUi,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (snooze.isAvailable) {
            OutlinedButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(
                    stringResource(R.string.alarm_action_snooze_with_duration, snooze.durationMinutes),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
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
            ui = AlarmCallUi(
                content = AlarmUi(title = "Payment received", text = "You got \$50 from Alex", appName = "Bank"),
                snooze = AlarmSnoozeUi(enabled = true, durationMinutes = 5, count = 0, maxCount = 3),
                background = AlarmBackgroundUi(type = AlarmBackgroundType.NONE, presetId = null, imageUri = null, imageIsDark = true),
            ),
            onDismiss = {},
            onSnooze = {},
        )
    }
}
