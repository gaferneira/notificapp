# Notificapp - Agent Guidelines

## Project Identity

- **Name**: Notificapp
- **Purpose**: Open-source notification automation for Android — rules that act on notifications (dismiss, snooze, alerts) and extract structured data from them, entirely on-device
- **Architecture**: MVI (Model-View-Intent), feature-first packaging with shared `core/` infrastructure
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Dependency Injection**: Hilt
- **Navigation**: Navigation3 with custom Navigator (ADR 007)
- **Build System**: Gradle with Kotlin DSL
- **Testing**: JUnit 5, Kotest, MockK, Turbine — 286 passing unit tests in `app/src/test` (extraction engine, use case, action executors, notification normalization, rule import/export codec with a golden-file wire-format test, `RuleEditorViewModel`/`AddFieldViewModel`/`NotificationDetailViewModel`/`RulesViewModel`); most other ViewModels and UI tests still pending
- **Structure**: Monolithic (single app module) with clean package separation, designed for future modularization

## Development Status

**Pre-launch — no installed user base yet.** Notificapp has not shipped a public release (F-Droid/Play). Until it does, there is no production data or backward-compatible surface to protect:

- **Room schema**: prefer a clean schema bump (destructive migration) over hand-written `Migration` objects when changing `core/data/local/entity/`. Don't add a real `Migration` just to preserve existing rows — there are none to preserve.
- **Rule wire format** (`core/rulesharing/RuleJsonCodec`, `docs/rule-format.md`): free to break `RuleExportDto`/schema version across commits without a compatibility shim — no one has exported rules from a shipped version yet.
- **Domain model shape**: sealed-class/structural rewrites of `Rule`, `RuleCondition`, etc. are fine — condition storage uses a JSON polymorphic column (`rule_conditions.payload`), so adding new condition families never requires a Room migration.

Revisit and remove this section once the app has a real release with installed users — at that point migrations and wire-format compatibility become mandatory again.

## Quick Reference

| I need to... | Reference |
|--------------|-----------|
| Understand MVI pattern | `docs/ARCHITECTURE.md` - "MVI Pattern" section |
| Understand clean architecture layers in depth | `docs/ARCHITECTURE.md` - "Clean Architecture Layers" section |
| Check architecture decisions | `docs/adr/*.md` - ADRs covering patterns |
| Check product direction & phases | `docs/roadmap.md` |
| See the full functional/feature map (conditions, extraction methods, actions) | `docs/capabilities.md` — **keep in sync**, see rule below |
| Add a new screen/repository/extraction method/action type | `docs/guides/common-patterns.md` |
| **Add navigation for a screen** | **`docs/guides/navigation-guide.md`** |
| Check file-specific rules | `.firebender/rules/*.mdc` (local tooling, not committed — skip if absent) |
| See feature specifications | `openspec/specs/[area]/` |
| Run tests | `./gradlew test` |
| Format code | `./gradlew spotlessApply` |

## Project Structure

### Current Structure (feature-first)

Package root is `dev.gaferneira.notificapp`.

