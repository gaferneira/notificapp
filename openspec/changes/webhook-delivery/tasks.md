# Tasks: Webhook Delivery (Phase 4 PR2)

## Review Workload Forecast

| Field                       | Value                                                                                                                                                |
|-----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Estimated changed lines     | ~1800-2200 (16 new files, WorkManager first-use infra, 5-executor signature change, Rule Editor UI, full test suite)                                 |
| 400-line budget risk        | High                                                                                                                                                 |
| Chained commits recommended | Yes                                                                                                                                                  |
| Suggested split             | PR2a: domain+data+WorkManager infra; PR2b: executor+payload builder+dispatcher wiring; PR2c: Rule Editor UI+webhook list indicator; PR2d: tests+docs |
| Delivery strategy           | ask-on-risk                                                                                                                                          |
| Chain strategy              | pending — needs your decision (stacked-to-main / feature-branch-chain / size-exception)                                                              |

Decision needed before apply: Yes
Chained commits recommended: Yes
Chain strategy: pending
400-line budget risk: High

**Flagging back per ask-on-risk**: this forecast is larger than PR1 (which was already High-risk and shipped as a `size:exception`). Before `sdd-apply` starts, tell me which chain strategy to use, or confirm `size:exception` if you want it as one PR again.

### Suggested Work Units

| Unit | Goal                                                                                                                                                                                    | Likely commits | Notes                                                                |
|------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------|----------------------------------------------------------------------|
| 1    | Domain (`ActionType`, config, `WebhookDeliveryStatus`) + Data layer (queue table, `Webhook` status fields) + WorkManager infra (gradle, `HiltWorkerFactory`, `MyApplication`, manifest) | commit 1       | Foundation, no executor logic, no UI. Base = feature/tracker branch. |
| 2    | Payload builder + `SendWebhookActionExecutor` + `ActionExecutor` signature change across 5 executors + `WebhookDeliveryWorker`/`WebhookRetrySweepWorker`/`WebhookDeliveryClient`        | commit 2       | Depends on Unit 1. Base = Unit 1 branch.                             |
| 3    | Rule Editor UI (`ActionTypeUi`, `WebhookConfigBottomSheet`, insert-field chips, default selection, empty state, preview) + webhook list tri-state indicator                             | commit 3       | Depends on Unit 2. Base = Unit 2 branch.                             |
| 4    | Tests (executor/payload-builder/classifier/worker/queue/editor-VM/no-log) + docs (ADR 012, PRIVACY.md) + manual verification                                                            | commit 4       | Depends on Unit 1-3. Base = Unit 3 branch.                           |

## Phase 1: Foundation / Domain

- [x] 1.1 Modify `domain/model/RuleAction.kt`: add `ActionType.SEND_WEBHOOK` (`@SerialName("send_webhook")`) + `createSendWebhook(id, webhookId, mode, fields, template, isEnabled)` factory. **Deviation**: `mode`/`fields`/`template` grouped into a `WebhookPayloadConfig` value object (`payload` param) instead of 3 flat params - keeps the factory under detekt's `LongParameterList` threshold (6), same tradeoff already used by `createAlarm`'s `options` param.
- [x] 1.2 Create `core/notification/action/WebhookActionConfig.kt`: key constants, `WebhookPayloadMode` enum, extension readers (`getWebhookId`, `getWebhookPayloadMode`, `getWebhookSelectedFields` storing `field.<fieldId>` for extracted fields, `getWebhookTemplate`). Also added `WebhookPayloadConfig` (see 1.1 deviation) and a `WEBHOOK_FIELD_ID_PREFIX` constant.
- [x] 1.3 Create `domain/model/WebhookDeliveryStatus.kt`: `UNKNOWN | DELIVERED | CONFIG_ERROR | UNREACHABLE`.
- [x] 1.4 Modify `domain/model/Webhook.kt`: add `lastDeliveryStatus: WebhookDeliveryStatus`, `lastDeliveryAt: Long?`.
- [x] 1.5 Modify `domain/repository/WebhookRepository.kt`: add `suspend fun updateDeliveryStatus(id, status, at): Result<Unit>`.
- [x] 1.6 Create `domain/repository/WebhookDeliveryRepository.kt` interface: `enqueue`, `markDelivered`, `markFailed`, `pendingFailures()`. **Note**: also added `domain/model/WebhookDelivery.kt` (id/webhookId/payload/failureType/attemptCount/createdAt/lastAttemptAt, redacted `toString()`) since the mapper (2.3) needs a domain type to round-trip.

## Phase 2: Data Layer

