## MODIFIED Requirements

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

## ADDED Requirements

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