```
Notificapp/
├── app/
│   ├── src/main/kotlin/dev/gaferneira/notificapp/
│   │   ├── MainActivity.kt
│   │   ├── core/
│   │   │   ├── common/            # Shared types (Failure)
│   │   │   ├── data/              # Data layer
│   │   │   │   ├── local/         # AppDatabase, converters, DAOs, entities, mappers
│   │   │   │   ├── preferences/   # DataStore-backed user preferences
│   │   │   │   └── repository/    # Repository implementations
│   │   │   ├── di/                # Hilt modules (Database, Repository, Dispatchers, Coil)
│   │   │   ├── extraction/        # Rule engine (RuleEngine, RuleMatcher, FieldExtractor)
│   │   │   ├── notification/      # NotificationNormalizer, ProcessNotificationUseCase, NotificationDeduplicator,
│   │   │   │                      # action/ (ActionDispatcher, per-type ActionExecutors)
│   │   │   └── ui/
│   │   │       ├── mvi/           # MviViewModel, CollectOneOffEffects
│   │   │       ├── navigation/    # Screen, Routes, Navigator, NavigationHandler, MainBottomNav
│   │   │       ├── theme/         # Material 3 theme
│   │   │       └── utils/
│   │   ├── domain/
│   │   │   ├── model/             # Notification, Rule, RuleCondition, RuleField, RuleAction,
│   │   │   │                      # RuleExecution, ExtractedFieldValue, AppInfo, SelectedApp
│   │   │   └── repository/        # Repository interfaces
│   │   ├── features/              # One package per feature: contract/ + ui/ + viewmodel/
│   │   │   ├── appselection/
│   │   │   ├── inbox/
│   │   │   ├── notification/      # NotificappListenerService, RawNotificationReader (Android boundary)
│   │   │   ├── notificationdetail/
│   │   │   ├── onboarding/
│   │   │   ├── ruleeditor/        # also has domain/ (RuleUiModel) and ui/components/
│   │   │   ├── rules/
│   │   │   └── settings/
│   │   └── util/
│   └── src/test/                  # Unit tests (88 tests: extraction, use case, action executors)
├── docs/                          # ARCHITECTURE.md, roadmap.md, adr/, SDD-METHODOLOGY.md
├── openspec/                      # specs/ (feature specs) + changes/ (active proposals)
├── gradle/                        # Gradle configuration
└── build.gradle.kts               # Root build script
```

**Dependency Rules:**
- Features depend on domain models, repository interfaces, `core/ui`, and pure-Kotlin `core/*` services (e.g. `RuleEditorViewModel` → `RuleEngine`, `RulesViewModel` → `RuleJsonCodec`) — never on `core/data` or Android-facing `core/notification` internals directly
- `core/data` implements repository interfaces defined in `domain/repository`
- `core/extraction` should be pure Kotlin depending only on domain models
- No circular dependencies between packages

### Future Modularization Path

See `docs/ARCHITECTURE.md` - "Project Structure" section for the planned `:core:*` / `:feature:*` module split.

## Critical Documents (Always Reference)

| Document | Purpose | When to Check |
|----------|---------|---------------|
| `docs/ARCHITECTURE.md` | Comprehensive architecture guide, extraction pipeline, coding standards, MVI pattern details | Before any architectural decision |
| `docs/roadmap.md` | Product positioning, phases, known debt, out-of-scope list | Before proposing new features |
| `docs/SDD-METHODOLOGY.md` | OpenSpec methodology, Gherkin format, validation workflow | Before implementing features |
| `docs/adr/*.md` | Architecture Decision Records — canonical index with statuses in `docs/adr/README.md` | When architecture questions arise |
| `openspec/specs/**/*.md` | Feature specifications with Gherkin scenarios | When implementing or modifying features |
| `.firebender/rules/*.mdc` | File-pattern-specific coding rules (local Firebender tooling, not committed — skip if absent) | When writing specific file types |
| `docs/capabilities.md` | Human/agent-readable functional map: every rule condition operator, extraction method, action type, and screen capability | Update it in the same PR whenever you touch `domain/model/RuleCondition.kt`, `RuleField.ExtractionMethod`, `RuleAction`/`ActionType`, or add/remove a screen under `features/*` |

## Mandatory Pre-Flight Checklist

Before making any code changes:

- [ ] Read `docs/ARCHITECTURE.md` section relevant to the layer being modified
- [ ] Check `.firebender/rules/*.mdc` files for file-specific guidelines matching your changes (local tooling — skip if the folder is absent)
- [ ] Check `openspec/specs/` for existing specifications related to the feature
- [ ] Check `openspec/changes/` for active changes and proposals
- [ ] Review relevant ADRs in `docs/adr/` for architectural constraints
- [ ] Ensure changes follow the MVI pattern (unidirectional data flow)
- [ ] Run Spotless formatting after changes: `./gradlew spotlessApply`

