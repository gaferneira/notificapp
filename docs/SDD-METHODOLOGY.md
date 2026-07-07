# SDD Methodology

## Overview

This project follows Spec-Driven Development (SDD): **detailed specifications drive implementation, testing, and validation**. Specifications serve as the single source of truth that connects user requirements to code implementation.

The folder structure and artifact names (`proposal.md`, `design.md`, `tasks.md`, delta specs) are borrowed from the [OpenSpec](https://github.com/Fission-AI/OpenSpec) naming convention, but this methodology defines a **folder structure and artifact format, not a dependency on that or any other specific tool**. Any developer, AI agent, or script can drive the workflow with its own commands/logic as long as it reads and writes the artifacts described below in the locations described below. Using the actual OpenSpec CLI is optional — some contributors may use it, others may not; either way the artifacts it reads and writes are the same ones this document specifies.

### Scope and Portability

Two things are deliberately kept separate:

- **The specs (`openspec/specs/`) are the canonical source of truth.** They are plain markdown with Gherkin scenarios — readable and maintainable by any human, AI tool, or future SDD standard.
- **The workflow (proposals, `changes/` lifecycle) is just a folder convention.** How a given session or tool produces/consumes `proposal.md`, `design.md`, `tasks.md` is up to that tool — this document does not prescribe commands.

### Branch Hygiene: `main` Holds Only Archived Changes

Active changes (`openspec/changes/[name]/`) live **exclusively on feature branches** — created there, worked there, and archived *before* the branch merges. `main` only ever contains `openspec/changes/archive/`. If a change is abandoned, its branch dies with it and nothing lands on `main`. This keeps the repo clean (no half-finished proposals rotting on `main`) while preserving what tracking `changes/` buys: PR diffs that include proposal/design/tasks for reviewers, and agent sessions that can resume `tasks.md` from any machine.

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
├── config.yaml           # Optional, gitignored — only used if a contributor runs the OpenSpec CLI
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

## Workflow Phases

The workflow below can be driven by any developer, AI agent, or automation — this document describes the artifacts each phase must produce, not the commands used to produce them.

### Phase 1: Proposal & Design

Create `openspec/changes/[name]/` with planning artifacts:

```
openspec/changes/add-dark-mode/
├── proposal.md         # Intent, scope, approach
├── design.md           # Technical decisions, trade-offs
└── tasks.md            # Implementation checklist
```

- `proposal.md` — what & why
- `design.md` — how, including technical trade-offs
- `tasks.md` — an implementation checklist, or a more detailed `implementation_plan.md` to bridge design and code

### Phase 2: Specification

New or modified capabilities get a delta spec in `openspec/changes/[name]/specs/`:

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

### Phase 3: Tasks & Implementation

Work through `tasks.md`, marking each task complete (`- [ ]` → `- [x]`) as it's finished. Follow the project's existing architectural patterns (see `docs/ARCHITECTURE.md`):

- MVI architecture with `MviViewModel` base class
- Repository pattern with interface/implementation separation
- Sealed classes for state, events, and effects
- Dependency injection with Hilt
- Reactive streams with Flow/StateFlow

### Phase 4: Validation

Verify before merging:
- All scenarios in the delta spec have corresponding tests
- Implementation matches spec requirements
- Coverage is complete

### Phase 5: Archive

Once implementation and validation are complete:
- Sync delta specs into the main `openspec/specs/` directory
  - ADDED requirements appended to main spec
  - MODIFIED requirements replace existing
  - REMOVED requirements deleted from main spec
- Move the change to `openspec/changes/archive/YYYY-MM-DD-[name]/`

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

1. **Create a change** to track the modification: `openspec/changes/[name]/`
2. **Edit the delta spec** in `openspec/changes/[name]/specs/[capability]/spec.md`
   - Use MODIFIED section for changes
   - Use ADDED section for new requirements
   - Use REMOVED section for deleted requirements
3. **Update tasks** in `tasks.md` to reflect implementation work
4. **Implement changes**
5. **Archive** the change to merge changes into main specs

---

## When to Create New Specs

Create a **new spec file** when:

1. **New feature area**: Adding completely new functionality (e.g., "Voice Chat")
2. **Unrelated flow**: Different user journey (e.g., "Signup" vs "Login")
3. **Large scope**: Current spec has 10+ scenarios (split for readability)

**Naming**: One `spec.md` per feature area (current convention). If an area grows past ~10 scenarios, split into numbered files: `001-feature.md`, `002-subfeature.md`
**Location**: `openspec/specs/[area]/spec.md`

---

## Checklist for OpenSpec Compliance

Before marking a feature complete:

- [ ] Change created with `proposal.md`, `design.md`, `tasks.md`
- [ ] Artifacts reviewed and edited
- [ ] Delta spec created with ADDED/MODIFIED/REMOVED sections
- [ ] Spec includes: Feature link, ADR refs, Manager, State, UI Components, Test file
- [ ] Implementation complete, tasks marked done
- [ ] ViewModel extends `MviViewModel` per ADR 001
- [ ] Contract object with UiState/UiEvent/UiEffect per ADR 002
- [ ] KDoc references spec file
- [ ] Tests generated and passing
- [ ] Spec coverage verified against implementation
- [ ] Change archived, delta specs merged into main specs
- [ ] Code formatted with Spotless

---

## Structure
```
openspec/specs/[area]/00X-feature.md  ← Main specs (updated via archive)
openspec/changes/[name]/  ← Active changes
openspec/changes/archive/YYYY-MM-DD-[name]/  ← History
```
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
14/14 scenarios implemented and covered by tests.

## Test Plan
- [x] Unit tests for guardrail mapping
- [x] Historical guardrail loading tested
- [x] Safety violation DTO parsing verified
```

---

## Summary

OpenSpec methodology ensures:
1. **Clear requirements** - Delta specs with ADDED/MODIFIED/REMOVED
2. **Traceable history** - Archived changes preserve proposal/design/tasks
3. **Validation** - Spec coverage checked before archiving

The workflow is: **Propose → [Review/Edit] → Specify → Implement → Validate → Archive**, driven by whatever tooling or agent logic the developer chooses.

---

## Resources

- [OpenSpec Documentation](https://github.com/Fission-AI/OpenSpec)
- [Gherkin Reference](https://cucumber.io/docs/gherkin/reference/)
