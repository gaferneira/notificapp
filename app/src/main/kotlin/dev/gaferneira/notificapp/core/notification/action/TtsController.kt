package dev.gaferneira.notificapp.core.notification.action

/**
 * Narrow interface over on-device text-to-speech, kept separate so [ReadAloudActionExecutor] can
 * be unit tested without touching `android.speech.tts.TextToSpeech` - mirrors [TorchController]'s
 * seam over the camera torch.
 */
interface TtsController {
    /**
     * Speak [text] aloud. Implementations queue rather than interrupt in-flight speech (multiple
     * rule matches shouldn't cut each other off), and never throw for ordinary TTS-unavailable
     * conditions - those are logged and swallowed, not surfaced to the caller.
     */
    suspend fun speak(text: String)
}