## Architecture Layers (Quick Reference)

### 1. Presentation Layer (`features/` + `core/ui`)

**Responsibilities:** Display UI, handle user interactions, observe ViewModel state

**Existing Screens:**
- **OnboardingScreen**: Notification listener permission flow
- **AppSelectionScreen**: Choose which installed apps to monitor
- **InboxScreen**: Paginated notification list with filters and search
- **NotificationDetailScreen**: Full notification content + rule execution results
- **RuleEditorScreen**: Multi-step rule creation (conditions, fields, actions, app targets)
- **RulesScreen**: View, filter, search, toggle rules
- **SettingsScreen**: Monitored apps, data collection, listener status
- **Data Browser**: *planned* (Roadmap Phase 3) — does not exist yet

**Key Rules:**
- Use Jetpack Compose with Material 3 components
- Use `collectAsStateWithLifecycle()` to observe ViewModel state
- **NEVER** access repositories or DAOs directly from Composables - only through ViewModel
- Accept `modifier: Modifier = Modifier` as first optional parameter in Composables
- Use `@Preview` for both Light and Dark modes
- Implement accessibility (content descriptions, semantic properties)
- Use theme colors, typography, and spacing

**Reference:** `docs/ARCHITECTURE.md` - "Presentation Layer" section

### 2. Domain Layer (`domain/` package)

**Responsibilities:** Domain models and repository contracts, pure Kotlin

**Key Domain Models:**
- `Notification` - Captured notification, normalized from the system
- `Rule` - User-defined rule: conditions + extraction fields + actions + target apps
- `RuleCondition` / `RuleField` / `RuleAction` - Rule building blocks
- `RuleExecution` - One record per rule match (extracted data + triggered actions)
- `ExtractedFieldValue` - Typed extracted value (text/number/date) for filtering
- `AppInfo`, `SelectedApp` - App metadata and monitoring selection

**Key Rules:**
- Platform-independent (no Android framework dependencies)
- Use data classes for models, sealed classes for state/events/effects
- Keep business logic in ViewModels or use case classes

### 3. Data Layer (`core/data`)

**Responsibilities:** Data persistence, repository implementations

**Key Components:**
- Repositories: `NotificationRepository`, `RuleRepository`, `SelectedAppRepository`, `UserPreferencesRepository`, `RuleExecutionRepository` (wraps `RuleExecutionDao`/`ExtractedFieldValueDao`/`NotificationDao` writes transactionally)
- Room database (`AppDatabase`) with 9 entities: Notification, Rule, RuleCondition, RuleField, RuleAction, RuleTargetApp, RuleExecution, ExtractedFieldValue, SelectedApp.
- DAOs per entity, mappers from entities to domain models
- DataStore for preferences (selected apps, settings)

**Key Rules:**
- Repository interface + implementation separation (per ADR 005)
- Return `Result<T>` for operations; sealed failure types live in `core/common/Failure.kt` (per ADR 006)
- Map database entities to domain models
- Provide reactive streams (Flow); use Paging3 for large lists
- Handle errors gracefully (no exceptions thrown to ViewModels)

### 4. Extraction Layer (`core/extraction`)

**Responsibilities:** Match notifications against rules and extract structured fields

**Key Components:**
- `RuleMatcher` - Checks if a notification matches rule conditions (6 operators; pure Kotlin)
- `FieldExtractor` - Extracts fields using 10 extraction methods (regex, anchors, keywords, JSON path, smart amount/date, ...; pure Kotlin)
- `RuleEngine` - Pure `evaluate(notification, rules): List<RuleMatch>`; zero I/O, zero coroutines, zero `core.data`/`domain.repository` imports. Rule loading and persistence live in `core/notification/ProcessNotificationUseCase`, which saves via `RuleExecutionRepository`

