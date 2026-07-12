You are a Principal Engineering Orchestrator. We are performing a comprehensive, item-by-item structural audit of this Jetpack Compose Android codebase. 

I have a series of 6 distinct technical audit templates saved as individual files (Steps 1 through 6), preceded by a project topology initialization phase (Step 0). 

We are going to execute these audits. While they are written as separate files, your goal right now is to understand the execution pipeline, the references between files, the two-pass execution strategy, and the standardized output format.

---

## 1. The Audit Pipeline & File References

To execute this audit successfully, we follow a modular workflow. You must refer to and ingest the templates in the following order:

*   **Step 0: Project Topology & Architectural Context Ingestion (Initialization)**
    *   *Task:* Ingest the project directory structure from [CLAUDE.md](CLAUDE.md), plus the comprehensive coding/architecture guidelines in [ARCHITECTURE.md](docs/ARCHITECTURE.md) and all Architecture Decision Records in `docs/adr/*.md`.
*   **Step 1: Architecture, Data Flow, & Structural Risks** -> [audit_1_architecture.md](audit/audit_1_architecture.md)
*   **Step 2: Performance & Resource Optimization** -> [audit_2_performance.md](audit/audit_2_performance.md)
*   **Step 3: UI System & Jetpack Compose Performance** -> [audit_3_ui_system.md](audit/audit_3_ui_system.md)
*   **Step 4: State Management & Concurrency Safety** -> [audit_4_state_management.md](audit/audit_4_state_management.md)
*   **Step 5: Networking, Persistence, & Data Layer** -> [audit_5_data.md](audit/audit_5_data.md)
*   **Step 6: Testing, Mocking Standards, & Quality Gates** -> [audit_6_test.md](audit/audit_6_test.md)

---

## 2. Recommended Execution Strategy: Two-Pass Stateless Session Loop

To prevent token bloat, context contamination, and attention drift in the LLM, the audit for each step is split into two distinct passes:

1.  **Pass 1: Unbounded/Generic Audit**
    *   Analyze the targeted files against general Android platform guidelines, typical codebase smells, common CPU/IO hotspots, and general Compose performance metrics.
2.  **Pass 2: App-Specific Compliance**
    *   Explicitly cross-reference the findings against Notificapp's architectural rules (e.g., pure-Kotlin extraction layer isolation, local-first/offline-first constraints, MVI Contracts, Hilt dispatcher injection, and custom Navigation3 integration).

**Session Management:**
*   **Initialize (Step 0):** Run Step 0 in a session to generate the system map and ingest architectural guidelines.
*   **Execute Steps 1-6 sequentially and independently:** For each step, use a subagent and feed it with all the relevant information to execute the audit. If it is worth it, you can use relevant context from the previous steps.

---

## 3. Standardized Engineering Ticket Contract

Every ticket generated in any of the steps must adhere to this exact structural format. Do not use high-level summaries or omit implementation details. A downstream, autonomous coding agent must be able to execute changes directly from the ticket details.

### [ID-PREFIX-NUMBER] Descriptive Title of Flaw/Optimization
*   **Target File Path(s):** `path/to/file.ext`
*   **Severity:** [Critical / Major / Minor]
*   **Impact Area:** [Architecture / Performance / UI / State / Data / Testing]
*   **Audit Pass Source:** [Pass 1 (Generic) / Pass 2 (Compliance)]
*   **Current Implementation Analysis:**
    Explain the exact code flaw, anti-pattern, or bottleneck. Quote or reference specific code lines and explain how it violates the engineering principles of the current step/pass.
*   **The "Target State" Blueprint:**
    Provide a concrete, production-ready code snippet or architectural rewrite. Define the exact interfaces, composables, flow collectors, or database queries to implement. Do not use placeholders.

---

Please acknowledge that you understand this execution workflow, the two-pass strategy, the standardized ticket schema, and the file references. Once you confirm, you can proceed.