# Design: Webhook Delivery (Phase 4 PR2)

## Technical Approach

Wire webhooks into the rule engine as a new `SEND_WEBHOOK` action and make delivery reliable + visible. On rule match, `SendWebhookActionExecutor` builds a JSON payload (raw field checklist OR `{{token}}` template) from the matched notification + extracted fields, persists a delivery-job row in the SQLCipher DB, and enqueues a WorkManager `CoroutineWorker` referenced only by that row id. The worker POSTs, classifies the outcome 3 ways (network / server / client), maps that to WorkManager `Result` + the persisted queue, and updates the webhook's `lastDeliveryStatus`/`lastDeliveryAt`. Unresolved rows survive process death and are re-enqueued on app open. Everything follows PR1's conventions: flat `Map<String,String>` config à la `FlashActionConfig`, `internal` data layer (architectureCheck rule 1), `Result<T>`/`Failure` mapping (ADR 006, rule 6), injected dispatchers (ADR 008, rule 2), feature-owned Rule Editor models (rule 7), and the no-log/redaction discipline for secrets.

WorkManager is the app's first use; its Hilt wiring lands in `MyApplication` alongside the existing `ImageLoaderFactory`, resolving the "no WorkManager yet" TODO in `MainActivity`'s retention sweep.

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Extracted data → executor | Extend `ActionExecutor.execute(notification, action, extractedFields: Map<String,String>)`; `ActionDispatcher.executeAll` threads a **name-keyed** map resolved from `RuleMatch.extractedData` (id-keyed) via `rule.saveDataFields()` | Special-case `SEND_WEBHOOK` in `ProcessNotificationUseCase` | `RuleEngine` keys `extractedData` by field **id** (`RuleEngine.kt:72`); the executor only receives `(notification, action)` and has no access to the rule's field names. A payload needs field **names**. Keeping the uniform executor abstraction (ADR 010) beats special-casing one type in the orchestrator. Cost: one mechanical signature change across 5 existing executors (each ignores the new param). |
| Payload transport to Worker | Persist payload+webhookId to `webhook_deliveries` (SQLCipher), pass only **row id** as WorkManager `inputData` | Serialize payload/URL/auth into `inputData` | WorkManager's `Data` store is (a) capped at ~10KB and (b) **not encrypted** — putting a payload (with possibly-sensitive extracted fields) or auth there violates decision 9 (treat like `authValue`). Row id in `inputData`; encrypted payload stays in SQLCipher (verified wired: `DatabaseModule.provideDatabase` → `SupportOpenHelperFactory`, DATA-02). |
| Delivery HTTP client | New `WebhookDeliveryClient` in `core/network`, shares the `NetworkModule` `OkHttpClient`, returns a classified `DeliveryResult` | Reuse `WebhookTestClient.post` (fixed sample body) | Delivery sends a *custom* body and needs 3-way classification; test sends a fixed sample. Both share request-building + the same no-log discipline (never log URL/headers/body/exception message). |
| Retry timing | Network → `Result.retry()` gated by `Constraints(NetworkType.CONNECTED)`; Server → manual re-enqueue with explicit `setInitialDelay` from `[1m, 5m, 30m]`, max 3; Client → fail fast, persist | Uniform `setBackoffCriteria(EXPONENTIAL)` for all | WorkManager `EXPONENTIAL` from a 1m base yields 1/2/4m, not the roadmap-locked 1/5/30m — it cannot express that curve. Explicit per-attempt initial delay hits the exact schedule. Network errors must not burn server attempts (decision 4); the `CONNECTED` constraint fires them when connectivity returns. |
| Queue = delivery journal | One `webhook_deliveries` table holds PENDING + FAILED rows; **deleted on success** | Separate "outbox" + "failed" tables | Row exists while a delivery is unresolved; success deletes it, so the table naturally drains (decision 7, unbounded is fine). App-open sweep re-enqueues remaining rows. |
| `AppDatabase` version | `CURRENT_VERSION` 2→3, no `Migration` | Hand-written migration | Pre-launch destructive policy (CLAUDE.md); fresh installs create v3 directly, debug uses existing destructive fallback (decision 8). |

