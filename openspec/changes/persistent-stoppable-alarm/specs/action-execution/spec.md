## MODIFIED Requirements

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
