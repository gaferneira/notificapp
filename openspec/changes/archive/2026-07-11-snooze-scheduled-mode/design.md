## Context

`SnoozeActionExecutor` today reads `SNOOZE_DURATION_MINUTES_KEY` from `RuleAction.config`, converts to milliseconds, and calls `SystemNotificationController.snooze(sbnKey, durationMs)` — a thin wrapper over `NotificationListenerService.snoozeNotification()`. That native API is duration-based only (no "until wall-clock time" concept), but since the executor already computes the millisecond value itself right before the call, an absolute-time mode is just a different way of computing that same number.

Constraints that shape this design:
- `NotificationListenerService.snoozeNotification()` is a fire-and-forget OS call: hide now, repost once after the duration, no further involvement from us. There is no OS-level "repeat."
- When the OS reposts a snoozed notification, it flows back through `NotificappListenerService.onNotificationPosted()` → `ProcessNotificationUseCase` → rule evaluation, same as any other post. `NotificationDeduplicator`'s window (30s in-memory, 5 min DB lookback) is far shorter than any realistic snooze duration, so the repost is **not** deduped — it re-matches the rule and, naively, would re-trigger the same Snooze action immediately (within the pipeline's normal processing latency, effectively instantly). Left unhandled, this makes the notification flicker and re-hide itself at every checkpoint instead of actually being shown — the opposite of the "digest" behavior we want.
- `RuleAction.config` is already a generic `Map<String, String>`, persisted as a JSON TEXT column (Room) and passed through unchanged by the `ActionDto` wire format (`core/rulesharing/dto/ActionDto.kt`). New keys need zero migration and zero wire-format changes.
- No injectable clock/time abstraction exists yet; the codebase reads `System.currentTimeMillis()` directly where needed (e.g. `NotificationDeduplicator`). The scheduling math here is worth unit-testing precisely (day rollover, window edges), so it needs "now" injected rather than read live.

## Goals / Non-Goals

**Goals:**
- Add a `SCHEDULED` snooze mode alongside the existing `DURATION` mode, fully backward compatible (rules/exports missing the new keys behave exactly as today).
- Support both a single daily checkpoint ("until 9am, then 9am tomorrow, ...") and a recurring interval within a bounded window ("every hour between 9am and 6pm; pass through outside that window").
- Guarantee the checkpoint's own release is actually visible — it must not be immediately re-snoozed to the next checkpoint by the rule re-matching its own repost.
- Keep the scheduling math as a pure, unit-testable function, isolated from Android and from wall-clock reads.
- Reconcile the two stale spec documents that describe the never-built `AlarmManager`/Room design.

**Non-Goals:**
- Persisting the schedule/marker across the notification listener service's process death with full durability (a Room-backed queue). The in-memory marker can theoretically be lost on an ill-timed process restart; see Risks.
- Cross-notification/cross-rule digest batching (e.g. combining multiple held notifications into a single summary at release time). Each notification is snoozed independently; multiple notifications released at the same checkpoint simply reappear as separate notifications, same as today.
- Any use of `AlarmManager` or a foreground service. The OS's own `snoozeNotification()` scheduling is sufficient and avoids the exact-alarm permission questions the `CREATE_ALARM` feature had to deal with.

## Decisions

### D1: Extend `RuleAction.config`, no new Room table for the schedule
New keys, following the existing const + typed-accessor pattern already used for alarm/flash config:
- `SNOOZE_MODE_KEY` ("snooze_mode"): `"duration"` (default) | `"scheduled"`
- `SNOOZE_SCHEDULE_START_HOUR_KEY` / `_MINUTE_KEY`: first checkpoint of the day
- `SNOOZE_SCHEDULE_INTERVAL_MINUTES_KEY`: nullable/absent = single daily checkpoint at the start time; present = recurring every N minutes from the start time
- `SNOOZE_SCHEDULE_WINDOW_END_HOUR_KEY` / `_MINUTE_KEY`: required whenever an interval is set (enforced by the UI, not the domain layer); absent when there's no interval

Absent keys fall back to `DURATION` mode exactly as `getSnoozeDurationMinutes()` falls back to the default today — no migration, no forward/backward-compat concern for existing saved rules or exported rule files.

### D2: A pure `SnoozeScheduleCalculator`, given `now` explicitly
```kotlin
data class SnoozeSchedule(
    val startHour: Int,
    val startMinute: Int,
    val intervalMinutes: Int? = null,
    val windowEndHour: Int? = null,
    val windowEndMinute: Int? = null,
)

object SnoozeScheduleCalculator {
    // Returns the next checkpoint (as an epoch-millis instant after `now`), or null to mean
    // "pass through immediately" (now is outside the configured window).
    fun nextCheckpoint(now: LocalDateTime, schedule: SnoozeSchedule): LocalDateTime?
}
```
Logic:
- No interval (single daily checkpoint): if `now` is before today's start time, checkpoint = today's start; otherwise checkpoint = tomorrow's start. No window applies.
- Interval + window end set: if `now < start` → checkpoint = today's start. If `now >= windowEnd` → return `null` (pass through). Otherwise compute `start + ceil((now - start) / interval) × interval`; if that lands at or past `windowEnd`, also return `null` (the window has effectively ended for today) rather than rolling into tomorrow mid-window.

