# Delta for rule-action-authoring

## ADDED Requirements

### Requirement: Send-webhook configuration sheet
The Rule Editor SHALL present `SEND_WEBHOOK` as a configurable action type in the type-picker dialog. Selecting it SHALL open a dedicated bottom sheet where the user picks an existing webhook or creates one inline, chooses a payload mode (raw field checklist or custom JSON template), and can preview the resulting payload. The sheet SHALL follow the same commit-on-confirm pattern as the other configurable action sheets (snooze, alarm, flash) — the action is written to the rule only when the sheet's Add/Update button is tapped.

#### Scenario: Selecting Send Webhook opens its sheet
- WHEN the user taps "Send Webhook" in the action type picker
- THEN the picker dialog closes
- AND the Send Webhook configuration sheet opens

#### Scenario: Webhook picker lists existing webhooks
- GIVEN two saved webhooks
- WHEN the Send Webhook sheet is open
- THEN both webhooks are listed as selectable in the picker

#### Scenario: Inline-create a webhook from the sheet
- WHEN the user chooses to create a new webhook from within the Send Webhook sheet
- THEN a webhook creation flow is presented without leaving the Rule Editor
- AND on save the new webhook becomes the selected webhook for this action

#### Scenario: Config committed only on confirm
- WHEN the user selects a webhook and a payload mode in the sheet, then cancels or dismisses it
- THEN no `SEND_WEBHOOK` action is added to the rule
- WHEN the user instead taps the sheet's Add/Update button
- THEN the `SEND_WEBHOOK` action is committed to the rule with the selected webhook and payload config

### Requirement: Payload mode toggle
The Send Webhook sheet SHALL let the user toggle between two payload modes: a checklist of available extraction fields to include raw, or a custom JSON text template supporting `{{field}}` tokens that are substituted with extracted field values at send time.

#### Scenario: Field-checklist mode selects fields to include
- GIVEN the Send Webhook sheet in field-checklist mode
- WHEN the user checks two of the rule's available extraction fields
- THEN only those two fields are included in the action's payload config

#### Scenario: Custom template mode accepts field tokens
- GIVEN the Send Webhook sheet in custom-template mode
- WHEN the user enters a JSON template containing `{{amount}}`
- THEN the template text is saved as the action's payload template
- AND `{{amount}}` is recognized as a token referencing the rule's `amount` extraction field

### Requirement: Payload preview
The Send Webhook sheet SHALL provide a "Preview payload" action that renders the JSON payload the action would send, using sample or currently available extracted values, for both payload modes.

#### Scenario: Preview renders for field-checklist mode
- GIVEN the Send Webhook sheet in field-checklist mode with fields checked
- WHEN the user taps "Preview payload"
- THEN a JSON preview is shown containing only the checked fields with sample or current values

#### Scenario: Preview renders for custom template mode
- GIVEN the Send Webhook sheet in custom-template mode with a template containing `{{merchant}}`
- WHEN the user taps "Preview payload"
- THEN a JSON preview is shown with `{{merchant}}` substituted by a sample or current value

#### Scenario: Editing an existing webhook action reopens with its config
- WHEN the user taps an existing `SEND_WEBHOOK` action card
- THEN the sheet opens pre-populated with the previously selected webhook, payload mode, and field/template config
- AND no option to change the action type is presented
