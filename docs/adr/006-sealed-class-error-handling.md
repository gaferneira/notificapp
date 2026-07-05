# ADR 006 – Sealed Class Error Handling with Failure Hierarchy

## Status
Accepted

## Context
Exception handling across the app was inconsistent, with some layers throwing exceptions and others returning nulls. This led to unpredictable crash behavior and poor user experience when errors were not properly surfaced or handled.

## Decision
Implement a hierarchical error handling strategy using sealed classes:

1. **Failure Sealed Class** (`core/common/Failure.kt`): Base class for all errors with specific subtypes:
   - `NetworkConnection` – for connectivity issues
   - `ServerError` – for HTTP error responses (includes status code)
   - `ApplicationException` – for business logic errors
   - `FeatureFailure` – abstract class for domain-specific errors
   - `UnknownException` – fallback for unhandled exceptions

2. **Throwable Extension**: `toFailure()` extension function analyzes exceptions and maps them to appropriate Failure types

3. **UI Mapping**: `Failure.asUiText()` extension converts Failures to `UiText` for display, mapping specific errors to string resources

4. **Result Pattern**: Repository methods return `Result<T>` allowing `fold()` handling of success/failure cases

## Consequences

**Positive:**
- Exhaustive when-expressions ensure all error cases are handled at compile time
- Clear separation between technical failures (connectivity) and business failures (via `FeatureFailure` subtypes)
- Consistent error mapping to user-friendly messages via string resources
- Timber logging integrated for unhandled exceptions
- Type-safe error propagation across layers

**Negative:**
- Requires discipline to wrap all exceptions in Failure types
- New error types require extending the sealed hierarchy
- Mapping logic can become complex for edge cases
- Some ambiguity exists: some ViewModels use try-catch while others rely on Result.fold
