# Notificapp - Agent Guidelines

## Project Identity

- **Name**: Notificapp
- **Purpose**: Open-source notification automation for Android — rules that act on notifications (dismiss, snooze, alerts) and extract structured data from them, entirely on-device
- **Architecture**: MVI (Model-View-Intent), feature-first packaging with shared `core/` infrastructure
- **Language**: Kotlin — **UI**: Jetpack Compose + Material 3 — **DI**: Hilt — **Nav**: Navigation3 with custom Navigator (ADR 007)
- **Build System**: Gradle with Kotlin DSL
- **Testing**: JUnit 5, Kotest, MockK, Turbine — 700+ unit tests across 70+ files in `app/src/test`; remaining gaps are mostly instrumented/UI tests
- **Structure**: Monolithic (single app module) with clean package separation, designed for future modularization

## Development Status

**Pre-launch — no installed user base yet.** Notificapp has not shipped a public release (F-Droid/Play). Until it does, there is no production data or backward-compatible surface to protect:

- **Room schema**: prefer a clean schema bump (destructive migration) over hand-written `Migration` objects when changing `core/data/local/entity/`. Don't add a real `Migration` just to preserve existing rows — there are none to preserve.
- **Rule wire format** (`core/rulesharing/RuleJsonCodec`, `docs/rule-format.md`): free to break `RuleExportDto`/schema version across commits without a compatibility shim — no one has exported rules from a shipped version yet.
- **Domain model shape**: sealed-class/structural rewrites of `Rule`, `RuleCondition`, etc. are fine — condition storage uses a JSON polymorphic column (`rule_conditions.payload`), so adding new condition families never requires a Room migration.

Revisit and remove this section once the app has a real release with installed users — at that point migrations and wire-format compatibility become mandatory again.

## Quick Reference — where to look

Prefer these docs over inlining their content here. Read the relevant one **before** working in that area.

| I need to... | Reference |
|--------------|-----------|
| Understand MVI, clean architecture layers, extraction pipeline, coding standards | `docs/ARCHITECTURE.md` |
| Check architecture decisions & constraints | `docs/adr/*.md` (index in `docs/adr/README.md`) |
| Check product direction, phases, known debt, out-of-scope | `docs/roadmap.md` |
| See the full functional/feature map (conditions, extraction methods, actions, screens) | `docs/capabilities.md` — **keep in sync** (see rule below) |
| Add a screen / repository / extraction method / action type | `docs/guides/common-patterns.md` |
| Wire a screen into navigation | `docs/guides/navigation-guide.md` (background: ADR 007) |
| Follow the OpenSpec/SDD feature workflow | `docs/SDD-METHODOLOGY.md` |
| See feature specifications & active proposals | `openspec/specs/[area]/`, `openspec/changes/[name]/` |
| Check file-specific coding rules | `.firebender/rules/*.mdc` (local tooling, not committed — skip if absent) |
| Run tests / format | `./gradlew test` · `./gradlew spotlessApply` |

**`docs/capabilities.md` sync rule:** update it in the same PR whenever you touch `domain/model/RuleCondition.kt`, `RuleField.ExtractionMethod`, `RuleAction`/`ActionType`, or add/remove a screen under `features/*`.

## Mandatory Pre-Flight Checklist

Before making any code changes:

- [ ] Read the `docs/ARCHITECTURE.md` section relevant to the layer being modified
- [ ] Check `.firebender/rules/*.mdc` for file-specific guidelines (local tooling — skip if absent)
- [ ] Check `openspec/specs/` and `openspec/changes/` for existing specs and active proposals
- [ ] Review relevant ADRs in `docs/adr/` for architectural constraints
- [ ] Ensure changes follow the MVI pattern (unidirectional data flow)
- [ ] Run `./gradlew spotlessApply` after changes

## Project Structure

Package root is `dev.gaferneira.notificapp`.

