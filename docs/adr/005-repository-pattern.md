# ADR 005 – Repository Pattern with Interface/Implementation Separation

## Status
Accepted

## Context
The data layer needs to provide a clean API to the presentation layer while hiding implementation details of data sources (network, database, cache). Direct data source access from ViewModels creates tight coupling and makes testing difficult.

## Decision
Implement the Repository pattern with strict separation of concerns:

1. **Repository Interface**: Defined in `domain/repository/` (e.g., `NotificationRepository`)
2. **Repository Implementation**: Implemented in `core/data/repository/`, coordinating local data sources (e.g., `NotificationRepositoryImpl`)
3. **Data Sources**: Local sources (Room DAOs, DataStore) — the app is local-first with no remote sources (see ADR 012)
4. **Hilt Binding**: Interfaces bound to implementations in `core/di/RepositoryModule` using `@Binds` annotation
5. **Result Wrapping**: Repository methods return `Result<T>` for explicit error handling

This pattern is applied across all data domains: notifications (`NotificationRepository`), rules (`RuleRepository`), monitored apps (`SelectedAppRepository`), and user preferences (`UserPreferencesRepository`). A `RuleExecutionRepository` for rule executions and extracted field values is planned (see ADR 009 and `docs/roadmap_tech_debt.md` TD-1); until it lands, `RuleEngine` and `NotificationDetailViewModel` access DAOs directly — a known violation of this ADR, not a pattern to copy.

## Consequences

**Positive:**
- Interfaces enable easy mocking for unit tests without database dependencies
- Implementation details are encapsulated, allowing data source changes without affecting ViewModels
- Entity↔domain mapping stays inside the data layer (mappers in `core/data/local/mapper/`)
- Consistent API surface across all data domains
- Reactive streams (Flow, Paging3) exposed uniformly from repositories

**Negative:**
- Additional abstraction layer adds boilerplate (interface + implementation)
- Repository implementations can become large when coordinating many data sources
- Risk of repository bloat if not kept focused on specific domain boundaries
