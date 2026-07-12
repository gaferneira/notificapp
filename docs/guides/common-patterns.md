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

## Adding a New Screen (Complete OpenSpec + PR Workflow)

1. Create change: `openspec/changes/[change-name]/` with `proposal.md`, `design.md`, `tasks.md`
2. Review and edit generated artifacts in `openspec/changes/[name]/`
3. Implement each task in `tasks.md`, marking it complete as you go
4. Validate: confirm all scenarios in `openspec/specs/[area]/spec.md` have tests and implementation
5. Archive: merge delta specs into main specs, move the change to `openspec/changes/archive/`
6. Create PR referencing the change and spec
