# Technical Debt Roadmap

This document details every technical debt item identified in the July 2026 architecture review, with the concrete solution for each. It is the implementation companion to **Phase 0: Foundation Hardening** in [`docs/roadmap.md`](roadmap.md) — the roadmap says *what* and *why*; this document says *how*. The corresponding architectural decisions are recorded in [ADR 009](adr/009-notification-processing-pipeline.md) (pipeline, TD-1/2/3), [ADR 010](adr/010-action-executor-multibindings.md) (actions, TD-4/5), and [ADR 011](adr/011-rule-definition-storage.md) (rule storage, TD-8).

**Execution order (as landed):** TD-1 → TD-2 & TD-3 (same commit) → TD-4 & TD-5 (same commit) → TD-6 → TD-7. Items TD-8 and TD-9 are decisions/conventions, not refactors, and were left untouched by design.

## Summary

| ID | Item | Priority | Effort | Status |
|----|------|----------|--------|--------|
| TD-1 | `RuleExecutionRepository` — remove DAO access outside the data layer | High | ~0.5 day | Resolved 2026-07-05 |
| TD-2 | Purify `RuleEngine` — evaluation without persistence | High | ~0.5 day | Resolved 2026-07-05 (with TD-3) |
| TD-3 | `ProcessNotificationUseCase` — pipeline out of the listener service | High | ~0.5 day | Resolved 2026-07-05 (with TD-2) |
| TD-4 | `ActionExecutor` — pluggable action handlers via Hilt multibindings | High | ~1 day | Resolved 2026-07-05 (with TD-5) |
| TD-5 | Per-action execution outcomes | Medium | ~0.5 day | Resolved 2026-07-05 (with TD-4) |
| TD-6 | Bootstrap the test suite (extraction engine first) | High | ~1 day | Resolved 2026-07-05 |
| TD-7 | `MviViewModel` hardcodes `Dispatchers.Main.immediate` | Low | ~1 hour | Resolved 2026-07-05 |
| TD-8 | Rule storage schema: normalized tables vs JSON column | Decision | — | Reevaluate before Roadmap Phase 2 |
| TD-9 | Accepted trade-offs (documented, no action) | — | — | Accepted |
| TD-10 | Documentation drift (`CLAUDE.md`, `ARCHITECTURE.md`) | High | — | **Resolved 2026-07-05** |

---

## TD-1: `RuleExecutionRepository` — remove DAO access outside the data layer

### Problem

The "extracted data" concept — the central product artifact — has no domain abstraction. Two unrelated consumers reach directly into Room:

- `core/extraction/RuleEngine.kt:30-36` injects `RuleExecutionDao`, `ExtractedFieldValueDao`, and `NotificationDao`, constructs `RuleExecutionEntity` by hand, and JSON-encodes fields inline (`RuleEngine.kt:159`).
- `features/notificationdetail/viewmodel/NotificationDetailViewModel.kt` imports the same DAOs plus entity types — a ViewModel depending on Room, violating the project's own dependency rules.

Every future consumer (Data Browser, export, statistics — Roadmap Phase 3) would otherwise couple to the same table schemas.

### Solution

Define the interface in `domain/repository/RuleExecutionRepository.kt`:

```kotlin
interface RuleExecutionRepository {

    /** Persist one execution and its typed field values atomically. */
    suspend fun saveExecution(execution: RuleExecution, fields: List<RuleField>): Result<Unit>

    /** Executions (with typed field values resolved) for one notification. */
    fun observeExecutionsForNotification(notificationId: String): Flow<List<RuleExecution>>

    /** Needed by the Data Browser later; add when Phase 3 starts. */
    // fun observeExtractedValuesPaged(filter: ExtractedValueFilter): Flow<PagingData<ExtractedFieldValue>>
}
```

Implement in `core/data/repository/RuleExecutionRepositoryImpl.kt`:

- Move the entity mapping out of `RuleEngine.saveExecution()` (`RuleEngine.kt:150-188`) into this impl and `core/data/local/mapper/`.
- Wrap `ruleExecutionDao.insert(...)` + `extractedFieldValueDao.insertAll(...)` + `notificationDao.incrementAppliedRulesCount(...)` in a Room `@Transaction` (add a `@Transaction` method on a DAO or use `db.withTransaction { }`) — today these are three separate writes that can partially fail.
- JSON encoding of `extractedData`/`triggeredActions` happens here (or in a mapper), never in `core/extraction`.
- Return `Result<T>`, log with Timber, never throw (per ADR 006).

Bind in `core/di/RepositoryModule.kt`:

