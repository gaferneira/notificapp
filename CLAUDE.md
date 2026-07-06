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
- **Testing**: JUnit 5, Kotest, MockK, Turbine — 229 passing unit tests in `app/src/test` (extraction engine, use case, action executors, notification normalization, rule import/export codec with a golden-file wire-format test, `RuleEditorViewModel`/`AddFieldViewModel`/`NotificationDetailViewModel`/`RulesViewModel`); most other ViewModels and UI tests still pending
- **Structure**: Monolithic (single app module) with clean package separation, designed for future modularization

## Quick Reference

| I need to... | Reference |
|--------------|-----------|
| Understand MVI pattern | `docs/ARCHITECTURE.md` - "MVI Pattern" section |
| Check architecture decisions | `docs/adr/*.md` - ADRs covering patterns |
| Check product direction & phases | `docs/roadmap.md` |
| Add a new screen/feature | See "Adding a New Screen" section below |
| **Add navigation for a screen** | **See "Navigation Guidelines" section below** |
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

### Package Dependencies

```
features → domain (models + repository interfaces)
features → core/ui (MVI base, navigation)
features → core/* pure-Kotlin services (e.g. RuleEngine, RuleJsonCodec - Android-free, no core/data/domain.repository imports of their own)
core/data → domain (implements repository interfaces)
core/extraction → domain
core/notification → core/extraction (runs the rule engine)
```

**Dependency Rules:**
- Features depend on domain models, repository interfaces, `core/ui`, and pure-Kotlin `core/*` services (e.g. `RuleEditorViewModel` → `RuleEngine`, `RulesViewModel` → `RuleJsonCodec`) — never on `core/data` or Android-facing `core/notification` internals directly
- `core/data` implements repository interfaces defined in `domain/repository`
- `core/extraction` should be pure Kotlin depending only on domain models
- No circular dependencies between packages

### Future Modularization Path

When the project grows, packages can be extracted into modules:

```
:app
├── :core:model                  # Domain models (pure Kotlin)
├── :core:data                   # Data layer
├── :core:extraction             # Rule engine (reusable, testable)
├── :core:notification           # Android notification APIs
├── :core:ui                     # MVI base, navigation, theme
├── :feature:inbox
├── :feature:ruleeditor
├── :feature:rules
├── :feature:notificationdetail
├── :feature:appselection
├── :feature:onboarding
└── :feature:settings
```

## Critical Documents (Always Reference)

| Document | Purpose | When to Check |
|----------|---------|---------------|
| `docs/ARCHITECTURE.md` | Comprehensive architecture guide, extraction pipeline, coding standards, MVI pattern details | Before any architectural decision |
| `docs/roadmap.md` | Product positioning, phases, known debt, out-of-scope list | Before proposing new features |
| `docs/SDD-METHODOLOGY.md` | OpenSpec methodology, Gherkin format, validation workflow | Before implementing features |
| `docs/adr/*.md` | Architecture Decision Records — canonical index with statuses in `docs/adr/README.md` | When architecture questions arise |
| `openspec/specs/**/*.md` | Feature specifications with Gherkin scenarios | When implementing or modifying features |
| `.firebender/rules/*.mdc` | File-pattern-specific coding rules (local Firebender tooling, not committed — skip if absent) | When writing specific file types |

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
- Room database (`AppDatabase`) with 9 entities: Notification, Rule, RuleCondition, RuleField, RuleAction, RuleTargetApp, RuleExecution, ExtractedFieldValue, SelectedApp
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

### Example Structure
```kotlin
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.LoadNotifications -> loadNotifications()
            is UiEvent.OnNotificationClick -> handleNotificationClick(event.id)
            is UiEvent.CreateRuleFromNotification -> createRule(event.notificationId)
        }
    }
}

object InboxContract {
    data class UiState(
        val notifications: List<Notification> = emptyList(),
        val isLoading: Boolean = false,
        val selectedApps: List<String> = emptyList(),
        val error: UiText? = null,
    )

    sealed class UiEvent {
        data object LoadNotifications : UiEvent()
        data class OnNotificationClick(val id: String) : UiEvent()
        data class CreateRuleFromNotification(val notificationId: String) : UiEvent()
    }

    sealed class UiEffect {
        data class NavigateToRuleEditor(val notificationId: String) : UiEffect()
        data class ShowError(val message: UiText) : UiEffect()
    }
}
```

