# Notificapp - Agent Guidelines

## Project Identity

- **Name**: Notificapp
- **Purpose**: Open-source Android app that turns notification text into structured, reusable data — locally on your device
- **Architecture**: MVI (Model-View-Intent) with Clean Architecture
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Dependency Injection**: Hilt
- **Build System**: Gradle with Kotlin DSL
- **Testing**: JUnit 5, Kotest, MockK, Turbine
- **Structure**: Monolithic (single app module) with clean package separation, designed for future modularization

## Quick Reference

| I need to... | Reference |
|--------------|-----------|
| Understand MVI pattern | `docs/ARCHITECTURE.md` - "MVI Pattern" section |
| Check architecture decisions | `docs/adr/*.md` - ADRs covering patterns |
| Add a new screen/feature | See "Adding a New Screen" section below |
| **Add navigation for a screen** | **See "Navigation Guidelines" section below** |
| Check file-specific rules | `.firebender/rules/*.mdc` |
| See feature specifications | `openspec/specs/[area]/` |
| Run tests | `./gradlew test` |
| Format code | `./gradlew spotlessApply` |

## Project Structure

### Current Monolithic Structure

```
Notificapp/
├── app/
│   ├── src/main/kotlin/com/notificapp/
│   │   ├── data/              # Data layer (repositories, Room database, DAOs)
│   │   ├── domain/              # Domain layer (models, repository interfaces)
│   │   ├── extraction/          # Core extraction engine (pure Kotlin, no Android)
│   │   ├── notification/        # Android notification APIs (listener service)
│   │   └── ui/                  # Presentation layer (screens, ViewModels)
│   │       ├── inbox/          # Inbox screen - incoming notifications
│   │       ├── ruleeditor/     # Rule editor - create/test extraction rules
│   │       ├── dataviewer/     # Data viewer - view/filter extracted events
│   │       ├── components/     # Shared UI components
│   │       └── theme/          # Material 3 theme
│   └── src/test/                # Unit tests (mirror of main structure)
├── docs/                        # Documentation
│   ├── ARCHITECTURE.md         # Architecture guide
│   ├── adr/                    # Architecture Decision Records
│   └── SDD-METHODOLOGY.md      # OpenSpec methodology
├── openspec/                    # OpenSpec specifications
│   ├── specs/                  # Feature specifications
│   └── changes/                # Active changes and proposals
├── gradle/                      # Gradle configuration
└── build.gradle.kts            # Root build script
```

### Package Dependencies

```
ui → domain → data
↑
extraction → domain
↑
notification → extraction → domain
```

**Dependency Rules:**
- UI only depends on domain models and repository interfaces
- Extraction engine depends only on domain models (pure Kotlin, testable)
- Data layer implements repository interfaces
- No circular dependencies between packages

### Future Modularization Path

When the project grows, packages can be extracted into modules:

```
:app                          # Main application
├── :core:model              # Domain models (pure Kotlin)
├── :core:data               # Data layer
├── :core:extraction         # Extraction engine (reusable, testable)
├── :core:notification       # Android notification APIs
├── :feature:inbox           # Inbox screen
├── :feature:ruleeditor      # Rule editor screen
└── :feature:dataviewer      # Data viewer screen
```

## Critical Documents (Always Reference)

| Document | Purpose | When to Check |
|----------|---------|---------------|
| `docs/ARCHITECTURE.md` | Comprehensive architecture guide, extraction pipeline, coding standards, MVI pattern details | Before any architectural decision |
| `docs/SDD-METHODOLOGY.md` | OpenSpec methodology, Gherkin format, validation workflow | Before implementing features |
| `docs/adr/*.md` | Architecture Decision Records (8 ADRs covering MVI, DI, Repositories, etc) | When architecture questions arise |
| `openspec/specs/**/*.md` | Feature specifications with Gherkin scenarios | When implementing or modifying features |
| `.firebender/rules/*.mdc` | File-pattern-specific coding rules | When writing specific file types |

## Mandatory Pre-Flight Checklist

Before making any code changes:

- [ ] Read `docs/ARCHITECTURE.md` section relevant to the layer being modified (Presentation/Domain/Data/Extraction)
- [ ] Check `.firebender/rules/*.mdc` files for file-specific guidelines matching your changes
- [ ] Check `openspec/specs/` for existing specifications related to the feature
- [ ] Check `openspec/changes/` for active changes and proposals
- [ ] Review relevant ADRs in `docs/adr/` for architectural constraints
- [ ] Ensure changes follow the MVI pattern (unidirectional data flow)
- [ ] Run Spotless formatting after changes: `./gradlew spotlessApply`

## Architecture Layers (Quick Reference)

### 1. Presentation Layer (`ui/` package)

**Responsibilities:** Display UI, handle user interactions, observe ViewModel state

**Key Screens:**
- **InboxScreen**: List of captured notifications, filtering by app
- **RuleEditorScreen**: Visual rule creation from notification samples
- **DataViewerScreen**: View, filter, and export extracted events

