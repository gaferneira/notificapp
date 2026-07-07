## Context

The Rule Editor screen (`RuleEditorScreen.kt`, step 1 / `LogicStep`) renders three sibling sections: **When** (conditions), **And then / Data Extraction** (`rule.fields`), and **Do** (actions). "Add action" opens a single `ActionBottomSheet` that lists all five `ActionType`s as selectable cards plus inline config for the chosen type, and lets the user add the same type repeatedly.

Two structural problems:

1. **Misleading persistence.** `ProcessNotificationUseCase.evaluateAndPersist` calls `saveExecution(execution, match.rule.fields)` on every match. `SaveDataActionExecutor` is a no-op. So extraction persistence is independent of the `SAVE_DATA` action — disabling "Save to Data Lab" saves data anyway.
2. **Redundant surfaces.** The standalone "And then" section and the `SAVE_DATA` action both represent "capture extracted data," but neither is aware of the other.

Constraints: MVI (per-feature Contract + `MviViewModel`), `RuleMatcher`/`FieldExtractor`/`RuleEngine` must stay pure Kotlin (no Android, no action-semantics awareness), TD-13 pattern for action config (one dispatch sheet + per-type `actionconfig/*` composables), one-config-per-type in `RuleAction.config`. The app is **not yet published** — `DatabaseModule` uses `fallbackToDestructiveMigration()` — so no production data migration is required.

## Goals / Non-Goals

**Goals:**
- Replace the all-types selection sheet with a **type-picker dialog** that only offers un-configured types, and open a **type-scoped** configuration sheet.
- Enforce **one action per type** in the ViewModel.
- Merge the field-extraction module into the renamed **"Extract data"** (`SAVE_DATA`) action's sheet; remove the standalone "And then" section.
- Make the "Extract data" action **actually gate** field-value persistence.
- Preserve behavior for existing in-flight rules (dev DBs) that have fields.

**Non-Goals:**
- Adding new action types or new extraction methods.
- Moving "Test against history" — it validates the whole draft rule and stays at screen level.
- Changing `RuleEngine`/`FieldExtractor` purity or signatures.
- Drag-to-reorder fields/actions (the existing non-functional drag handle is out of scope).

## Decisions

### D1: Type-picker is a dialog; one reusable type-scoped sheet (not five)
"Add action" → `AlertDialog`-style list dialog showing only `ActionType`s absent from `rule.actions`. Selecting a type dismisses the dialog and shows `ActionBottomSheet` **pre-seeded with that type**. The sheet keeps its single-composable structure (TD-13): it no longer renders the all-types `ActionTypeCard` list; it renders the header + the pre-selected type's `actionconfig/*` block. Editing an existing action opens the same sheet seeded from the action, with the type fixed.
- *Why not five separate sheet composables:* duplicates the ModalBottomSheet/VM plumbing and fights TD-13. The type is just a parameter.
- *Why a dialog not another bottom sheet for the picker:* matches the requested UX and visually separates "choose" from "configure."

### D1a: Per-type action sheets (revised after device testing)
Device testing showed the single seeded `ActionBottomSheet` (one composable + one ViewModel branching by type) still tangled every type's logic in one place. Revised: each type owns its surface.
- **Dismiss** has no config → no sheet; the picker adds it directly.
- **Snooze / Alarm / Flash** each get their own stateless sheet composable (`SnoozeBottomSheet`, `AlarmBottomSheet`, `FlashBottomSheet`) built on a shared `ActionConfigSheet` scaffold; each holds its own `remember` state seeded from the edited action and emits a completed `RuleAction` via `RuleAction.create*`. The shared `ActionBottomSheet`/`ActionBottomSheetViewModel`/`ActionBottomSheetContract` are deleted.
- **Extract data** keeps its dedicated sheet (D2).
- **Alarm permission timing:** notification permission is requested on the sheet's confirm/"Add" click, not from a `LaunchedEffect` when the config renders (the previous behavior prompted the moment the alarm option showed).
- *Why stateless sheets over per-type ViewModels:* each type's config is small and the parent `RuleEditorViewModel` already owns `rule.actions`; composable-local state keeps each type isolated without proliferating ViewModels.

