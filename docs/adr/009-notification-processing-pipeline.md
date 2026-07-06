# ADR 009 – Notification Processing Pipeline as a Use Case

## Status
Accepted

## Context
The capture → extract → act flow is the heart of the product, and every roadmap feature (actions, webhooks, AI extraction, backtesting) plugs into it. Today it is orchestrated inline in `NotificappListenerService` — an OS-bound class that is the hardest thing in the app to test — and `RuleEngine` mixes evaluation (pure domain logic) with persistence (Room DAOs). The extracted-data concept has no repository abstraction: DAOs are consumed directly by `RuleEngine` and `NotificationDetailViewModel`.

## Decision
Restructure the pipeline into three separated responsibilities:

1. **`NotificappListenerService` becomes a thin adapter**: it filters raw `StatusBarNotification`s (enabled app, has content, not stale), normalizes them into the domain `Notification` model (this needs `packageManager`), registers the `SystemNotificationController` (ADR 010), and hands off to the pipeline. Nothing else.

2. **`ProcessNotificationUseCase`** — a plain injectable class — orchestrates the flow: deduplicate → persist notification → load rules → evaluate → execute actions → persist executions with outcomes. It has no Android imports and is testable end-to-end on the JVM with fakes.

3. **`RuleEngine` becomes pure evaluation**: `evaluate(notification, rules): List<RuleMatch>` with no I/O, no DAOs, no dispatchers. Persistence of executions and extracted field values moves behind a new **`RuleExecutionRepository`** (interface in `domain/repository`, implementation in `core/data`), which also replaces the direct DAO access in `NotificationDetailViewModel`. The repository wraps the multi-table write (execution + field values + notification counter) in a single Room transaction.

## Consequences

**Positive:**
- Core product logic (matching + extraction) is unit-testable without Room or an emulator
- The same `evaluate()` powers Roadmap Phase 2 backtesting (run rules against history without persisting) — no parallel code path needed
- `core/extraction → domain` dependency direction is restored, keeping the engine extractable as a standalone module/library
- New pipeline stages (AI extraction, content filtering) are added to one plain class instead of an OS service
- Data Browser and export (Phase 3) consume `RuleExecutionRepository` instead of coupling to table schemas

**Negative:**
- One more layer of indirection between the service and the database
- The interim state during migration touches several files at once (TD-2 and TD-3 must land together)
- `RuleMatch` (evaluation result before persistence) is a new concept developers must distinguish from `RuleExecution` (persisted record)
