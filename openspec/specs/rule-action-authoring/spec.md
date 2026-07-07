# rule-action-authoring Specification

## Purpose

Defines the Rule Editor's action-authoring workflow: type-picker dialog for adding actions, per-type configuration sheets (snooze, alarm, flash, dismiss, extract-data), one-action-per-type constraint, and special handling for extract-data as a hosted field-extraction module with draft management and field-persistence gating.
## Requirements
### Requirement: Add action via type-picker dialog
The Rule Editor SHALL let the user add an action by first choosing its type from a dialog. The "Add action" affordance SHALL open a dialog that lists action types. Selecting a type SHALL dismiss the dialog and then either open a type-scoped configuration sheet (for types with configuration) or add the action directly (for types with no configuration).

#### Scenario: Opening the type picker
- **WHEN** the user taps "Add action"
- **THEN** a dialog listing selectable action types is shown

#### Scenario: Selecting a configurable type opens its configuration sheet
- **WHEN** the user taps a configurable action type (snooze, alarm, flash, or extract data) in the picker dialog
- **THEN** the dialog closes
- **AND** that type's own bottom sheet opens for configuration

#### Scenario: Selecting a no-config type adds it directly
- **WHEN** the user taps a type with no configuration (dismiss) in the picker dialog
- **THEN** the dialog closes
- **AND** the action is added to the rule with no bottom sheet shown

### Requirement: Each action type owns its configuration surface
Each action type SHALL own its own configuration surface so per-type logic is isolated, rather than sharing one bottom sheet that branches by type. Types with no configuration (dismiss) SHALL NOT present a bottom sheet. Snooze, alarm, and flash SHALL each present their own type-scoped sheet. Editing an action SHALL open (or, for no-config types, not open) the same surface used when adding it.

#### Scenario: Dismiss has no configuration sheet
- **WHEN** the user adds or taps a dismiss action
- **THEN** no configuration bottom sheet is shown

#### Scenario: Configurable types present their own sheet
- **WHEN** the user adds a snooze, alarm, or flash action
- **THEN** a bottom sheet dedicated to that type is shown

### Requirement: Alarm requests notification permission on commit
The alarm action's sheet SHALL request notification permission when the user commits the action (taps its confirm/"Add" button), not when the sheet is opened or the type is selected.

#### Scenario: Permission requested on add, not on open
- **WHEN** the user opens the alarm sheet with notification permission missing
- **THEN** no permission request is triggered by opening the sheet
- **WHEN** the user then taps the sheet's confirm button
- **THEN** the notification permission request is triggered

### Requirement: One action per type
A rule SHALL contain at most one action of each `ActionType`. The type-picker dialog SHALL list only types that are not already configured on the rule. When every type is already configured, the "Add action" affordance SHALL be disabled or hidden. The ViewModel SHALL reject adding a second action of an already-configured type.

#### Scenario: Configured types are excluded from the picker
- **WHEN** the rule already has a `CREATE_ALARM` action and the user opens the picker
- **THEN** `CREATE_ALARM` is not listed among the selectable types

#### Scenario: Add affordance unavailable when all types configured
- **WHEN** the rule has one action of every available type
- **THEN** the "Add action" affordance is disabled or hidden

### Requirement: Edit existing action in its type-scoped sheet
Tapping an existing configurable action card SHALL open the bottom sheet for that action's type, pre-populated with its current configuration. The action's type SHALL be fixed while editing (no type switching). Tapping a no-config action (dismiss) SHALL do nothing.

#### Scenario: Editing preserves type
- **WHEN** the user taps an existing snooze action card
- **THEN** the snooze configuration sheet opens with the action's current duration
- **AND** no option to change the action type is presented

### Requirement: Extract-data action hosts field extraction
The `SAVE_DATA` action SHALL be presented as "Extract data". Its configuration sheet SHALL host the data-extraction module — the list of extraction fields with add, edit, and remove, plus auto-generate. The Rule Editor SHALL NOT show a standalone data-extraction section outside this action. While the sheet is open, the field list SHALL be edited on a **draft** that is independent of the rule; the rule's committed fields SHALL NOT change until the sheet is confirmed.

#### Scenario: Extract-data sheet shows the field module
- **WHEN** the user opens the "Extract data" action sheet
- **THEN** the sheet shows the extraction fields with add, edit, remove, and auto-generate controls

#### Scenario: No standalone extraction section
- **WHEN** the Rule Editor logic step is shown
- **THEN** there is no separate "And then" / data-extraction section outside the Extract-data action

#### Scenario: Editing the field list does not touch the rule until confirmed
- **WHEN** the user adds, edits, removes, or auto-generates fields inside the open Extract-data sheet
- **THEN** the rule's committed fields and its list of actions are unchanged until the user confirms the sheet

### Requirement: Extract-data action is committed on confirm
The "Extract data" action SHALL be written to the rule only when the user confirms the sheet with its Add/Update button (commit-on-confirm). Opening the sheet, or editing its draft fields, SHALL NOT by itself create or modify the `SAVE_DATA` action on the rule. Cancelling or dismissing the sheet SHALL discard the draft and leave the rule unchanged.

When adding a new Extract-data action, confirming SHALL add the `SAVE_DATA` action together with its draft fields. When editing an existing Extract-data action, the draft SHALL be seeded from the action's current fields and confirming SHALL replace the rule's fields with the draft.

#### Scenario: New action created only on confirm
- **WHEN** the user selects "Extract data" from the type picker, adds one or more fields, and taps the sheet's Add button
- **THEN** the `SAVE_DATA` action and those fields are committed to the rule

#### Scenario: Cancelling a new action commits nothing
- **WHEN** the user selects "Extract data", adds one or more fields, and then cancels or dismisses the sheet
- **THEN** no `SAVE_DATA` action is added to the rule
- **AND** the rule's fields are unchanged

#### Scenario: Editing seeds the draft and commits on update
- **WHEN** the user taps an existing "Extract data" action card
- **THEN** the sheet opens with the action's current fields as the draft
- **WHEN** the user changes the fields and taps the sheet's Update button
- **THEN** the rule's fields are replaced with the edited draft

#### Scenario: Cancelling an edit reverts to committed fields
- **WHEN** the user edits an existing "Extract data" action's fields and then cancels or dismisses the sheet
- **THEN** the rule's committed fields are unchanged

### Requirement: Extract-data confirm requires at least one field
The Extract-data sheet's Add/Update button SHALL be disabled while the draft contains zero fields, so a `SAVE_DATA` action is never committed without at least one extraction field.

#### Scenario: Confirm disabled with no fields
- **WHEN** the Extract-data sheet is open and the draft has no fields
- **THEN** the sheet's Add/Update button is disabled

#### Scenario: Confirm enabled once a field exists
- **WHEN** the draft has at least one field
- **THEN** the sheet's Add/Update button is enabled

### Requirement: Removing Extract-data action clears fields
Removing the "Extract data" action SHALL clear the rule's extraction fields, since fields have no effect without the action. When the rule has non-empty fields, the system SHALL confirm before removing the action and clearing the fields.

#### Scenario: Confirm before clearing fields
- **WHEN** the user removes an "Extract data" action that has fields
- **THEN** the system asks for confirmation
- **AND** on confirmation the action is removed and the rule's fields are cleared

#### Scenario: Removing empty Extract-data action needs no confirmation
- **WHEN** the user removes an "Extract data" action that has no fields
- **THEN** the action is removed without a confirmation prompt

