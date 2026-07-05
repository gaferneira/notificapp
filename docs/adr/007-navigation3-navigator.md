# ADR 007 – Navigation3 with Custom Navigator

## Status

Accepted

## Context

The project requires type-safe navigation with programmatic control over the back stack for complex flows (e.g., authentication, deep linking, modal presentations). Traditional navigation approaches lacked flexibility for dynamic back stack manipulation.

## Decision

Adopt Navigation3 (AndroidX Navigation 3.x) with a custom `Navigator` wrapper class:

1. **Navigation3 Runtime**: Uses `NavKey` interfaces for type-safe routes with back stack as `MutableList<NavKey>`
2. **Custom Navigator**: Wraps `NavigationState` to provide:
   - `navigate(route, navOptions)` – with `popUpTo`, `launchSingleTop`, and `clearStack` support
   - `goBack()` – controlled back navigation
   - `popBackStack(target, inclusive)` – selective back stack clearing
   - `popToRoot()` – clear to start destination
   - `clearAndNavigate(route)` – reset for flows like logout

3. **NavigationState**: Observable back stack that survives configuration changes
4. **Screen Types**: Routes defined as serializable sealed classes implementing `NavKey`

5. **NavigationHandler**: Singleton for ViewModel-driven navigation via SharedFlow

6. **Effect Handling**: Screens handle effects internally (bottom sheets) rather than exposing generic callbacks

Navigation is centralized in `core/ui/navigation` package, ensuring consistent behavior across features.

## Navigation Architecture

### Core Components

| Component | Purpose | Location |
|-----------|---------|----------|
| `Screen` | Sealed class with serializable routes | `core/ui/navigation/Screen.kt` |
| `Routes` | Factory object for route creation | `core/ui/navigation/Routes.kt` |
| `Navigator` | Back stack manipulation wrapper | `core/ui/navigation/Navigator.kt` |
| `NavigationState` | Observable back stack | `core/ui/navigation/NavigationState.kt` |
| `NavOptions` | Navigation options builder | `core/ui/navigation/NavOptions.kt` |
| `NavigationHandler` | ViewModel-driven navigation | `core/ui/navigation/NavigationHandler.kt` |
| `MainBottomNav` | Bottom navigation component | `core/ui/navigation/MainBottomNav.kt` |

### Route Definition

Routes are defined as a sealed class with `@Serializable` annotation:

```kotlin
@Serializable
sealed class Screen : NavKey {
    @Serializable
    data object Inbox : Screen()
    
    @Serializable
    data class NotificationDetails(val notificationId: String) : Screen()
    
    @Serializable
    data class RuleEditor(
        val ruleId: String? = null,
        val notificationId: String? = null,
    ) : Screen()
}
```

### Navigation Setup in MainActivity

```kotlin
NavDisplay(
    backStack = navigator.state.backStack,
    onBack = { navigator.goBack() },
    entryProvider = entryProvider {
        entry<Screen.Inbox> {
            InboxScreen(navigateTo = navigator::navigate)
        }
        entry<Screen.NotificationDetails> { screen ->
            NotificationDetailScreen(
                notificationId = screen.notificationId,
                onBackClicked = { navigator.goBack() }
            )
        }
    }
)
```

## Effect Handling Patterns

### Pattern 1: Internal Effect Handling (Bottom Sheets)

Screens should handle internal effects like bottom sheets internally rather than exposing them as callbacks:

```kotlin
@Composable
fun RuleEditorScreen(
    onBackClicked: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val addFieldSheetState = rememberAddFieldSheetState()
    
    // Collect effects from ViewModel
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.NavigateBack -> onBackClicked()
            is UiEffect.ShowAddFieldSheet -> addFieldSheetState.show(effect.sampleText)
            is UiEffect.ShowSuccess -> { /* Show snackbar */ }
        }
    }
    
    // Screen content...
    
    // Bottom sheet handled internally
    AddFieldBottomSheet(
        sampleText = addFieldSheetState.sampleText,
        isVisible = addFieldSheetState.isVisible,
        onDismiss = { addFieldSheetState.hide() },
        onFieldAdded = { field ->
            viewModel.onEvent(UiEvent.OnFieldAdded(field))
            addFieldSheetState.hide()
        }
    )
}
```

**Why this pattern:**
- Keeps parent (MainActivity) clean from sub-flow details
- Screen is self-contained with its sub-flows
- Simpler data flow - no shared state or result passing through navigation
- Better UX with contextual bottom sheets vs full screen navigation

### Pattern 2: Specific Callbacks for Parent Coordination

