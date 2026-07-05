# OpenSpec Methodology

## Overview

OpenSpec is a specification-driven development workflow where **detailed specifications drive implementation, testing, and validation**. Specifications serve as the single source of truth that connects user requirements to code implementation.

This methodology uses skills that automate the OpenSpec workflow using the `openspec` CLI tool.

### Scope and Portability

Two things are deliberately kept separate:

- **The specs (`openspec/specs/`) are the canonical, tool-agnostic source of truth.** They are plain markdown with Gherkin scenarios — readable and maintainable by any human, AI tool, or future SDD standard without the OpenSpec tooling.
- **The workflow (proposals, `changes/` lifecycle, slash commands) is replaceable maintainer tooling.** It is how maintainers and AI-agent sessions work on this repo; it is *not* a requirement for external contributors (see `CONTRIBUTING.md`), and it can be swapped if a better SDD standard emerges without touching the specs' value.

### Branch Hygiene: `main` Holds Only Archived Changes

Active changes (`openspec/changes/[name]/`) live **exclusively on feature branches** — created there, worked there, and archived (`/opsx-archive`) *before* the branch merges. `main` only ever contains `openspec/changes/archive/`. If a change is abandoned, its branch dies with it and nothing lands on `main`. This keeps the repo clean (no half-finished proposals rotting on `main`) while preserving what tracking `changes/` buys: PR diffs that include proposal/design/tasks for reviewers, and agent sessions that can resume `tasks.md` from any machine.

### Core Principle

```
Proposal/Design (Why/How) → Spec (What it does) → Implementation Plan (Steps) → Verification (Proof) → Archive (History)
```

### OpenSpec Benefits

1. **Clear Requirements**: Gherkin-style specs eliminate ambiguity
2. **Traceable Changes**: Proposals and designs document decision rationale
3. **Test-Driven**: Every scenario has a corresponding test
4. **Living Documentation**: Specs evolve with code, archived changes preserve history
5. **Code Review Aid**: PR reviewers verify spec compliance

---

## Repository Structure

```
openspec/
├── config.yaml           # OpenSpec configuration
├── specs/               # Detailed Gherkin specifications
│   ├── action-execution/
│   │   └── spec.md
│   ├── snooze-scheduling/
│   │   └── spec.md
│   └── [feature-area]/
│       └── spec.md
└── changes/             # Active and archived changes
    ├── archive/         # Completed changes
    │   ├── YYYY-MM-DD-change-name/
    │   │   ├── proposal.md
    │   │   ├── design.md
    │   │   ├── tasks.md
    │   │   └── specs/   # Optional: new/modified specs
    │   └── ...
    └── [active-changes]/
        ├── proposal.md
        ├── design.md
        ├── tasks.md
        └── specs/

docs/
├── features/            # High-level feature descriptions
│   ├── chat.md
│   ├── auth.md
│   └── ...
├── adr/                # Architecture Decision Records
│   ├── 001-mvi-pattern.md
│   └── ...
└── ARCHITECTURE.md     # Detailed architecture guide

feature/                # Implementation modules
├── chat/src/.../
│   ├── ChatViewModel.kt       # Implementation
│   ├── ChatContract.kt        # State definition
│   ├── ChatScreen.kt          # UI
│   └── ChatNavigation.kt      # Navigation
└── chat/src/test/.../
    └── ChatViewModelTest.kt   # Tests
```

---

## Workflow with AI Agents

The OpenSpec workflow can be executed by multiple AI agents (Firebender, Antigravity, etc.) or manually by developers. While Firebender provides built-in slash commands for automation, Antigravity follows the same principles using `implementation_plan.md` and `task.md` to orchestrate the work.

### Phase 1: Proposal & Design

Use the skill to create a change with all planning artifacts:

```
/opsx-propose "[change-name]"
```

**What it does:**
- Creates `openspec/changes/[name]/` directory with `.openspec.yaml`
- Automatically generates `proposal.md` (what & why)
- Automatically generates `design.md` (how)
- Automatically generates `tasks.md` (implementation steps)

**Example:**
```
/opsx-propose "add-dark-mode"
```

**You'll get:**
```
openspec/changes/add-dark-mode/
├── .openspec.yaml      # Change configuration
├── proposal.md         # Intent, scope, approach
├── design.md           # Technical decisions, trade-offs
└── tasks.md            # Implementation checklist
```

**Then review and edit:**
- Edit `proposal.md` to refine the intent and scope.
- Edit `design.md` to add technical details.
- Edit `tasks.md` or create a more detailed `implementation_plan.md` (recommended for Antigravity) to bridge the gap between design and code.

### Phase 2: Specification

The `/opsx-propose` command may also create delta specs in `openspec/changes/[name]/specs/`.