- [x] 2.1 Create `core/data/local/entity/WebhookDeliveryEntity.kt` (`internal`, redacting `toString()` per design.md).
- [x] 2.2 Create `core/data/local/dao/WebhookDeliveryDao.kt` (`internal`): insert(REPLACE), getById, getAllFailed, deleteById, updateFailure.
- [x] 2.3 Create `core/data/local/mapper/WebhookDeliveryMapper.kt` (`internal object`).
- [x] 2.4 Modify `core/data/local/entity/WebhookEntity.kt` + `mapper/WebhookMapper.kt`: add `lastDeliveryStatus`/`lastDeliveryAt` round-trip (discriminator string + nullable long).
- [x] 2.5 Modify `core/data/local/AppDatabase.kt`: add `WebhookDeliveryEntity`, `webhookDeliveryDao()`, bump `CURRENT_VERSION` 2→3, no `Migration`. (Found `CURRENT_VERSION` had an uncommitted stray edit back to `1` in the working tree pre-existing before this session; restored to the committed `2` baseline before bumping to `3`.)
- [x] 2.6 Modify `core/di/DatabaseModule.kt`: `provideWebhookDeliveryDao`.
- [x] 2.7 Create `core/data/repository/WebhookDeliveryRepositoryImpl.kt` (`internal`, IO dispatcher, `Result<T>`, via the shared `dbCatching` boundary in `RepositoryResult.kt` - the established TD-16 single-suppression convention, used instead of a per-method `toFailureResult()` catch to keep detekt's `TooGenericExceptionCaught` clean without a new baseline entry).
- [x] 2.8 Modify `core/data/repository/WebhookRepositoryImpl.kt`: implement `updateDeliveryStatus`.
- [x] 2.9 Modify `core/di/RepositoryModule.kt`: `bindWebhookDeliveryRepository`.

## Phase 3: WorkManager Infra

- [x] 3.1 Modify `gradle/libs.versions.toml` + `app/build.gradle.kts`: add `work-runtime-ktx`, `hilt-work`, `hilt-compiler` (ksp), `work-testing` (androidTest/test).
- [x] 3.2 Modify `MyApplication.kt`: implement `Configuration.Provider`, inject `HiltWorkerFactory`, keep `ImageLoaderFactory`; enqueue `WebhookRetrySweepWorker` in `onCreate` guarded once-per-process. **Deviation (as directed)**: the guarded `onCreate` block is in place but the actual `WorkManager.enqueueUniqueWork(...)` call is left as a one-line `TODO(webhook-delivery Unit 2, task 3.2)` comment, since `WebhookRetrySweepWorker` doesn't exist until Unit 2.
- [x] 3.3 Modify `AndroidManifest.xml`: remove default WorkManager initializer (`tools:node="remove"` on `WorkManagerInitializer` meta-data).
- [x] 3.4 Modify `MainActivity.kt`: drop "no WorkManager yet" TODO wording near the retention sweep.

**Unit 1 scope note**: task 5.1 (`ActionTypeUi.kt` `SEND_WEBHOOK` branch, label "Send webhook", `Icons.Default.Send`) was pulled forward from Phase 5 out of necessity - adding `ActionType.SEND_WEBHOOK` (1.1) makes every exhaustive `when` over `ActionType` a compile error, and `ActionTypeUi.ui()` was the only one in the codebase without an `else` branch. No other Phase 5 UI work (bottom sheet, config UI, indicator) was touched.
- [x] 3.5 Create `core/network/DeliveryResult.kt`: sealed interface (`Delivered`/`NetworkError`/`ServerError`/`ClientError`).
- [x] 3.6 Create `core/network/WebhookDeliveryClient.kt`: shares `NetworkModule` `OkHttpClient`, classifies outcome, never logs URL/headers/body/exception message.
- [x] 3.7 Create `core/notification/action/WebhookDeliveryEnqueuer.kt`: writes queue row + `WorkManager.enqueue(OneTimeWorkRequest(rowId), Constraints(CONNECTED))`. **Deviation**: `enqueue()` takes an optional `initialDelayMillis` param so `WebhookDeliveryWorker` can reuse it for the roadmap-locked `[1m, 5m, 30m]` server-retry schedule instead of duplicating enqueue logic.
- [x] 3.8 Create `core/notification/action/WebhookDeliveryWorker.kt`: `@HiltWorker CoroutineWorker`, classify + map to `Result`/queue/status update per design.md's Data Flow table, `withContext(@Dispatcher(IO))`. **Deviation**: added `WebhookDeliveryRepository.getById(id)` (+ impl) - the repository interface from Unit 1 didn't expose a single-row read, but the worker only receives a row id and needs to load the full row before posting.
- [x] 3.9 Create `core/notification/action/WebhookRetrySweepWorker.kt`: drains `pendingFailures()` on app open, re-enqueues each.
- [x] 3.2 (deferred half) Modified `MyApplication.kt`: replaced the guarded `onCreate` TODO with a real `WorkManager.enqueueUniqueWork(WebhookRetrySweepWorker.UNIQUE_WORK_NAME, KEEP, ...)` call now that the worker exists.

## Phase 4: Action Executor + Payload Builder

- [x] 4.1 Modify `domain/action/ActionExecutor.kt`: add `extractedFields: Map<String, String>` param to `execute(...)`.
- [x] 4.2 Modify `core/notification/action/ActionDispatcher.kt` + `ProcessNotificationUseCase.kt`: resolve `RuleMatch.extractedData` (id-keyed) to a name-keyed map via `rule.saveDataFields()`, thread it through `executeAll`.
- [x] 4.3 Modify `{Dismiss,Snooze,SaveData,FlashAlert,alarm/Alarm}ActionExecutor.kt`: accept (ignore) the new param. **Boy-scout fix**: `Dismiss`/`SnoozeActionExecutor.execute()` already had baseline-grandfathered `ReturnCount` violations; since this task touched both files anyway, rewrote both to a single `when`-expression return (both baseline entries removed, `config/detekt/baseline.xml` shrunk).
- [x] 4.4 Create `core/notification/action/WebhookPayloadBuilder.kt`: FIELDS mode (`buildJsonObject`, built-ins + `fields.<name>` nesting, drops fields whose id no longer exists) and TEMPLATE mode (`{{token}}` substitution, JSON-string-escaping, unknown token → empty string). **Deviation** (documented in the file's KDoc): design.md's id→name resolution for `field.<fieldId>` selections requires `rule.fields`, which this pure builder doesn't have access to (`ActionExecutor.execute`'s contract is only `(notification, action, extractedFields)`). Until Phase 5's Rule Editor UI/ViewModel exist to actually author these selections, the builder matches the suffix after `field.` directly against the already name-keyed `extractedFields` - forward-compatible once Phase 5 lands.
- [x] 4.5 Create `core/notification/action/SendWebhookActionExecutor.kt`: `SKIPPED` on missing `webhook_id`, else build payload + enqueue, return `SUCCESS`.
- [x] 4.6 Modify `core/di/ActionModule.kt`: `@Binds @IntoMap @ActionTypeKey(SEND_WEBHOOK)` for the new executor.

**Unit 2 scope note**: `./gradlew test` (unit test compilation) is left red by design per the task brief - `testutil/fakes/FakeWebhookRepository.kt` doesn't yet implement `updateDeliveryStatus`, and the 5 existing executor test files (`AlarmActionExecutorTest`, `DismissActionExecutorTest`, `FlashAlertActionExecutorTest`, `SaveDataActionExecutorTest`, `SnoozeActionExecutorTest`, `ActionDispatcherTest`) don't yet pass the new `extractedFields` param to `execute(...)`/`executeAll(...)`. Both are explicitly deferred to Phase 7 (task 7.8 and the executor-signature update) - too large a surface (6 test files, ~40 call sites) to fold into this unit without turning it into a test-writing pass. `./gradlew spotlessApply detekt architectureCheck :app:compileDebugKotlin` all green with zero new baseline entries (baseline net shrunk by 2 lines).

## Phase 5: Rule Editor UI

- [x] 5.1 Modify `features/ruleeditor/domain/ActionTypeUi.kt`: add `SEND_WEBHOOK` branch (label "Send webhook", `Icons.Default.Send`). Already complete from Unit 1's forced deviation (adding `ActionType.SEND_WEBHOOK` broke the exhaustive `when`) - verified label/icon/description match design.md, no changes needed.
- [x] 5.2 Create `features/ruleeditor/domain/WebhookConfigUiModel.kt`: `WebhookConfigMode` enum + `WebhookConfigUiModel` (webhookId, mode, selectedBuiltins, selectedFieldIds, template) - feature-owned, no `core.notification.action` imports.
- [x] 5.3 Create `features/ruleeditor/ui/WebhookConfigBottomSheet.kt` on the shared `ActionConfigSheet` scaffold: webhook picker (radio rows) from `WebhookConfigViewModel` observing `WebhookRepository.observeWebhooks()`; "New webhook" row navigates to `WebhookEditorScreen` via `NavigationHandler`; ViewModel auto-selects the newest not-previously-seen webhook id once the picker has nothing selected yet.
- [x] 5.4 In `WebhookConfigBottomSheet.kt`: `SingleChoiceSegmentedButtonRow` Fields/Template toggle + built-in/extracted-field checklist (FIELDS mode) or `OutlinedTextField` JSON template editor (TEMPLATE mode).
- [x] 5.5 In `WebhookConfigBottomSheet.kt`: `InsertFieldChipRow` (`SuggestionChip`s for built-ins + extracted fields) inserting `{{token}}` at the `TextFieldValue` cursor position in Template mode.
- [x] 5.6 In `WebhookConfigViewModel.kt`: `DEFAULT_CHECKED_BUILTINS` = title/content/app_name/timestamp; `raw_content`/`package_name` unchecked; all `ruleFields` ids pre-checked - applied in `initialize()` for a new (non-editing) action.
- [x] 5.7 In `WebhookConfigBottomSheet.kt`'s `FieldChecklistSection`: empty-state hint text when `uiState.ruleFields` is empty, exact copy per spec.
- [x] 5.8 In `WebhookConfigViewModel.kt`: `updatePreview()` builds `WebhookPayloadBuilder.build(...)` against a synthetic sample notification + per-field sample values, surfacing invalid-JSON (via `Json.parseToJsonElement` catch) and unknown-`{{token}}` (TEMPLATE mode only) warnings in the sheet's Preview card.
- [x] 5.9 **Deviation**: created a dedicated `features/ruleeditor/contract/WebhookConfigContract.kt` + `features/ruleeditor/viewmodel/WebhookConfigViewModel.kt` (own MVI, mirroring `AlarmContract`/`AlarmViewModel`) instead of modifying the already-576-line `RuleEditorViewModel.kt`/`RuleEditorContract.kt`. The `WebhookConfigUiModel` ↔ flat config map mapping lives in `WebhookConfigContract.kt` (`toRuleAction()`/`toWebhookConfigUiModel()`), matching every other type-scoped action sheet's existing pattern (Alarm/Flash/ExtractData each own their sheet's state, none of them touch `RuleEditorViewModel`). "Reopen with existing config" already worked for free via `RuleEditorContract.UiState.editingAction` + the existing `initial: RuleAction?` parameter every sheet already takes - only `RuleEditorScreen.kt`'s `when` block needed a new `SEND_WEBHOOK` branch.
- [x] 5.10 Modified `features/webhook/ui/WebhookListScreen.kt` only: `DeliveryStatusRow` renders the tri-state indicator (icon + label + relative timestamp via `DateUtils.getRelativeTimeSpanString`) under each `WebhookListItem`, reading `Webhook.lastDeliveryStatus`/`lastDeliveryAt` directly (already present on the domain model since Unit 1) - renders nothing for `UNKNOWN`. **Deviation**: no `contract`/`viewmodel` changes needed - `WebhookListContract.UiState` already carries full `Webhook` objects, so the new fields were already in scope; the task description's "modify contract,viewmodel,ui" was written before Unit 1 confirmed the fields' shape.