This function takes a `LocalDateTime` directly so tests can assert exact checkpoint math (including day-rollover and window-edge cases) without touching the clock. The executor is the only caller that supplies a live value.

### D3: A minimal injectable time source, not a full `Clock` abstraction
A one-method interface (e.g. `CurrentTimeProvider { fun now(): LocalDateTime }`) bound in `core/di`, Android impl just returns `LocalDateTime.now()` (system default zone — wall-clock local time is what a user means by "9am", consistent with how the rest of the app has no multi-timezone concept). Kept deliberately narrow rather than introducing a general `Clock` wrapper the codebase doesn't otherwise need.

### D4: `SnoozeReleaseTracker` — in-memory marker, no new Room table
Mirrors `NotificationDeduplicator`'s existing pattern (a `@Singleton`, mutex-guarded in-memory set) rather than adding persistence:
```kotlin
@Singleton
class SnoozeReleaseTracker @Inject constructor() {
    private val pending = mutableSetOf<String>()
    private val mutex = Mutex()

    suspend fun markPending(sbnKey: String) = mutex.withLock { pending.add(sbnKey) }

    // Returns true (and clears) if this key was expected to reappear from a scheduled release —
    // the caller should let it show as-is instead of re-batching it.
    suspend fun consumeIfPending(sbnKey: String): Boolean = mutex.withLock { pending.remove(sbnKey) }
}
```
No timestamp bookkeeping: once `SnoozeActionExecutor` calls `snooze()` in `SCHEDULED` mode, the *next* `onNotificationPosted` event for that same `sbnKey` is by construction the release (nothing else should legitimately post under an actively-snoozed key), so a plain presence check is sufficient — no epsilon/time-window matching needed.

**Executor flow for `SCHEDULED` mode:**
```
if (releaseTracker.consumeIfPending(sbnKey)) {
    return SUCCESS  // this post is the checkpoint's own release; let it show untouched
}
val checkpoint = calculator.nextCheckpoint(timeProvider.now(), schedule)
if (checkpoint == null) {
    return SUCCESS  // outside the window; pass through, no snooze call
}
controller.snooze(sbnKey, durationMs = checkpoint - now)
releaseTracker.markPending(sbnKey)
return SUCCESS
```

### D5: One Snooze action, mode toggle in the existing bottom sheet
`SnoozeBottomSheet` gets a segmented control ("For a duration" / "On a schedule"). The duration path is untouched (`SnoozeDurationSelector`, unchanged). The schedule path is a new `ScheduledSnoozeSelector`: a start-time picker, a "Repeat" toggle that reveals an interval selector (chips: 30/60/120 min, or custom) and a window-end time picker when enabled. The UI enforces `start < windowEnd` and requires a window end whenever repeat is on — the domain layer does not need to validate this itself since malformed config just degrades to "always pass through" via `nextCheckpoint` returning `null` when `windowEnd` is missing but interval is set (treated as misconfigured → pass-through, never a crash).

## Risks / Trade-offs

- **[In-memory release marker can be lost]** If `NotificappListenerService`'s process dies between `markPending` and the repost (rare — Android generally keeps the listener service alive), that one checkpoint's release gets treated as "new" and re-batched to the next checkpoint instead of showing. Self-correcting on the next cycle; no data loss (the notification is just delayed one more interval). Accepted, same risk class as `NotificationDeduplicator`'s existing in-memory cache.
- **[DST / day-boundary edge cases]** `LocalDateTime` arithmetic across a DST transition could shift a checkpoint by an hour on the two days a year this matters. Accepted as a minor, rare drift — not worth a `ZonedDateTime`/`ZoneId`-aware implementation for this feature's value.
- **[Same-app rules interacting]** If two different rules both configure a `SCHEDULED` snooze targeting overlapping notifications, the release marker is keyed only by `sbnKey`, not by rule — the tracker doesn't distinguish which rule's checkpoint is being released. Out of scope; matches the existing "one action per type per rule" model and is an unlikely authoring scenario.
- **[Spec drift already existed before this change]** Correcting `snooze-scheduling` and `action-execution` here means this PR's diff includes removing content unrelated to the new feature. Called out explicitly in the proposal so reviewers don't mistake it for scope creep.

## Open Questions

None outstanding — mode semantics, stop/window behavior, and clock alignment were confirmed in discussion before this design was written.
