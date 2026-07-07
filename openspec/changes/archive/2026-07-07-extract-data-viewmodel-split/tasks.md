## 1. New contract

- [x] 1.1 Create `features/ruleeditor/contract/ExtractDataContract.kt` with `UiState` (`fields: List<RuleField>` draft, `isFieldSheetVisible`, `editingFieldId`, `isEditingAction`, derived `canConfirm = fields.isNotEmpty()`, derived `editingField`), `UiEvent` (`Init(initialFields, isEditingAction)`, `OnAutoGenerate`, `OnAddFieldClicked`, `OnEditFieldClicked(fieldId)`, `OnRemoveFieldClicked(fieldId)`, `OnFieldSaved(field)`, `OnDismissFieldSheet`, `OnConfirm`, `OnDismiss`), `UiEffect` (`Committed(fields)`, `Dismiss`). Mirror `MatchingLogicContract`.

## 2. New ViewModel

- [x] 2.1 Create `features/ruleeditor/viewmodel/ExtractDataViewModel.kt` as `@HiltViewModel`, extending `MviViewModel<ExtractDataContract.UiState, UiEvent, UiEffect>`, self-contained (no parent dependencies), following `MatchingLogicViewModel`.
- [x] 2.2 Implement `Init` (seed draft from `initialFields`, set `isEditingAction`), field add/edit/remove on the draft, and `OnFieldSaved` (add or replace by `editingFieldId`, then close the field sheet).
- [x] 2.3 Move `autoGenerateExtraction`'s field-detection into `onAutoGenerate()` writing detected fields into the draft only (no `SAVE_DATA` creation here).
- [x] 2.4 Implement `OnConfirm`: guard on `fields.isNotEmpty()`, emit `UiEffect.Committed(fields)` then `UiEffect.Dismiss`. Implement `OnDismiss`: reset state, emit `UiEffect.Dismiss` (discard draft).

## 3. Wire the Extract-data sheet to the new ViewModel

- [x] 3.1 Update `ExtractDataBottomSheet` to take `initialFields`, `isEditingAction`, `notification`, `onCommitted(fields)`, `onDismiss`; obtain `ExtractDataViewModel` via `hiltViewModel()`; fire `Init` in `LaunchedEffect(Unit)`; collect effects via `CollectOneOffEffects` mapping `Committed` → `onCommitted`, `Dismiss` → `onDismiss`.
- [x] 3.2 Drive the field list, auto-generate, and add-field button from the sub-ViewModel's state/events; host `AddFieldBottomSheet` inside the sheet, routing its `onFieldSaved` to `OnFieldSaved` and dismiss to `OnDismissFieldSheet`.
- [x] 3.3 Replace the "Done" confirm with an Add Action/Update button (label from `isEditingAction`, e.g. reuse `confirmLabelFor`) wired to `OnConfirm` and **disabled when `!canConfirm`**.

## 4. Slim down the parent contract & ViewModel

- [x] 4.1 In `RuleEditorContract`: remove events `OnAutoGenerateClicked`, `OnAddFieldClicked`, `OnEditFieldClicked`, `OnRemoveFieldClicked`, `OnFieldSaved`, `OnDismissFieldSheet`; remove state `isFieldSheetVisible`, `editingFieldId` and the `editingField` accessor; add `OnExtractDataCommitted(val fields: List<RuleField>)`.
- [x] 4.2 In `RuleEditorViewModel`: delete `autoGenerateExtraction`, `addField`, `openFieldForEditing`, `removeField`, `onFieldSaved`, `dismissFieldSheet`; remove their `onEvent` branches; add `onExtractDataCommitted(fields)` that, in one `setState`, adds the `SAVE_DATA` action if absent (respect one-action-per-type) and sets `rule.fields = fields`.
- [x] 4.3 Update the SAVE_DATA paths of `onActionTypeSelected` / `openActionForEditing` to only open the sheet (no eager action creation), carrying the seed fields (empty for new, `rule.fields` for edit) — no other action-type behavior changes.
- [x] 4.4 Keep `removeAction` (SAVE_DATA path), `confirmExtractDataRemoval`, `performRemoveAction`, and `pendingExtractDataRemovalId` on the parent; verify `dismissBottomSheet` no longer references removed field-sheet state.

## 5. Screen wiring

- [x] 5.1 In `RuleEditorScreen`, update the SAVE_DATA branch of `RuleEditorBottomSheets` to call `ExtractDataBottomSheet` with `initialFields = uiState.rule.fields`, `isEditingAction = editingAction?.type == SAVE_DATA`, `onCommitted = { onEvent(OnExtractDataCommitted(it)) }`, `onDismiss = { onEvent(OnDismissSheet) }`; remove the standalone `AddFieldBottomSheet` host and its `isFieldSheetVisible` block (now nested inside the Extract-data sheet).
- [x] 5.2 Remove `ExtractionFieldCallbacks`-based field wiring from the screen where it now belongs to the sub-ViewModel; update imports.

## 6. Tests

- [x] 6.1 Add `ExtractDataViewModelTest` (JUnit5 + Kotest + Turbine, `MockKExtension`): `Init` seeds draft; add/edit/remove field on draft; auto-generate populates draft; `canConfirm` false when empty / true with ≥1 field; `OnConfirm` emits `Committed(fields)` + `Dismiss`; `OnDismiss` discards without `Committed`. Reuse `testutil/TestFixtures.kt`.
- [x] 6.2 Update `RuleEditorViewModelTest`: remove assertions on eager `SAVE_DATA` creation / live field mutation; add coverage for `onExtractDataCommitted` (adds action once, sets fields) and that the removal/field-clearing flow is unchanged.

## 7. Verification

- [x] 7.1 Run `./gradlew spotlessApply detekt test` and fix any new detekt/baseline or test regressions.
- [x] 7.2 Manually verify (or via preview) the four flows: new action commit, new action cancel (nothing committed), edit action update, edit action cancel (fields unchanged); and Add/Update disabled with zero fields.
