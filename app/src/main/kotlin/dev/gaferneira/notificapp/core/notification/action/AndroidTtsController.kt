package dev.gaferneira.notificapp.core.notification.action

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls on-device speech synthesis via [TextToSpeech]. `TextToSpeech`'s constructor is
 * asynchronous - `onInit` reports readiness sometime after it returns - so [speak] queues the
 * *latest* requested text if it arrives before init completes, rather than dropping it: a rule
 * match triggering this action is a one-off, so silently losing it would be worse than speaking
 * it a beat late. Only the newest pending text is kept - if several matches land before init
 * finishes, replaying every one of them back-to-back on cold start would be a confusing backlog,
 * whereas the newest match is the one most likely to still matter.
 *
 * Both [speak] and `TextToSpeech`'s construction/`onInit` callback run on [mainDispatcher] (init
 * requires a main-looper-bound context; per ADR 008 this is an injected dispatcher, never a
 * hardcoded platform default). Confining every read/write of [isReady] / [pendingText] to that
 * single thread is what makes the queue-latest handoff race-free without extra locking -
 * executors call [speak] from the IO dispatcher (see `ProcessNotificationUseCase`), so this
 * marshal is required, not just a formality.
 *
 * `@Singleton` and never torn down: unlike an alarm player, there is no natural "done" moment for
 * TTS - each call is fire-and-forget - so this instance is allowed to live for the process.
 */
@Singleton
class AndroidTtsController @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(DispatcherType.Main) private val mainDispatcher: CoroutineDispatcher,
) : TtsController {

    private var textToSpeech: TextToSpeech? = null
    private var isReady = false
    private var pendingText: String? = null

    override suspend fun speak(text: String) {
        if (text.isBlank()) return
        withContext(mainDispatcher) {
            val tts = textToSpeech ?: createTextToSpeech()
            if (isReady) {
                enqueue(tts, text)
            } else {
                pendingText = text
            }
        }
    }

    private fun createTextToSpeech(): TextToSpeech {
        lateinit var created: TextToSpeech
        created = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                applyDeviceLocale(created)
                isReady = true
                pendingText?.let { queuedText ->
                    pendingText = null
                    enqueue(created, queuedText)
                }
            } else {
                Timber.w("TextToSpeech initialization failed with status $status")
            }
        }
        textToSpeech = created
        return created
    }

    /** Defaults to the device locale; on unsupported/missing voice data, logs and falls back to the engine's own default rather than crashing or blocking speech entirely. */
    private fun applyDeviceLocale(tts: TextToSpeech) {
        val locale = Locale.getDefault()
        val result = tts.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Timber.w("TTS voice data unavailable for locale $locale, falling back to the engine default")
        }
    }

    /** `QUEUE_ADD` so multiple rule matches queue up and speak in turn instead of cutting each other off. */
    private fun enqueue(tts: TextToSpeech, text: String) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }
}