**Notification normalization** (`NotificationNormalizer`) is pure Kotlin (takes `RawNotificationData`, no Android imports) and lives in `core/notification/` alongside `NotificationDeduplicator` and `ProcessNotificationUseCase`. Only the thin `RawNotificationReader` extension functions in `features/notification/` touch the Android-specific `StatusBarNotification`/`PackageManager` APIs.

**Key Rules:**
- `RuleMatcher` and `FieldExtractor` must stay pure Kotlin (no Android imports) — this is critical for testability
- Extensible design for new extraction methods

## MVI Pattern Implementation

### State Management
- Use `StateFlow<UiState>` in ViewModels
- Keep state immutable (use `copy()` for updates)
- Single source of truth per screen
- Use sealed classes for different states (Loading, Success, Error)

### Event/Intent Handling
- Define events in a per-feature Contract object (`features/[name]/contract/`)
- Handle all events in ViewModel's `onEvent()` method
- Use the `MviViewModel` base class (`core/ui/mvi`)

### Effects (One-time Events)
- `MviViewModel` exposes effects via a `Channel`; send with `sendEffect()`
- Collect in Composables with `CollectOneOffEffects` (`core/ui/mvi`)

**Example Structure:** see `docs/ARCHITECTURE.md` - "MVI Pattern" section for a full ViewModel + Contract example.

**Reference:** ADR 001, ADR 002 in `docs/adr/`

## OpenSpec-Driven Development Workflow

**All feature implementations MUST follow OpenSpec.** This is a folder/artifact convention, not a specific tool — drive it with whatever command, script, or agent logic you have, as long as it produces these artifacts in these locations.

**Reference:** `docs/SDD-METHODOLOGY.md` - Complete OpenSpec workflow guide

## Dependency Injection Guidelines

- Use Hilt annotations: `@HiltViewModel`, `@Module`, `@InstallIn`, `@Provides`, `@Binds`
- Modules live in `core/di` (`DatabaseModule`, `RepositoryModule`, `DispatchersModule`, `CoilModule`)
- Use appropriate scoping: `@Singleton`, `@ViewModelScoped`, `@ActivityScoped`
- **Always** use `@Dispatcher(DispatcherType.X)` qualifier for coroutine dispatchers (per ADR 008)
- Inject dispatchers for testability, never use `Dispatchers.IO`/`Main` directly

**Key Injection Points:**
- `NotificappListenerService` - `@AndroidEntryPoint` service with field injection
- `RuleEngine` - Extraction logic
- Repositories - Data access
- ViewModels - UI state management

**Reference:** ADR 004, ADR 008 in `docs/adr/`

## Testing Standards

> **Current status:** `app/src/test` has 286 passing tests covering `RuleMatcher` (all 6 operators), `FieldExtractor` (all 10 extraction methods), `RuleEngine`, `ProcessNotificationUseCase`, `ActionDispatcher`, the per-action executors, `NotificationDeduplicator`, `NotificationNormalizer` (pure Kotlin since TD-14), `RuleJsonCodec` (import/export, including a golden-file test locking the wire format), and four ViewModels (`RuleEditorViewModel`, `AddFieldViewModel`, `NotificationDetailViewModel`, `RulesViewModel`), with shared fixtures in `testutil/TestFixtures.kt`. Most other ViewModels and repositories still have no tests — follow the standards below when adding them.

### Unit Tests
- **Framework**: JUnit 5 with Kotest assertions
- **Mocking**: MockK with `MockKExtension`
- **Flow Testing**: Turbine for StateFlow/SharedFlow testing
- **Pattern**: Given-When-Then (arrange-act-assert)
- **Coroutine Testing**: Use `runTest` with injected dispatchers

### Test Structure
Given-When-Then, one behavior per test. See `app/src/test/kotlin/dev/gaferneira/notificapp/core/extraction/RuleMatcherTest.kt` for a living reference example — it's kept up to date by definition, so prefer it over a hardcoded snippet here.

### Test Coverage Priorities
1. **Extraction Engine**: All matching operators and extraction methods, edge cases (pure JVM, no emulator)
2. **ViewModels**: All events handled, all state transitions tested
3. **Repositories**: Success and error cases, mapping logic
4. **Normalization**: Various notification formats from different apps

