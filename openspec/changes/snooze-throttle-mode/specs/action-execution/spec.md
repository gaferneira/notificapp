# Delta for Action Execution

## MODIFIED Requirements

### Requirement: Snooze action triggers scheduling
(Replaces the requirement of the same name in the current spec, which incorrectly described `AlarmManager`-based scheduling and a `snoozed_notifications` database entry — neither was ever implemented.)
The system SHALL snooze or drop the Android notification when a matched rule has an enabled `SNOOZE_NOTIFICATION` action, using `NotificationListenerService.snoozeNotification(sbnKey, durationMs)` for delivery-delaying modes and `SystemNotificationController.cancel(sbnKey)` for the drop path (not `AlarmManager` — no separate scheduling infrastructure is involved). The action's `config` map SHALL determine behavior, based on `snooze_mode`:

- **`DURATION` mode (default):** the duration is read directly from `snoozeDurationMinutes` in the config, defaulting to 15 minutes if not specified, converted to milliseconds.
- **`SCHEDULED` mode:** the duration is computed as the time remaining until the next configured checkpoint (see the `snooze-scheduling` capability for checkpoint computation). If the current match is recognized as the release of a previously scheduled checkpoint, the system SHALL NOT snooze it again — the notification is left to show as posted. If the current time falls outside a configured recurrence window, the system SHALL NOT snooze it — the notification passes through immediately.
- **`THROTTLE` mode:** the first match within the configured window SHALL post normally (no `snoozeNotification()` call); every subsequent match within that same window SHALL be dropped via `SystemNotificationController.cancel(sbnKey)` (never delayed, queued, or rescheduled). See the `snooze-scheduling` capability for window and scope semantics.

(Previously: covered only `DURATION` and `SCHEDULED` modes; the drop path via `cancel()` did not exist for this action.)

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

#### Scenario: Throttle mode delivers the first match in a window
- **WHEN** a notification matches a rule with an enabled `SNOOZE_NOTIFICATION` action in `THROTTLE` mode
- **AND** no prior delivery is recorded for that rule+app within the configured window
- **THEN** the notification posts normally
- **AND** the system does NOT call `snoozeNotification()` or `cancel()`

#### Scenario: Throttle mode drops a subsequent match in the same window
- **WHEN** a notification matches a rule with an enabled `SNOOZE_NOTIFICATION` action in `THROTTLE` mode
- **AND** a match for the same rule+app already delivered inside the current window
- **THEN** the system calls `cancel(sbnKey)` for the new match
- **AND** it does NOT call `snoozeNotification()`

## ADDED Requirements

### Requirement: Throttle-suppressed matches are recorded with a distinct outcome
The system SHALL record a match suppressed by `THROTTLE` mode in `RuleExecution` history using a dedicated `ActionOutcome` value distinct from `SUCCESS`, `FAILED`, and `SKIPPED`, so the audit trail can distinguish "matched but muted by throttle" from other outcomes.

#### Scenario: Suppressed match is visible in execution history
- **WHEN** a match is dropped by an enabled `THROTTLE`-mode `SNOOZE_NOTIFICATION` action
- **THEN** a `RuleExecution` record is created for that match
- **AND** its action outcome is the throttle-suppressed value, not `SUCCESS`, `FAILED`, or `SKIPPED`

#### Scenario: Delivered throttle match records success
- **WHEN** a match is the first-through delivery of a `THROTTLE`-mode `SNOOZE_NOTIFICATION` action
- **THEN** the recorded action outcome is `SUCCESS`