**Key Rules:**
- Use Jetpack Compose with Material 3 components
- Use `collectAsStateWithLifecycle()` to observe ViewModel state
- **NEVER** access Repository directly - only through ViewModel
- Accept `modifier: Modifier = Modifier` as first optional parameter in Composables
- Use `@Preview` for both Light and Dark modes
- Implement accessibility (content descriptions, semantic properties)
- Use theme colors, typography, and spacing

**Reference:** `docs/ARCHITECTURE.md` - "Presentation Layer" section

### 2. Domain Layer (`domain/` package)

**Responsibilities:** Business logic, domain models, pure Kotlin

**Key Domain Models:**
- `Notification` - Raw notification from system
- `ExtractionRule` - User-defined rule for extracting fields
- `ExtractedEvent` - Structured output from rule matching

**Key Rules:**
- Platform-independent (no Android framework dependencies)
- Use data classes for models
- Use sealed classes for state/events/effects
- Keep business logic in ViewModels or use case classes

**Reference:** `docs/ARCHITECTURE.md` - "Domain Layer" section

### 3. Data Layer (`data/` package)

**Responsibilities:** Data persistence, repository implementations

**Key Components:**
- `NotificationRepository` - Store raw notifications
- `RuleRepository` - Store user-defined extraction rules
- `EventRepository` - Store extracted structured events
- Room database with DAOs
- DataStore for preferences (selected apps, settings)

**Key Rules:**
- Repository interface + implementation separation (per ADR 005)
- Return `Result<T>` or sealed `DataResult` for operations (per ADR 006)
- Use Room for local database
- Map database entities to domain models
- Provide reactive streams (Flow)
- Handle errors gracefully (no exceptions thrown to ViewModels)

**Reference:** `docs/ARCHITECTURE.md` - "Data Layer" section

### 4. Extraction Layer (`extraction/` package)

**Responsibilities:** Transform raw notifications to structured events

**Key Components:**
- `Normalizer` - Converts Android notifications to domain models
- `RuleMatcher` - Checks if notification matches a rule
- `FieldExtractor` - Extracts specific fields using patterns
- `RuleEngine` - Orchestrates matching and extraction

**Key Rules:**
- **No Android dependencies** (pure Kotlin) - this is critical for testability
- Fully unit testable without emulator
- Extensible design for new extraction methods
- Pattern-based extraction (regex, templates)

**Reference:** `docs/ARCHITECTURE.md` - "Extraction Layer" section

## MVI Pattern Implementation

### State Management
- Use `StateFlow<UiState>` in ViewModels
- Keep state immutable (use `copy()` for updates)
- Single source of truth per screen
- Use sealed classes for different states (Loading, Success, Error)

### Event/Intent Handling
- Define events as sealed interface/class (e.g., `InboxEvent`, `RuleEditorEvent`)
- Handle all events in ViewModel's `onEvent()` method
- Use `MviViewModel` base class

### Effects (One-time Events)
- Use `Channel` or `SharedFlow` for navigation, toasts, dialogs
- Send effects via `sendEffect()` in ViewModel

### Example Structure
```kotlin
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository
) : MviViewModel<InboxUiState, InboxEvent, InboxEffect>(InboxUiState()) {
    
    override fun onEvent(event: InboxEvent) {
        when (event) {
            is InboxEvent.LoadNotifications -> loadNotifications()
            is InboxEvent.OnNotificationClick -> handleNotificationClick(event.id)
            is InboxEvent.CreateRuleFromNotification -> createRule(event.notificationId)
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
- Generates `proposal.md` (what & why)
- Generates `design.md` (how)
- Generates `tasks.md` (implementation steps)
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
- Implements each pending task from `tasks.md`
- Marks tasks complete automatically
- Continues until all tasks are done or blocked

**Key patterns:**
- Follow MVI pattern (ADR 001, 002)
- Implement ViewModel extending `MviViewModel`
- Add KDoc referencing spec file

### Phase 4: Validation & Archive
```
/validate-spec openspec/specs/[area]/00N-feature-name.md
/opsx-archive [change-name]
```
- Verify all scenarios have tests and implementation
- Archive merges delta specs to main specs
- Change moved to `openspec/changes/archive/`

**Reference:** `docs/SDD-METHODOLOGY.md` - Complete OpenSpec workflow guide

## Dependency Injection Guidelines

- Use Hilt annotations: `@HiltViewModel`, `@Module`, `@InstallIn`, `@Provides`, `@Binds`
- Organize modules by feature or layer (e.g., `NotificationModule`, `DataModule`)
- Use appropriate scoping: `@Singleton`, `@ViewModelScoped`, `@ActivityScoped`
- **Always** use `@Dispatcher` qualifier for coroutine dispatchers (per ADR 008)
- Inject dispatchers for testability, never use `Dispatchers.IO`/`Main` directly

**Key Injection Points:**
- `NotificationListenerService` - System service binding
- `RuleEngine` - Extraction logic
- Repositories - Data access
- ViewModels - UI state management

**Reference:** ADR 004, ADR 008 in `docs/adr/`

## Testing Standards

### Unit Tests
- **Framework**: JUnit 5 with Kotest assertions
- **Mocking**: MockK with `MockKExtension`
- **Flow Testing**: Turbine for StateFlow/SharedFlow testing
- **Pattern**: Given-When-Then (arrange-act-assert)
- **Coroutine Testing**: Use `runTest` with injected dispatchers

### Test Structure
```kotlin
@ExtendWith(MockKExtension::class)
class RuleEngineTest {
    @MockK private lateinit var ruleRepository: RuleRepository
    private lateinit var ruleEngine: RuleEngine