```kotlin
@Binds
abstract fun bindRuleExecutionRepository(impl: RuleExecutionRepositoryImpl): RuleExecutionRepository
```

### Migration steps

1. Create interface + impl + Hilt binding (no behavior change yet).
2. Switch `NotificationDetailViewModel` to the repository; delete its DAO/entity imports.
3. Switch `RuleEngine` to the repository (interim step — TD-2 removes persistence from it entirely).
4. Verify: `grep -rn "local.dao\|local.entity" app/src/main/kotlin --include="*.kt"` must only hit files under `core/data/` and `core/di/`.

**Done when:** no DAO or entity import exists outside `core/data` and `core/di`.

---

## TD-2: Purify `RuleEngine` — evaluation without persistence

### Problem

`RuleEngine.process()` (`RuleEngine.kt:44-76`) mixes three responsibilities: loading rules, evaluating them (`RuleMatcher` + `FieldExtractor` — genuinely pure), and persisting results. Consequences:

- The core product logic cannot be unit-tested without Room.
- `core/extraction → core/data` dependency contradicts the intended `core/extraction → domain` direction.
- The engine can't be reused for backtesting (Roadmap Phase 2 "test against history" needs evaluate-without-persist).

### Solution

`RuleEngine` becomes a pure function over inputs; it no longer loads or saves anything:

```kotlin
// core/extraction/RuleEngine.kt — no DAO, no repository, no dispatcher needed
class RuleEngine @Inject constructor() {

    /** Evaluate a notification against rules. Pure: no I/O, no side effects. */
    fun evaluate(notification: Notification, rules: List<Rule>): List<RuleMatch> =
        rules.mapNotNull { rule -> evaluateRule(notification, rule) }

    private fun evaluateRule(notification: Notification, rule: Rule): RuleMatch? {
        if (!RuleMatcher.matches(notification, rule.conditions)) return null
        val extractedData = extractFields(notification, rule)   // existing logic, unchanged
        return RuleMatch(rule = rule, extractedData = extractedData)
    }
}

/** Evaluation result before persistence — lives in domain/model. */
data class RuleMatch(
    val rule: Rule,
    val extractedData: Map<String, String>,
)
```

