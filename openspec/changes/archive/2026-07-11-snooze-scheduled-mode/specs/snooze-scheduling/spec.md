## REMOVED Requirements

### Requirement: Snoozed notification database tracking
Removed — never implemented. No `snoozed_notifications` table exists; snooze state is owned entirely by the Android OS via `NotificationListenerService.snoozeNotification()`.

### Requirement: AlarmManager snooze scheduling
Removed — never implemented. Snoozing does not use `AlarmManager`; it calls the native `snoozeNotification()` API, which the OS itself is responsible for scheduling.

### Requirement: Snooze alarm receiver re-posts notification
Removed — never implemented. There is no `SnoozeAlarmReceiver`; the OS re-posts the snoozed notification itself once the native snooze duration elapses.

### Requirement: Snooze cancellation support
Removed — never implemented. There is no app-owned pending-snooze record to cancel.

### Requirement: Notification channel for snoozed notifications
Removed — never implemented. Snoozed notifications are re-posted by the OS as the original notification; the app does not create a dedicated channel or repost it itself.

## ADDED Requirements

### Requirement: Duration-mode snoozing uses the native OS snooze
The system SHALL snooze notifications using `NotificationListenerService.snoozeNotification(sbnKey, durationMs)`. In `DURATION` mode this is the only scheduling mechanism involved — the OS owns hiding and re-posting the notification after the given duration; the app does not track or reschedule it.

#### Scenario: Notification hidden and reappears after the OS-managed duration
- **WHEN** a notification is snoozed for a duration in `DURATION` mode
- **THEN** the notification disappears from the shade
- **AND** it reappears automatically once the OS's snooze duration elapses, with no app-side scheduling involved

### Requirement: Scheduled mode — single daily checkpoint
The system SHALL support configuring a `SCHEDULED` snooze with a start time (hour and minute) and no interval, meaning exactly one checkpoint per day. A match before that time on a given day SHALL be snoozed until that time; a match at or after that time SHALL be snoozed until the same time on the next day.

#### Scenario: Match before today's start time
- **WHEN** a notification matches a `SCHEDULED`-mode snooze configured with start time 09:00 and no interval
- **AND** the current time is before 09:00
- **THEN** the notification is snoozed until 09:00 today

#### Scenario: Match after today's start time rolls to tomorrow
- **WHEN** a notification matches a `SCHEDULED`-mode snooze configured with start time 09:00 and no interval
- **AND** the current time is at or after 09:00
- **THEN** the notification is snoozed until 09:00 tomorrow

### Requirement: Scheduled mode — recurring checkpoints within a window
The system SHALL support configuring a `SCHEDULED` snooze with a start time, a repeat interval (in minutes), and a window end time. A match at or after the start time and before the window end SHALL be snoozed until the next `start + k × interval` checkpoint. A match at or after the window end SHALL NOT be snoozed — it passes through immediately, unmodified.

#### Scenario: Match inside the recurrence window
- **WHEN** a notification matches a `SCHEDULED`-mode snooze configured with start 09:00, interval 60 minutes, window end 18:00
- **AND** the current time is 09:30
- **THEN** the notification is snoozed until 10:00

#### Scenario: Match at or after the window end
- **WHEN** a notification matches a `SCHEDULED`-mode snooze configured with start 09:00, interval 60 minutes, window end 18:00
- **AND** the current time is 18:15
- **THEN** the notification is not snoozed and passes through immediately

### Requirement: Scheduled checkpoint release is not immediately re-batched
Because a re-posted (checkpoint-released) notification re-enters rule evaluation like any other post, the system SHALL track which notifications are expected to reappear from a scheduled release and SHALL NOT re-snooze that specific reappearance to the next checkpoint. The tracking SHALL be keyed by the notification's system key (`sbnKey`) and consumed (cleared) on the first post event observed for that key after a schedule was set.

#### Scenario: Checkpoint release shows the notification
- **WHEN** a `SCHEDULED`-mode snooze reaches its checkpoint and the OS reposts the notification
- **THEN** the reappearance is recognized as the expected release
- **AND** the notification is left visible, not re-snoozed to the next checkpoint

#### Scenario: A genuinely new match after a release is batched normally
- **WHEN** a new notification for the same app arrives after a previous scheduled release was already consumed
- **THEN** it is treated as a fresh match and snoozed until the next applicable checkpoint per the configured schedule
