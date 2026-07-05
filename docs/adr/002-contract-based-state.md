# ADR 002 – Contract-Based State Definition

## Status
Accepted

## Context
Each screen requires clear definition of three components: the state it displays, the events it can receive, and the effects it can trigger. Without a unified structure, these definitions become scattered and inconsistent across features, making the codebase harder to maintain and understand.

## Decision
Define a Contract object for each screen/flow that contains:

1. **UiState**: A data class with all screen properties (loading flags, data, error states)
2. **UiEvent**: A sealed class hierarchy where each event represents a user action or system trigger
3. **UiEffect**: A sealed class hierarchy for one-time side effects (navigation, snackbars, dialogs)

Contracts are declared as `object` types in a dedicated `contract/` package within each feature (`features/[name]/contract/`), separate from `viewmodel/` and `ui/`, providing a single source of truth for screen contracts. Example: `features/inbox/contract/InboxContract.kt` containing `UiState`, `UiEvent`, and `UiEffect` definitions, consumed by `features/inbox/viewmodel/InboxViewModel.kt`.

## Consequences

**Positive:**
- Screen state, events, and effects are co-located and discoverable
- Sealed classes enable exhaustive `when` expressions with compiler checks
- Clear contract makes UI logic testable and mockable
- Pattern consistency across all features reduces cognitive load

**Negative:**
- Additional boilerplate for simple screens
- Contracts can become large for complex screens with many states/events
- Requires discipline to keep events granular and focused
