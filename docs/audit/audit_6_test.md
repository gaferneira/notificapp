You are an expert Principal Android Engineer specializing in Test-Driven Development (TDD), automated testing strategies, and CI/CD quality gates. Your task is to perform a deep-dive audit of the testing infrastructure, mock patterns, and code quality controls within this codebase.

Your goal is to identify untestable code paths, refactor brittle test suites, establish modern mocking/fake standards, and define automated linting rules to prevent future code regression. Write your response entirely in clean, well-structured Markdown, optimized to be used as a direct technical specification for an autonomous coding agent.

---

## 1. Testability & Dependency Decoupling
Analyze how easily components can be isolated and verified in automated tests.
*   **Tight Coupling Barriers:** Identify tightly coupled singletons, static global utilities, or direct Android framework dependencies inside business logic that prevent unit testing.
*   **Architecture Verification:** Evaluate whether your Use Cases, ViewModels, and Repositories expose clean, interface-driven or constructor-injected APIs.

---

## 2. Audit Focus: Two-Pass Strategy

### Pass 1: Unbounded/Generic Testing Audit
Analyze the test suites against general testing best practices:
*   **Dynamic Mocking Over-reliance:** Verify if tests rely heavily on dynamic mocks (`mockk` or `mock`) for simple data objects or repositories, resulting in fragile tests.
*   **Flaky Coroutine/Flow Assertions:** Look for race conditions in tests, improper use of `StandardTestDispatcher` vs `UnconfinedTestDispatcher`, and unhandled background threads in `runTest`.

### Pass 2: App-Specific Testing Compliance Audit
Perform a strict check against Notificapp's dedicated testing stack and standards:
*   **Testing Stack Compliance:** Verify that all unit tests are built using **JUnit 5**, **Kotest** assertions (e.g. `result shouldBe expected`), **MockK** for stubbing dependencies, and **Turbine** for flow collection verification. JUnit 4 or standard Kotlin assert/JUnit assert libraries are prohibited.
*   **Instant JVM Unit Tests:** Check that extraction engine tests ([core/extraction](app/src/main/kotlin/dev/gaferneira/notificapp/core/extraction/)) are **pure JVM tests** with zero Android dependencies, executing without requiring an Android emulator or Robolectric.
*   **MVI VM Coverage:** Ensure ViewModel unit tests verify all three facets of the contract:
    1. Correct initial state.
    2. Proper transition of `UiState` in response to each `UiEvent`.
    3. Proper emission of `UiEffect` in the effects channel (asserted using Turbine).
*   **Static Quality Gates:** Confirm Spotless formatting passes and Detekt rules are met. Ensure that if a changed file is listed in the detekt baseline (`config/detekt/baseline.xml`), the baseline entries are cleaned up and count is reduced (Boy-Scout policy).

---

## 3. Testing & Quality Engineering Tickets
For *every single testing gap or untestable component* identified, provide an actionable engineering ticket using the exact ticket format defined in the Standardized Engineering Ticket Contract in [audit_orchestration.md](audit/audit_orchestration.md):

### [TEST-NUMBER] Descriptive Testing/Quality Optimization Title
*   **Target File Path(s):** `path/to/component/` or `src/test/`
*   **Severity:** [Critical / Major / Minor]
*   **Impact Area:** Testing
*   **Audit Pass Source:** [Pass 1 (Generic) / Pass 2 (Compliance)]
*   **Current Implementation Analysis:**
    Explain precisely why the current implementation is untestable or why the existing tests are flaky/brittle. Identify the specific dependencies that cannot be cleanly swapped. Cite the exact files.
*   **The "Target State" Blueprint:**
    Provide the architectural refactor or the missing test implementation. Show the exact Kotlin test structure using JUnit 5, Kotest, and Turbine.

---

## 4. Quality Guardrails & Automated Static Analysis Rules
*   **Test Delivery Mandatory Rule:** Every new feature or architectural refactor must be accompanied by matching Unit Tests. Code changes without a corresponding test file modification must be rejected.
*   **Mocking Restrictions:** Do not use dynamic mocks for pure data models or local data layers; implement explicit, deterministic local Fakes instead.
*   **Detekt Boy-Scout Rule:** The detekt baseline count must only shrink or remain static for existing files; it must never expand. All new code must pass static analysis cleanly.