## Domain Model & Contracts

### Config (`core/notification/action/WebhookActionConfig.kt`, mirrors `FlashActionConfig`)

```kotlin
const val WEBHOOK_ID_KEY = "webhook_id"
const val WEBHOOK_PAYLOAD_MODE_KEY = "webhook_payload_mode" // "fields" | "template"
const val WEBHOOK_SELECTED_FIELDS_KEY = "webhook_selected_fields" // CSV; built-ins by fixed name, extracted fields as "field.<fieldId>"
const val WEBHOOK_TEMPLATE_KEY = "webhook_template"       // custom JSON with {{tokens}}

enum class WebhookPayloadMode { FIELDS, TEMPLATE }

fun RuleAction.getWebhookId(): String? = config[WEBHOOK_ID_KEY]
fun RuleAction.getWebhookPayloadMode(): WebhookPayloadMode =
    config[WEBHOOK_PAYLOAD_MODE_KEY]?.let { runCatching { WebhookPayloadMode.valueOf(it.uppercase()) }.getOrNull() }
        ?: WebhookPayloadMode.FIELDS
fun RuleAction.getWebhookSelectedFields(): Set<String> =
    config[WEBHOOK_SELECTED_FIELDS_KEY]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
fun RuleAction.getWebhookTemplate(): String = config[WEBHOOK_TEMPLATE_KEY].orEmpty()
```

`ActionType` gains `@SerialName("send_webhook") SEND_WEBHOOK`. `RuleAction.createSendWebhook(id, webhookId, mode, fields, template, isEnabled)` factory mirrors `createFlashAlert`.

**FIELDS-mode selection references `RuleField.id`, not name.** Built-in tokens (`title`, `content`, `app_name`, `package_name`, `timestamp`, `raw_content`) are fixed system keys that never change, so they're stored as-is. Extracted fields are user-renameable (`RuleField.name` is editable independent of `RuleField.id`), so a checklist entry stored as a bare name would silently stop matching after a rename. Extracted-field entries are stored as `field.<fieldId>` and resolved to the *current* `RuleField.name` at payload-build time via `rule.fields`; if the referenced id no longer exists (field deleted), `WebhookPayloadBuilder` drops that key from the output — no error, the payload just has one less field. Template-mode `{{field.<name>}}` tokens remain name-based (inherent to free-text substitution — see Payload builder section for the rename/unknown-token handling this implies).

### Executor (`core/notification/action/SendWebhookActionExecutor.kt`, mirrors `FlashAlertActionExecutor`)

```kotlin
class SendWebhookActionExecutor @Inject constructor(
    private val payloadBuilder: WebhookPayloadBuilder,
    private val deliveryEnqueuer: WebhookDeliveryEnqueuer, // wraps queue-write + WorkManager enqueue
) : ActionExecutor {
    override suspend fun execute(
        notification: Notification, action: RuleAction, extractedFields: Map<String, String>,
    ): ActionOutcome {
        val webhookId = action.getWebhookId() ?: return ActionOutcome.SKIPPED // misconfigured
        val payload = payloadBuilder.build(notification, action, extractedFields)
        deliveryEnqueuer.enqueue(webhookId, payload) // writes row, enqueues worker by row id
        return ActionOutcome.SUCCESS // "accepted for delivery"; actual send is async
    }
}
```
Registered in `ActionModule` via `@Binds @IntoMap @ActionTypeKey(SEND_WEBHOOK)`. `SUCCESS` here means *enqueued*, not *delivered* — the tri-state indicator (below) reports real delivery outcome, so execution history staying `SUCCESS` is honest at the "handed off" boundary.

### Payload builder (`core/notification/action/WebhookPayloadBuilder.kt`, pure Kotlin)

**FIELDS mode** — fixed shape via `buildJsonObject` (inherently JSON-safe), only selected keys included:
```json
{ "title": "...", "content": "...", "app_name": "...", "package_name": "...",
  "timestamp": 1234, "raw_content": "...", "fields": { "<name>": "<value>" } }
```
Built-in token names: `title, content, app_name, package_name, timestamp, raw_content`. Extracted fields nest under `fields.<name>` to avoid collision with built-ins.

