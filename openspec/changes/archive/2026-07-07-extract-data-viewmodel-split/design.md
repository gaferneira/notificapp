## Context

Today the extract-data flow is fully owned by `RuleEditorViewModel`. Fields live on `rule.fields` (parent state) and are mutated in place; the `SAVE_DATA` action is created eagerly inside `addField()` / `autoGenerateExtraction()`; the `ExtractDataBottomSheet` confirm button is a passive "Done" that only dismisses. The methods involved are `onActionTypeSelected` (SAVE_DATA path), `openActionForEditing` (SAVE_DATA path), `autoGenerateExtraction`, `addField`, `openFieldForEditing`, `removeField`, `onFieldSaved`, `dismissFieldSheet`, plus the state `isFieldSheetVisible` / `editingFieldId`.

The codebase already has the target pattern: `MatchingLogicViewModel` + `MatchingLogicContract` is a self-contained, Hilt-provided sub-ViewModel that seeds from an initial value, keeps a working copy, validates, and hands the result back to the parent via a one-off `UiEffect` that the composable maps to a parent event (`OnConditionSaved`). `AddFieldBottomSheet` similarly already has its own `AddFieldViewModel` and returns a `RuleField` via an `onFieldSaved` callback.

Constraint: `rule.fields` remains the committed source of truth on the parent (`RuleUiModel` → `toEntity()` reads it for save and backtest). No domain/repository/persistence changes.

## Goals / Non-Goals

**Goals:**
- Move the entire extract-data flow into a new self-contained `ExtractDataViewModel` + `ExtractDataContract`, following the `MatchingLogicViewModel` precedent.
- The sub-ViewModel owns a **draft** of the fields, the nested add/edit-field sheet visibility, and auto-generate.
- Commit-on-confirm: the `SAVE_DATA` action + fields reach the parent only when the user taps Add/Update; the button is disabled while the draft is empty; cancel/dismiss discards.
- Seed the draft from `rule.fields` when editing; commit replaces them.
- Keep the `SAVE_DATA` removal + field-clearing confirmation in `RuleEditorViewModel`.
- Add `ExtractDataViewModelTest`.

**Non-Goals:**
- No change to `AddFieldViewModel` / `AddFieldBottomSheet` internals (only who hosts it).
- No change to `MatchingLogicViewModel`, domain models, repositories, or the extraction engine.
- No change to auto-generate's field-detection logic (only where it writes — to the draft, not the rule).
- Not touching the backtest / "test against history" flow (stays on parent, reads committed fields).

## Decisions

### Decision 1: New `ExtractDataViewModel` seeded per-open, mirroring `MatchingLogicViewModel`
The sub-ViewModel is `@HiltViewModel`, obtained in `ExtractDataBottomSheet` via `hiltViewModel()`. Because a Compose-scoped `hiltViewModel()` inside the sheet is created fresh when the sheet enters composition and cleared when it leaves, the draft naturally resets between opens — the same lifecycle `MatchingLogicViewModel` relies on.

Seeding uses an explicit init event rather than constructor args (constructor can't see runtime fields): the sheet fires `UiEvent.Init(initialFields)` in a `LaunchedEffect(Unit)`, matching `MatchingLogicContract.UiEvent.InitForEdit`. For a new action the initial list is empty; for an edit it is the action's current fields.

*Alternative considered:* hoist the draft into `RuleEditorViewModel` as `draftFields`. Rejected — it keeps the extract-data state on the parent, which is exactly what we're removing, and diverges from the established sub-ViewModel pattern.

### Decision 2: Draft state + nested add-field sheet live entirely in `ExtractDataContract.UiState`
`UiState` holds `fields: List<RuleField>` (the draft), `isFieldSheetVisible: Boolean`, `editingFieldId: String?`, and an `isEditingAction: Boolean` (to pick the Add vs Update label). A derived `canConfirm = fields.isNotEmpty()` drives the disabled button. The nested `AddFieldBottomSheet` is hosted by `ExtractDataBottomSheet` and its result routes to the sub-ViewModel (`OnFieldSaved`), not the parent.

Auto-generate moves in as `onAutoGenerate()`, writing detected fields into the draft (same detection logic, minus the eager `SAVE_DATA` creation — creation now happens only on confirm).

### Decision 3: Handoff to parent via a single effect → single parent event
The sub-ViewModel confirms via `UiEffect.Committed(fields)` (plus `UiEffect.Dismiss`), which the composable maps to a callback the screen turns into one parent event, e.g. `RuleEditorContract.UiEvent.OnExtractDataCommitted(fields)`. The parent handler:
- adds the `SAVE_DATA` action if absent (respecting one-action-per-type), and
- sets `rule.fields = fields`,
in a single `setState`. This replaces the removed `autoGenerateExtraction`/`addField`/`onFieldSaved`/`removeField`/`dismissFieldSheet` and the SAVE_DATA branches of `onActionTypeSelected`/`openActionForEditing` (which now just toggle sheet visibility, carrying the initial fields).

*Alternative considered:* have the sub-ViewModel emit granular per-field events to the parent (like today). Rejected — that keeps the parent coupled to field-level mutation and defeats commit-on-confirm.

### Decision 4: Removal + field-clearing confirmation stays on the parent
`removeAction` (SAVE_DATA path), `confirmExtractDataRemoval`, `performRemoveAction(clearFields=…)`, and `pendingExtractDataRemovalId` remain in `RuleEditorViewModel`. Rationale: removal is triggered from the `DoSection` action card, which renders **committed** rule state on the main screen — not from inside the sheet. It operates on `rule.actions` + `rule.fields`, which the parent owns.

### Decision 5: Contract surface changes
Remove from `RuleEditorContract`: events `OnAutoGenerateClicked`, `OnAddFieldClicked`, `OnEditFieldClicked`, `OnRemoveFieldClicked`, `OnFieldSaved`, `OnDismissFieldSheet`; state `isFieldSheetVisible`, `editingFieldId`, and the `editingField` derived accessor. Add one event `OnExtractDataCommitted(fields)`. Keep `isActionSheetVisible` / `pendingActionType` / `editingActionId` for opening the Extract-data sheet (consistent with the other action sheets), but the SAVE_DATA branch now passes `rule.fields` as the sheet's seed and no longer creates the action on open.

## Risks / Trade-offs

- **A committed `SAVE_DATA` action with 0 fields loaded from an older rule** → the removal requirement's "empty action needs no confirmation" scenario still covers it; block-until-≥1-field only governs *new* commits, so legacy empty actions remain removable without a prompt. No migration needed.
- **Backtest ("test against history") won't see uncommitted draft fields** → acceptable and correct: backtest reads the committed rule, and it is unreachable while the sheet is open (it sits behind the modal on the main screen).
- **`hiltViewModel()` scoping subtlety** → the draft-reset-on-reopen behavior depends on the sheet leaving composition when dismissed (same assumption `MatchingLogicBottomSheet` already makes). The `Init` event is idempotent-per-open via `LaunchedEffect(Unit)`, so a recomposition won't wipe an in-progress draft.
- **Regression surface in `RuleEditorViewModelTest`** → tests asserting eager `SAVE_DATA` creation / live field mutation must be removed or moved to `ExtractDataViewModelTest`; mitigated by porting the field-level assertions into the new test.