```
Notificapp/
├── app/
│   ├── src/main/kotlin/dev/gaferneira/notificapp/
│   │   ├── MainActivity.kt
│   │   ├── core/
│   │   │   ├── common/            # Shared types (Failure)
│   │   │   ├── data/              # Data layer: local/ (AppDatabase, DAOs, entities, mappers),
│   │   │   │                      # preferences/ (DataStore), repository/ (impls)
│   │   │   ├── di/                # Hilt modules (Database, Repository, Dispatchers, Coil)
│   │   │   ├── extraction/        # Rule engine (RuleEngine, RuleMatcher, FieldExtractor)
│   │   │   ├── notification/      # NotificationNormalizer, ProcessNotificationUseCase, NotificationDeduplicator,
│   │   │   │                      # action/ (ActionDispatcher, per-type ActionExecutors)
│   │   │   └── ui/                # mvi/, navigation/, theme/, utils/
│   │   ├── domain/                # model/ (domain models) + repository/ (interfaces) — pure Kotlin
│   │   ├── features/              # One package per feature: contract/ + ui/ + viewmodel/
│   │   │   │                      # appselection, inbox, notification (Android boundary), notificationdetail,
│   │   │   │                      # onboarding, ruleeditor, rules, settings
│   │   └── util/
│   └── src/test/                  # Unit tests (see Testing Standards)
├── docs/                          # ARCHITECTURE.md, roadmap.md, adr/, SDD-METHODOLOGY.md, guides/, capabilities.md
├── openspec/                      # specs/ (feature specs) + changes/ (active proposals)
└── build.gradle.kts               # Root build script
```

**Dependency Rules** (enforced by `./gradlew architectureCheck`):
- Features depend on domain models, repository interfaces, `core/ui`, and pure-Kotlin `core/*` services (e.g. `RuleEditorViewModel` → `RuleEngine`) — never on `core/data` or Android-facing `core/notification` internals directly
- `core/data` implements repository interfaces defined in `domain/repository`
- `core/extraction` and `domain/**` are pure Kotlin (no Android imports) — critical for testability
- No circular dependencies between packages

Future `:core:*` / `:feature:*` module split: see `docs/ARCHITECTURE.md` — "Project Structure".

## Architecture Layers

Four layers: **Presentation** (`features/` + `core/ui`), **Domain** (`domain/`), **Data** (`core/data`), **Extraction** (`core/extraction`). Full responsibilities, screens, key domain models, and per-layer rules live in `docs/ARCHITECTURE.md` — "Clean Architecture Layers". Non-obvious constraints worth keeping in mind:

- **Presentation**: never access repositories/DAOs from Composables — only through a ViewModel. Composables take `modifier: Modifier = Modifier` first; `@Preview` for light + dark.
- **Data**: repository interface + impl separation (ADR 005); return `Result<T>`, map failures to `core/common/Failure.kt` (ADR 006); never throw to ViewModels. Reactive via Flow; Paging3 for large lists.
- **Extraction**: `RuleMatcher`, `FieldExtractor`, `RuleEngine` are pure Kotlin — zero I/O, zero coroutines, zero `core.data`/`domain.repository` imports. `NotificationNormalizer` is also pure (takes `RawNotificationData`); only `RawNotificationReader` in `features/notification/` touches Android `StatusBarNotification`/`PackageManager` APIs.

## MVI Pattern

- `StateFlow<UiState>` in ViewModels; immutable state via `copy()`; one source of truth per screen.
- Events defined in a per-feature Contract (`features/[name]/contract/`), handled in `onEvent()`, on the `MviViewModel` base class (`core/ui/mvi`).
- One-off effects go through a `Channel` (`sendEffect()`) and are collected with `CollectOneOffEffects` — never a raw `viewModel.effect.collect { }`.

Full ViewModel + Contract example: `docs/ARCHITECTURE.md` — "MVI Pattern". Background: ADR 001, ADR 002.

## Dependency Injection

- Hilt: `@HiltViewModel`, `@Module`, `@InstallIn`, `@Provides`, `@Binds`; modules live in `core/di`.
- **Always** inject dispatchers with the `@Dispatcher(DispatcherType.X)` qualifier (ADR 008) — never use `Dispatchers.IO`/`Main` directly. This is testability-critical and enforced by `architectureCheck`.
- `NotificappListenerService` is an `@AndroidEntryPoint` with field injection. Background: ADR 004, ADR 008.

## Testing Standards

- **Framework**: JUnit 5 + Kotest assertions; **Mocking**: MockK (`MockKExtension`); **Flow**: Turbine; **Coroutines**: `runTest` with injected dispatchers.
- **Pattern**: Given-When-Then, one behavior per test. Living reference: `app/src/test/kotlin/dev/gaferneira/notificapp/core/extraction/RuleMatcherTest.kt`. Shared fixtures in `testutil/TestFixtures.kt`, fakes in `testutil/fakes/`.
- **Coverage priorities**: (1) Extraction engine — all operators/methods + edge cases, pure JVM; (2) ViewModels — all events and state transitions; (3) Repositories — success/error + mapping; (4) Normalization — varied notification formats.

## Code Quality Gates

