## Why

The `RuleEditorViewModel` has grown into a monolith that owns the entire extract-data flow (draft fields, the add/edit-field sheet, auto-generate, and field mutation) alongside everything else in the editor. The extract-data fields are also mutated live on the rule and the `SAVE_DATA` action is created eagerly the moment the first field is added or auto-generated — so a half-configured "Extract data" action can leak onto the rule even if the user backs out. This mirrors the already-solved `MatchingLogicViewModel` pattern (self-contained sub-ViewModel, commit-on-confirm), which the extract-data flow should follow for consistency and testability.

## What Changes

- Introduce a new self-contained `ExtractDataViewModel` + `ExtractDataContract` that owns the extract-data flow, mirroring the existing `MatchingLogicViewModel`/`MatchingLogicContract` pattern.
- Move the following out of `RuleEditorViewModel` into the new ViewModel: a **draft** copy of the extraction fields, the nested add/edit-field sheet visibility (`isFieldSheetVisible`/`editingFieldId`), auto-generate, and field add/edit/remove.
- **BREAKING (behavioral):** the `SAVE_DATA` ("Extract data") action is committed to the rule **only when the user taps Add/Update** on the Extract-data sheet (commit-on-confirm), rather than eagerly on first field add / auto-generate.
- The Add/Update button is **disabled while the draft has zero fields**; Cancel/dismiss **discards** the draft without touching the rule.
- On edit, the draft is **seeded from the rule's current fields**; on Update the fields + action are re-committed.
- The `SAVE_DATA` removal + field-clearing confirmation **stays in `RuleEditorViewModel`** (it is driven from the `DoSection` card, which operates on already-committed rule state).
- The sub-ViewModel communicates its result back to the parent via a one-off effect that the screen maps to a parent event (same handoff as `MatchingLogicBottomSheet` → `OnConditionSaved`).
- Add unit tests for `ExtractDataViewModel`, matching the existing `RuleEditorViewModel`/`AddFieldViewModel` test style.

## Capabilities

### New Capabilities
<!-- The ExtractDataViewModel/Contract split is an implementation refactor (design.md), not a new spec-level capability. -->
- None.

### Modified Capabilities
- `rule-action-authoring`: the Extract-data action's authoring behavior changes from live mutation + eager creation to **commit-on-confirm** — the `SAVE_DATA` action and its fields are written to the rule only when the user confirms the sheet; the confirm button is disabled until at least one field exists; cancelling discards the draft; editing seeds the draft from the current fields.

## Impact

- **ViewModels:** new `features/ruleeditor/viewmodel/ExtractDataViewModel.kt`; `RuleEditorViewModel.kt` loses the extract-data methods and state handling (`autoGenerateExtraction`, `addField`, `openFieldForEditing`, `removeField`, `onFieldSaved`, `dismissFieldSheet`, and the SAVE_DATA paths of `onActionTypeSelected`/`openActionForEditing`).
- **Contracts:** new `features/ruleeditor/contract/ExtractDataContract.kt`; `RuleEditorContract.kt` loses the extract-data-only events/state (`OnAutoGenerateClicked`, `OnAddFieldClicked`, `OnEditFieldClicked`, `OnRemoveFieldClicked`, `OnFieldSaved`, `OnDismissFieldSheet`, `isFieldSheetVisible`, `editingFieldId`) and gains a single "extract-data committed" event.
- **UI:** `ExtractDataBottomSheet.kt` is driven by the new ViewModel and gains an Add/Update confirm button (replacing "Done") with disabled-when-empty behavior; `AddFieldBottomSheet` hosting moves under the Extract-data sheet's ViewModel; `RuleEditorScreen.kt` wiring updated for the new handoff. `MatchingLogicViewModel` is the reference pattern.
- **Tests:** new `ExtractDataViewModelTest`; existing `RuleEditorViewModelTest` updated for the removed extract-data responsibilities.
- No changes to domain models, repositories, persistence, or the extraction engine — `rule.fields` remains the committed source of truth on the parent.
