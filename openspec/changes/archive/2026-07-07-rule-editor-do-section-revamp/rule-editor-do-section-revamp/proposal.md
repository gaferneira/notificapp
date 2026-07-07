## Why

The Rule Editor's "Do" section conflates two concepts and hides a misleading toggle. Extraction fields (`rule.fields`) and the `SAVE_DATA` action are decoupled: `ProcessNotificationUseCase` persists `match.rule.fields` on **every** match regardless of the action, and `SaveDataActionExecutor` is an explicit no-op. So disabling "Save to Data Lab" still saves data — the toggle lies. The single monolithic action sheet that lists all action types (and lets you keep adding duplicates) compounds the confusion.

## What Changes

- **Add-action flow**: the "Add action" button opens a **type-picker dialog** listing only action types not yet configured on the rule. Selecting a type opens a **type-scoped bottom sheet** to configure and save just that action.
- **Edit flow**: tapping an existing action card opens its type-scoped sheet (type is fixed, no type switching).
- **One action per type**: enforced in the ViewModel; the picker filters out already-configured types. When every type is configured, the "Add action" affordance is disabled/hidden.
- **Rename `SAVE_DATA` display to "Extract data"** and fold the Data Extraction module (the current "And then" section: `rule.fields` list, add/edit/remove field, auto-generate) into that action's bottom sheet. The standalone "And then" section is removed from the main screen.
- **BREAKING (behavior): the "Extract data" action now gates extraction persistence.** When no enabled "Extract data" action is present on a matched rule, its fields are **not** extracted or persisted. Removing the action clears the rule's fields.
- Retire the all-types monolithic selection UI inside `ActionBottomSheet` (type is chosen upstream in the dialog).

## Capabilities

### New Capabilities
- `rule-action-authoring`: How a user adds, edits, and removes rule actions in the Rule Editor — the type-picker dialog, one-action-per-type constraint, type-scoped configuration sheets (including the "Extract data" sheet that hosts field extraction), and edit-existing behavior.

### Modified Capabilities
- `action-execution`: Field extraction and persistence become gated by an enabled `SAVE_DATA` ("Extract data") action, instead of running on every match. Governs engine behavior in `ProcessNotificationUseCase` / `RuleExecutionRepository.saveExecution`.

## Impact

- **UI**: `features/ruleeditor/ui/RuleEditorScreen.kt` (remove standalone extraction section, add type-picker dialog wiring), `ui/ActionBottomSheet.kt` (drop all-types selector, render single pre-selected type), `ui/components/DoSection.kt` and `ui/components/DataExtractionSection.kt` (extraction moves into the sheet), new type-picker dialog component.
- **Contract/VM**: `contract/ActionBottomSheetContract.kt`, `contract/RuleEditorContract.kt`, `viewmodel/RuleEditorViewModel.kt`, `viewmodel/ActionBottomSheetViewModel.kt` — pre-selected type, one-per-type enforcement, field editing inside the action sheet, remove-clears-fields.
- **Engine**: `core/notification/ProcessNotificationUseCase.kt` — only persist `rule.fields` when an enabled `SAVE_DATA` action exists; `core/notification/action/SaveDataActionExecutor.kt` semantics.
- **Domain**: `RuleUiModel`/`Rule` field lifecycle relative to the `SAVE_DATA` action; display-name mapping ("Extract data").
- **Tests**: `RuleEditorViewModel`, `ActionBottomSheetViewModel`, `ProcessNotificationUseCase` gating tests; strings in `res/values/strings.xml`.
