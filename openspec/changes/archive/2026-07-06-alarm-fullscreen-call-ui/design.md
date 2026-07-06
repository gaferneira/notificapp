## Context

Iteration 1 (`persistent-stoppable-alarm`, ADR 013) established a foreground `AlarmService` that owns a ringing alarm: looping `MediaPlayer` audio, repeating vibration, and an ongoing high-importance notification with Dismiss/Snooze actions delivered as `ACTION_DISMISS` / `ACTION_SNOOZE` intents. The ring content (`AlarmRequest`: soundUri, vibrationEnabled, title, text, appName) is already threaded through `AlarmService.startIntent`.

The gap: when the phone is locked or the screen is off, the alarm is only a notification — easy to miss. This iteration adds a phone-call-style full-screen surface, reusing everything from iteration 1 unchanged.

Constraints:
- **Android 14+ restricts `USE_FULL_SCREEN_INTENT`.** It is auto-granted only to apps whose core function is alarm-clock or calling. Notificapp is neither, so it may be denied; `NotificationManager.canUseFullScreenIntent()` reports the state, and `Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` opens the grant screen.
- A full-screen intent only launches its Activity full-screen when the device is locked / screen off; otherwise the system shows it as a heads-up notification. So the ongoing notification must remain fully functional.
- The alarm can be stopped from three places now (notification action, full-screen button, a superseding ring), so the surfaces must stay consistent.

## Goals / Non-Goals

**Goals:**
- A call-style `AlarmActivity` over the lock screen showing the triggering content + large Dismiss/Snooze.
- Reuse the iteration-1 `AlarmService` stop path verbatim — no duplicated stop logic.
- Keep the full-screen surface and the ongoing notification in sync.
- Degrade cleanly to the iteration-1 notification when the full-screen intent can't be shown.

**Non-Goals:**
- Any change to ring/stop/snooze behavior, audio, or scheduling.
- Custom full-screen layouts per rule (a single call-style screen for all alarms).
- Dismissing the keyguard / authenticating the user (we show over it, we don't unlock it).

**Added after device feedback:** a per-alarm "Full-screen alarm (call style)" toggle in the rule editor (default on), stored in the action config like sound/vibration; when off, the alarm stays notification-only (no full-screen intent attached). This was originally a non-goal but requested during verification.

## Decisions

### D1: Full-screen intent on the existing ongoing notification
`AlarmService.buildNotification()` gains `setFullScreenIntent(pendingIntent, true)` targeting `AlarmActivity`, carrying the same `AlarmRequest` content as extras. This is the platform-sanctioned way to raise a call-style screen and it automatically degrades to heads-up when the device is unlocked/active — so the notification path from iteration 1 keeps working untouched.

- **Alternative — `startActivity` directly from the service:** rejected. Background activity starts are heavily restricted (Android 10+), and the full-screen-intent mechanism exists precisely to grant this exemption for alarms/calls.

### D2: `AlarmActivity` is a standalone `@AndroidEntryPoint` ComponentActivity
Mirrors `MainActivity`'s pattern (`ComponentActivity` + `setContent { NotificappTheme { … } }`). It calls `setShowWhenLocked(true)` + `setTurnScreenOn(true)` (API 27+, with the pre-27 `WindowManager` flags fallback), reads `AlarmRequest` from its launch intent, and renders a call-style Compose screen. Declared `exported=false`, `launchMode=singleTask` (or `singleInstance`) + `excludeFromRecents`/`noHistory` so it never stacks or lingers in Recents.

- **Why not a Compose screen inside `MainActivity`?** A ringing alarm must appear over the lock screen independent of the main app's navigation/back stack; a dedicated Activity with the lock-screen flags is the clean, isolated surface.

### D3: `AlarmStateHolder` (`@Singleton`, `StateFlow<Boolean>`) keeps surfaces in sync
A tiny singleton holds `isRinging: StateFlow<Boolean>`, mirroring the existing `SystemNotificationControllerHolder` style. `AlarmService` sets it `true` on ring start and `false` in its single `stopAlarm()` routine. `AlarmActivity` collects it and `finish()`es when it turns `false`. This closes the "dismissed from the notification while full-screen is open" case with one observable source of truth.

- **Alternative — broadcasts:** `LocalBroadcastManager` is deprecated and app-global broadcasts are leaky/racy. A `@Singleton` `StateFlow` is testable (pure-Kotlin unit test) and matches an existing project pattern.
- **Note:** because playback/state live in a `@Singleton` (see the iteration-1 `@Singleton` `AndroidAlarmPlayer`), the holder is process-wide and correct as long as the app process lives; if the process is killed the ring stops anyway.

### D4: Dismiss/Snooze from the Activity go through `AlarmService`
The Activity's buttons `startService` with `ACTION_DISMISS` / `ACTION_SNOOZE` (the same intents the notification actions use), then `finish()`. No stop/snooze logic is duplicated in the UI — the service stays the single owner. (The Activity may finish immediately and let the `AlarmStateHolder` confirm, or finish on the state turning false; finishing eagerly is fine since the service is authoritative.)

### D5: Permission handling — best-effort, notification is the floor
Attaching a full-screen intent is always safe (no crash if `USE_FULL_SCREEN_INTENT` is denied — it just degrades to heads-up). `USE_FULL_SCREEN_INTENT` is declared in the manifest (normal on ≤13, restricted on 14+). Optionally, when configuring the alarm action we can check `canUseFullScreenIntent()` and offer a "for the best alarm experience, allow full-screen" hint routing to `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` — but the alarm is fully usable via the notification without it, so this is a nicety, not a gate.

## Risks / Trade-offs

- **[Android 14+ denies USE_FULL_SCREEN_INTENT]** The headline feature silently degrades to heads-up. → Mitigation: the iteration-1 notification is the guaranteed floor; optionally surface a one-time hint to grant it. Verify the degraded path on a 14+ device.
- **[Activity lingers over a stopped alarm]** If sync fails, the user sees a dead full-screen alarm. → Mitigation: `AlarmStateHolder` finish-on-false, plus the Activity also finishes on its own button taps; belt and suspenders.
- **[Lock-screen flags vary by OEM]** `setShowWhenLocked`/`setTurnScreenOn` behavior differs across manufacturers and secure-keyguard settings. → Mitigation: use the documented API + pre-27 window flags; manually verify on at least one locked secure device.
- **[Re-ring after snooze relaunches full-screen]** The `AlarmManager` re-ring re-posts the notification with the full-screen intent, which may re-launch the Activity — desired, but verify it doesn't double-show when the screen is already on. → Mitigation: `singleTask`/`singleInstance` + the sync holder.
- **[Framework-heavy, thin unit coverage]** Only `AlarmStateHolder` and any mapping logic are unit-testable; the rest is manual. → Mitigation: accept manual verification for the Activity/keyguard/full-screen-intent surface, consistent with iteration 1.

## Open Questions

- **Launch mode:** `singleTask` vs `singleInstance` for `AlarmActivity` — pick whichever most reliably avoids stacking on re-ring; default `singleInstance` + `noHistory`.
- **Grant hint:** do we proactively route users to the full-screen-intent settings when configuring an alarm on Android 14+, or stay silent and rely on the notification? Leaning silent for iteration 2 (the notification already works), revisit if testing shows the full-screen is commonly denied.
