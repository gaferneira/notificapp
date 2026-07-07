## Purpose

Defines how a matched rule's actions are executed on-device: one `ActionExecutor` per `ActionType` (via Hilt multibindings), how action configuration is persisted, and how per-action outcomes are recorded so execution history reflects what actually happened.
## Requirements
### Requirement: Action config persistence
The system SHALL persist the `config` map of each `RuleAction` to the database so that action parameters (e.g., snooze duration) survive save/load cycles. The `RuleActionEntity` SHALL include a `config` TEXT column storing JSON-serialized `Map<String, String>`. Mappers SHALL round-trip the config without data loss.

#### Scenario: Snooze duration survives database round-trip
- **WHEN** a rule with a snooze action configured for 30 minutes is saved and then loaded
- **THEN** the loaded `RuleAction` has `config["snoozeDurationMinutes"] == "30"`

#### Scenario: Action with empty config
- **WHEN** a rule with a dismiss action (no config) is saved and loaded
- **THEN** the loaded `RuleAction` has an empty `config` map

### Requirement: SBN key captured with notifications
The system SHALL capture and persist the `StatusBarNotification.key` when storing incoming notifications. The domain `Notification` model and `NotificationEntity` SHALL include an `sbnKey` field.

#### Scenario: SBN key stored on notification capture
- **WHEN** the `NotificappListenerService` receives a notification with SBN key `0|com.example|123|null|10042`
- **THEN** the persisted `Notification` has `sbnKey == "0|com.example|123|null|10042"`

### Requirement: Dismiss action execution
The system SHALL dismiss the Android notification when a matched rule has an enabled `DISMISS_NOTIFICATION` action. Dismissal SHALL use `NotificationListenerService.cancelNotification(sbnKey)`.

#### Scenario: Rule with dismiss action dismisses notification
- **WHEN** a notification matches a rule that has an enabled `DISMISS_NOTIFICATION` action
- **THEN** the system calls `cancelNotification()` with the notification's SBN key
- **AND** the notification is removed from the Android notification shade

#### Scenario: Disabled dismiss action is not executed
- **WHEN** a notification matches a rule that has a disabled `DISMISS_NOTIFICATION` action
- **THEN** the system does NOT call `cancelNotification()`
- **AND** the notification remains in the notification shade

### Requirement: Snooze action triggers scheduling
The system SHALL schedule a snooze alarm when a matched rule has an enabled `SNOOZE_NOTIFICATION` action. The snooze duration SHALL be read from the action's `config` map, defaulting to 15 minutes if not specified.

#### Scenario: Rule with snooze action schedules alarm
- **WHEN** a notification matches a rule with an enabled `SNOOZE_NOTIFICATION` action configured for 10 minutes
- **THEN** the system dismisses the original notification
- **AND** schedules a snooze alarm to fire in 10 minutes
- **AND** records a snoozed notification entry in the database

#### Scenario: Snooze with default duration
- **WHEN** a notification matches a rule with an enabled `SNOOZE_NOTIFICATION` action that has no duration configured
- **THEN** the system schedules a snooze alarm to fire in 15 minutes (default)

### Requirement: ActionExecutor interface
The system SHALL provide an `ActionExecutor` interface in the domain layer that accepts a notification and a list of triggered `RuleAction` objects, and executes each action. The Android implementation SHALL be injected via Hilt.

An executor MAY hand an action off to a longer-lived component (for example, a foreground service) rather than completing all of the action's observable effects within the `execute` call. For such actions, an `ActionOutcome.SUCCESS` return SHALL mean "the action was successfully initiated," not "the action's effects have finished." The `CREATE_ALARM` executor works this way: it starts the alarm foreground service and returns, while the alarm continues ringing under that service's ownership.

#### Scenario: Multiple actions on same notification
- **WHEN** a notification matches a rule with both `SAVE_DATA` and `DISMISS_NOTIFICATION` actions enabled
- **THEN** the system executes all enabled actions for that rule match

#### Scenario: Action execution failure does not block other actions
- **WHEN** a dismiss action fails (e.g., notification already dismissed)
- **THEN** remaining actions for that rule match are still executed
- **AND** the failure is logged

#### Scenario: Alarm executor delegates to a longer-lived owner
- **WHEN** a notification matches a rule with an enabled `CREATE_ALARM` action
- **THEN** the executor starts the alarm foreground service and returns `ActionOutcome.SUCCESS`
- **AND** the alarm keeps ringing after `execute` has returned

### Requirement: Field persistence gated by the Extract-data action
The system SHALL persist a rule's extracted field values only when that rule has an enabled `SAVE_DATA` ("Extract data") action. When no enabled `SAVE_DATA` action is present on a matched rule, the system SHALL NOT persist extracted field values for that match, while still recording the rule execution and the outcomes of any other enabled actions.

The `SAVE_DATA` action's persistence effect is a pipeline concern: `SaveDataActionExecutor` SHALL remain a no-op that returns `ActionOutcome.SUCCESS`, and the gating SHALL be applied where the execution is saved (`ProcessNotificationUseCase` / `RuleExecutionRepository.saveExecution`), not inside `RuleEngine` or `FieldExtractor`, which SHALL remain action-agnostic pure Kotlin.

#### Scenario: Fields persisted when Extract-data action is enabled
- **WHEN** a notification matches a rule that has fields and an enabled `SAVE_DATA` action
- **THEN** the extracted field values are persisted with the rule execution

#### Scenario: Fields not persisted without an enabled Extract-data action
- **WHEN** a notification matches a rule that has fields but no `SAVE_DATA` action, or whose `SAVE_DATA` action is disabled
- **THEN** no extracted field values are persisted for that match
- **AND** the rule execution is still recorded

#### Scenario: Other actions still recorded when extraction is gated off
- **WHEN** a notification matches a rule with an enabled `CREATE_ALARM` action and no enabled `SAVE_DATA` action
- **THEN** the alarm action executes and its outcome is recorded on the rule execution
- **AND** no extracted field values are persisted

### Requirement: Legacy rules with fields retain an Extract-data action
Because extraction fields and the `SAVE_DATA` action were previously decoupled, a stored rule MAY have non-empty fields without a `SAVE_DATA` action. To preserve existing capture behavior after gating is introduced, the system SHALL ensure that any rule with non-empty fields and no `SAVE_DATA` action is normalized to include an enabled `SAVE_DATA` action (at load time or via migration).

#### Scenario: Fields-bearing rule without action is normalized
- **WHEN** a stored rule has non-empty fields and no `SAVE_DATA` action
- **THEN** the system treats the rule as having an enabled `SAVE_DATA` action so its field values continue to be persisted

