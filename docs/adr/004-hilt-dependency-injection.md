# ADR 004 – Hilt Dependency Injection with Custom Qualifiers

## Status
Accepted

## Context
Manual dependency management leads to tight coupling and testability issues. The project needs a standardized DI framework that integrates well with Android lifecycle components (ViewModels, WorkManager) while supporting test doubles for unit testing.

## Decision
Use Hilt for dependency injection with the following conventions:

1. **Hilt Modules**: Organized by layer in `core/di` (`DatabaseModule`, `RepositoryModule`, `DispatchersModule`, `CoilModule`)
2. **Interface Binding**: Repository interfaces bound to implementations using `@Binds` in `RepositoryModule`
3. **Custom Qualifiers**: `@Dispatcher` annotation with `DispatcherType` enum to differentiate IO vs Default dispatchers
4. **Scoped Dependencies**: `@Singleton` for application-wide dependencies, `@ViewModelScoped` where appropriate
5. **Constructor Injection**: Preferred over field injection for testability (exception: `NotificappListenerService` uses field injection because Android instantiates the service)

The `@Dispatcher` qualifier pattern enables explicit dispatcher selection:
- `@Dispatcher(DispatcherType.IO)` for blocking I/O operations
- `@Dispatcher(DispatcherType.Default)` for CPU-intensive work

## Consequences

**Positive:**
- Compile-time generated code eliminates runtime reflection overhead
- ViewModel injection integrates seamlessly with `hiltViewModel()`
- Custom qualifiers enable explicit, type-safe dispatcher selection
- Interface/implementation separation supports easy mocking in tests
- Consistent scoping prevents memory leaks

**Negative:**
- Build configuration requires KSP and plugin setup
- Generated code increases APK size marginally
- Debugging DI issues requires understanding generated components
- Migration from manual DI requires significant refactoring effort