**For new capabilities**, the skill creates:
```
openspec/changes/[name]/specs/
└── [capability]/
    └── spec.md         # Delta spec with ADDED/MODIFIED/REMOVED sections
```

**Delta spec format:**
```markdown
# Delta for [Capability]

## ADDED Requirements

### Requirement: [Name]
[Description]

#### Scenario: [Name]
- GIVEN [initial state]
- WHEN [action]
- THEN [expected outcome]

## MODIFIED Requirements
...

## REMOVED Requirements
...
```

### Phase 3: Tasks & Implementation (Automated)

Use the skill to implement tasks:

```
/opsx-apply [change-name]
```

**What it does:**
- Reads all context files (proposal, design, tasks, specs)
- Implements each pending task from `tasks.md`
- Automatically marks tasks complete (`- [ ]` → `- [x]`)
- Continues until all tasks are done or blocked

**Example:**
```
/opsx-apply add-dark-mode
```

**You'll see:**
```
## Implementing: add-dark-mode (schema: spec-driven)

Working on task 1/7: Create ThemeContext with light/dark state
[...implementation happening...]
✓ Task complete

Working on task 2/7: Add CSS custom properties for colors
[...implementation happening...]
✓ Task complete
```

**Key patterns to follow:**
- MVI architecture with `MviViewModel` base class
- Repository pattern with interface/implementation separation
- Sealed classes for state, events, and effects
- Dependency injection with Hilt
- Reactive streams with Flow/StateFlow

### Phase 4: Validation

Use Firebender commands to validate implementation against specs:

```
/validate-spec openspec/specs/chat/001-message-streaming.md
```

This verifies:
- All scenarios have corresponding tests
- Implementation matches spec requirements
- Coverage is complete

### Phase 5: Archive (Automated)

Use the skill to archive a completed change:

```
/opsx-archive [change-name]
```

**What it does:**
- Checks for incomplete artifacts and tasks (warns if any)
- Syncs delta specs to main `openspec/specs/` directory
  - ADDED requirements appended to main spec
  - MODIFIED requirements replace existing
  - REMOVED requirements deleted from main spec
- Moves change to `openspec/changes/archive/YYYY-MM-DD-[name]/`

**Example:**
```
/opsx-archive add-dark-mode
```

**Result:**
```
## Archive Complete

**Change:** add-dark-mode
**Schema:** spec-driven
**Archived to:** openspec/changes/archive/2025-01-24-add-dark-mode/
**Specs:** ✓ Synced to main specs
```

---

## Firebender Skill Commands Reference

| Command | Purpose | When to Use |
|---------|---------|-------------|
| `/opsx-propose "[name]"` | Create new change with all artifacts | Starting a new feature/modification |
| `/opsx-apply [name]` | Implement tasks from a change | Ready to write code |
| `/opsx-archive [name]` | Archive completed change | All tasks done |
| `/opsx-explore [topic]` | Explore mode for discovery | Thinking/research phase |
| `/validate-spec [path]` | Check spec implementation | After implementation |
| `/generate-test [path]` | Generate test skeleton | After spec is written |
| `/validate-all-specs` | Validate entire project | Before releases |

---

## Specification Format Reference

### Header Fields

| Field | Required | Description |
|-------|----------|-------------|
| **Related Feature** | Yes | Link to high-level feature doc in `docs/features/` |
| **ADR References** | Yes | Architecture decisions this spec follows |
| **Manager** | Yes | Domain class managing this feature (e.g., `MessageManager`) |
| **State Management** | Yes | State class name (e.g., `ChatMessageState`) |
| **UI Components** | Yes | Key Composables (e.g., `MessagesContainer, ChatScreen`) |
| **Test File** | Yes | Test class name (e.g., `MessageManagerTest`) |

### Scenario Structure

Use Gherkin Given-When-Then format:

```markdown
### Scenario N: [Descriptive name]
**Given** [initial context or state]  
**And** [additional context]  
**When** [user action or system event]  
**Then** [expected outcome]  
**And** [additional outcome]
```

### Required Sections

1. **Goal** - One paragraph summary
2. **Architecture Overview** - Component interaction diagram
3. **Concepts** - Domain concepts explanation
4. **Scenarios** - At least 3-5 scenarios (happy path, errors, edge cases)
5. **State Management** - State structure, events, effects
6. **Test Checklist** - Checkboxes for test coverage

---

## Modifying Existing Specs

When requirements change:

1. **Create a change proposal** to track the modification:
   ```
   /opsx-propose "update-guardrail-display"
   ```

2. **Edit the delta spec** in `openspec/changes/[name]/specs/[capability]/spec.md`
   - Use MODIFIED section for changes
   - Use ADDED section for new requirements
   - Use REMOVED section for deleted requirements

3. **Update tasks** in `tasks.md` to reflect implementation work

4. **Implement changes** with `/opsx-apply`

