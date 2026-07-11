## Why

Today `SNOOZE_NOTIFICATION` only supports a fixed relative duration ("snooze for 15 minutes"), picked from presets or a 1-120 min slider. Two more useful patterns users actually want are not expressible:

1. **"Until a specific time"** — e.g. "snooze until 9:00 AM" regardless of when the notification actually arrived.
2. **"Digest windows"** — e.g. "only let WhatsApp notifications through once an hour, between 9am and 6pm" (release the first one at 10am, hold everything that arrives between 10 and 11 for the 11am release, and so on), rather than a nag that reappears and re-hides itself every cycle.

Separately, while researching this, two existing spec files were found to describe a design that was never built: `openspec/specs/snooze-scheduling/spec.md` (an `AlarmManager` + `snoozed_notifications` Room table + `SnoozeAlarmReceiver` design) and the "Snooze action triggers scheduling" requirement in `openspec/specs/action-execution/spec.md`. The shipped implementation instead calls Android's native `NotificationListenerService.snoozeNotification(key, durationMs)` directly — much simpler, and it's what this change builds on. This proposal corrects that drift as part of extending the feature, so the specs describe reality going forward.

## What Changes

- The `SNOOZE_NOTIFICATION` action gains a `snooze_mode`: `DURATION` (today's behavior, unchanged and still the default) or `SCHEDULED`.
- **`SCHEDULED` mode, single checkpoint** ("until time"): configure a start time (hour:minute); a match before that time snoozes until then; a match after it rolls to the same time tomorrow.
- **`SCHEDULED` mode, recurring** ("digest window"): additionally configure a repeat interval and a window end time. Matches inside `[start, end)` are held until the next `start + k×interval` checkpoint; matches outside the window pass through immediately (no snooze). The checkpoint's own release is **not** re-batched — a small in-process marker (mirroring the existing `NotificationDeduplicator` pattern, no new database table) recognizes "this post is the release I just scheduled" and lets it show instead of immediately re-snoozing it to the next checkpoint.
- No `AlarmManager`, no new Room table for the schedule itself: the existing `RuleAction.config` map (already persisted as JSON, per `action-execution` spec) carries the new keys with zero storage migration; the only new runtime state is the transient in-memory release marker.
- `SnoozeBottomSheet` gets a mode toggle ("For a duration" / "On a schedule") with a new schedule-config composable; the existing duration selector is unchanged and stays the default path.
- Corrects `openspec/specs/snooze-scheduling/spec.md` and the snooze requirement in `openspec/specs/action-execution/spec.md` to describe the actual native-snooze mechanism plus the new scheduled behavior, replacing the never-built `AlarmManager`/Room design.

Out of scope: per-rule custom digest history/UI for reviewing what was held back; cross-device/cloud sync of schedules (local-first, per ADR 012); changing `DURATION` mode's UX (presets/slider stay as-is).

## Capabilities

### Modified Capabilities
- `action-execution`: the `SNOOZE_NOTIFICATION` requirement is corrected to describe the native `snoozeNotification()` call (not `AlarmManager`) and extended to cover `snooze_mode` and the release-marker behavior for `SCHEDULED` mode.

### Removed / Replaced Capability Content
- `snooze-scheduling`: the entire existing spec (`AlarmManager` scheduling, `snoozed_notifications` table, `SnoozeAlarmReceiver`, dedicated notification channel) is removed as it was never implemented. Replaced with requirements describing `DURATION` mode (as shipped) and the new `SCHEDULED` mode (single checkpoint + recurring digest window + release marker).

## Impact

- **Code (modified):** `RuleAction.kt` (new config keys, typed accessors, `createScheduledSnooze` factory), `SnoozeActionExecutor.kt` (mode branch), `SnoozeBottomSheet.kt` (mode toggle).
- **Code (new):** `SnoozeScheduleCalculator` (pure Kotlin, computes the next checkpoint or "pass through"), `SnoozeReleaseTracker` (in-memory marker, mirrors `NotificationDeduplicator`'s singleton+mutex pattern), a small injectable time-provider seam for testability, a new `ScheduledSnoozeSelector` composable (time pickers + interval chips).
- **No DB migration:** config stays a `Map<String, String>` column; no new Room entities/DAOs.
- **No new permissions:** unlike the alarm feature, this never touches `AlarmManager`, so no `SCHEDULE_EXACT_ALARM` concerns.
- **Specs:** delta for `action-execution`; full replacement delta for `snooze-scheduling`.
- **Tests:** new pure-Kotlin tests for `SnoozeScheduleCalculator` (checkpoint math, window pass-through, day rollover) and `SnoozeReleaseTracker`; updated `SnoozeActionExecutorTest` for the mode branch.
- **ADR:** none needed — this reuses the existing action/config/executor pattern and an existing in-memory-tracker pattern already accepted in the codebase (`NotificationDeduplicator`); no new architectural approach is introduced.
