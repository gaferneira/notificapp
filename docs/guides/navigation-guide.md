# Navigation Guidelines

Full walkthrough for wiring a screen into Navigation3. Referenced from `CLAUDE.md`'s Quick Reference table — read this file when actually adding navigation, not preemptively. Background/rationale: ADR 007.

## Navigation Architecture Overview

We use Navigation3 with the following pattern:
- **Routes**: Defined in `Screen` sealed class with `@Serializable`
- **Entry Provider**: Maps routes to composables in `MainActivity`
- **NavigationHandler**: Singleton for ViewModel-driven navigation
- **Effect Handling**: Internal for sub-flows, specific callbacks for parent coordination

## Adding a Screen to Navigation

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

## Effect Handling: Internal vs Callbacks

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

## NavOptions Usage

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

## ViewModel-Driven Navigation

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
