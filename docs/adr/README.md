# Architecture Decision Records

This folder contains the Architecture Decision Records (ADRs) for Notificapp. An ADR captures one significant architectural decision: the context that forced it, the decision itself, and its consequences — so future contributors (human or AI) understand *why* the code is shaped the way it is, not just *how*.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [001](001-mvi-pattern.md) | MVI Pattern for UI State Management | Accepted |
| [002](002-contract-based-state.md) | Contract-Based State Definition | Accepted |
| [003](003-multi-module-architecture.md) | Monolithic-First with Planned Multi-Module Extraction | Accepted |
| [004](004-hilt-dependency-injection.md) | Hilt Dependency Injection with Custom Qualifiers | Accepted |
| [005](005-repository-pattern.md) | Repository Pattern with Interface/Implementation Separation | Accepted |
| [006](006-sealed-class-error-handling.md) | Sealed Class Error Handling with Failure Hierarchy | Accepted |
| [007](007-navigation3-navigator.md) | Navigation3 with Custom Navigator | Accepted |
| [008](008-injected-dispatchers.md) | Injected Coroutine Dispatchers | Accepted |
| [009](009-notification-processing-pipeline.md) | Notification Processing Pipeline as a Use Case | Accepted |
| [010](010-action-executor-multibindings.md) | Action Execution via ActionExecutor Multibindings | Accepted |
| [011](011-rule-definition-storage.md) | Rule Definition Storage: Normalized Tables vs JSON Column | Proposed (decide before Roadmap Phase 2) |
| [012](012-local-first-network-policy.md) | Local-First Network Policy and Distribution | Accepted |

## Statuses

- **Accepted** — the decision stands and the code follows it (or the ADR notes known violations explicitly)
- **Accepted (implementation pending)** — decided, but the refactor hasn't landed yet; see [`docs/roadmap_tech_debt.md`](../roadmap_tech_debt.md) for the implementation plan
- **Proposed** — under consideration; the ADR records the options and the criteria/trigger for deciding
- **Superseded by ADR-XXX** — replaced by a later decision (keep the file; history matters)

## Adding a new ADR

1. Copy the structure of an existing ADR: `# ADR NNN – Title`, then `## Status`, `## Context`, `## Decision`, `## Consequences` (with **Positive** and **Negative** — every decision has costs; write them down)
2. Number it sequentially (`013-...`), use a kebab-case filename
3. Add a row to the index table above
4. Reference it from code/docs where the decision applies (e.g., `docs/ARCHITECTURE.md`, KDoc comments)

Write an ADR when a decision is expensive to reverse, constrains future work, or will make someone ask "why is it done this way?" — not for routine implementation choices.