**TEMPLATE mode** — token substitution over the author's JSON string:
- Tokens: `{{title}}`, `{{content}}`, `{{app_name}}`, `{{package_name}}`, `{{timestamp}}`, `{{raw_content}}`, and `{{field.<name>}}` per extracted field.
- **JSON-safety**: each substituted value is JSON-string-escaped before insertion — `Json.encodeToString(String.serializer(), value)` then strip the outer quotes, injecting inside the author's existing `"..."`. Guarantees a value containing `"`/`\`/newline can't break the document.
- **Unknown token**: substitutes to empty string at delivery time (a live notification must never be dropped over an author typo); the Rule Editor "Preview payload" flags unknown tokens + invalid-JSON result as a warning (strict at authoring, lenient at runtime).

### Delivery client + result (`core/network/`)

```kotlin
sealed interface DeliveryResult {
    data class Delivered(val httpCode: Int) : DeliveryResult   // 2xx
    data object NetworkError : DeliveryResult                  // IOException / timeout
    data class ServerError(val httpCode: Int) : DeliveryResult // 5xx, 408, 429
    data class ClientError(val httpCode: Int) : DeliveryResult // other 4xx
}
```
`WebhookDeliveryClient.post(webhook, body): DeliveryResult` catches `IOException` → `NetworkError`, maps status per above. Like `WebhookTestClient`, it **never** throws, never touches `Failure`, and never logs URL/headers/body/exception message (only `webhook.id`) — satisfies rule 6 and decision 9.

### Queue table (`core/data/local`, all `internal` per rule 1)

```kotlin
@Entity(tableName = "webhook_deliveries")
internal data class WebhookDeliveryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo("webhook_id") val webhookId: String,
    val payload: String,                              // JSON snapshot, encrypted at rest (SQLCipher)
    @ColumnInfo("failure_type") val failureType: String?, // NETWORK/SERVER/CLIENT; null while PENDING
    @ColumnInfo("attempt_count") val attemptCount: Int,
    @ColumnInfo("created_at") val createdAt: Long,
    @ColumnInfo("last_attempt_at") val lastAttemptAt: Long?,
) {
    // No-log: redact payload (may carry extracted secrets), mirror WebhookEntity.toString().
    override fun toString(): String = "WebhookDeliveryEntity(id=$id, webhookId=$webhookId, " +
        "payload=REDACTED, failureType=$failureType, attemptCount=$attemptCount, " +
        "createdAt=$createdAt, lastAttemptAt=$lastAttemptAt)"
}
```
`WebhookDeliveryDao` (mirrors `WebhookDao`): `insert(REPLACE)`, `getById(id)`, `getAllFailed(): List<..>` (for the app-open sweep), `deleteById(id)`, `updateFailure(id, type, attemptCount, at)`. `WebhookDeliveryRepository` (+ interface in `domain/repository`, impl `internal` in `core/data/repository`) exposes `enqueue`, `markDelivered` (delete), `markFailed`, `pendingFailures()` — catch blocks map via `e.toFailureResult()` (rule 6). Added to `AppDatabase` entities; `webhookDeliveryDao()`; `CURRENT_VERSION = 3`.

### Webhook status fields

`Webhook` / `WebhookEntity` gain `lastDeliveryStatus: WebhookDeliveryStatus` (`UNKNOWN | DELIVERED | CONFIG_ERROR | UNREACHABLE`) and `lastDeliveryAt: Long?`. Persisted as a discriminator string + nullable long (mirrors the `authType` flattening). `WebhookRepository` gains `suspend fun updateDeliveryStatus(id, status, at): Result<Unit>`, called by the worker after each terminal outcome. Mapper + `WebhookMapper` round-trip updated; `toString()` redaction unchanged.

## Data Flow