**Reference:** ADR 001, ADR 002 in `docs/adr/`

## OpenSpec-Driven Development Workflow

**All feature implementations MUST follow OpenSpec:**

### Phase 1: Proposal & Design
```
/opsx-propose "[change-name]"
```
- Automatically creates `openspec/changes/[name]/` with all artifacts
- Generates `proposal.md` (what & why), `design.md` (how), `tasks.md` (implementation steps)
- Edit these files to refine the content

### Phase 2: Specification
The skill may create delta specs in `openspec/changes/[name]/specs/`:
- Use Gherkin Given-When-Then format
- Include ADDED/MODIFIED/REMOVED sections
- Reference related features, ADRs, repositories, UI components, test files

### Phase 3: Tasks & Implementation
```
/opsx-apply [change-name]
```
- Reads all context files (proposal, design, tasks, specs)
- Implements each pending task from `tasks.md`, marking tasks complete
- Continues until all tasks are done or blocked

### Phase 4: Validation & Archive
```
/validate-spec openspec/specs/[area]/spec.md
/opsx-archive [change-name]
```
- Verify all scenarios have tests and implementation
- Archive merges delta specs to main specs; change moves to `openspec/changes/archive/`

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

> **Current status:** `app/src/test` has 229 passing tests covering `RuleMatcher` (all 6 operators), `FieldExtractor` (all 10 extraction methods), `RuleEngine`, `ProcessNotificationUseCase`, `ActionDispatcher`, the per-action executors, `NotificationDeduplicator`, `NotificationNormalizer` (pure Kotlin since TD-14), `RuleJsonCodec` (import/export, including a golden-file test locking the wire format), and four ViewModels (`RuleEditorViewModel`, `AddFieldViewModel`, `NotificationDetailViewModel`, `RulesViewModel`), with shared fixtures in `testutil/TestFixtures.kt`. Most other ViewModels and repositories still have no tests — follow the standards below when adding them.

### Unit Tests
- **Framework**: JUnit 5 with Kotest assertions
- **Mocking**: MockK with `MockKExtension`
- **Flow Testing**: Turbine for StateFlow/SharedFlow testing
- **Pattern**: Given-When-Then (arrange-act-assert)
- **Coroutine Testing**: Use `runTest` with injected dispatchers

### Test Structure
```kotlin
@ExtendWith(MockKExtension::class)
class RuleMatcherTest {

    @Test
    fun `notification matches rule with CONTAINS condition`() = runTest {
        // Given: a notification and a matching condition
        val notification = createTestNotification(
            title = "ICA Kvantum",
            content = "Totalt: 153,50 kr",
        )
        val conditions = listOf(
            createTestCondition(MatchingCondition.CONTENT, MatchingOperator.CONTAINS, "Totalt"),
        )

        // When: matching the notification
        val result = RuleMatcher.matches(notification, conditions)

        // Then: the rule matches
        result shouldBe true
    }
}
```

### Test Coverage Priorities
1. **Extraction Engine**: All matching operators and extraction methods, edge cases (pure JVM, no emulator)
2. **ViewModels**: All events handled, all state transitions tested
3. **Repositories**: Success and error cases, mapping logic
4. **Normalization**: Various notification formats from different apps

## Build Configuration

### Build Variants
| Variant | Purpose | Usage |
|---------|---------|-------|
| `debug` | Development with debugging | Daily development |
| `release` | Production release | Distribution |

### Essential Gradle Commands

