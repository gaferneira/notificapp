## Why

The app already has UI for configuring snooze and dismiss actions on rules, and the RuleEngine identifies which actions should trigger — but nothing actually executes them. Snooze duration config is also lost on save because `RuleActionEntity` lacks a `config` field. This change closes the loop so that rule-based snooze and dismiss actions work end-to-end, from rule matching through to actual notification dismissal or snooze scheduling.

## What Changes

- **Persist action config**: Add `config` column to `RuleActionEntity` and update mappers so snooze duration (and future action parameters) survive database round-trips.
- **Action executor**: Introduce an `ActionExecutor` that the `NotificappListenerService` calls after `RuleEngine.process()` to perform triggered actions (dismiss via `cancelNotification`, snooze via `AlarmManager` + re-post).
- **Snooze scheduling**: Use `AlarmManager` exact alarms to re-surface snoozed notifications after the configured duration. Include a `BroadcastReceiver` to handle the alarm and re-post the notification.
- **Snoozed notification tracking**: Add a `snoozed_notifications` table to track snooze state (notification ID, wake-up time, original notification data) so the app can list and cancel pending snoozes.
- **Dismiss via NotificationListenerService**: Use the service's `cancelNotification(key)` API to programmatically dismiss matched notifications.

## Capabilities

### New Capabilities
- `action-execution`: Core engine that executes triggered rule actions (dismiss, snooze) after rule matching completes
- `snooze-scheduling`: AlarmManager-based scheduling and re-posting of snoozed notifications

### Modified Capabilities

## Impact

- **Database**: Migration to add `config` column to `rule_actions` table and new `snoozed_notifications` table.
- **NotificappListenerService**: Gains action execution step after rule processing; needs to pass `StatusBarNotification` key for dismiss support.
- **RuleEngine / Extraction layer**: No changes — action execution is a separate concern downstream.
- **Permissions**: App may need `SCHEDULE_EXACT_ALARM` permission on Android 12+ for snooze scheduling.
- **Existing UI**: No UI changes required — the ActionBottomSheet and Rule Editor already support configuring these actions.
