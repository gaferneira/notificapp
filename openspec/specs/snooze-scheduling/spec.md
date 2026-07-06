## Purpose

Defines how a snoozed notification's state is persisted and rescheduled so the notification can be re-posted after its snooze window elapses.

## Requirements

### Requirement: Snoozed notification database tracking
The system SHALL maintain a `snoozed_notifications` table to track pending snooze state. Each entry SHALL store: id, notification_id, sbn_key, wake_up_time (epoch millis), rule_id, notification_title, notification_content, notification_package_name, and created_at.

#### Scenario: Snooze entry created on snooze action
- **WHEN** a snooze action is executed for a notification
- **THEN** a row is inserted into `snoozed_notifications` with the correct wake-up time and notification data

#### Scenario: Snooze entry removed after alarm fires
- **WHEN** the snooze alarm fires and the notification is re-posted
- **THEN** the corresponding `snoozed_notifications` row is deleted

### Requirement: AlarmManager snooze scheduling
The system SHALL use `AlarmManager.setExactAndAllowWhileIdle()` to schedule snooze wake-ups. If the `SCHEDULE_EXACT_ALARM` permission is not granted (Android 12+), the system SHALL fall back to `AlarmManager.setAndAllowWhileIdle()`.

#### Scenario: Exact alarm scheduled with permission
- **WHEN** a snooze is scheduled and the app has `SCHEDULE_EXACT_ALARM` permission
- **THEN** the system uses `setExactAndAllowWhileIdle()` with the computed wake-up time

#### Scenario: Inexact alarm fallback without permission
- **WHEN** a snooze is scheduled and the app does NOT have `SCHEDULE_EXACT_ALARM` permission on Android 12+
- **THEN** the system uses `setAndAllowWhileIdle()` as a fallback
- **AND** logs a warning about imprecise timing

### Requirement: Snooze alarm receiver re-posts notification
The system SHALL include a `SnoozeAlarmReceiver` BroadcastReceiver that, when triggered by the alarm, re-posts the snoozed notification using Android's `NotificationManager`. The re-posted notification SHALL include the original title and content.

#### Scenario: Snoozed notification re-appears after duration
- **WHEN** a notification was snoozed for 10 minutes and the alarm fires
- **THEN** a new Android notification is posted with the original title and content
- **AND** the snoozed notification entry is removed from the database

#### Scenario: Snoozed notification data deleted before alarm
- **WHEN** the snooze alarm fires but the snoozed notification entry no longer exists in the database
- **THEN** no notification is posted
- **AND** the receiver completes without error

### Requirement: Snooze cancellation support
The system SHALL support cancelling a pending snooze by its database ID. Cancellation SHALL remove the `AlarmManager` alarm and delete the database entry.

#### Scenario: Pending snooze cancelled
- **WHEN** a pending snooze is cancelled by its ID
- **THEN** the `AlarmManager` alarm for that snooze is removed
- **AND** the `snoozed_notifications` row is deleted

### Requirement: Notification channel for snoozed notifications
The system SHALL create a dedicated notification channel (`snoozed_notifications`) for re-posted snoozed notifications so users can control the notification behavior independently.

#### Scenario: Snoozed notification uses dedicated channel
- **WHEN** a snoozed notification is re-posted
- **THEN** the notification is posted on the `snoozed_notifications` channel
- **AND** the channel is created if it doesn't already exist