```bash
# Code formatting (REQUIRED before commits)
./gradlew spotlessApply

# Static analysis (complexity/size budgets, runs as part of `check`)
./gradlew detekt

# Run all tests
./gradlew test

# Run tests with coverage
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

## Firebender Commands

### OpenSpec Workflow Commands (Firebender Skills)
| Command                  | Purpose | When to Use |
|--------------------------|---------|-------------|
| `/opsx-propose "[name]"` | Create new change with all artifacts | Starting a new feature/modification |
| `/opsx-apply [name]`     | Implement tasks from a change | Ready to write code |
| `/opsx-archive [name]`   | Archive completed change | All tasks done |
| `/opsx-explore [topic]`  | Explore mode for discovery | Thinking/research phase |

### PR Creation Commands
| Command | Purpose | When to Use |
|---------|---------|-------------|
| `/create-pr` | PR generation | Any code changes locally or in the current branch |

### Validation & Testing Commands
| Command | Purpose | When to Use |
|---------|---------|-------------|
| `/validate-spec [path]` | Check spec implementation | After implementation |
| `/generate-test [path]` | Generate test skeleton | After spec is written |
| `/validate-all-specs` | Validate entire project | Before releases |

**Reference:** `docs/SDD-METHODOLOGY.md` - Complete OpenSpec workflow

## Code Quality Gates

### Pre-Commit (via Git Hooks)
1. Spotless format check
2. Unit tests for changed files

### Static Analysis (Detekt)
`./gradlew detekt` runs complexity/size checks (`LongMethod`, `LongParameterList`, `CyclomaticComplexMethod`) on top of the default ruleset, gated the same as Spotless. Pre-existing debt is grandfathered via `config/detekt/baseline.xml` — new code must pass clean. Regenerate the baseline only when intentionally accepting new debt: `./gradlew detektBaseline`.

**Boy-scout baseline policy (TD-16):** the baseline is meant to shrink over time, not just accumulate. When a PR meaningfully touches a file that already has baseline entries, fix those entries in the same PR and regenerate the baseline (`./gradlew detektBaseline`) — the diff must show the baseline count going *down*. Never regenerate to *add* entries except via the existing explicit rule above (intentionally accepted new debt, called out in the PR description).

### Manual Verification
Always run before submitting PRs:
```bash
./gradlew spotlessApply
./gradlew detekt
./gradlew test
```

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

### Adding a New Screen

1. Create package in `features/[screenname]/` with `contract/`, `ui/`, `viewmodel/` sub-packages
2. Create Contract object with UiState, UiEvent, UiEffect sealed classes in `contract/`
3. Create ViewModel extending `MviViewModel<UiState, UiEvent, UiEffect>` in `viewmodel/`
4. Create screen Composable in `ui/`
5. Add to navigation (see "Navigation Guidelines" below)
6. Write unit tests for the ViewModel

### Adding a New Repository

1. Define interface in `domain/repository/`
2. Create implementation in `core/data/repository/`
3. Create Room entity in `core/data/local/entity/` (if needed)
4. Create DAO in `core/data/local/dao/` and mapper in `core/data/local/mapper/`
5. Bind in `core/di/RepositoryModule` using `@Binds`
6. Return `Result<T>` or sealed class
7. Add unit tests

### Adding a New Extraction Method

1. Add the method to `RuleField.ExtractionMethod` (domain model)
2. Implement in `core/extraction/FieldExtractor` (pure Kotlin, no Android imports)
3. Add tests in `app/src/test` (pure JVM, no emulator)
4. Add a config composable in `features/ruleeditor/ui/fieldconfig/` and one `when` branch in `AddFieldBottomSheet.kt`'s `AddFieldBottomSheetContent` (per TD-13)

### Adding a New Action Type

1. Add the type to `ActionType` (domain model); keep config in `RuleAction.config` (`Map<String, String>`) with typed accessor methods
2. Implement execution as an `ActionExecutor` (`domain/action/ActionExecutor.kt`) and register it in `core/di/ActionModule.kt` via `@Binds @IntoMap @ActionTypeKey(ActionType.X)` — the `ActionDispatcher` picks it up automatically; no service edits needed
3. Add a config composable in `features/ruleeditor/ui/actionconfig/` and one `when` branch in `ActionBottomSheet.kt`'s `ActionsContent` (per TD-13 — don't grow the sheet itself, it only dispatches)
4. Record execution outcome on the `RuleExecution`

### Adding a New Screen (Complete OpenSpec + PR Workflow)

1. Create change with `/opsx-propose "[change-name]"` (creates proposal, design, tasks)
2. Review and edit generated artifacts in `openspec/changes/[name]/`
3. Implement with `/opsx-apply [change-name]` (skill implements all tasks)
4. Validate with `/validate-spec openspec/specs/[area]/spec.md`
5. Archive with `/opsx-archive [change-name]`
6. Create PR with `/create-pr --change [change-name] [ticket-number] [ticket-title]`

## Navigation Guidelines

### Navigation Architecture Overview

We use Navigation3 with the following pattern:
- **Routes**: Defined in `Screen` sealed class with `@Serializable`
- **Entry Provider**: Maps routes to composables in `MainActivity`
- **NavigationHandler**: Singleton for ViewModel-driven navigation
- **Effect Handling**: Internal for sub-flows, specific callbacks for parent coordination

### Adding a Screen to Navigation

1. **Add route to `Screen` sealed class** (`core/ui/navigation/Screen.kt`):
```kotlin
@Serializable
data class NotificationDetails(val notificationId: String) : Screen()
```

2. **Add factory to `Routes` object** (`core/ui/navigation/Routes.kt`):
```kotlin
fun notificationDetails(notificationId: String): Screen =
    Screen.NotificationDetails(notificationId)
