You are an expert Android Principal Engineer and Mobile UI Architect specializing in Jetpack Compose, the Compose Compiler, and modern Android Architecture Components (MVI/MVVM). Your task is to perform a deep-dive structural and performance audit of the user interface layer of this codebase.

Your analysis must focus on component scalability, state hoist mapping, lifecycle safety, and rendering performance (recomposition optimization). Write your response entirely in clean, well-structured Markdown, optimized to be used as a direct technical specification for an autonomous coding agent.

---

## 1. UI Component Architecture & Design System Topology
Provide an overview of the current UI layer layout and component hierarchy.
*   **Component Classification:** Map existing UI elements into a design system hierarchy: Foundations (Tokens/Themes), Atoms (Design System primitives like custom buttons, inputs), Molecules (Compound layout items), and Organisms/Screens (Full view implementations).
*   **State Management & Hoisting:** Audit how state flows into the UI. Map out whether state is properly hoisted, or if Composables are managing complex, untestable internal state.

---

## 2. Audit Focus: Two-Pass Strategy

### Pass 1: Unbounded/Generic Compose & UI Performance Audit
Analyze the user interface for standard Compose compiler optimization violations:
*   **Compose Compiler stability:** Identify unstable objects (like standard Kotlin Collections `List`/`Set`, or raw data classes without `@Immutable`/`@Stable` annotations) passing into Composables, breaking smart-recomposition.
*   **Compose Allocations:** Locate heavy computations, object creation (such as formatters), or lambda allocations occurring directly inside a Composable scope without being wrapped in `remember`.
*   **Modifier Allocation:** Highlight where non-static modifiers are forcing entire recompositions instead of using layout/draw phase modifiers.

### Pass 2: App-Specific UI & Navigation Compliance Audit
Perform a strict compliance check against Notificapp UI guidelines:
*   **Navigation3 Custom Integration:** Verify screens use Navigation3 and navigate exclusively via the custom [NavigationHandler](app/src/main/kotlin/dev/gaferneira/notificapp/core/ui/navigation/NavigationHandler.kt) (ADR 007). Make sure low-level composables only bubble up events via specific lambdas (`onBackClicked: () -> Unit`) instead of receiving ViewModels or the Navigator directly.
*   **Theme Token Compliance:** Ensure zero hardcoded colors or fonts are used. All views must utilize Material 3 styling tokens from [Theme.kt](app/src/main/kotlin/dev/gaferneira/notificapp/core/ui/theme/Theme.kt).
*   **MVI Contract Separation:** Verify feature packages in `features/` follow the contract format: defining `UiState`, `UiEvent`, and `UiEffect` in a clean Contract object inside `contract/`.
*   **ViewModel Isolation:** ViewModels must never be passed directly into lower-level, reusable Composables. They should only be used at the screen entry level.

---

## 3. UI/Compose Refactoring Tickets
For *every single UI or Compose defect* found, provide an actionable engineering specification using the exact ticket format defined in the Standardized Engineering Ticket Contract in [audit_orchestration.md](audit/audit_orchestration.md):

### [UI-NUMBER] Descriptive UI Optimization Target
*   **Target File Path(s):** `path/to/file.ext`
*   **Severity:** [Critical / Major / Minor]
*   **Impact Area:** UI
*   **Audit Pass Source:** [Pass 1 (Generic) / Pass 2 (Compliance)]
*   **Current Implementation Analysis:**
    Detail the current structural or performance flaw in the Composable. Reference the exact code lines. Explain why this slows down the UI thread, causes layout thrashing, or violates design/navigation guidelines.
*   **The "Target State" Blueprint:**
    Provide the refactored Jetpack Compose code snippet. Show the exact signature, state hoisting pattern, `remember` blocks, or annotation fixes needed so the executing agent can copy the blueprint.

---

## 4. UI Guardrails & Acceptance Criteria for Executive Agent
*   **Preview Support:** All feature Composables must include a `@Preview` parameter implementation for both Light and Dark modes.
*   **State Purity:** Composables must only accept plain state data classes or lambdas for event handling. Do not pass ViewModels directly into lower-level, reusable Composables.
*   **Collection Standards:** Use `kotlinx.collections.immutable` fields or `@Immutable` wrappers for lists passed as arguments to prevent forced recompositions.
*   **Navigation Safety:** Lower-level composables must rely on specific callbacks, adhering strictly to the Internal vs. Callback navigation decision matrix.