**Reference:** `docs/SDD-METHODOLOGY.md` - Complete OpenSpec workflow (tool-agnostic; use whatever commands/agent your session provides)

## Code Quality Gates

### Static Analysis (Detekt)
`./gradlew detekt` runs complexity/size checks (`LongMethod`, `LongParameterList`, `CyclomaticComplexMethod`) on top of the default ruleset, gated the same as Spotless. Pre-existing debt is grandfathered via `config/detekt/baseline.xml` — new code must pass clean. Regenerate the baseline only when intentionally accepting new debt: `./gradlew detektBaseline`.

**Boy-scout baseline policy (TD-16):** the baseline is meant to shrink over time, not just accumulate. When a PR meaningfully touches a file that already has baseline entries, fix those entries in the same PR and regenerate the baseline (`./gradlew detektBaseline`) — the diff must show the baseline count going *down*. Never regenerate to *add* entries except via the existing explicit rule above (intentionally accepted new debt, called out in the PR description).

### Architecture Check (`./gradlew architectureCheck`)
Implemented in `config/architecture/architectureCheck.gradle.kts` (applied from `app/build.gradle.kts`), grandfathered violations tracked in `config/architecture/baseline.txt`. Runs as part of `check` alongside Detekt. Enforces seven rules that Detekt's built-in rule set can't express (visibility-with-exceptions, forbidden-except-here) and that repeated as findings across `audit/reports/*.md` despite already being documented in prose in this file / the ADRs:

1. **Visibility** — `core/data/repository/*Impl`, and every DAO/entity/mapper under `core/data/local`, must be `internal`.
2. **Dispatcher injection** (ADR 008) — no hardcoded `Dispatchers.IO`/`Default`/`Main` outside `core/di/DispatchersModule.kt`.
3. **Effect collection** — one-off effects must go through `CollectOneOffEffects`, never a raw `viewModel.effect.collect { }`.
4. **Platform statics** — no `PackageManager` / `Settings.Secure` inside `features/*/viewmodel` or `domain/**`.
5. **Domain purity** — `domain/**` must never import `features/**` (the dependency graph only flows `features → domain`).
6. **No raw exception leaks** — a repository/data-source catch block must not hand a raw exception back through `Result.failure(...)`; it has to be mapped to the `Failure` hierarchy (ADR 006) first. This one starts with 41 grandfathered call sites because the `Failure`-mapping helper the fix needs doesn't exist yet — see DATA-07 in `audit/reports/step_5_data.md`.
7. **Contract purity** — a feature's `contract/` (its public `UiState`/`UiEvent`/`UiEffect` surface) must not import `core.extraction` internals; map to a feature-owned model at the ViewModel boundary instead.

Rules 1-4 start clean (their violations were fixed). Rules 5-7 start with their pre-existing violations grandfathered in `config/architecture/baseline.txt`, using the same shrink-only boy-scout policy as the Detekt baseline: if you touch a listed file, fix its entry and remove the line in the same PR. The task fails the build on any **new** violation not already in the baseline — this stops new code from adding to any of the seven categories, even the ones with pre-existing debt.

Not every audit finding is (or should be) a mechanical rule here — regex/pattern recompilation on hot paths (PERF-001/002) and N+1 DAO fan-out (PERF-008, DATA-01/03/05) are real repeated defects but need real data-flow analysis to detect reliably; a naive text-matching rule for either would be too fragile (false positives erode trust in the whole check faster than it prevents mistakes). Those stay as manual review checklist items in `.claude/commands/review-pr.md` instead.

### Manual Verification
Always run before submitting PRs:
```bash
./gradlew spotlessApply
./gradlew detekt
./gradlew architectureCheck
./gradlew test
```

Note: the "Pre-Commit via Git Hooks" section above describes the intended gate, but no `.git/hooks/pre-commit` is currently installed in this repo (only the sample hook exists) — until that's wired up, these checks are enforced by CI / `./gradlew check`, not locally on every commit.

