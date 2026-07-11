## Why

The Snooze action offers three outcomes today — **Delay each one** (`DURATION`), **Hold until a time** (`SCHEDULED` single checkpoint), and **Batch into a digest** (`SCHEDULED` recurring). All three eventually *deliver* every matching notification; none can say "I only care about the first one." A user drowning in a chatty app (group chats, delivery-tracking pings, CI/monitoring spam) wants a rate limit, not a delay: **let the first through, mute the rest** for a while.

There is no expressible pattern for "1 alert per N minutes." This proposal adds a fourth outcome, **Throttle**, that delivers the first match immediately and *drops* (does not delay, queue, or reschedule) every further match until a configured window elapses.

Example: "WhatsApp → 1 alert per 10 min." The first WhatsApp match posts and opens a 10-minute window; matches 2..N inside that window are cancelled outright; the next match after 10 minutes posts and opens a fresh window.

## What Changes

- `SnoozeBottomSheet` gains a fourth `SnoozeOutcome` — **Throttle** ("Let the first through, mute the rest") — alongside the existing three, with a window selector (5m / 10m / 30m / 1h / custom) modeled on `SnoozeDurationSelector`.
- New `SnoozeMode.THROTTLE` in `RuleAction.kt` plus a `createThrottleSnooze(...)` factory and a config key for the window duration. Config stays in the persisted `RuleAction.config` map — **no DB migration**.
- `SnoozeActionExecutor` gains a `THROTTLE` branch: on the first match in a window it lets the notification post and records delivery; on subsequent matches inside the window it calls `SystemNotificationController.cancel(sbnKey)` (the drop path `DismissActionExecutor` already uses) — **not** `snooze()`, since dropped matches are never re-delivered.
- New throttle-state tracker following the **`NotificationDeduplicator` hybrid pattern** (in-memory `Map` fast path + DB lookback fallback for durability across restart), keyed per **rule + source app**, storing the last-delivery timestamp.
- `RuleExecutionRepository` exposes a "last delivered execution for rule+app" lookback (a `LIMIT 1` variant of the existing `RuleExecutionDao.getExecutionsForRule`, filtered to delivered/non-suppressed rows).
- New `ActionOutcome` case distinguishing "matched but suppressed by throttle" from `SUCCESS`/`FAILED`/`SKIPPED`, so suppressed matches remain visible in `RuleExecution` history.

### Settled product decisions (not options)

1. **Scope = per rule + source app.** Each targeted app of a rule keeps its own independent window/timer; a burst from App A never suppresses App B under the same rule.
2. **Suppressed matches ARE audited.** Dropped matches are recorded in `RuleExecution` history under the new outcome, so a user can answer "why didn't I get notified."
3. **Durable state.** Throttle state must survive app/process restart via the hybrid in-memory + DB-lookback tracker — never pure in-memory.
4. **Edit resets the timer.** Editing the window duration, or disabling then re-enabling the Throttle action, always makes the next match deliver (fresh window), regardless of any in-flight window at edit time.

Out of scope: throttle counters or "N suppressed" badges in the UI; user-configurable scope (rule-only vs rule+app is fixed at rule+app); cross-device / cloud sync (local-first, per ADR 012); any change to the existing three outcomes' UX or behavior.

## Capabilities

### Modified Capabilities
- `action-execution`: extend the `SNOOZE_NOTIFICATION` requirement to cover `THROTTLE` mode — first-through delivery, in-window drop via `cancel()`, per-rule+app scope, restart-durable tracker, and the suppressed `ActionOutcome`.
- `snooze-scheduling`: add requirements for the Throttle outcome (window config, delivery/drop semantics, timer-reset-on-edit) alongside the existing `DURATION` / `SCHEDULED` modes.

### New Capabilities
- None — reuses the existing action/config/executor and hybrid-tracker patterns.

## Impact

- **Code (modified):** `RuleAction.kt` (mode + config key + factory), `SnoozeActionExecutor.kt` (throttle branch), `SnoozeBottomSheet.kt` + `SnoozeOutcome.kt` (fourth outcome + selector), `RuleExecutionRepository.kt` / `RuleExecutionDao.kt` (last-delivered lookback), `ActionOutcome.kt` (suppressed case).
- **Code (new):** throttle-state tracker (singleton + mutex, mirrors `NotificationDeduplicator`), throttle-window selector composable, injectable time-provider seam for testability.
- **No DB migration / no new permissions.**
- **Tests:** pure-Kotlin tests for the tracker (window math, per-rule+app isolation, restart lookback, edit-reset); updated `SnoozeActionExecutorTest` for the throttle branch.
- **ADR:** none — no new architectural approach.

## Risks / open questions for design

| Risk / question | Why it matters |
|---|---|
| **Fail-open vs fail-closed at restart race** | Between process start and DB warm-up, the in-memory map is empty. Design must decide: deliver (fail-open, risk a duplicate alert right after restart) or wait on the DB lookback (fail-closed, risk suppressing a legitimately-due delivery). Lean fail-open — a stray alert is less harmful than silent suppression, and matches the "let the first through" promise. |
| **Unresolvable source app** | Scope keys on source package. If a notification's package can't be resolved (rare, but happens with normalization edge cases), design must define a fallback scope key (e.g. rule-only, or a sentinel package) so throttling still functions deterministically. |
| **Concurrent matches within the mutex window** | Rapid-fire notifications may hit the tracker near-simultaneously. The mutex must make the "first-through" check-and-set atomic so exactly one match wins the window open. |
| **DB lookback cost on hot path** | The lookback query runs on the notification-processing path. Must stay a single indexed `LIMIT 1` read (ruleId + createdAt are already indexed) and be gated behind the in-memory fast path to avoid per-notification DB fan-out. |
| **Multi-process** | The listener service and UI can run in the same process here; no multi-process/multi-device coordination is in scope — flag if that assumption ever breaks. |
| **Audit volume** | Recording every suppressed match grows `RuleExecution` history faster for high-frequency apps. Acceptable for MVP; note retention/pruning as future work if it becomes a concern. |