**Unit 3 scope note**: `./gradlew spotlessApply detekt architectureCheck :app:compileDebugKotlin` all green, zero new baseline entries. `./gradlew compileDebugUnitTestKotlin` still fails for the exact same pre-existing reasons flagged in Unit 2's note (5 executor tests + `ActionDispatcherTest` missing the `extractedFields` param, `FakeWebhookRepository` missing `updateDeliveryStatus`) - untouched by this unit, deferred to Phase 7/Unit 4 as directed. Also refactored (Unit 2 file, safe/behavior-preserving): promoted `WebhookPayloadBuilder.kt`'s private `BUILTIN_*` consts + `TOKEN_REGEX` to public `WEBHOOK_BUILTIN_*`/`WEBHOOK_ALL_BUILTINS`/`WEBHOOK_TOKEN_REGEX` in `WebhookActionConfig.kt` so the new UI/ViewModel layer shares the same token literals instead of duplicating them.

## Phase 6: Docs

- [x] 6.1 Modify `docs/adr/012-*.md`: add retry/queue/status-update addendum.
- [x] 6.2 Modify `PRIVACY.md`: add retry/queue-storage addendum (queued payloads treated like `authValue` — SQLCipher at-rest, never logged).

## Phase 7: Testing

- [x] 7.1 Write `WebhookPayloadBuilderTest`: FIELDS shape (only selected keys, `fields.<name>` nesting, dropped-field-id case), TEMPLATE substitution (built-ins + `{{field.<name>}}`), JSON-escaping of `"`/newline values, unknown token → empty.
- [x] 7.2 Write `WebhookDeliveryClientTest` / classifier test: 200/201→Delivered, 500/408/429→ServerError, 400/401/404→ClientError, `IOException`→NetworkError.
- [x] 7.3 Write `WebhookDeliveryWorkerTest` using `work-testing`'s `TestListenableWorkerBuilder`: Delivered path, Client→fail-fast, Server<3→re-enqueue, Server=3→`UNREACHABLE`, Network→`Result.retry()`. **Deviation**: `TestListenableWorkerBuilder`'s default `WorkerFactory` reflection path only supports a plain `(Context, WorkerParameters)` worker constructor; `WebhookDeliveryWorker`'s real `@AssistedInject` constructor takes 4 extra collaborators, so the test supplies a small anonymous `WorkerFactory` that constructs the worker directly with mocked collaborators, per design.md's Testing Strategy note ("mock repos/client, injected `StandardTestDispatcher`").
- [x] 7.4 Write `WebhookDeliveryRepositoryImplTest`: enqueue/markFailed/pendingFailures entity↔domain mapping, dao exception → `Result.failure`.
- [x] 7.5 Write `SendWebhookActionExecutorTest`: missing `webhook_id` → `SKIPPED`; happy path builds payload + enqueues once.
- [x] 7.6 **Deviation**: wrote `WebhookConfigViewModelTest` (not `RuleEditorViewModelTest`) additions - mode toggle, field selection, template edit persist to config; preview surfaces unknown-token warning; picker reflects observed webhooks. Per Unit 3's task 5.9 deviation, the webhook config sheet owns a dedicated `WebhookConfigViewModel`/`WebhookConfigContract`, not `RuleEditorViewModel` - that's the correct test target given how Unit 3 was actually implemented (directed in the Unit 4 brief).
- [x] 7.7 Extended the no-log Timber-probe test (`WebhookNoLogTest.kt`): added cases exercising `WebhookDeliveryClient` (network error + invalid request config), `WebhookDeliveryWorker` (client-error failure path via a `WorkerFactory`-built instance), and `WebhookDeliveryRepositoryImpl` (dao exception during enqueue) failure paths with the existing fixed sentinel secret in payload + `authValue`, asserting it never appears in any log/throwable.
- [x] 7.8 Updated `testutil/fakes/FakeWebhookRepository.kt` for `updateDeliveryStatus`; added `FakeWebhookDeliveryRepository.kt`. Also fixed the Unit 2/3-deferred compile breakage ahead of writing new tests: `ActionDispatcherTest`'s two inline `ActionExecutor` fakes plus 5 existing executor test files (`AlarmActionExecutorTest`, `DismissActionExecutorTest`, `FlashAlertActionExecutorTest`, `SaveDataActionExecutorTest`, `SnoozeActionExecutorTest`) now pass the new `extractedFields` param (empty map - irrelevant to those tests' scenarios).