5. **Archive** with `/opsx-archive` to merge changes into main specs

---

## When to Create New Specs

Create a **new spec file** when:

1. **New feature area**: Adding completely new functionality (e.g., "Voice Chat")
2. **Unrelated flow**: Different user journey (e.g., "Signup" vs "Login")
3. **Large scope**: Current spec has 10+ scenarios (split for readability)

**Naming**: One `spec.md` per feature area (current convention). If an area grows past ~10 scenarios, split into numbered files: `001-feature.md`, `002-subfeature.md`
**Location**: `openspec/specs/[area]/spec.md`

---

## MVI Implementation (unchanged)

The MVI pattern implementation remains consistent regardless of workflow.

### ViewModel Structure

```kotlin
@HiltViewModel
class ExampleViewModel @Inject constructor(
    private val repository: ExampleRepository
) : MviViewModel<ExampleUiState, ExampleEvent, ExampleEffect>(ExampleUiState()) {
    
    override fun onEvent(event: ExampleEvent) {
        when (event) {
            is ExampleEvent.LoadData -> loadData()
            is ExampleEvent.OnItemClick -> handleItemClick(event.id)
        }
    }
}

object ExampleContract {
    data class UiState(
        val items: List<Item> = emptyList(),
        val isLoading: Boolean = false,
        val error: UiText? = null,
    )
    
    sealed class UiEvent {
        data object LoadData : UiEvent()
        data class OnItemClick(val id: String) : UiEvent()
    }
    
    sealed class UiEffect {
        data class NavigateToDetail(val id: String) : UiEffect()
    }
}
```

**Reference**: `docs/ARCHITECTURE.md` - "MVI Pattern Implementation" section

---

## Checklist for OpenSpec Compliance

Before marking a feature complete:

- [ ] Change proposal created with `/opsx-propose`
- [ ] Artifacts reviewed and edited (proposal.md, design.md, tasks.md)
- [ ] Delta spec created with ADDED/MODIFIED/REMOVED sections
- [ ] Spec includes: Feature link, ADR refs, Manager, State, UI Components, Test file
- [ ] Implementation complete with `/opsx-apply`
- [ ] ViewModel extends `MviViewModel` per ADR 001
- [ ] Contract object with UiState/UiEvent/UiEffect per ADR 002
- [ ] KDoc references spec file
- [ ] Tests generated and passing
- [ ] `/validate-spec` shows coverage
- [ ] Change archived with `/opsx-archive`
- [ ] Code formatted with Spotless

---

## Migration from Legacy SDD

### Old Structure (deprecated)
```
docs/specs/[area]/00X-feature.md  ← No longer used for new specs
```

### New Structure
```
openspec/specs/[area]/00X-feature.md  ← Main specs (updated via archive)
openspec/changes/[name]/  ← Active changes (use /opsx-propose)
openspec/changes/archive/YYYY-MM-DD-[name]/  ← History
```

### Existing Legacy Specs
Legacy specs in `docs/specs/` remain valid for reference but new specs should be created in `openspec/specs/` via the skill workflow. Consider migrating legacy specs to OpenSpec format when making significant modifications.

---

## Example PR Description

When submitting PRs, reference the spec and change:

```markdown
## Summary
Implements guardrail persistence per spec `openspec/specs/chat/002-guardrail-safety-system.md`

## Change
- Proposal: `openspec/changes/persist-guardrail-traces/proposal.md`
- Design: `openspec/changes/persist-guardrail-traces/design.md`

## Changes
- Added `SafetyViolationDto` and mapping logic
- Updated `Message` domain model
- Guardrail traces now persist in database
- All scenarios tested

## Validation
```
/validate-spec openspec/specs/chat/002-guardrail-safety-system.md
✅ 14/14 scenarios implemented
```

## Test Plan
- [x] Unit tests for guardrail mapping
- [x] Historical guardrail loading tested
- [x] Safety violation DTO parsing verified
```

---

## Summary

OpenSpec methodology with skills ensures:
1. **Automated proposal & design** - `/opsx-propose` creates all artifacts
2. **Clear requirements** - Delta specs with ADDED/MODIFIED/REMOVED
3. **Automated implementation** - `/opsx-apply` implements tasks
4. **Traceable history** - `/opsx-archive` preserves changes
5. **Validation tools** - `/validate-spec` enforces compliance

The workflow is: **Propose → [Review/Edit] → Apply → Validate → Archive**.

---

## Resources

- [OpenSpec Documentation](https://github.com/Fission-AI/OpenSpec)
- [Firebender OpenSpec Skills](.firebender/commands/)
- [Now in Android](https://github.com/android/nowinandroid) - Architecture inspiration
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [MVI Architecture](https://hannesdorfmann.com/android/model-view-intent/)
- [Gherkin Reference](https://cucumber.io/docs/gherkin/reference/)