    @BeforeEach
    fun setup() {
        ruleEngine = RuleEngine(ruleRepository)
    }

    @Test
    fun `extract fields from ICA receipt notification`() = runTest {
        // Given: a notification and matching rule
        val notification = createTestNotification(
            title = "ICA Kvantum",
            content = "Totalt: 153,50 kr\\nKort **** 9241"
        )
        val rule = createTestRule(
            pattern = "Totalt: (?<amount>[\\d,.]+) (?<currency>\\w+)",
            extractors = listOf(
                FieldExtractor("amount", "amount"),
                FieldExtractor("currency", "currency")
            )
        )
        
        // When: processing the notification
        val result = ruleEngine.extract(notification, rule)
        
        // Then: fields are extracted correctly
        result shouldBe Success(
            ExtractedEvent(
                extractedFields = mapOf(
                    "amount" to "153.50",
                    "currency" to "kr"
                )
            )
        )
    }
}
```

### Test Coverage Requirements
- **Extraction Engine**: All rule matching scenarios, pattern types, edge cases
- **ViewModels**: All events handled, all state transitions tested
- **Repositories**: Success and error cases, mapping logic
- **Normalization**: Various notification formats from different apps

**Reference:** `docs/ARCHITECTURE.md` - "Testing" section

## Build Configuration

### Build Variants
| Variant | Purpose | Usage |
|---------|---------|-------|
| `debug` | Development with debugging | Daily development |
| `release` | Production release | Play Store / Distribution |

### Essential Gradle Commands

```bash
# Code formatting (REQUIRED before commits)
./gradlew spotlessApply

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

### Manual Verification
Always run before submitting PRs:
```bash
./gradlew spotlessApply
./gradlew test
```

## Error Handling

- Use sealed class hierarchies for failures (per ADR 006)
- Repository methods return `Result<T>` or custom `DataResult<T>`
- Never throw exceptions from repositories to ViewModels
- Show user-friendly error messages (use `UiText` for string resources)
- Log errors using Timber for debugging
- Handle edge cases gracefully (malformed notifications, invalid rules)

## Performance Guidelines

- Use `derivedStateOf` for computed state in Compose
- Avoid unnecessary recompositions
- Use Room for efficient database queries with proper indexing
- Use Flow for reactive updates without polling
- Paginate large lists (inbox, extracted events)
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

1. Create package in `ui/[screenname]/`
2. Create ViewModel extending `MviViewModel<UiState, UiEvent, UiEffect>`
3. Create Contract object with UiState, UiEvent, UiEffect sealed classes
4. Create screen Composable
5. Add to navigation graph
6. Write unit tests for ViewModel

### Adding a New Repository

1. Define interface in `domain/repository/`
2. Create implementation in `data/repository/`
3. Create Room entity in `data/local/entity/` (if needed)
4. Create DAO in `data/local/dao/`
5. Bind in Hilt module using `@Binds`
6. Return `Result<T>` or sealed class
7. Add unit tests

### Adding a New Extraction Pattern Type

1. Define interface in `extraction/`
2. Implement in `extraction/parser/`
3. Add tests in `test/extraction/` (pure JVM, no Android)
4. Integrate into `RuleEngine`

### Adding a New Screen (Complete OpenSpec + PR Workflow)

1. Create change with `/opsx-propose "[change-name]"` (creates proposal, design, tasks)
2. Review and edit generated artifacts in `openspec/changes/[name]/`
3. Implement with `/opsx-apply [change-name]` (skill implements all tasks)
4. Validate with `/validate-spec openspec/specs/[area]/00N-name.md`
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
| Feature Specs | `openspec/specs/[area]/00N-name.md` |
| Change Proposals | `openspec/changes/[name]/proposal.md` |
| Change Designs | `openspec/changes/[name]/design.md` |
| Change Tasks | `openspec/changes/[name]/tasks.md` |
| Archived Changes | `openspec/changes/archive/YYYY-MM-DD-name/` |
| Architecture Decisions | `docs/adr/00N-title.md` |
| Domain Models | `app/src/main/kotlin/.../domain/model/` |
| Repository Interfaces | `app/src/main/kotlin/.../domain/repository/` |
| Repository Implementations | `app/src/main/kotlin/.../data/repository/` |
| Extraction Engine | `app/src/main/kotlin/.../extraction/` |
| Notification Service | `app/src/main/kotlin/.../notification/` |
| MVI Base Classes | `app/src/main/kotlin/.../ui/base/` |
| Screen Composables | `app/src/main/kotlin/.../ui/[screen]/` |
| ViewModels | `app/src/main/kotlin/.../ui/[screen]/` |
| Feature Strings | `app/src/main/res/values/strings.xml` |
| Theme | `app/src/main/kotlin/.../ui/theme/` |

