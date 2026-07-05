# ADR 001 – MVI Pattern for UI State Management

## Status
Accepted

## Context
The codebase requires a clear, predictable UI state management pattern that ensures unidirectional data flow and eliminates race conditions common in traditional MVVM approaches. The project needs consistent handling of user actions, state updates, and side effects across all features.

## Decision
Adopt the Model-View-Intent (MVI) pattern using a custom `MviViewModel` base class that enforces:

1. **StateFlow for UI State**: A single `StateFlow<UiState>` provides immutable state updates that the UI observes
2. **Channel for Effects**: One-time side effects (toasts, navigation) are sent through a `Channel<UiEffect>` converted to `Flow`
3. **Abstract Event Handler**: All user actions flow through `onEvent(UiEvent)` method
4. **Protected State Updates**: State changes only via `setState()` with reducer pattern using `copy()`

The implementation resides in `core/ui/mvi/MviViewModel.kt` and all feature ViewModels extend this base class.

## Consequences

**Positive:**
- Unidirectional data flow eliminates state inconsistency bugs
- Immutable state ensures predictable UI updates
- Centralized event handling makes user action tracking straightforward
- Side effects are isolated from state updates, preventing UI flickering
- Base class enforces consistency across all screens

**Negative:**
- Slight boilerplate overhead compared to plain MVVM
- All state changes must be explicit through reducer functions
- Learning curve for developers unfamiliar with MVI pattern
- Effects require careful lifecycle handling to avoid loss during configuration changes
