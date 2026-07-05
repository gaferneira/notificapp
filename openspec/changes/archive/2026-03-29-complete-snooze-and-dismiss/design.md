## Context

The app captures Android notifications, matches them against user-defined rules, and records which actions were triggered — but never executes those actions. The `RuleAction` domain model supports `DISMISS_NOTIFICATION` and `SNOOZE_NOTIFICATION` action types with a `config` map for parameters like snooze duration. The `ActionBottomSheet` UI lets users configure these actions with duration presets and a slider.

However, two critical gaps prevent end-to-end functionality:
1. **Data loss**: `RuleActionEntity` has no `config` column, so snooze duration is lost on save.
2. **No execution**: `NotificappListenerService` calls `ruleEngine.process()` but discards the triggered action list without performing any side effects.

## Goals / Non-Goals

**Goals:**
- Execute dismiss and snooze actions when rules match incoming notifications
- Persist action configuration (snooze duration) through database round-trips
- Schedule snooze wake-ups using `AlarmManager` with reliable exact alarms
- Track snoozed notifications so they can be listed or cancelled
- Re-surface snoozed notifications after the configured delay

**Non-Goals:**
- UI for listing/managing snoozed notifications (future change)
- Manual snooze/dismiss buttons on inbox items (future change)
- Snooze across device reboots (requires `RECEIVE_BOOT_COMPLETED` — defer to later)
- `CREATE_ALARM` action type execution (out of scope for this change)
- Cloud sync of snooze state

## Decisions

### 1. Action execution lives in the service layer, not extraction

**Decision**: Create an `ActionExecutor` interface in the domain layer with an Android implementation in the notification feature package. The `NotificappListenerService` calls it after `RuleEngine.process()`.

**Rationale**: The extraction layer is pure Kotlin with no Android dependencies. Dismissing notifications requires `NotificationListenerService.cancelNotification()`, and scheduling snoozes requires `AlarmManager` — both Android APIs. Keeping action execution separate preserves the extraction layer's testability.

**Alternative considered**: Putting execution in `RuleEngine` — rejected because it would introduce Android dependencies into the pure-Kotlin extraction layer.

### 2. Store action config as JSON string column

**Decision**: Add a `config` TEXT column to `RuleActionEntity` that stores the `Map<String, String>` as a JSON string using a Room `TypeConverter`.

**Rationale**: Simple, flexible, and matches the domain model's `Map<String, String>`. Avoids creating separate tables per action type. The config map is small and doesn't need to be queried.

**Alternative considered**: Separate columns per config key (e.g., `snoozeDurationMinutes`) — rejected because it requires schema changes for every new action parameter.

### 3. AlarmManager for snooze scheduling

**Decision**: Use `AlarmManager.setExactAndAllowWhileIdle()` for snooze wake-ups, with a `BroadcastReceiver` (`SnoozeAlarmReceiver`) to handle the alarm.

**Rationale**: Snooze durations are user-configured (1-120 minutes) and users expect precise timing. `WorkManager` periodic tasks have a 15-minute minimum interval and are imprecise. `AlarmManager` exact alarms provide the precision needed.

**Alternative considered**: `WorkManager` with one-time delayed work — viable but less precise and adds complexity for a straightforward delayed-execution use case.

### 4. Snoozed notifications table for state tracking

**Decision**: Create a `snoozed_notifications` Room table with: id, notification_id, original_sbn_key, wake_up_time, snooze_rule_id, and serialized notification data needed to re-post.

**Rationale**: We need to track pending snoozes for cancellation and to re-post the notification when the alarm fires. The original `StatusBarNotification` cannot be persisted directly, so we store the essential fields (title, content, package, etc.).

### 5. Dismiss uses StatusBarNotification key

**Decision**: Pass the `StatusBarNotification.key` from the listener service through to the `ActionExecutor` so it can call `cancelNotification(key)`.

**Rationale**: `cancelNotification()` requires the exact SBN key. The current flow only passes the domain `Notification` model which doesn't include this key. We need to thread the key through or store it.

**Implementation**: Add an `sbnKey` field to the domain `Notification` model and persist it in `NotificationEntity`.

## Risks / Trade-offs

- **`SCHEDULE_EXACT_ALARM` permission**: Android 12+ requires this permission. If the user doesn't grant it, snooze scheduling will fail silently or use inexact alarms. → **Mitigation**: Check permission at snooze execution time; fall back to `setAndAllowWhileIdle()` (inexact) and log a warning. Future UI can prompt the user to grant the permission.

- **Snooze lost on reboot**: `AlarmManager` alarms are cleared on device reboot. → **Mitigation**: Documented as non-goal. Future change can add `RECEIVE_BOOT_COMPLETED` receiver to reschedule from the `snoozed_notifications` table.

- **Notification re-posting fidelity**: Re-posted snoozed notifications may differ from the original (no action buttons, different styling). → **Mitigation**: Store enough data to create a reasonable approximation. Users understand this is a local re-notification, not the original.

- **Database migration**: Adding columns and tables requires a Room migration. → **Mitigation**: Use incremental migration (not destructive) to preserve existing data.