```

3. **Add entry to `entryProvider`** in `MainActivity.kt`:
```kotlin
entry<Screen.NotificationDetails> { screen ->
    NotificationDetailScreen(
        notificationId = screen.notificationId,
        onBackClicked = { navigator.goBack() },
    )
}
```

### Effect Handling: Internal vs Callbacks

**Internal Handling (Bottom Sheets, Self-Contained Flows):**
```kotlin
// Screen manages its own bottom sheet
@Composable
fun RuleEditorScreen(onBackClicked: () -> Unit) {
    val sheetState = rememberSheetState()

    // Handle ViewModel effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.ShowAddFieldSheet -> sheetState.show()
            is UiEffect.NavigateBack -> onBackClicked()
        }
    }

    // Bottom sheet is internal implementation detail
    AddFieldBottomSheet(
        isVisible = sheetState.isVisible,
        onDismiss = { sheetState.hide() },
        onFieldAdded = { field ->
            viewModel.onEvent(UiEvent.OnFieldAdded(field))
            sheetState.hide()
        }
    )
}
```

**Specific Callbacks (When Parent Coordination Needed):**
```kotlin
// Good - specific callbacks
@Composable
fun AppSelectionScreen(
    onNavigateToMainApp: () -> Unit,
    onNavigateBack: () -> Unit,
    onShowError: (message: String) -> Unit,
)

// Avoid - generic callback
@Composable
fun AppSelectionScreen(
    onNavigate: (UiEffect) -> Unit,  // Don't do this
)
```

**Decision Matrix:**

| Scenario | Pattern | Example |
|----------|---------|---------|
| Simple back navigation | Specific callback | `onBackClicked: () -> Unit` |
| Success/error messages | Specific callbacks | `onShowSuccess: (String) -> Unit` |
| Results needing parent action | Specific typed callback | `onFieldAdded: (ExtractionField) -> Unit` |
| Sub-screens within same flow | Internal handling | `AddFieldBottomSheet` in `RuleEditorScreen` |
| Full screen transitions | NavigationHandler | ViewModel emits effect, MainActivity handles |

### NavOptions Usage

```kotlin
// Tab switching - clear stack to avoid back stack buildup
navigator.navigate(Routes.inbox(), navOptions { clearStack() })

// Pop up to specific screen before navigating
navigator.navigate(
    Routes.inbox(),
    navOptions { 
        popUpTo(Screen.Inbox::class)
        launchSingleTop = true
    }
)
```

### ViewModel-Driven Navigation

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val navigationHandler: NavigationHandler
) : ViewModel() {
    fun onItemClick(id: String) {
        viewModelScope.launch {
            navigationHandler.navigate(Routes.notificationDetails(id))
        }
    }
}
```

MainActivity collects commands:
```kotlin
LaunchedEffect(Unit) {
    navigationHandler.navigationFlow.collect { command ->
        when (command) {
            is NavigationCommand.Navigate -> navigator.navigate(command.screen)
            is NavigationCommand.GoBack -> navigator.goBack()
        }
    }
}
```

**Reference:** ADR 007 - Navigation3 with Custom Navigator

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
