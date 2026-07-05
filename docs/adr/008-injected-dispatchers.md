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
- Main dispatcher remains hardcoded in `MviViewModel.sendEffect()` for UI thread guarantees. Resolution planned (`docs/roadmap_tech_debt.md` TD-7): a constructor parameter with `Dispatchers.Main.immediate` as production default, overridable in tests; until then, tests rely on `Dispatchers.setMain()` intercepting `Main.immediate`
