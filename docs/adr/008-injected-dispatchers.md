# ADR 008 – Injected Coroutine Dispatchers

## Status
Accepted

## Context
Hard-coded `Dispatchers.IO` and `Dispatchers.Default` throughout the codebase make unit testing difficult, as tests cannot easily substitute test dispatchers. Additionally, explicit dispatcher selection makes the concurrency model clearer for developers.

## Decision
Inject coroutine dispatchers using Hilt with a custom qualifier pattern:

1. **DispatcherType Enum**: Defines dispatcher types (`IO`, `Default`) in `core/di/Dispatchers.kt`
2. **@Dispatcher Qualifier**: `@Qualifier` annotation that takes `DispatcherType` as parameter
3. **DispatchersModule**: Hilt module providing:
   - `@Dispatcher(DispatcherType.IO) -> Dispatchers.IO`
   - `@Dispatcher(DispatcherType.Default) -> Dispatchers.Default`
4. **Constructor Injection**: ViewModels and repositories receive dispatchers via constructor injection

Example usage:
```kotlin
class MyViewModel @Inject constructor(
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    // Use ioDispatcher for blocking operations
}
```

## Consequences

**Positive:**
- Testability: Unit tests can inject `StandardTestDispatcher` or `UnconfinedTestDispatcher`
- Explicit dispatcher intent makes code reviews easier
- Consistent dispatcher usage across the codebase
- Easy to add new dispatcher types (e.g., computation-specific)

**Negative:**
- Additional boilerplate in constructors
- Developers must remember to inject rather than use `Dispatchers` directly
- Slight overhead of qualification lookups (negligible in practice)
- Main dispatcher in `MviViewModel.sendEffect()` is now an `effectDispatcher` constructor parameter defaulting to `Dispatchers.Main.immediate` (resolved 2026-07-05, TD-7). Subclasses inherit the production default automatically; tests can either pass a test dispatcher explicitly or continue relying on `Dispatchers.setMain()`, which also intercepts `Main.immediate`