## Error Handling

- Use sealed class hierarchies for failures (per ADR 006, see `core/common/Failure.kt`)
- Repository methods return `Result<T>`
- Never throw exceptions from repositories to ViewModels
- Show user-friendly error messages (use `UiText` for string resources)
- Log errors using Timber for debugging
- Handle edge cases gracefully (malformed notifications, invalid rules)

## Performance Guidelines

- Use `derivedStateOf` for computed state in Compose
- Avoid unnecessary recompositions
- Use Room for efficient database queries with proper indexing
- Use Flow for reactive updates without polling
- Paginate large lists with Paging3 (inbox, extracted data)
- Lazy load notification content when possible

## Privacy & Security Considerations

- **Local-first**: All notification data stored locally, no cloud sync in MVP
- **User control**: User selects which apps are monitored
- **Optional encryption**: Database can be encrypted with SQLCipher if needed
- **No analytics**: No telemetry without explicit user opt-in
- **Data export**: User can export their extracted data anytime
- **Never commit**: keystores, API keys, `local.properties`

## Common Patterns

Full step-by-step checklists (adding a screen, repository, extraction method, action type, or a complete OpenSpec + PR workflow) live in **`docs/guides/common-patterns.md`** — read it when doing one of these tasks.

## Navigation Guidelines

Navigation3 uses `Screen` sealed routes + `Routes` factories + `entryProvider` in `MainActivity`, with `NavigationHandler` for ViewModel-driven navigation. Full walkthrough, code examples, and the internal-handling-vs-callback decision matrix live in **`docs/guides/navigation-guide.md`** — read it whenever wiring a screen into navigation. Background: ADR 007.

## Resources & References

- [Now in Android](https://github.com/android/nowinandroid) - Architecture inspiration
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [MVI Architecture](https://hannesdorfmann.com/android/model-view-intent/)
- [Android Clean Architecture](https://developer.android.com/topic/architecture)
- [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService)

## File Locations Summary

| Type | Location |
|------|----------|
| Product roadmap | `docs/roadmap.md` |
| Common implementation patterns (new screen/repo/extraction method/action) | `docs/guides/common-patterns.md` |
| Navigation setup guide | `docs/guides/navigation-guide.md` |
| Feature Specs | `openspec/specs/[area]/spec.md` |
| Change Proposals | `openspec/changes/[name]/proposal.md` |
| Change Designs | `openspec/changes/[name]/design.md` |
| Change Tasks | `openspec/changes/[name]/tasks.md` |
| Archived Changes | `openspec/changes/archive/YYYY-MM-DD-name/` |
| Architecture Decisions | `docs/adr/00N-title.md` |
| Domain Models | `app/src/main/kotlin/.../domain/model/` |
| Repository Interfaces | `app/src/main/kotlin/.../domain/repository/` |
| Repository Implementations | `app/src/main/kotlin/.../core/data/repository/` |
| Room Entities / DAOs / Mappers | `app/src/main/kotlin/.../core/data/local/` |
| Extraction Engine | `app/src/main/kotlin/.../core/extraction/` |
| Notification Processing Pipeline | `app/src/main/kotlin/.../core/notification/` |
| Notification Service | `app/src/main/kotlin/.../features/notification/` |
| MVI Base Classes | `app/src/main/kotlin/.../core/ui/mvi/` |
| Navigation | `app/src/main/kotlin/.../core/ui/navigation/` |
| Feature Screens / ViewModels / Contracts | `app/src/main/kotlin/.../features/[feature]/` |
| Hilt Modules | `app/src/main/kotlin/.../core/di/` |
| Feature Strings | `app/src/main/res/values/strings.xml` |
| Theme | `app/src/main/kotlin/.../core/ui/theme/` |
| Architecture Check rules / baseline | `config/architecture/architectureCheck.gradle.kts`, `config/architecture/baseline.txt`