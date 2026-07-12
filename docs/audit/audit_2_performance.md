You are an expert Principal Performance Engineer and Systems Architect. Your task is to perform a deep-dive performance and resource-utilization audit of the provided codebase. 

Your goal is to identify concrete optimization targets to achieve maximum execution speed, minimal memory footprints, efficient resource utilization, and lightning-fast rendering/execution. Write your response entirely in clean, well-structured Markdown, optimized to be used as a direct technical specification for an autonomous coding agent.

---

## 1. Hot Paths & Execution Bottlenecks
Analyze the critical execution paths of the application (e.g., background notification reception, rule matching, and main user list loops).
*   **CPU & I/O Hotspots:** Identify heavy operations occurring on a UI/Main thread that should be offloaded.
*   **Memory Footprint & Allocation churn:** Pinpoint code segments causing high object churn, unnecessary garbage collection (GC) pressure, or redundant collection copies.

---

## 2. Audit Focus: Two-Pass Strategy

### Pass 1: Unbounded/Generic Performance Audit
Analyze the codebase against standard mobile performance pitfalls:
*   **Main-Thread Blockers:** Heavy computations (like parsing, string mapping, regex processing) or database/preferences writes executed synchronously on the Main thread.
*   **Garbage Collection Pressure:** Loop allocations, boxed primitives, and heavy collection copies inside critical paths.
*   **Asynchronous Overhead:** Improper dispatching, excessive thread switches, or un-throttled coroutines.

### Pass 2: App-Specific Performance & Background Compliance Audit
Perform a strict check against background execution and offline constraints:
*   **Background Listener Footprint:** `NotificappListenerService` must remain lightweight. Confirm that notification processing and extraction are offloaded to background dispatchers immediately and do not block the system's binder threads.
*   **Wake Lock Safety:** Verify that background execution does not hold persistent wake locks or cause excessive CPU wakes.
*   **Rule Engine Allocation Churn:** Since the pure-Kotlin `RuleEngine` processes all incoming notifications, review `RuleEngine.evaluate` and `FieldExtractor` to verify they do not instantiate heavy temporary objects (like compiling Regex patterns repeatedly or recreating formatters) inside the matcher/extractor loop. Compile Regexes statically or cache them.
*   **Paginated Query Performance:** Ensure database queries for large notification lists or rule executions use Paging3 instead of loading full collections into memory.

---

## 3. Performance Optimization Tickets
For *every single performance flaw or bottleneck* identified, provide an actionable engineering ticket using the exact ticket format defined in the Standardized Engineering Ticket Contract in [audit_orchestration.md](audit/audit_orchestration.md):

### [PERF-NUMBER] Descriptive Optimization Title
*   **Target File Path(s):** `path/to/file.ext`
*   **Severity:** [Critical / Major / Minor]
*   **Impact Area:** Performance
*   **Audit Pass Source:** [Pass 1 (Generic) / Pass 2 (Compliance)]
*   **Current Implementation Analysis:**
    Explain precisely what the code is doing incorrectly. Describe the impact on hardware resources (CPU, RAM, Main Thread blocking) and reference the specific lines or structures responsible.
*   **The "Target State" Blueprint:**
    Provide the highly optimized code rewrite or architectural replacement. Implement concrete performance patterns (e.g., data structure optimization, lazy initialization, statically compiled regex patterns, object pooling, or non-blocking async primitives).

---

## 4. Performance Guardrails & Acceptance Criteria
*   **Main-Thread Prohibitions:** All DB queries and rule evaluations must execute on injected background dispatchers.
*   **Regex / Formatting Caching:** Any Regex patterns or date/number formatters used during field extraction must be compiled statically in a `companion object` or provider, never inside the extraction loop.
*   **Pagination Rule:** Large collections (like notification lists) must be loaded reactively via Paging3 or small flows.
*   **Verification:** Measure execution time before and after refactoring using microbenchmarks or coroutine test timings.