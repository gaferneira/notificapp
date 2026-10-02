package dev.gaferneira.notificapp.core.ui.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dev.gaferneira.notificapp.domain.model.preferences.AppLanguage

/**
 * Applies the user's [AppLanguage] preference to the app's per-app locale via
 * [AppCompatDelegate], so Android re-composes every string resource lookup in the chosen
 * language without an Activity restart.
 *
 * Must be called on the main thread - [AppCompatDelegate.setApplicationLocales] is not
 * thread-safe.
 *
 * Idempotent and non-destructive for [AppLanguage.SYSTEM]: since API 33+ makes the app
 * discoverable in Settings > Apps > App languages (`android:localeConfig`), a non-empty
 * [AppCompatDelegate.getApplicationLocales] can already reflect a locale the user picked
 * from that OS screen rather than from this app's own Settings. On the *first* call in a
 * process where the stored preference is `SYSTEM`, an already-non-empty locale is left
 * untouched instead of being reset to empty. Any later `SYSTEM` call in the same process is
 * treated as a live, explicit in-app choice (e.g. the user switching away from a previously
 * chosen `EN`/`ES` back to `SYSTEM`) and does reset the locale, since by then this object has
 * already applied at least one language itself.
 *
 * This distinction is tracked with an in-memory, per-process flag only - no new
 * [AppLanguage]/preference field. It cannot tell apart "OS-set locale, preference never
 * touched in-app" from "user explicitly set SYSTEM in-app in a *previous* process" (the
 * latter would also look like a non-empty-locale-at-cold-start if the OS itself persisted a
 * non-default locale some other way); [AppCompatDelegate] persisting its own last-applied
 * state (including an explicit empty) is what keeps that case correct in practice, since a
 * prior explicit `SYSTEM` apply already leaves the delegate's stored locale empty for the
 * next cold start to read back.
 */
object LocaleController {

    @Volatile
    private var hasAppliedInProcess = false

    /**
     * Applies [language] as the app's current locale.
     */
    fun applyLanguage(language: AppLanguage) {
        if (language == AppLanguage.SYSTEM) {
            val current = AppCompatDelegate.getApplicationLocales()
            if (current.isEmpty) {
                // Already following the system locale - nothing to do.
                return
            }
            if (!hasAppliedInProcess) {
                // Cold start with a non-empty locale we never set ourselves this process:
                // respect whatever set it (most likely the OS "App languages" screen) rather
                // than clobbering it back to empty.
                hasAppliedInProcess = true
                return
            }
        }

        hasAppliedInProcess = true
        val localeList = when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.ES -> LocaleListCompat.forLanguageTags("es")
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }
}
