## MODIFIED Requirements

### Requirement: Snooze action triggers scheduling
(Replaces the requirement of the same name in the current spec, which incorrectly described `AlarmManager`-based scheduling and a `snoozed_notifications` database entry — neither was ever implemented.)
The system SHALL snooze the Android notification when a matched rule has an enabled `SNOOZE_NOTIFICATION` action, using `NotificationListenerService.snoozeNotification(sbnKey, durationMs)` (not `AlarmManager` — no separate scheduling infrastructure is involved). The action's `config` map SHALL determine how the duration is computed, based on `snooze_mode`:

- **`DURATION` mode (default):** the duration is read directly from `snoozeDurationMinutes` in the config, defaulting to 15 minutes if not specified, converted to milliseconds.
- **`SCHEDULED` mode:** the duration is computed as the time remaining until the next configured checkpoint (see the `snooze-scheduling` capability for checkpoint computation). If the current match is recognized as the release of a previously scheduled checkpoint, the system SHALL NOT snooze it again — the notification is left to show as posted. If the current time falls outside a configured recurrence window, the system SHALL NOT snooze it — the notification passes through immediately.

#### Scenario: Rule with duration-mode snooze action
- **WHEN** a notification matches a rule with an enabled `SNOOZE_NOTIFICATION` action in `DURATION` mode configured for 10 minutes
- **THEN** the system calls `snoozeNotification(sbnKey, 10 * 60_000L)`

#### Scenario: Snooze with default duration
- **WHEN** a notification matches a rule with an enabled `SNOOZE_NOTIFICATION` action in `DURATION` mode with no duration configured
- **THEN** the system snoozes for 15 minutes (default)

#### Scenario: Rule with scheduled-mode snooze action before the checkpoint
- **WHEN** a notification matches a rule with an enabled `SNOOZE_NOTIFICATION` action in `SCHEDULED` mode
- **AND** the current time is before the next configured checkpoint
- **THEN** the system snoozes the notification until that checkpoint

#### Scenario: Scheduled-mode release is not re-snoozed
- **WHEN** a notification reappears because a previously scheduled `SCHEDULED`-mode snooze checkpoint was reached
- **AND** the reappearance re-matches the same rule
- **THEN** the system does NOT call `snoozeNotification()` again for it
- **AND** the notification remains visible

#### Scenario: Scheduled-mode match outside the recurrence window
- **WHEN** a notification matches a rule with an enabled `SNOOZE_NOTIFICATION` action in `SCHEDULED` mode with a recurrence window configured
- **AND** the current time is outside that window
- **THEN** the system does NOT snooze the notification
- **AND** it remains visible as posted

#### Scenario: Disabled snooze action is not executed
- **WHEN** a notification matches a rule that has a disabled `SNOOZE_NOTIFICATION` action
- **THEN** the system does NOT call `snoozeNotification()`
