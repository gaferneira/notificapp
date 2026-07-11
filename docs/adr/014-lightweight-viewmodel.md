# ADR 014 – Lightweight ViewModel Pattern for Flat Screens and Components

## Status
Accepted

## Context
[ADR 001](001-mvi-pattern.md)/[002](002-contract-based-state.md) mandate full MVI for every screen: a `Contract` object in its own `contract/` file with a `UiState` data class, a sealed `UiEvent` hierarchy, and a sealed `UiEffect` hierarchy. That pattern earns its cost when a screen has real branching logic — validation, derived/computed state, multiple flows in and out (`RuleEditorViewModel`, `AddFieldViewModel` with its live extraction preview).

It does not earn its cost for small, flat, single-purpose screens or components: e.g. the type-scoped action bottom sheets in `features/ruleeditor/ui/` (`FlashBottomSheet`, `SnoozeBottomSheet`). These hold 1-2 primitive/enum fields with 1:1 setters and no branching — the only "decision" is building one output value on confirm. Wrapping every `soundEnabled = it` in its own `UiEvent` subclass, plus a separate `contract/` file, adds boilerplate ADR 002 already flags as a known negative without adding safety: there is no exhaustive-`when` branching logic for the sealed hierarchy to protect.

## Decision
**Full Contract MVI ([ADR 001](001-mvi-pattern.md)/[002](002-contract-based-state.md)) remains the default for every screen.** The lightweight pattern below is the exception, not a free choice between two equally valid styles — reach for it only when a component clearly fits the criteria, not by default for "small-looking" screens.

A screen, bottom sheet, or self-contained component is a candidate for the lightweight pattern when it meets **all** of the following:

- **Up to ~5 state values** — a flat data class of primitives/enums. Below 2-3, question whether a ViewModel is even needed (`FlashBottomSheet`/`SnoozeBottomSheet` get by with local `remember`, no ViewModel at all). Past ~5, the setter count itself becomes a promotion signal — `AlarmContract.UiState` reached ~15 fields, which is exactly the case this ADR's Promotion path documents as too many.
- **A matching handful of methods** — direct 1:1 setters (`onSoundToggle`, `onLabelChange`, ...), one per field, not a growing surface of near-identical setters (see the `AlarmViewModel` promotion example below for what "too many" looks like in practice).
- **No side effects** — no one-shot signals to emit (navigation, snackbars, external I/O triggered mid-flow). The only "output" is the final state read on confirm; there is no `UiEffect` hierarchy to route through.
- **No derived/branching state** — no validation, no computed value depending on multiple fields, no state machine beyond "hold N independent fields."

If any of these doesn't hold, use full Contract MVI. When in doubt, default to MVI — it is cheap to over-apply and expensive to under-apply (see Promotion path).

For components meeting all four criteria, use a lighter pattern instead of full Contract MVI:

1. **Plain `ViewModel`, not `MviViewModel`.** `@HiltViewModel class XxxViewModel @Inject constructor() : ViewModel()` — no shared event/effect base class, since there's no `UiEvent`/`UiEffect` hierarchy to route through it.
2. **State lives with the ViewModel, not in a separate `contract/` file.** `XxxUiState` is a flat data class declared in the same `viewmodel/XxxViewModel.kt` file, exposed as `val uiState: StateFlow<XxxUiState>` backed by a private `MutableStateFlow`.
3. **Direct update methods instead of dispatched events.** One method per field (`onSoundToggle(enabled: Boolean)`, `onVibrationPatternChange(pattern: VibrationPattern)`, ...), each doing `_uiState.update { it.copy(...) }`. No `onEvent(event: UiEvent)` dispatcher.
4. **`initialize(...)` seeds state once**, called from the composable via `LaunchedEffect(Unit)`.

**Scope**: applies to any component matching the shape above, not only bottom sheets — a simple screen with the same flat-state characteristics qualifies too. Today's examples: `FlashBottomSheet`, `SnoozeBottomSheet` — both are actually stateless composables with no ViewModel at all (local `remember { mutableStateOf(...) }`), an even lighter case than this ADR describes; they're listed here as the floor of the spectrum this ADR covers, not as ViewModel examples. As of this writing there is no live ViewModel in the codebase actually using the pattern described in steps 1-4 above (`AlarmViewModel` was promoted to full Contract MVI, see below) — this ADR documents the pattern for the next component that fits the criteria, not a currently-implemented one.

**Does not apply** to screens/components with real branching or derived logic: `RuleEditorScreen`, `InboxScreen`, `RulesScreen`, `NotificationDetailScreen`, `AddFieldBottomSheet`. `AddFieldBottomSheet` in particular has validation and a live extraction preview recomputed from multiple fields — that branching complexity is exactly what full Contract MVI (ADR 001/002) is for, and it correctly stays there.

**Promotion path**: this pattern is a starting point, not a permanent exemption. The moment a component under this ADR grows real branching logic, derived state depending on multiple fields, or accumulates more than a couple of one-shot signals, convert it to full Contract MVI rather than layering more special cases onto the lightweight ViewModel. Two examples so far:

- `AddFieldBottomSheet` is the reference example: it needed validation and a live extraction preview recomputed from multiple fields.
- `AlarmViewModel` is a second, subtler example. It never grew branching logic — it stayed 11 flat setters producing one `RuleAction` on save. What it did grow was setter *count*: 10 near-identical `onXChange`/`onXToggle` methods, which pushed the Compose layer that consumed them into its own boilerplate-avoidance workaround — `AlarmOptionsCallbacks`, a 10-lambda value class that existed purely to keep `AlarmOptionsSelector` under detekt's `LongParameterList` limit. That callback bag was itself the signal: once a lightweight ViewModel's fields multiply enough to need a *second* boilerplate-avoidance layer on the UI side just to pass its setters around, the "no `UiEvent` ceremony" savings this ADR trades on have already been spent elsewhere. Collapsing the 10 setters into one sealed `AlarmContract.UiEvent` and one `onEvent(event: UiEvent)` dispatcher removed the callback bag entirely (call sites now thread a single `onEvent: (UiEvent) -> Unit` lambda) and brought the ViewModel in line with the rest of the app in the same move. Rule of thumb: if a lightweight ViewModel's setter count forces a grouped-callback value class on the UI side to satisfy detekt, that's a promotion signal even without real branching logic.

Each ViewModel written under this ADR should reference it in a KDoc comment, so a reader who expects `MviViewModel`/Contract MVI everywhere understands the deviation is intentional and scoped.

## Consequences

**Positive:**
- State still survives configuration changes (rotation), is unit-testable, and DI-friendly — the actual wins of a ViewModel — without the Contract/`UiEvent` ceremony. (Process death is a separate concern: neither this pattern nor full Contract MVI survives it today, since no ViewModel in the codebase uses `SavedStateHandle`.)
- Roughly a third of the boilerplate of full Contract MVI for sheets this flat.
- Keeps ADR 001/002 the default; this is a documented, bounded exception rather than silent inconsistency.

**Negative:**
- Reads as inconsistent with the rest of the codebase unless the reader knows this ADR exists (mitigated by the required KDoc reference).
- Doesn't scale if a component's logic grows — see Promotion path above; requires discipline to actually convert rather than bolt on more setters and signals indefinitely.
- Two ViewModel shapes now exist project-wide, so "is this MVI or lightweight?" is a judgment call at the boundary — the Scope/Does-not-apply lists above are the tiebreaker.