Rule loading (`ruleRepository.getRulesForApp(...)`) and persistence (via TD-1's `RuleExecutionRepository`) move to the pipeline (TD-3), which converts each `RuleMatch` into a `RuleExecution` (UUID, timestamps, triggered actions).

This directly enables Phase 2 backtesting: the same `evaluate()` runs against historical notifications with results shown in UI instead of persisted.

### Migration steps

1. Introduce `RuleMatch` in `domain/model`.
2. Rewrite `RuleEngine` as above; delete its DAO/dispatcher constructor params (`withContext(ioDispatcher)` is no longer needed — there is no I/O).
3. Move rule-loading + persistence into `ProcessNotificationUseCase` (TD-3). Do TD-2 and TD-3 in the same PR to avoid a broken intermediate state.

**Done when:** `core/extraction/*.kt` has zero imports from `core.data` and zero suspend/coroutine dependencies.

---

## TD-3: `ProcessNotificationUseCase` — pipeline out of the listener service

### Problem

`NotificappListenerService` (`features/notification/NotificappListenerService.kt`) orchestrates the entire pipeline inline: enabled-app filtering, skip heuristics, normalization, deduplication, persistence, rule processing, and action execution (`onNotificationPosted` → `processRules` → `executeAction`). A `NotificationListenerService` is the hardest class in the app to test or reason about — it's OS-bound, has field injection, and its lifecycle is managed by the system. Every roadmap feature (webhooks, alarms, AI extraction, backtesting) plugs into this flow.

### Solution

The service keeps only what genuinely needs Android: translating `StatusBarNotification` → domain `Notification` (needs `packageManager`) and the pre-filters that read `sbn` internals. Everything after that moves into an injectable class:

```kotlin
// features/notification/ProcessNotificationUseCase.kt (or core/, see note)
class ProcessNotificationUseCase @Inject constructor(
    private val deduplicator: NotificationDeduplicator,
    private val notificationRepository: NotificationRepository,
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val ruleExecutionRepository: RuleExecutionRepository,
    private val actionDispatcher: ActionDispatcher,          // from TD-4
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) {
    suspend operator fun invoke(notification: Notification): Result<Unit> = withContext(ioDispatcher) {
        if (deduplicator.isDuplicate(notification)) return@withContext Result.success(Unit)

        notificationRepository.saveNotification(notification).onFailure { return@withContext Result.failure(it) }

        val rules = ruleRepository.getRulesForApp(notification.packageName).getOrElse { return@withContext Result.failure(it) }
        val matches = ruleEngine.evaluate(notification, rules)

        for (match in matches) {
            val outcomes = actionDispatcher.executeAll(notification, match.rule.actions)   // TD-4/TD-5
            val execution = match.toExecution(notification.id, outcomes)
            ruleExecutionRepository.saveExecution(execution, match.rule.fields)
        }
        Result.success(Unit)
    }
}
```

The service shrinks to:

```kotlin
override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null || !isAppEnabled(sbn.packageName) || shouldSkipNotification(sbn)) return
    serviceScope.launch {
        val notification = normalizer.normalize(sbn, packageManager)
        processNotification(notification)
            .onFailure { Timber.e(it, "Pipeline failed for ${sbn.packageName}") }
    }
}
```

**Placement note:** the use case has no Android imports, so it can live anywhere; `features/notification/` keeps it next to its callers, `core/` signals reuse. Either is fine — pick one and stay consistent with the future `:core:notification` module split.

**Design decision embedded here:** actions execute *before* the execution record is saved, so the record can store real outcomes (TD-5). If an action executor hangs (webhooks later), WorkManager-based executors return `SKIPPED`/enqueued immediately rather than blocking the pipeline.

**Done when:** `NotificappListenerService` contains only lifecycle wiring, pre-filters, normalization, and the `SystemNotificationController` registration (TD-4); an end-to-end pipeline test runs on plain JVM with fakes.

---

## TD-4: `ActionExecutor` — pluggable action handlers via Hilt multibindings

### Problem

`NotificappListenerService.executeAction()` (`NotificappListenerService.kt:205-220`) is a `when` over `ActionType` with a silent `else -> no-op`. Every new action (webhook, alarm, AI) means editing the OS-bound service. Dismiss/snooze also only work while the listener is connected, and nothing records that constraint.

### Solution

**1. The executor contract** (`domain/` or `features/notification/action/`):

```kotlin
interface ActionExecutor {
    suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome
}

enum class ActionOutcome { SUCCESS, FAILED, SKIPPED }   // see TD-5 for persistence
```

**2. Hilt multibindings** — one handler per type, registered declaratively:

```kotlin
@MapKey
annotation class ActionTypeKey(val value: ActionType)

@Module
@InstallIn(SingletonComponent::class)
abstract class ActionModule {
    @Binds @IntoMap @ActionTypeKey(ActionType.DISMISS_NOTIFICATION)
    abstract fun dismiss(impl: DismissActionExecutor): ActionExecutor

    @Binds @IntoMap @ActionTypeKey(ActionType.SNOOZE_NOTIFICATION)
    abstract fun snooze(impl: SnoozeActionExecutor): ActionExecutor
    // Adding SEND_WEBHOOK in Phase 4 = one new class + one @Binds line. No service edits.
}
```

**3. The dispatcher** consumed by the pipeline (TD-3):

```kotlin
class ActionDispatcher @Inject constructor(
    private val executors: Map<ActionType, @JvmSuppressWildcards Provider<ActionExecutor>>,
) {
    suspend fun executeAll(notification: Notification, actions: List<RuleAction>): Map<String, ActionOutcome> =
        actions.filter { it.isEnabled }.associate { action ->
            val outcome = executors[action.type]?.get()?.let { executor ->
                runCatching { executor.execute(notification, action) }
                    .getOrElse { e -> Timber.e(e, "Action ${action.type} failed"); ActionOutcome.FAILED }
            } ?: ActionOutcome.SKIPPED   // no handler registered — explicit, not silent
            action.id to outcome
        }
}
```

**4. System-dependent actions.** `cancelNotification()`/`snoozeNotification()` only exist on the live `NotificationListenerService` instance, which Hilt cannot inject elsewhere. Bridge with a narrow interface + a singleton holder the service populates:

```kotlin
interface SystemNotificationController {
    fun cancel(sbnKey: String)
    fun snooze(sbnKey: String, durationMs: Long)
}

@Singleton
class SystemNotificationControllerHolder @Inject constructor() {
    private val ref = AtomicReference<SystemNotificationController?>(null)
    fun set(controller: SystemNotificationController?) = ref.set(controller)
    fun get(): SystemNotificationController? = ref.get()
}
```

The service implements the interface (delegating to `cancelNotification`/`snoozeNotification`), calls `holder.set(this)` in `onListenerConnected()` and `holder.set(null)` in `onListenerDisconnected()`/`onDestroy()`. Executors then look like:

```kotlin
class DismissActionExecutor @Inject constructor(
    private val holder: SystemNotificationControllerHolder,
) : ActionExecutor {
    override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome {
        val controller = holder.get() ?: return ActionOutcome.SKIPPED   // listener not connected
        val key = notification.sbnKey ?: return ActionOutcome.SKIPPED   // no live SBN to act on
        controller.cancel(key)
        return ActionOutcome.SUCCESS
    }
}
```

`SnoozeActionExecutor` reuses `action.getSnoozeDurationMinutes()` (typed accessor pattern — see TD-9). In tests, executors get a fake holder/controller; no service, no emulator.

**Done when:** the service contains no `when (action.type)` block; adding an action type touches zero existing execution code.

---

## TD-5: Per-action execution outcomes

### Problem

`RuleEngine.executeRule()` records actions as "triggered" (`RuleEngine.kt:104-116`) **before** anything executes. If a dismiss fails (no `sbnKey`, listener disconnected) or the type is unimplemented (`CREATE_ALARM` today), the execution history claims it happened. Once webhooks exist (retries, network failures), truthful per-action status becomes mandatory, and retrofitting it later means backfilling data you never captured.

### Solution

**Domain:** add outcomes to `RuleExecution`:

```kotlin
data class RuleExecution(
    // ...existing fields...
    val triggeredActions: List<String>,                       // keep for compatibility
    val actionOutcomes: Map<String, ActionOutcome> = emptyMap(),  // actionId → outcome
)
```

**Persistence:** add a nullable JSON column to `rule_executions` (consistent with how `extracted_data`/`triggered_actions` are already stored):

```kotlin
// RuleExecutionEntity: @ColumnInfo(name = "action_outcomes") val actionOutcomes: String? = null
```

As landed, this didn't need a formal `Migration` object: `DatabaseModule` already builds `AppDatabase` with `.fallbackToDestructiveMigration()` since the app is unpublished, so the fix was a version bump (`version = 1` → `version = 2` in `AppDatabase`) plus the new nullable column — Room destructively recreates the schema on version mismatch instead of running a migration path. A real `Migration` (`ALTER TABLE ... ADD COLUMN`) will be needed for the first schema change after this app ships to real users.

Old rows read as `null` → `emptyMap()`; no backfill needed.

**Flow:** `ActionDispatcher.executeAll()` (TD-4) returns the map; `ProcessNotificationUseCase` (TD-3) stores it on the execution before saving. **UI:** Notification Detail shows outcome per action (✓ / ✗ / — with a short reason where available) — this is the "action execution feedback" task in Roadmap Phase 1.

**Done when:** a failed dismiss shows as `FAILED` in Notification Detail, and a rule whose only action is unimplemented shows `SKIPPED`, not "triggered".

---

## TD-6: Bootstrap the test suite (extraction engine first)

### Problem

`app/src/test` contains no test files at all (only `.DS_Store`). The extraction engine is the product core and the code most likely to churn as the product matures — exactly where regressions are most expensive and tests are cheapest (pure Kotlin, plain JVM, no emulator).

### Solution

Priority order (all JUnit 5 + Kotest + MockK per existing conventions; coroutines via `runTest` where needed):

1. **`RuleMatcherTest`** — one test per `MatchingOperator` (all 6), plus: empty condition list matches everything; null condition/value handling; multiple conditions are AND-ed; case sensitivity behavior (pin down whatever current behavior is — it becomes the contract).
2. **`FieldExtractorTest`** — one happy path + one failure path per `ExtractionMethod` (all 10). Edge cases per method: out-of-range indices (`FixedPosition` already coerces — pin it), missing anchors/keywords, invalid regex patterns, malformed JSON for `JsonPath`, locale formats for `SmartAmountDetection`/`SmartDateDetection`.
3. **`RuleEngineTest`** — after TD-2: pure `evaluate()` tests; matched rule with failed required field; rule with no fields; multiple matching rules.
4. **`ProcessNotificationUseCaseTest`** — after TD-3: end-to-end with fake repositories and a fake `ActionDispatcher`; duplicate short-circuits; save failure aborts; outcomes persisted.
5. **`ActionDispatcherTest` / executor tests** — after TD-4: disabled actions skipped; missing handler → `SKIPPED`; executor exception → `FAILED`; disconnected controller → `SKIPPED`.

Shared fixtures in `app/src/test/kotlin/.../testutil/`: `createTestNotification(...)`, `createTestRule(...)`, `createTestCondition(...)` builders with sensible defaults, plus a `MainDispatcherExtension` (JUnit 5 extension wrapping `Dispatchers.setMain(StandardTestDispatcher())`) for ViewModel tests later.

Wire `./gradlew test` into CI (Community & Distribution track in the roadmap) so coverage can't silently rot again.

**Done when:** `./gradlew test` runs a non-empty suite covering all 6 operators and all 10 extraction methods, and CI fails on test failure.

---

## TD-7: `MviViewModel` hardcodes `Dispatchers.Main.immediate`

### Problem

`core/ui/mvi/MviViewModel.kt` — `sendEffect()` launches on `Dispatchers.Main.immediate` directly, contradicting ADR 008 ("always inject dispatchers"). Impact is limited because `kotlinx-coroutines-test`'s `Dispatchers.setMain()` also intercepts `Main.immediate`, but the base class should model the rule the rest of the codebase must follow.

### Solution

Add a constructor parameter with a production default so the ~7 existing ViewModels don't all need signature changes at once:

```kotlin
abstract class MviViewModel<UiState, UiEvent, UiEffect>(
    initialState: UiState,
    private val effectDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    protected fun sendEffect(effect: UiEffect) {
        viewModelScope.launch(effectDispatcher) { _effect.send(effect) }
    }
}
```

Tests pass a `StandardTestDispatcher`; ViewModels that want full ADR 008 compliance pass their injected `@Dispatcher(DispatcherType.Main)` explicitly. Alternatively, accept the `Dispatchers.setMain()` escape hatch and just document it in the test conventions — either resolution is fine; pick one and note it in ADR 008.

---

## TD-8: Rule storage schema — normalized tables vs JSON column (decision, not a refactor)

### Context

The rule definition is normalized across 5 tables (`RuleEntity`, `RuleConditionEntity`, `RuleFieldEntity`, `RuleActionEntity`, `RuleTargetAppEntity`). While the *concept* of a rule is still maturing (OR-groups? nested conditions? AI-suggested rules?), every shape change costs a multi-table Room migration plus mapper churn. Meanwhile the app only ever *queries* rule metadata (`is_active`, `is_global`, target packages) — never condition internals. And `Rule` is already `@Serializable`.

### Options

| | Normalized (current) | JSON definition column |
|---|---|---|
| Query condition/field internals | Possible | Not without JSON1 functions |
| Rule shape change | Multi-table migration | Usually zero migration |
| Import/export (Phase 2) | Needs assembly/disassembly | Trivially the same format |
| Mapper surface | 5 entity mappers | 1 serializer |

### Recommendation

**Reevaluate before starting Roadmap Phase 2** (rule import/export makes the JSON representation canonical anyway). If migrating: keep a thin `rules` table (`id`, `name`, `is_active`, `is_global`, timestamps) + `rule_target_apps` (queried for matching) + a `definition TEXT` column holding the serialized conditions/fields/actions. Include a `schema_version` field inside the JSON for forward migration of rule definitions. Do **not** do this preemptively — only if Phase 2 work confirms rule shape is still churning.

---

## TD-9: Accepted trade-offs (documented, no action planned)

- **`androidx.paging.PagingData` in `domain/repository/NotificationRepository`** — an Android library type leaking into domain. Accepted: the alternative (custom paging abstraction) costs more than it buys for a single-app codebase. Revisit only if the extraction core is ever extracted as a standalone library.
- **`Notification.sbnKey` on the domain model** — an Android system identifier on a domain type. Accepted: it is the only handle for dismiss/snooze, and modeling it out (e.g., a side-table) adds indirection with no benefit.
- **`RuleAction.config: Map<String, String>`** — stringly-typed action config. Accepted **deliberately**: schemaless config means new action types need no migration. Convention to enforce: access only through typed readers per action type (the existing `getSnoozeDurationMinutes()` pattern); never scatter raw key lookups or key-name constants at call sites. The map stays flexible at rest, typed at the boundaries.

---

## TD-10: Documentation drift — **Resolved 2026-07-05**

`CLAUDE.md` and `docs/ARCHITECTURE.md` described a nonexistent structure (`com.notificapp`, layer-first packaging, a DataViewer screen, `ExtractionRule`/`ExtractedEvent`/`EventRepository` types) and claimed a test suite that didn't exist. Both files were rewritten to match the real codebase (`dev.gaferneira.notificapp`, `core/` + `features/`, real model/repository names, honest test status, known-gaps sections). Residual rule: **when the Phase 0 refactors above land, update the "Known Gaps"/"Known violations" sections in both docs and this file in the same PR.**
