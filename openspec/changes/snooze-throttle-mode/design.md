## Context

`SnoozeActionExecutor` already dispatches on `RuleAction.getSnoozeMode()`: `DURATION` snoozes for a fixed relative duration and `SCHEDULED` snoozes until a computed checkpoint (both call `SystemNotificationController.snooze(sbnKey, durationMs)`). All existing modes eventually *deliver* every matching notification. This change adds a fourth outcome, **Throttle** — "let the first through, mute the rest": the first match in a rolling window is left to post untouched; every further match inside that window is *dropped* via `SystemNotificationController.cancel(sbnKey)` (the same call `DismissActionExecutor` uses) and never re-delivered. Example: "WhatsApp → 1 alert per 10 min."

Constraints that shape this design:

- `RuleAction.config` is a generic `Map<String, String>` persisted as a JSON TEXT column and carried verbatim by the `ActionDto` wire format. New keys need **zero migration and zero wire-format change**, exactly as the `SCHEDULED` keys were added.
- The throttle decision must be **restart-durable** (settled decision #3). The `SnoozeReleaseTracker` pattern (a plain in-memory presence `Set`, no timestamps) is not reusable — throttle needs a per-key *timestamp* and a durable fallback. `NotificationDeduplicator` is the correct template: in-memory `Map<String, Long>` fast path guarded by a `Mutex`, with an indexed DB lookback that only runs on a cache miss (never per-notification fan-out).
- `RuleExecution` history is the natural durable store: every match already produces a persisted `RuleExecutionEntity` with `created_at` (indexed) and an `action_outcomes` JSON map keyed by action id. A *delivered* throttle match is one whose outcome for the snooze action is `SUCCESS`; a *suppressed* one carries the new outcome. So durability needs **no new table** — the lookback reads existing rows.
- `ActionExecutor.execute(notification, action)` does **not** receive the rule id. Rather than widen that shared contract (which would ripple across all five executors and break the "adding an action type is additive" spirit of ADR 010), the scope key is derived from data the executor already has: `action.id` (globally unique, exactly one snooze action per rule) plus `notification.packageName`.
- The existing `CurrentTimeProvider` seam exposes only `now(): LocalDateTime`. Throttle window math is in epoch millis and is worth unit-testing precisely, so the seam is widened with `nowEpochMillis(): Long` rather than reading `System.currentTimeMillis()` directly in the tracker.

## Goals / Non-Goals

**Goals:**
- Add `SnoozeMode.THROTTLE` alongside `DURATION`/`SCHEDULED`, fully backward compatible (rules/exports without the new keys behave exactly as today; `getSnoozeMode()` already falls back to `DURATION`).
- First-through delivery + in-window drop via `cancel()`, scoped **per rule + source app** (each targeted app keeps an independent window).
- Restart-durable throttle state via an in-memory `Map` fast path + a single indexed `RuleExecution` lookback on cache miss.
- Record suppressed matches in `RuleExecution` history under a new, distinct `ActionOutcome` so the user can answer "why didn't I get notified."
- Reset the window on edit (window-duration change or disable→re-enable) durably, surviving restart and export/import.
- Keep the window/decision math pure and unit-testable (injected clock, no live `System.currentTimeMillis()` in the tracker).

**Non-Goals:**
- No new Room table, no schema migration, no new permission (explicitly — see D6).
- No throttle counters / "N suppressed" badges in the UI (out of scope per proposal).
- No user-configurable scope (rule+app is fixed).
- No change to the existing three outcomes' UX or behavior.
- No cross-process/cross-device coordination — the listener service and UI share one process (same assumption as `NotificationDeduplicator`).

## Decisions

### D1: Extend `RuleAction.config`, new mode + two keys, no migration
New constants next to the existing snooze keys:
- `SNOOZE_THROTTLE_WINDOW_MINUTES_KEY = "snooze_throttle_window_minutes"` — the rate-limit window; `DEFAULT_SNOOZE_THROTTLE_WINDOW_MINUTES = 10`. Read via a new `RuleAction.getThrottleWindowMinutes()` accessor, clamped to a sane range (mirroring the flash/alarm defense-in-depth coercion, e.g. `MIN`/`MAX` 1..1440) so a malformed import can't produce a zero/negative window.
- `SNOOZE_THROTTLE_RESET_AT_KEY = "snooze_throttle_reset_at"` — an epoch-millis watermark (see D5). Read via `RuleAction.getThrottleResetAt(): Long` defaulting to `0L`.

`SnoozeMode` gains `THROTTLE`. `getSnoozeMode()` already resolves it by enum-name match with no change. A `createThrottleSnooze(id, windowMinutes, resetAt, isEnabled)` factory mirrors `createScheduledSnooze`, writing `SNOOZE_MODE_KEY = "throttle"`, the window, and the reset watermark into `config`.

### D2: `NotificationThrottleTracker` — hybrid in-memory + DB-lookback, modeled on `NotificationDeduplicator`
New component `core/notification/action/NotificationThrottleTracker.kt`, a `@Singleton` with an `@Inject` constructor (like `SnoozeReleaseTracker`, so **no `ActionModule` binding is required** — Hilt provides the concrete class directly). It depends on `RuleExecutionRepository` (durable fallback) and `CurrentTimeProvider` (clock seam).

```kotlin
@Singleton
class NotificationThrottleTracker @Inject constructor(
    private val ruleExecutionRepository: RuleExecutionRepository,
    private val timeProvider: CurrentTimeProvider,
) {
    private val lastDelivered = mutableMapOf<String, Long>() // key -> epoch millis of last delivery
    private val mutex = Mutex()

    /**
     * Atomically decide whether this match opens/continues a delivery.
     * Returns true = let it through (window opens now); false = suppress (drop).
     */
    suspend fun shouldDeliver(
        actionId: String,
        packageName: String,
        windowMs: Long,
        resetAt: Long,
    ): Boolean = mutex.withLock {
        val key = "$actionId|${packageName.ifBlank { UNKNOWN_PACKAGE }}"
        val now = timeProvider.nowEpochMillis()
        cleanupOldEntries(now, windowMs)

        val last = lastDelivered[key] ?: dbLastDeliveredAt(actionId, packageName, since = now - windowMs)
        // A delivery recorded before the last edit no longer counts (D5).
        val effectiveLast = last?.takeIf { it >= resetAt }

        val deliver = effectiveLast == null || (now - effectiveLast) >= windowMs
        if (deliver) lastDelivered[key] = now
        deliver
    }
}
```

Key points:
- **Scope key = `action.id | packageName`** → per rule + source app without needing the rule id in the executor. Unresolvable/blank package falls back to a `UNKNOWN_PACKAGE` sentinel so throttling stays deterministic (resolves the proposal's "unresolvable source app" risk).
- **In-memory fast path**: after the first decision for a key, steady-state matches (deliver *and* suppress) are served purely from `lastDelivered` — the DB is touched only on a cold key (first match after process start for that rule+app), never per notification.
- On **suppress** the map is left untouched (it still holds the opener timestamp); once `now - opener >= windowMs` the next match delivers and rewrites the opener to `now`.
- `cleanupOldEntries` evicts keys whose last delivery is older than a generous multiple of their window, mirroring `NotificationDeduplicator.cleanupOldEntries`.

### D3: DB lookback query — package-scoped, action-id filtered in Kotlin, no rule id needed
The durable fallback answers "what is the latest *delivered* throttle timestamp for this action+app, within the window?" Because `action.id` is unique to the rule's snooze action, filtering on it in Kotlin gives exact rule+app scope, so the SQL only needs `package_name` + `created_at` (both already indexed) via a join to `notifications`:

New `RuleExecutionDao` query:
```kotlin
@Query(
    """
    SELECT re.* FROM rule_executions re
    INNER JOIN notifications n ON re.notification_id = n.id
    WHERE n.package_name = :packageName AND re.created_at >= :since
    ORDER BY re.created_at DESC
    """,
)
suspend fun getRecentExecutionsForPackageSince(packageName: String, since: Long): List<RuleExecutionEntity>
```
`created_at >= :since` (since = `now - windowMs`) bounds the result to at most one open window's worth of rows — tiny even for chatty apps. New `RuleExecutionRepository` method:
```kotlin
suspend fun lastThrottleDeliveryAt(actionId: String, packageName: String, sinceMs: Long): Result<Long?>
```
The impl maps rows and returns the max `createdAt` where `actionOutcomes[actionId] == ActionOutcome.SUCCESS`. The tracker treats a `Result.failure` (or null) as **fail-open** (deliver) — matching `NotificationDeduplicator`'s `.getOrNull() ?: emptyList()` posture and the proposal's "a stray alert beats silent suppression" lean.

This deliberately avoids the proposal's literal "LIMIT 1 variant of `getExecutionsForRule`" because that requires the rule id in the executor; scoping by `action.id` in Kotlin over a window-bounded, indexed row set is functionally identical and keeps the `ActionExecutor` contract untouched.

### D4: `ActionOutcome.SUPPRESSED` and its flow to history + UI
Add a fourth case to the `@Serializable enum class ActionOutcome`:
```kotlin
/** The action matched but was intentionally dropped (e.g. throttle window still open). */
SUPPRESSED,
```
Adding an enum value is backward compatible: kotlinx-serialization encodes/decodes it automatically, and `RuleExecutionMapper` already wraps decode in `runCatching` (a value an older build can't parse is skipped, not crashed). The flow needs **no plumbing changes**:
- `SnoozeActionExecutor` returns `SUCCESS` (delivered) or `SUPPRESSED` (dropped).
- `ActionDispatcher.executeAll` returns the executor's value verbatim → `action.id → SUPPRESSED` (no change).
- `ProcessNotificationUseCase.toExecution` folds it into `actionOutcomes` and `RuleExecutionRepository.saveExecution` persists it (no change).
- `RuleExecutionMapper` (de)serializes it (no change).

The only edits are the three exhaustive `when (action.outcome)` blocks in `NotificationDetailScreen.kt` (`ActionChip`, ~lines 512/517/522): add a `SUPPRESSED` branch — muted color (`onSurfaceVariant`), a distinct glyph (e.g. `⊘`), and description "throttled". The compiler forces these (non-`else` `when`), so nothing is silently missed.

### D5: Edit resets the window via a durable `resetAt` watermark (no in-memory clearing)
Because `config` is persisted and travels with the rule (including export/import), the reset is expressed as a watermark rather than a cache mutation. The gate ignores any delivery older than `resetAt` (D2: `last.takeIf { it >= resetAt }`), so bumping `resetAt` makes the next match deliver — even in-memory (the stale `lastDelivered` entry is simply older than the new watermark) and even after a restart (the DB lookback is filtered the same way). **No explicit tracker invalidation is needed.**

Where it is stamped: `SnoozeBottomSheet.buildSnoozeAction`. When building a `THROTTLE` action it sets `resetAt = now` (a `System.currentTimeMillis()` read passed in at confirm time — UI code, not the testable core) whenever the throttle window changed *or* the action transitions from a non-throttle mode / disabled state into an enabled throttle; otherwise it preserves the loaded action's existing `resetAt`. This keeps the reset scoped to the settled trigger (window edit or disable→re-enable) rather than firing on every unrelated rule save. The hook lives at the UI/build boundary, so `RuleRepository`/domain stay agnostic of throttle semantics.

### D6: No migration, no new binding, one clock-seam addition
- **Migration:** none. New mode + keys live inside the existing `config` JSON column; `SUPPRESSED` lives inside the existing `action_outcomes` JSON column. The only DB addition is a read-only DAO query (D3). Explicitly no `AppDatabase` version bump.
- **Hilt:** `NotificationThrottleTracker` is a concrete `@Inject @Singleton` class → no `ActionModule` entry (same as `SnoozeReleaseTracker`). It is added as a constructor parameter of `SnoozeActionExecutor`, which Hilt resolves automatically.
- **Clock:** widen `CurrentTimeProvider` with `nowEpochMillis(): Long` (`SystemCurrentTimeProvider` returns `System.currentTimeMillis()`); its `@Binds` already exists.

### D7: Executor `THROTTLE` branch
`SnoozeActionExecutor` gains a `SnoozeMode.THROTTLE -> executeThrottle(...)` arm:
```kotlin
private suspend fun executeThrottle(controller, sbnKey, notification, action): ActionOutcome {
    val windowMs = action.getThrottleWindowMinutes() * 60_000L
    val deliver = throttleTracker.shouldDeliver(
        actionId = action.id,
        packageName = notification.packageName,
        windowMs = windowMs,
        resetAt = action.getThrottleResetAt(),
    )
    return if (deliver) {
        ActionOutcome.SUCCESS            // first-through: leave it posted, do nothing
    } else {
        controller.cancel(sbnKey)        // drop; never re-delivered
        ActionOutcome.SUPPRESSED
    }
}
```
The existing null-controller / null-`sbnKey` guards (returning `SKIPPED`) are reused unchanged.

### D8: UI — fourth `SnoozeOutcome` card + window selector
- `SnoozeOutcome` enum gains `THROTTLE` with its card copy ("Let the first through, mute the rest", example "WhatsApp → 1 alert per 10 min") in the existing `info()` `when`.
- A new `ThrottleWindowSelector` composable modeled on `SnoozeDurationSelector` (chips 5m/10m/30m/1h + custom).
- `SnoozeBottomSheet`: hoist `throttleWindowMinutes` state, add a `THROTTLE` arm to the `SnoozeOutcomeSelector` config `when`, extend `toInitialOutcome()` (throttle mode → `THROTTLE` card) and `buildSnoozeAction()` (calls `createThrottleSnooze`, applying D5's `resetAt` rule). `canConfirm` stays true for throttle (a window always has a valid default).

### D9: Concurrency — mutex check-and-set, not a DB constraint
The whole read-decide-write in `shouldDeliver` runs under a single `Mutex.withLock`, so two near-simultaneous matches serialize and exactly one sees `effectiveLast == null`/expired and wins the window open; the other sees the just-written timestamp and is suppressed. Chosen over a DB unique-constraint approach because: (a) it matches the existing `NotificationDeduplicator`/`SnoozeReleaseTracker` in-process pattern, (b) a constraint would require a new table + migration (rejected in D6), and (c) a DB constraint can't guard the in-memory fast path, which is where nearly all decisions are made.

## Risks / Trade-offs

- **[Fail-open on restart / DB error]** On a cold key or a lookback failure the tracker delivers (fail-open), risking a rare duplicate alert right after process start. Accepted and intentional — a stray alert beats silent suppression and matches the "let the first through" promise.
- **[`action.id`-scoped lookback scans other rules' rows for the same package]** The window-bounded query returns all executions for the package in that window, then filters by `action.id` in Kotlin. For a package with many rules this reads a few extra tiny rows on cache miss only; negligible and gated behind the in-memory map. The alternative (rule-id-scoped SQL) was rejected to avoid widening the shared `ActionExecutor` contract.
- **[Audit volume]** Recording every suppressed match grows `RuleExecution` history faster for high-frequency apps. Acceptable for MVP; retention/pruning noted as future work (the existing `deleteExecutionsOlderThan` DAO already provides the lever).
- **[Wall-clock jumps]** `nowEpochMillis()` reads wall-clock time; a manual clock change or DST shift could lengthen/shorten one window. Rare and low-impact (a rate limiter, not a precise scheduler); not worth a monotonic-clock abstraction.
- **[`resetAt` eager-stamp scope]** Stamping is gated to window-change / disable→re-enable in the UI build path; a future non-UI edit surface (e.g. bulk import overwriting a rule) must preserve or intentionally set `resetAt`. Flagged so import codepaths don't silently reset live windows.

## Open Questions

None outstanding — scope (rule+app), audit outcome, durability strategy, and edit-reset semantics were settled in the proposal and are designed against above.