### D2: "Extract data" sheet hosts the field-extraction module
When the pre-selected/edited type is `SAVE_DATA`, the sheet renders the extraction UI currently in `DataExtractionSection` — field list (add/edit/remove) + auto-generate — instead of the snooze/alarm/flash config. The `AddFieldBottomSheet` field editor is launched from within this flow (nested sheet or sequential), reusing the existing component. `DataExtractionSection` is removed from `LogicStep`.

### D3: Fields stay on `RuleUiModel`/`Rule`; the action is their gate
`rule.fields` remains the storage location (no move into `RuleAction.config`). The `SAVE_DATA` action's **presence + enabled state** is the contract:
- Adding the "Extract data" action enables editing fields.
- **Removing** the "Extract data" action **clears `rule.fields`** (after a confirmation dialog when fields exist — destructive).
- Editing fields is only reachable through the action's sheet.
- *Why not store fields inside `config`:* fields are structured (`RuleField` with sealed `ExtractionMethod`); `config` is `Map<String,String>`. Keeping `rule.fields` avoids a codec/entity rewrite and preserves the golden wire-format test surface.

### D4: Gate persistence at the use-case boundary, keep the engine pure
In `evaluateAndPersist`, compute `fieldsToPersist = if (rule.hasEnabledExtractDataAction) rule.fields else emptyList()` and pass that to `saveExecution`. The execution record is still created (other actions like Alarm still need their outcomes recorded); only extracted field **values** are gated. `RuleEngine`/`FieldExtractor` stay untouched and action-agnostic.
- *Why gate at persistence, not in the engine:* architecture rule keeps `RuleEngine` free of action semantics. Extraction compute is pure and cheap; the honest, user-visible contract is "not saved," which this delivers. `SaveDataActionExecutor` stays a no-op (persistence is a pipeline concern, not an executor side effect) with an updated KDoc.

### D5: Load-time normalization for legacy fields-without-action rules
Because `SAVE_DATA` and fields were decoupled, a rule can currently have fields and no `SAVE_DATA` action. On rule load (or a lightweight Room migration), any rule with non-empty `fields` and no `SAVE_DATA` action gets an enabled `SAVE_DATA` action synthesized, so its extraction keeps working post-change. No production migration is needed (unpublished app), but this protects dev/test DBs and is the correct default.

## Risks / Trade-offs

- **[Silent data-capture change for legacy rules]** → D5 normalization ensures fields-bearing rules keep an enabled Extract-data action; verified by a `ProcessNotificationUseCase` gating test plus a normalization test.
- **[Accidental field loss on action removal]** → D3 confirmation dialog before clearing non-empty fields.
- **[Nested sheet UX]** (field editor launched from the Extract-data sheet) can feel deep → reuse the existing `AddFieldBottomSheet` pattern already used from the screen; keep the extraction sheet full-height as today.
- **[One-per-type on edit]** → editing must not offer type switching; the picker (add path) is the only place type is chosen, and it excludes configured types.

## Migration Plan

1. Ship engine gating (D4) + normalization (D5) together so no window exists where a fields-rule loses capture.
2. No DB schema migration required beyond D5's optional normalization; destructive migration remains for the unpublished app.
3. Rollback: revert the change set; `rule.fields` storage is unchanged, so no data shape rollback is needed.

## Open Questions

- None blocking. Auto-generate placement inside the Extract-data sheet vs. a header affordance is a UI detail to settle during apply.

## Future Work (Iteration 2)

- **Move the extraction fields onto the Extract-data `RuleAction`.** Once this UX lands, `fields` conceptually belong to the action, not the rule. Deferred here (D3) because it changes the `RuleAction` domain model to carry a structured `List<RuleField>` and ripples into the Room `RuleField` entity/DAO/mapper, the JSON codec + its golden wire-format test, `RuleEngine`, and `ProcessNotificationUseCase`. Best done as its own change after this one merges, so the wire-format migration isn't entangled with the UX revamp.
