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

This pattern is applied across all data domains: notifications (`NotificationRepository`), rules (`RuleRepository`), monitored apps (`SelectedAppRepository`), user preferences (`UserPreferencesRepository`), and webhooks (`WebhookRepository`).

**Amendment (webhook-management, Phase 4 PR1):** point 3's "the app is local-first with no remote sources" is now stale. As of this change, `WebhookRepositoryImpl` coordinates a remote sink (`core/network/WebhookTestClient`) in addition to local sources (`WebhookDao`) - the local-first default still holds, since egress is user-initiated only (see ADR 012's status update).

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
