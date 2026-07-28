# Common Patterns

Step-by-step checklists for recurring implementation tasks. Referenced from `CLAUDE.md`'s Quick Reference table — read this file when actually doing one of these tasks, not preemptively.

## Adding a New Screen

1. Create package in `features/[screenname]/` with `contract/`, `ui/`, `viewmodel/` sub-packages
2. Create Contract object with UiState, UiEvent, UiEffect sealed classes in `contract/`
3. Create ViewModel extending `MviViewModel<UiState, UiEvent, UiEffect>` in `viewmodel/`
4. Create screen Composable in `ui/`
5. Add to navigation (see `docs/guides/navigation-guide.md`)
6. Write unit tests for the ViewModel

## Adding a New Repository

1. Define interface in `domain/repository/`
2. Create implementation in `core/data/repository/`
3. Create Room entity in `core/data/local/entity/` (if needed)
4. Create DAO in `core/data/local/dao/` and mapper in `core/data/local/mapper/`
5. Bind in `core/di/RepositoryModule` using `@Binds`
6. Return `Result<T>` or sealed class
7. Add unit tests

## Adding a New Extraction Method

1. Add the method to `RuleField.ExtractionMethod` (domain model)
2. Implement in `core/extraction/FieldExtractor` (pure Kotlin, no Android imports)
3. Add tests in `app/src/test` (pure JVM, no emulator)
4. Add a config composable in `features/ruleeditor/ui/fieldconfig/` and one `when` branch in `AddFieldBottomSheet.kt`'s `AddFieldBottomSheetContent` (per TD-13)

## Adding a New Action Type

1. Add the type to `ActionType` (domain model); keep config in `RuleAction.config` (`Map<String, String>`) with typed accessor methods. Only `SAVE_DATA` ("Extract data") uses `RuleAction.fields: List<RuleField>` — extraction fields are structured (a sealed `ExtractionMethod`) and don't belong in the string `config` map, so they're a first-class property instead; every other action type leaves `fields` empty.
2. Implement execution as an `ActionExecutor` (`domain/action/ActionExecutor.kt`) and register it in `core/di/ActionModule.kt` via `@Binds @IntoMap @ActionTypeKey(ActionType.X)` — the `ActionDispatcher` picks it up automatically; no service edits needed
3. Add a config composable in `features/ruleeditor/ui/actionconfig/` and one `when` branch in `ActionBottomSheet.kt`'s `ActionsContent` (per TD-13 — don't grow the sheet itself, it only dispatches)
4. Record execution outcome on the `RuleExecution`

## Adding a New core/ui Component

Shared, reusable visual primitives (cards, badges, pills, indicators — anything used by 2+ screens) belong in `core/ui/components/`, styled with the Compose Styles API (`docs/adr/`… see `OnboardingScreen.kt`'s `HighlightCard`/`PermissionToggleCard` for the reference example). Screen-specific one-offs stay inline in the screen file.

1. Add a `Style` default in `core/ui/theme/ComponentStyles.kt` (`NotificappStyles`), built from `NotificappTokens` (colors/spacing/shapes) — never a literal `Color(...).copy(alpha = ...)` or a raw `RoundedCornerShape(N.dp)`.
2. Create the composable in `core/ui/components/`, taking `style: Style = Style` and applying it via `Modifier.styleable(styleState, NotificappStyles.xStyle then style)` (or the component's built-in `style` param if one exists).
3. Only fall back to a plain `Modifier`/`Surface` color/shape param for actual Material components (`Button`, `Card`, `TextField`) — Material doesn't support `Style` yet, so leave those as-is.
4. `./gradlew architectureCheck` enforces this: a `.copy(alpha = ...)` inside `core/ui/components/*` fails the build (rule `design-system-styling`, `config/architecture/baseline.txt`).

## Adding/Translating a User-Facing String

Every user-facing string goes through `strings.xml`, never a literal in a Composable (ADR 014). `OnboardingScreen` and its supporting files (`FirstStepContent.kt`, `PermissionExplanationContent.kt`, `OnboardingHighlight.kt`) are the reference implementation — copy their pattern for other screens.

1. Add the key to `app/src/main/res/values/strings.xml`, feature-prefixed snake_case (e.g. `onboarding_get_started`, `settings_language_title`), grouped under that feature's `<!-- Comment -->` section.
2. Add the same key with a Spanish translation to `app/src/main/res/values-es/strings.xml`. Both files change in the same PR — never add a key to only one.
3. Use `stringResource(R.string.x)` in the Composable. Use positional format specifiers (`%1$s`, `%1$d`) instead of concatenation if the string needs interpolation — word order differs between English and Spanish. Use `<plurals>`/`quantityString` for anything plural-sensitive.
4. For a data class holding UI copy outside a Composable (e.g. `OnboardingHighlight`), store `@StringRes Int` fields, not `String`, and resolve them with `stringResource(...)` at the point they're rendered.
5. Exceptions that stay as Kotlin literals, not resources: brand wordmarks/logos, and values meant to look like literal identifiers (e.g. an illustrative package name like `com.bank.app`).

## Adding a New Screen (Complete OpenSpec + PR Workflow)

1. Create change: `openspec/changes/[change-name]/` with `proposal.md`, `design.md`, `tasks.md`
2. Review and edit generated artifacts in `openspec/changes/[name]/`
3. Implement each task in `tasks.md`, marking it complete as you go
4. Validate: confirm all scenarios in `openspec/specs/[area]/spec.md` have tests and implementation
5. Archive: merge delta specs into main specs, move the change to `openspec/changes/archive/`
6. Create PR referencing the change and spec