```
Rule match (ProcessNotificationUseCase)
  → ActionDispatcher.executeAll(notification, actions, extractedFields[name→value])
  → SendWebhookActionExecutor.execute(...)
      → WebhookPayloadBuilder.build(...)  → JSON string
      → WebhookDeliveryEnqueuer.enqueue(webhookId, payload)
          → deliveryRepo.enqueue(row PENDING)          [SQLCipher]
          → WorkManager.enqueue(OneTimeWorkRequest(rowId), Constraints(CONNECTED))
  ── async ──
WebhookDeliveryWorker.doWork()  (withContext(injected IO))
  → row = deliveryRepo.getById(rowId); webhook = webhookRepo.getWebhook(row.webhookId)
  → DeliveryResult = deliveryClient.post(webhook, row.payload)
      ├ Delivered  → deliveryRepo.markDelivered(delete); webhook.status=DELIVERED;  Result.success()
      ├ NetworkError → row stays PENDING;                                           Result.retry()  (CONNECTED gate)
      ├ ServerError  → attempt+1 < 3 ? re-enqueue(delay=[1m,5m,30m][attempt]) ; else markFailed(SERVER)/status=UNREACHABLE; Result.success()/failure()
      └ ClientError  → markFailed(CLIENT); webhook.status=CONFIG_ERROR;             Result.failure()  (fail fast)

App open (MyApplication.onCreate, next to retention-sweep precedent)
  → WorkManager.enqueue(WebhookRetrySweepWorker)
      → deliveryRepo.pendingFailures().forEach { re-enqueue delivery worker (reset PENDING) }
```

## WorkManager + Hilt Wiring

- **gradle** (`libs.versions.toml` + `app/build.gradle.kts`): `androidx.work:work-runtime-ktx`, `androidx.hilt:hilt-work`, `androidx.hilt:hilt-compiler` (ksp), and `androidx.work:work-testing` (androidTest/test).
- **`MyApplication`**: add `Configuration.Provider`; `@Inject lateinit var workerFactory: HiltWorkerFactory`; `override val workManagerConfiguration = Configuration.Builder().setWorkerFactory(workerFactory).build()`. Keep `ImageLoaderFactory`. In `onCreate`, after `super`, enqueue the retry sweep (guarded once-per-process like `MainActivity.hasEnforcedRetentionThisProcess`).
- **Manifest**: remove the default WorkManager initializer so on-demand init via `Configuration.Provider` applies —
  `<provider android:name="androidx.startup.InitializationProvider" ... tools:node="merge">` with `<meta-data android:name="androidx.work.WorkManagerInitializer" tools:node="remove"/>`.
- **Worker**:
```kotlin
@HiltWorker
class WebhookDeliveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val deliveryRepo: WebhookDeliveryRepository,
    private val webhookRepo: WebhookRepository,
    private val deliveryClient: WebhookDeliveryClient,
    private val enqueuer: WebhookDeliveryEnqueuer,
    @Dispatcher(DispatcherType.IO) private val io: CoroutineDispatcher,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(io) { /* classify + map as above */ }
}
```
`CoroutineWorker` defaults to `Dispatchers.Default`; wrapping in `withContext(io)` with the injected `@Dispatcher(IO)` qualifier satisfies architectureCheck **rule 2** (no hardcoded `Dispatchers.IO`).

## architectureCheck / ADR Compliance

- **Rule 1** (visibility): queue entity/DAO/mapper/`*Impl` are `internal`.
- **Rule 2** (dispatcher injection): worker + client + repo use `@Dispatcher(IO)`; no hardcoded dispatcher.
- **Rule 6** (no raw exception leak): `WebhookDeliveryClient` returns `DeliveryResult` (never `Failure`); repo catch blocks map via `toFailureResult()`.
- **Rule 7** (contract purity): Rule Editor `contract/` exposes only feature-owned models — a `features/ruleeditor/domain/WebhookConfigUiModel` (mode, selected fields, template, picked webhook) + a rendered-preview `String`. The ViewModel maps to/from the flat `Map<String,String>` config and calls `WebhookPayloadBuilder` internally; `core.notification`/`core.extraction` types never appear in the contract.

## Rule Editor UI