**Unit 4 scope note**: fixing the above compile breakage also surfaced 3 latent runtime test failures (blocked from ever running by the compile break, so undetected until now), all caused by Unit 1's `ActionType.SEND_WEBHOOK` addition, not by Unit 4's own changes: (1)/(2) `RuleJsonCodecTest`/`RulesViewModelTest` each used the string `"send_webhook"` as a stand-in for "an action type this app version doesn't recognize" - now a real recognized type, so both were changed to use a clearly-fake `"some_future_action_type"` sentinel instead, preserving the original intent (unrecognized-action-drop behavior) independent of real `ActionType` membership. (3) `RuleTemplatesTest`'s curation-gap guard ("templates together cover every action type at least once") failed because no curated starter template exercised `SEND_WEBHOOK` yet - fixed by adding a second, disabled `send_webhook` action to `assets/rules/package-delivery-tracker.json` (FIELDS mode, `title` + the tracking-number field; `isEnabled: false` since a fresh install has no webhook to target yet, matching the disabled-action-is-fully-excluded semantics `ActionDispatcherTest` already locks in) rather than fabricating an entirely new hero-use-case template.

## Phase 8: Manual Verification

- [x] 8.1 Run `./gradlew spotlessApply detekt architectureCheck test` and confirm all green. 711 tests, 0 failures.
- [ ] 8.2 Manually trigger a rule with a `SEND_WEBHOOK` action against a local test endpoint; confirm POST fires, retry/backoff behaves per classification, and app-restart drains the queue.
