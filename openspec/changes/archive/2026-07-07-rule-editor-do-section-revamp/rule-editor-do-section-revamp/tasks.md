## 1. Engine gating & normalization (D4, D5)

- [x] 1.1 Add a helper on `Rule`/`RuleUiModel` (e.g. `hasEnabledExtractDataAction`) that reports whether an enabled `SAVE_DATA` action is present
- [x] 1.2 In `ProcessNotificationUseCase.evaluateAndPersist`, gate persisted fields: pass `rule.fields` to `saveExecution` only when the rule has an enabled `SAVE_DATA` action, else pass an empty list; keep the execution record for other actions
- [x] 1.3 Update `SaveDataActionExecutor` KDoc to state persistence is gated at the pipeline boundary (executor stays a no-op)
- [x] 1.4 Add load-time normalization: any rule with non-empty fields and no `SAVE_DATA` action is treated as having an enabled `SAVE_DATA` action (repository/mapper load path, or Room migration)
- [x] 1.5 Unit tests in `ProcessNotificationUseCase` test: fields persisted with enabled `SAVE_DATA`; not persisted without it / when disabled; other action outcomes still recorded; normalization applies to legacy fields-only rules (mapper test)

## 2. Action authoring: type-picker dialog & one-per-type (rule-action-authoring)

- [x] 2.1 Add a display-name/description/icon mapping keyed by `ActionType` (single source for picker + `DoSection` cards), renaming `SAVE_DATA` to "Extract data"
- [x] 2.2 Create an action type-picker dialog composable that takes the list of available (un-configured) types and emits the chosen type
- [x] 2.3 Add contract/state to `RuleEditorContract` + `RuleEditorViewModel`: compute available types (all types minus configured), show/hide picker, disable/hide "Add action" when all types configured, reject duplicate-type adds
- [x] 2.4 Wire `LogicStep` "Add action" to open the picker; on type chosen, open `ActionBottomSheet` seeded with that type
- [x] 2.5 Wire existing action card tap to open `ActionBottomSheet` seeded from the action (type fixed)

## 3. Type-scoped action sheet (D1)

- [x] 3.1 Remove the all-types `ActionTypeCard` selector from `ActionBottomSheet`/`ActionsContent`; render header + the pre-selected type's `actionconfig/*` block only
- [x] 3.2 Update `ActionBottomSheetContract`/`ActionBottomSheetViewModel` to initialize from a required pre-selected `ActionType` (add path) or an existing action (edit path); drop `OnActionTypeChange` (added `InitForAdd`)
- [x] 3.3 Ensure Snooze/Alarm/Flash config still render and save correctly through the seeded-type path (reuse existing `actionconfig/*` selectors)

## 4. Extract-data sheet hosts field extraction (D2, D3)

- [x] 4.1 Render the extraction module (field list add/edit/remove + auto-generate) in a dedicated `ExtractDataBottomSheet` for the `SAVE_DATA` type, reusing `AddFieldBottomSheet` for the field editor
- [x] 4.2 Route field events (add/edit/remove/auto-generate) so edits update `rule.fields` on the parent `RuleEditorViewModel`
- [x] 4.3 Remove the standalone `DataExtractionSection` usage from `LogicStep`; repurposed the component as the Extract-data sheet content
- [x] 4.4 On removing the "Extract data" action: if `rule.fields` non-empty, show a confirmation dialog; on confirm, remove the action and clear `rule.fields`; remove without prompt when fields are empty

## 5. Strings, wiring & cleanup

- [x] 5.1 Centralized action copy in `ActionTypeUi` and updated in-place copy ("Extract data", picker title, remove-confirmation). NOTE: kept copy in Kotlin rather than `strings.xml` — this feature hardcodes all UI copy in Compose today, so introducing `strings.xml` for three strings would be inconsistent; flagged for a separate localization pass.
- [x] 5.2 Update `DoSection` "Extract data" card to summarize configured fields (field count) via the shared mapping
- [x] 5.3 Update Compose `@Preview`s affected by the removed section and renamed action

## 6. Tests & verification

- [x] 6.1 `RuleEditorViewModel` tests: available-types computation, one-per-type rejection, add via picker → action added, remove Extract-data clears fields (with confirmation path)
- [x] 6.2 `ActionBottomSheetViewModel` tests: seeded-type add path, edit path preserves type, save produces correct `RuleAction`
- [x] 6.3 Run `./gradlew spotlessApply detekt test` — all green (253 tests, up from 229); regenerated detekt baseline (shrank 118→114 per TD-16)
- [x] 6.4 Manual device/emulator check: add each action type once, edit one, verify picker hides configured types, verify Extract-data gating (no action ⇒ no saved fields) — REQUIRES on-device run by the user

## 7. Per-type action sheets (post-testing refinement, D1a)

- [x] 7.1 Add a shared `ActionConfigSheet` scaffold (title + Cancel/confirm) and `ActionSheetDescription` helper
- [x] 7.2 Add per-type sheets: `SnoozeBottomSheet`, `AlarmBottomSheet`, `FlashBottomSheet` (own state, build `RuleAction` on confirm)
- [x] 7.3 Dismiss adds directly with no sheet; editing a dismiss action is a no-op (ViewModel)
- [x] 7.4 Move alarm notification-permission request to the sheet's confirm click (removed the on-render `LaunchedEffect` in `AlarmConfig`)
- [x] 7.5 Render the correct per-type sheet from `RuleEditorScreen`; delete shared `ActionBottomSheet`/`ActionBottomSheetViewModel`/`ActionBottomSheetContract` (+ its test)
- [x] 7.6 Update tests (dismiss-adds-directly, dismiss-edit-no-op); `./gradlew spotlessApply detekt test` green; baseline shrank 114→113
