# ADR 014 – Internationalization Strategy: String Resources + In-App Locale Switch

## Status
Accepted

## Context
Notificapp already had ~150 strings in `app/src/main/res/values/strings.xml`, but no second locale ever shipped, and no locale-switching infrastructure existed (no `AppCompatDelegate` usage, no `LocaleConfig`, no language picker). `docs/roadmap.md` listed "Full internationalization" as a planned, unchecked item.

The onboarding screens (`features/onboarding/`) were a recent, ground-up rewrite and had drifted fully off the resource convention — every string was a hardcoded Kotlin literal, disconnected from `strings.xml`. Bringing that feature back in line, and adding the first non-English locale (Spanish), was the trigger for writing this decision down: multiple contributors will touch string-heavy screens after this, and the pattern needs to be explicit rather than re-derived per PR.

`minSdk = 26` (Android 8), well below Android 13 (API 33)'s native per-app language settings (`LocaleConfig` + system "App languages" screen). A `LocaleConfig`-only approach would leave every user below API 33 with no way to override the device language for just this app.

## Decision

1. **All user-facing text goes through `strings.xml` / `stringResource(...)`.** No literal `Text("...")` or `contentDescription = "..."` in Composables. This was already an unenforced bullet in `docs/ARCHITECTURE.md`; this ADR makes it the recorded rule and ties it to a concrete checklist (`docs/guides/common-patterns.md`).
   - Exception: brand wordmarks/logos (e.g. "NOTIFICAPP") and values that are meant to look like literal identifiers (e.g. an illustrative package name like `com.bank.app`) stay as Kotlin literals — they aren't prose and shouldn't be translated.
   - Data classes that hold UI copy outside a `@Composable` context (e.g. `OnboardingHighlight`) store `@StringRes Int` fields instead of `String`, and resolve them via `stringResource(...)` at the point they're rendered.

2. **Locale switching uses `AppCompatDelegate.setApplicationLocales()`**, not `LocaleConfig` alone. This AndroidX Core/AppCompat API backports per-app language switching down to API 21 (well under our `minSdk = 26`), so every supported device gets the in-app picker, not just Android 13+. `androidx.appcompat` was already a dependency — no new library.
   - **The backport path only applies below API 33 if the host Activity has an active `AppCompatDelegate`.** `AppCompatDelegate.setApplicationLocales()` on API < 33 works by calling `applyLocalesToActiveDelegates()`, which iterates the process-wide set of currently-created delegates — populated only by Activities that extend `AppCompatActivity` (or otherwise construct their own `AppCompatDelegate`). A plain `ComponentActivity` never registers one, so the call silently becomes a no-op for that Activity's resources on API 26-32. Every Activity that renders user-facing localized strings — currently `MainActivity` and `AlarmActivity` — must extend `AppCompatActivity`, and its theme must derive from an AppCompat-compatible parent (`Theme.AppCompat.*`; see `app/src/main/res/values/themes.xml` and `values-night/themes.xml`). This is a wiring point every future entry point (a new top-level Activity) must remember, the same way decision #2's manual `MainActivity` + `LocaleController` wiring already is (see Consequences).

3. **The selected language is persisted via the existing `UserPreferences` DataStore blob**, as a new `AppLanguage` field (`SYSTEM | EN | ES`), mirroring `ThemePreference` exactly: same enum shape, same repository `observe*()`/`set*()` pair, same single-JSON-blob storage (`core/data/preferences/UserPreferencesLocalDataSource`). No new DataStore key, no new storage mechanism — consistency with the rest of the app's preferences was preferred over a dedicated `SharedPreferences` locale store.

4. **English is the source of truth; Spanish is the second locale**, in `values-es/strings.xml`. Every string added or changed gets both languages updated in the same PR — the same "keep it in sync in the same PR" discipline CLAUDE.md already applies to `docs/capabilities.md`.

5. **Format strings use positional specifiers** (`%1$s`, `%1$d`, already the convention — see `alarm_snooze_subtitle`), never string concatenation, since word order differs between English and Spanish. **Plurals use `<plurals>`/`quantityString`** rather than manual singular/plural branching in Kotlin, once a plural-sensitive string is introduced (none yet in the onboarding pass).

6. **Rollout is phased, not all-at-once.** The onboarding pass (this change) ships the infrastructure — string extraction pattern, `AppLanguage` preference, `AppCompatDelegate` wiring, Settings language picker — and fully translates onboarding itself. The other ~150 pre-existing strings (Rule Editor, Settings, Alarm, Filters, etc.) are extracted-and-translated incrementally as each screen is next touched, using onboarding as the reference implementation. This keeps each PR reviewable and avoids a single giant translation diff with no functional testing behind most of it.

## Consequences

**Positive:**
- Every future screen has a concrete, copy-pasteable reference (onboarding) instead of a written-only convention.
- The in-app picker works across the entire supported OS range (API 26+), not just Android 13+.
- Locale preference reuses proven, tested storage/repository plumbing — no new persistence layer to validate.
- Adding a third language later is additive: a new `values-XX/strings.xml` plus one new `AppLanguage` enum entry, no architecture change.

**Negative:**
- Until the phased rollout completes, the app is in a mixed state — some screens resource-backed and translated, most still hardcoded-English-only. `docs/roadmap.md` tracks this as an open remainder, not a hidden gap.
- `AppLanguage` fields resolved via `@StringRes Int` (as in `OnboardingHighlight`) push string resolution out of plain data and into the Composable layer — a minor loss of "just read the data class" simplicity, accepted because it's the only way to keep that data translatable without making it composable itself.
- `AppCompatDelegate.setApplicationLocales()` must be applied on the main thread and reacted to on every preference change (not just app cold start) — a manual wiring point (`MainActivity` + `LocaleController`) that has to be remembered for any future entry point that reads `UserPreferences` before the UI is shown.
- The API 26-32 backport additionally requires every such entry-point Activity to extend `AppCompatActivity` with an AppCompat-compatible theme (decision #2) — a second wiring point alongside the `LocaleController` call itself. A new top-level/launcher Activity that extends plain `ComponentActivity` will build and run fine, but the language switch will silently no-op for it below API 33.
