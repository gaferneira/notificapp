# ADR 003 – Monolithic-First with Planned Multi-Module Extraction

## Status
Accepted (multi-module extraction deferred)

## Context
Multi-module Gradle projects offer parallel development, incremental builds, and enforced boundaries — but carry real setup and maintenance costs (module configuration, convention plugins, cross-module navigation wiring). Notificapp is an early-stage MVP built by a small team where iteration speed matters more than build parallelism, and the product concept is still evolving (see `docs/roadmap.md`), so module boundaries drawn today may be wrong tomorrow.

## Decision
Start with a **single `:app` module**, but organize packages as if modules already existed, so extraction later is mechanical:

- **`core/*` packages** (`common`, `data`, `di`, `extraction`, `ui`) hold shared infrastructure
- **`domain/`** holds pure Kotlin models and repository interfaces
- **`features/*` packages** hold one self-contained package per screen/feature, each with `contract/`, `ui/`, and `viewmodel/` sub-packages
- Package dependency rules mirror future module boundaries: `features → domain + core/ui`, `core/data → domain`, `core/extraction → domain`, no cross-feature dependencies

Planned extraction path when the project grows (roughly one module per existing package):

```
:app
├── :core:model, :core:data, :core:extraction, :core:notification, :core:ui
└── :feature:inbox, :feature:ruleeditor, :feature:rules, :feature:notificationdetail,
    :feature:appselection, :feature:onboarding, :feature:settings
```

Triggers to revisit: build times become painful, a second contributor team forms, or the extraction engine is wanted as a standalone library (`:core:extraction` is the most likely first extraction).

## Consequences

**Positive:**
- Zero module-configuration overhead during the exploratory MVP phase
- Feature-first packaging keeps pivots cheap: deleting or reshaping a screen is a directory change
- Dependency discipline is preserved by convention, making later modularization low-risk
- Single module keeps the OpenSpec/agent workflow simple (one source set, one test task)

**Negative:**
- Package-level boundaries are convention, not compiler-enforced — violations are possible and have occurred (see `docs/roadmap_tech_debt.md` TD-1/TD-2: `core/extraction` reaching into `core/data`)
- No incremental build benefits until extraction happens
- All unit tests live in one `app/src/test` source set