Run before submitting PRs:
```bash
./gradlew spotlessApply
./gradlew detekt
./gradlew architectureCheck
./gradlew test
```
No `.git/hooks/pre-commit` is installed yet — these are enforced by CI / `./gradlew check`, not locally on every commit.

### Static Analysis (Detekt)
`./gradlew detekt` runs complexity/size checks (`LongMethod`, `LongParameterList`, `CyclomaticComplexMethod`) on top of the default ruleset. Pre-existing debt is grandfathered via `config/detekt/baseline.xml` — new code must pass clean. Regenerate only when intentionally accepting new debt: `./gradlew detektBaseline`.

**Boy-scout baseline policy (TD-16):** the baseline is meant to shrink, not accumulate. When a PR meaningfully touches a file with baseline entries, fix those entries in the same PR and regenerate — the diff must show the count going *down*. Never regenerate to *add* entries except via the explicit rule above (intentionally accepted new debt, called out in the PR description).

### Architecture Check (`./gradlew architectureCheck`)
Implemented in `config/architecture/architectureCheck.gradle.kts` (applied from `app/build.gradle.kts`), grandfathered violations in `config/architecture/baseline.txt`. Runs as part of `check`. Enforces seven rules Detekt can't express:

1. **Visibility** — `core/data/repository/*Impl` and every DAO/entity/mapper under `core/data/local` must be `internal`.
2. **Dispatcher injection** (ADR 008) — no hardcoded `Dispatchers.IO`/`Default`/`Main` outside `core/di/DispatchersModule.kt`.
3. **Effect collection** — one-off effects must go through `CollectOneOffEffects`, never a raw `viewModel.effect.collect { }`.
4. **Platform statics** — no `PackageManager` / `Settings.Secure` inside `features/*/viewmodel` or `domain/**`.
5. **Domain purity** — `domain/**` must never import `features/**` (graph flows `features → domain` only).
6. **No raw exception leaks** — a repository/data-source catch must not return a raw exception via `Result.failure(...)`; map to `Failure` (ADR 006) first. Starts with 41 grandfathered call sites — see DATA-07 in `audit/reports/step_5_data.md`.
7. **Contract purity** — a feature's `contract/` must not import `core.extraction` internals; map to a feature-owned model at the ViewModel boundary.

Rules 1-4 start clean; 5-7 start with grandfathered violations under the same shrink-only boy-scout policy: touch a listed file → fix its entry and remove the line in the same PR. The build fails on any **new** violation not in the baseline.

Not every audit finding is a mechanical rule — regex recompilation on hot paths (PERF-001/002) and N+1 DAO fan-out (PERF-008, DATA-01/03/05) need real data-flow analysis; a naive text-matching rule would be too fragile. Those stay as manual review checklist items in `.claude/commands/review-pr.md`.

## Error Handling & Privacy

- **Errors**: sealed failures in `core/common/Failure.kt` (ADR 006); repositories return `Result<T>` and never throw to ViewModels; user-facing messages via `UiText`; log with Timber; handle malformed notifications / invalid rules gracefully.
- **Privacy**: local-first (no cloud sync in MVP); user selects monitored apps; optional SQLCipher encryption; no telemetry without opt-in; user can export their data anytime. **Never commit** keystores, API keys, or `local.properties`.

## Code Location Map

| Type | Location |
|------|----------|
| Product roadmap | `docs/roadmap.md` |
| Common patterns (new screen/repo/extraction method/action) | `docs/guides/common-patterns.md` |
| Navigation setup guide | `docs/guides/navigation-guide.md` |
| Feature specs / proposals / designs / tasks | `openspec/specs/[area]/spec.md`, `openspec/changes/[name]/{proposal,design,tasks}.md` |
| Archived changes | `openspec/changes/archive/YYYY-MM-DD-name/` |
| Architecture Decisions | `docs/adr/00N-title.md` |
| Domain models / repository interfaces | `.../domain/model/`, `.../domain/repository/` |
| Repository implementations | `.../core/data/repository/` |
| Room entities / DAOs / mappers | `.../core/data/local/` |
| Extraction engine | `.../core/extraction/` |
| Notification processing pipeline / service | `.../core/notification/`, `.../features/notification/` |
| MVI base classes / navigation / theme | `.../core/ui/mvi/`, `.../core/ui/navigation/`, `.../core/ui/theme/` |
| Feature screens / ViewModels / contracts | `.../features/[feature]/` |
| Hilt modules | `.../core/di/` |
| Feature strings | `app/src/main/res/values/strings.xml` |
| Architecture Check rules / baseline | `config/architecture/architectureCheck.gradle.kts`, `config/architecture/baseline.txt` |
