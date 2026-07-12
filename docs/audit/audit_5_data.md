You are an expert Principal Android Engineer specializing in mobile data systems, local persistence architectures, and network optimization. Your task is to perform a deep-dive audit of how data is fetched from remote APIs and cached locally on the device.

Your goal is to evaluate the data layer for network payload efficiency, secure local persistence, proper database query execution, and robust offline-first synchronization logic. Write your response entirely in clean, well-structured Markdown, optimized to be used as a direct technical specification for an autonomous coding agent.

---

## 1. Local Persistence & Cache Architecture
Analyze how data is stored locally on the device (e.g., Room DB, DataStore).
*   **Database Schema & Query Efficiency:** Audit local database structures. Identify sub-optimal indexing, missing foreign key constraints, or queries that run on the main thread.
*   **Encrypted Storage:** Ensure that sensitive user configurations, keys, or credentials are encrypted at rest using Android KeyStore-backed storage.

---

## 2. Audit Focus: Two-Pass Strategy

### Pass 1: Unbounded/Generic Data Layer Audit
Analyze the codebase against standard mobile persistence issues:
*   **Missing Database Optimization:** Redundant queries, lack of indices on foreign keys, and synchronous blocking calls to databases on the Main thread.
*   **Inefficient Data Serialization:** Using reflection-based JSON parsers (e.g., Gson) instead of lightweight compiler-generated solutions like Kotlinx Serialization.

### Pass 2: App-Specific Data Layer Compliance Audit
Perform a strict check against the core local-first and interface separation rules:
*   **Strict Network Restrictions (Local-First Promise):** Scan the codebase for unauthorized network frameworks (Retrofit, Ktor, OkHttp) or analytics/telemetry SDKs (Mixpanel, Firebase Analytics, etc.). Notificapp is **local-first** (ADR 012). The ONLY permitted networking egress is user-configured custom webhooks.
*   **Repository Design Pattern:** Repository interfaces must live in `domain/repository/` and their implementations must reside in [core/data/repository/](app/src/main/kotlin/dev/gaferneira/notificapp/core/data/repository/) and be marked `internal` (ADR 005).
*   **Failure & Error Mapping:** All repository operations must return Kotlin's `Result<T>` and map database/system exceptions to sealed subclasses of [Failure](app/src/main/kotlin/dev/gaferneira/notificapp/core/common/Failure.kt) (ADR 006).
*   **Transactional Writes:** Database updates containing rules, executions, or extracted data must be wrapped in transactions via `RuleExecutionRepository` to guarantee data integrity across Room tables.

---

## 3. Data Layer Refactoring Tickets
For *every single networking or persistence flaw* identified, provide an actionable engineering ticket using the exact ticket format defined in the Standardized Engineering Ticket Contract in [audit_orchestration.md](audit/audit_orchestration.md):

### [DATA-NUMBER] Descriptive Data Layer Optimization Title
*   **Target File Path(s):** `path/to/repository/` or `path/to/database/`
*   **Severity:** [Critical / Major / Minor]
*   **Impact Area:** Data
*   **Audit Pass Source:** [Pass 1 (Generic) / Pass 2 (Compliance)]
*   **Current Implementation Analysis:**
    Explain the data-layer or networking anti-pattern. Detail exactly how it degrades system efficiency, battery life, or user experience (e.g., redundant API requests, database locks, lack of encryption, or parsing crashes on missing fields). Cite the specific classes/interfaces.
*   **The "Target State" Blueprint:**
    Provide the refactored Kotlin data-layer code snippet. Show the corrected Room DAO query, the Kotlinx Serialization data transfer object (DTO) schema mapping, or the repository synchronization flow pattern required.

---

## 4. Data Layer Constraints & Acceptance Criteria for Executive Agent
*   **No Unapproved Outgoing Network Calls:** Zero third-party telemetry, analytics, or unapproved API integrations. Only user-configured webhooks.
*   **SSOT Enforcement:** The UI layer must never directly observe raw network response streams. All domain state must be observed via the local database cache or local data store.
*   **Main-Thread Prohibitions:** All Room database transactions must be structurally enforced to run entirely off the Main/UI thread.
*   **Schema Evolution:** Any modifications to the database schema must include explicit, robust migration scripts or tested fallback strategies to avoid production data loss.
*   **Error wrapping:** Expose failures exclusively via `Failure` sealed class wrapper types in the `Result` signature. Do not throw raw DB/SQLite exceptions up to the UI/ViewModel layer.