- `ActionType.SEND_WEBHOOK` branch in `ActionTypeUi.ui()` (label "Send webhook", `Icons.Default.Send`).
- New `WebhookConfigBottomSheet` using the shared `ActionConfigSheet` scaffold:
  - **Webhook picker** sourced from `WebhookRepository.observeWebhooks()` (domain, allowed).
  - **Inline-create**: a "New webhook" entry navigates to `WebhookEditorScreen` (reuse PR1's existing screen) rather than an inline dialog — the editor already owns URL/header/auth validation + the test button; duplicating that in a dialog would fork validation logic and re-introduce leak risks. On return, the picker refreshes from the observed flow and preselects the newest.
  - **Payload-mode toggle** (Fields / Template) + field checklist (built-ins + extracted fields discovered live from `rule.fields`, referenced by `fieldId` per the config note above) or a JSON template editor with an **"Insert field" chip row** (grouped "Notification" / "Extracted fields") that inserts `{{token}}` at the cursor — the primary authoring path, so hand-typed tokens are the exception rather than the rule.
  - **Default selection** (FIELDS mode, new action): built-ins `title`, `content`, `app_name`, `timestamp` pre-checked; `raw_content` and `package_name` unchecked (verbose/low-signal); **all currently-defined extracted fields pre-checked** — pairing extraction with a webhook is usually specifically to relay the parsed value.
  - **Empty state**: if `rule.fields` is empty when this sheet opens, the "Extracted fields" group is replaced with a hint: "No extracted fields yet — add an Extract Data action to reference custom fields here." (Extract Data is a sibling action in the same `DoSection` list, not a prerequisite step, so this is a normal transient state, not an error.)
  - **"Preview payload"** renders `WebhookPayloadBuilder.build(...)` against a sample/most-recent notification, surfacing unknown-token / invalid-JSON warnings.

## File Changes

| File | Action |
|---|---|
| `domain/model/RuleAction.kt` (`ActionType`) | Modify — add `SEND_WEBHOOK` + `createSendWebhook` |
| `core/notification/action/WebhookActionConfig.kt` | Create — keys + readers + `WebhookPayloadMode` |
| `core/notification/action/WebhookPayloadBuilder.kt` | Create — FIELDS/TEMPLATE builder + token substitution |
| `core/notification/action/SendWebhookActionExecutor.kt` | Create — executor |
| `core/notification/action/WebhookDeliveryEnqueuer.kt` | Create — queue-write + WorkManager enqueue helper |
| `core/notification/action/WebhookDeliveryWorker.kt` | Create — `@HiltWorker CoroutineWorker` |
| `core/notification/action/WebhookRetrySweepWorker.kt` | Create — app-open drain |
| `domain/action/ActionExecutor.kt` | Modify — add `extractedFields` param |
| `core/notification/action/ActionDispatcher.kt`, `ProcessNotificationUseCase.kt` | Modify — thread name-keyed extracted map |
| `core/notification/action/{Dismiss,Snooze,SaveData,FlashAlert,alarm/Alarm}ActionExecutor.kt` | Modify — accept (ignore) new param |
| `core/network/WebhookDeliveryClient.kt`, `DeliveryResult.kt` | Create |
| `core/data/local/entity/WebhookDeliveryEntity.kt`, `dao/WebhookDeliveryDao.kt`, `mapper/WebhookDeliveryMapper.kt` | Create |
| `domain/repository/WebhookDeliveryRepository.kt`, `core/data/repository/WebhookDeliveryRepositoryImpl.kt` | Create |
| `domain/model/Webhook.kt`, `core/data/local/entity/WebhookEntity.kt`, `mapper/WebhookMapper.kt` | Modify — `lastDeliveryStatus`, `lastDeliveryAt` |
| `domain/model/WebhookDeliveryStatus.kt` | Create |
| `domain/repository/WebhookRepository.kt`, `core/data/repository/WebhookRepositoryImpl.kt` | Modify — `updateDeliveryStatus` |
| `core/data/local/AppDatabase.kt` | Modify — add entity, `webhookDeliveryDao()`, `CURRENT_VERSION = 3` |
| `core/di/{DatabaseModule,RepositoryModule,ActionModule}.kt` | Modify — DAO/repo/executor wiring |
| `features/ruleeditor/domain/ActionTypeUi.kt`, `domain/WebhookConfigUiModel.kt`, `ui/WebhookConfigBottomSheet.kt`, `viewmodel/RuleEditorViewModel.kt`, `contract/*` | Modify/Create — config UI (rule-7-safe) |
| `features/webhook/` (list contract/vm/ui) | Modify — tri-state indicator from status fields |
| `MyApplication.kt` | Modify — `Configuration.Provider` + retry sweep |
| `MainActivity.kt` | Modify — drop "no WorkManager yet" TODO wording |
| `AndroidManifest.xml` | Modify — remove default `WorkManagerInitializer` |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modify — WorkManager + hilt-work + work-testing |
| `docs/adr/012-*.md`, `PRIVACY.md` | Modify — retry/queue addenda |

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit — payload builder | FIELDS shape (only selected keys; `fields.<name>` nesting); TEMPLATE substitution incl. built-ins + `{{field.<name>}}`; JSON-escaping of values with `"`/newline; unknown token → empty | Pure JVM, Kotest |
| Unit — classifier | HTTP 200/201→Delivered; 500/408/429→ServerError; 400/401/404→ClientError; `IOException`→NetworkError | Mock OkHttp `Call`/`Response` |
| Unit — worker | Delivered→delete+status DELIVERED+`success`; Client→markFailed CONFIG_ERROR+`failure`; Server<3→re-enqueue; Server=3→UNREACHABLE; Network→`retry` | `work-testing` `TestListenableWorkerBuilder`, mock repos/client, injected `StandardTestDispatcher` |
| Unit — queue repo | enqueue/markFailed/pendingFailures map entity↔domain; dao exception→`Result.failure` | MockK dao |
| Unit — executor | missing `webhook_id`→SKIPPED; happy path builds payload + enqueues once | MockK builder+enqueuer |
| Unit — editor VM | mode toggle, field selection, template edit persist to config; preview surfaces unknown-token warning; picker reflects observed webhooks | Fake repo, Turbine |
| No-log guarantee | probe `Timber.Tree`; exercise client/worker/repo failure paths with a fixed sentinel secret in payload + auth; assert it never appears in any log/throwable | Extend PR1's no-log test |

## Migration / Rollout

No data migration — pre-launch destructive `CURRENT_VERSION` 2→3 bump (decision 8). Fresh installs create v3; debug uses `fallbackToDestructiveMigration()`; a non-debug build over a v2 DB crashes loudly (accepted pre-first-release, same tradeoff documented in PR1). **Must not ship post-first-release without a real `Migration`.** Rollback = revert PR; new files are self-contained; shared edits (DB version, DI, `ActionType`, dispatcher signature, `MyApplication`, manifest, gradle, Rule Editor) revert cleanly; dropping `webhook_deliveries` + reverting the two `Webhook` fields is safe (no production data).

## Resolved Questions

- **`ActionExecutor` signature change (5 executors)**: confirmed no external/plugin implementors — `ActionExecutor` is an internal, monolithic-app interface with exactly the 5 in-tree implementations (grep-verified). Explicit `extractedFields` param proceeds as designed.
- **Server-retry attempt counting**: the persisted `attemptCount` column on the `webhook_deliveries` row is the sole source of truth, not WorkManager's `runAttemptCount`. Server-classified failures are handled by manual re-enqueue (a fresh `WorkRequest` per attempt, per decision row 16), so `runAttemptCount` resets to 0 every time and can't track the 3-attempt cap — the worker must read/increment the DB row's `attemptCount` itself. `Result.retry()` is reserved exclusively for the network-classified path, where a single long-lived `WorkRequest` legitimately waits on the `Constraints(NetworkType.CONNECTED)` gate without needing attempt counting.
- [ ] Preview "sample notification" source — most-recent captured notification for the rule's target apps vs. a synthetic fixture; lean synthetic to avoid a DB read in the editor, confirm in tasks.
