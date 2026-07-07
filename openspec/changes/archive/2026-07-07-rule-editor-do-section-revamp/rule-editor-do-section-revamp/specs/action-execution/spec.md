## ADDED Requirements

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