Only expose specific named callbacks when the parent needs to coordinate:

```kotlin
// Good - specific callbacks for actions needing parent coordination
@Composable
fun RuleEditorScreen(
    onBackClicked: () -> Unit,
    onShowSuccess: (message: String) -> Unit,
)

// Good - specific callback with typed result
@Composable
fun AppSelectionScreen(
    onNavigateToMainApp: () -> Unit,
    onNavigateBack: () -> Unit,
    onShowError: (message: String) -> Unit,
)
```

**Avoid generic navigation callbacks:**

```kotlin
// Avoid - generic callback requires parent to handle all effects
@Composable
fun RuleEditorScreen(
    onNavigate: (UiEffect) -> Unit,  // Don't do this
)
```

**Guidelines for when to expose callbacks:**

| Scenario | Pattern | Example |
|----------|---------|---------|
| Simple back navigation | Specific callback | `onBackClicked: () -> Unit` |
| Success/error messages | Specific callbacks | `onShowSuccess: (String) -> Unit` |
| Results needing parent action | Specific typed callback | `onFieldAdded: (ExtractionField) -> Unit` |
| Sub-screens within same flow | Internal handling | `AddFieldBottomSheet` inside `RuleEditorScreen` |
| Full screen transitions | NavigationHandler | ViewModel emits `ShowSuccess` effect |

## ViewModel-Driven Navigation

Use `NavigationHandler` for navigation from ViewModels:

```kotlin
@HiltViewModel
class NotificationDetailViewModel @Inject constructor(
    private val navigationHandler: NavigationHandler,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {
    
    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnCreateRuleClicked -> {
                viewModelScope.launch {
                    navigationHandler.navigate(
                        Routes.ruleEditor(notificationId = event.notificationId)
                    )
                }
            }
        }
    }
}
```

MainActivity collects navigation commands:

```kotlin
LaunchedEffect(Unit) {
    navigationHandler.navigationFlow.collect { command ->
        when (command) {
            is NavigationCommand.Navigate -> navigator.navigate(command.screen)
            is NavigationCommand.GoBack -> navigator.goBack()
            is NavigationCommand.ClearAndNavigate -> {
                navigator.clearAndNavigate(command.screen)
            }
        }
    }
}
```

## NavOptions

Navigation options control navigation behavior:

```kotlin
// Clear stack before navigating (for bottom nav tabs)
navigator.navigate(Routes.inbox(), navOptions { clearStack() })

// Pop up to specific screen
navigator.navigate(
    Routes.inbox(),
    navOptions { 
        popUpTo(Screen.Inbox::class)
        launchSingleTop = true
    }
)
```

| Option | Purpose |
|--------|---------|
| `clearStack()` | Clear entire back stack before navigating (tab switching) |
| `popUpTo(screenClass)` | Pop back stack up to specific screen |
| `popUpToInclusive` | Also remove the destination screen when popping |
| `launchSingleTop` | Don't add if same destination is already on top |

## Bottom Navigation

Each main screen owns its Scaffold with bottom navigation:

```kotlin
@Composable
fun InboxScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
) {
    Scaffold(
        bottomBar = {
            MainBottomNav(
                selectedDestination = AppDestinations.INBOX,
                navigateTo = navigateTo,
            )
        }
    ) { padding ->
        // Screen content
    }
}
```

Tab switching uses `clearStack` to avoid building up back stack:

```kotlin
// In MainBottomNav
NavigationBarItem(
    selected = selectedDestination == destination,
    onClick = {
        if (selectedDestination != destination) {
            navigateTo(destination.screen, navOptions { clearStack() })
        }
    }
)
```

## Consequences

**Positive:**

- Type-safe navigation prevents runtime crashes from invalid routes
- Full back stack visibility enables complex navigation scenarios
- Back stack manipulation is straightforward (pop to specific screens, clear all)
- Navigation logic is testable outside of Composables
- Deep link handling integrates cleanly with the route system
- Screens are self-contained with their sub-flows (bottom sheets)
- Cleaner parent (MainActivity) with specific callbacks only when needed

**Negative:**

- Navigation3 is newer API with less community documentation than Navigation2
- Custom Navigator adds maintenance burden as Navigation3 evolves
- Back stack state must be managed carefully to avoid memory leaks with large stacks
- Some edge cases (conditional navigation) require careful handling
- Developers must understand when to use internal effects vs callbacks

## References

- [Navigation3 Documentation](https://developer.android.com/reference/kotlin/androidx/navigation3/runtime/package-summary)
- [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
- `core/ui/navigation/` - Implementation package
