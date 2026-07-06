## Why

Iteration 1 (`persistent-stoppable-alarm`) made `CREATE_ALARM` ring until dismissed, surfaced through an ongoing notification with Dismiss/Snooze. That works, but when the phone is locked or the screen is off, the alarm is easy to miss — it's just a notification. A real alarm should command the screen the way an incoming phone call does: a full-screen UI that lights up the device, shows what fired, and offers large, unmissable Dismiss/Snooze controls.

This is iteration 2 of 2. It layers a full-screen, call-style presentation on top of iteration 1's `AlarmService`; the service, playback, and stop/snooze paths are unchanged.

## What Changes

- A new full-screen, phone-call-style **`AlarmActivity`** (Compose) that shows the triggering notification's title / content / app name with large Dismiss and Snooze buttons.
- The ongoing alarm notification (from iteration 1) gains a **full-screen intent** (`setFullScreenIntent`) targeting `AlarmActivity`, so the system launches it over the lock screen / when the screen is off, and falls back to the existing heads-up notification otherwise.
- `AlarmActivity` shows **over the lock screen and turns the screen on** (`setShowWhenLocked` / `setTurnScreenOn`), without requiring the user to unlock.
- Dismiss / Snooze from the full-screen UI drive the **same `AlarmService` stop path** (`ACTION_DISMISS` / `ACTION_SNOOZE`) — no duplicate stop logic.
- The full-screen UI and the ongoing notification stay in sync: when the alarm stops (from either surface, or a new ring), the open `AlarmActivity` finishes itself, via a small shared ringing-state holder.
- **`USE_FULL_SCREEN_INTENT` permission handling**: request/declare it, and because Android 14+ treats it as a *restricted* permission not auto-granted to this app category, detect when it is not granted and degrade gracefully to the iteration-1 notification (optionally guide the user to grant it when configuring the alarm).

Out of scope: changing the ring/stop/snooze behavior itself, per-rule full-screen toggles, and any alarm scheduling.

## Capabilities

### New Capabilities
- `alarm-fullscreen-ui`: The full-screen, call-style presentation of a ringing alarm — launching `AlarmActivity` via a full-screen intent, showing it over the lock screen with the triggering notification's content and large Dismiss/Snooze controls, driving the shared `AlarmService` stop path, keeping the full-screen surface and the ongoing notification in sync, and degrading to the notification when `USE_FULL_SCREEN_INTENT` is unavailable.

### Modified Capabilities
<!-- None. Iteration 1's alarm-playback delta spec is not yet archived into openspec/specs/, so this change is additive: it consumes AlarmService/AlarmController/AlarmRequest without restating or altering their requirements. The full-screen intent attachment on the ongoing notification is specified under the new alarm-fullscreen-ui capability. -->

## Impact

- **Code (new):** `AlarmActivity` (Compose, `@AndroidEntryPoint`, show-when-locked / turn-screen-on), its call-style screen composable, and an `AlarmStateHolder` (`@Singleton`, `StateFlow<Boolean>` isRinging) so the Activity can finish when the alarm stops elsewhere.
- **Code (modified):** `AlarmService` — attach a full-screen `PendingIntent` to the ongoing notification (`setFullScreenIntent`), and publish ringing state to `AlarmStateHolder` on start/stop; `AndroidManifest.xml` — `USE_FULL_SCREEN_INTENT` permission + the `AlarmActivity` `<activity>` (exported=false, singleInstance/noHistory, lock-screen-friendly theme).
- **Permissions:** `USE_FULL_SCREEN_INTENT` (Android 14+ restricted — `NotificationManager.canUseFullScreenIntent()` gates it; may route the user to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`).
- **Specs:** new `alarm-fullscreen-ui` spec.
- **Tests:** the full-screen surface is Android-framework (Activity/keyguard/full-screen intent) and is primarily manually verified; any extractable pure-Kotlin logic (e.g. the ringing-state holder) gets unit tests.
- **Docs/ADR:** likely a short note or ADR addendum; the core lifecycle decision (service-owned alarm) is already ADR 013 — full-screen is a presentation layer on top.
