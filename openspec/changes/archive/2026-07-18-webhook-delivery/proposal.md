# Proposal: Webhook Delivery (Phase 4 PR2)

## Intent

PR1 shipped webhook CRUD + a test-payload button but left webhooks inert — no rule can actually send one. This slice wires webhooks into the rule engine as a `SEND_WEBHOOK` action, fires the POST on rule match, and makes delivery *reliable* and *visible*: retries via WorkManager (the app's first use) and a failed-delivery queue, plus a per-webhook last-delivery indicator so silent failure — the most trust-destroying behavior for an automation tool — never happens.

## Scope

### In Scope
- `ActionType.SEND_WEBHOOK` + flat `WebhookActionConfig` (webhook id, payload mode, field checklist, custom JSON template) following the `FlashActionConfig` key-constant + extension-reader convention.
- `SendWebhookActionExecutor` (mirrors `FlashAlertActionExecutor`; ActionDispatcher multibinding).
- Rule Editor: `ActionTypeUi.SEND_WEBHOOK` branch + `ActionConfigSheet` branch — webhook picker, inline-create, payload-mode toggle, `{{field}}` token support, "Preview payload".
- **Both payload modes now**: (a) raw field checklist; (b) custom JSON template with `{{field}}` tokens — avoids a second config-shape migration.
- WorkManager infra: gradle deps, `HiltWorkerFactory` + `Configuration.Provider` in `MyApplication`, `CoroutineWorker` for delivery/retry.
- **3-way retry classification** (see Decisions).
- Failed-delivery Room table (mirrors WebhookEntity/DAO) + retry-on-app-open trigger in `MyApplication.onCreate()`; `AppDatabase` 2→3 clean bump.
- New `Webhook`/`WebhookEntity` fields: `lastDeliveryStatus`, `lastDeliveryAt`; last-delivery indicator on webhook list (delivered / config error / unreachable).
- ADR 012 status-update addendum, PRIVACY.md retry/queue-storage addendum.
- Unit tests: executor, payload builder, retry classification, queue; net-new `work-testing` infra.

### Out of Scope
- Queue max-size / max-age eviction (unbounded for MVP — deferred as speculative).
- AI/natural-language payload authoring (Phase 5).
- Webhook delivery to anything other than the rule-matched notification (no batching/aggregation).
- Non-JSON content types, response-body parsing, bidirectional webhooks.

## Capabilities

### New Capabilities
- `webhook-delivery`: fire webhook POST on rule match, classify failures, retry via WorkManager, persist a failed-delivery queue, retry on app open.

### Modified Capabilities
- `action-execution`: add `SEND_WEBHOOK` executor to the dispatched action set.
- `rule-action-authoring`: add Send Webhook config (picker, inline-create, payload modes, preview).
- `webhook-management`: add per-webhook last-delivery status/timestamp fields + list indicator.

## Decisions (product/architecture calls)

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Both payload modes (field checklist + custom JSON `{{token}}` template) ship now | User chose to avoid a second `WebhookActionConfig` shape migration later. |
| 2 | Flat `Map<String,String>` config + key constants + extension readers | Matches `FlashActionConfig`; NOT the JSON-polymorphic `rule_conditions.payload` pattern. |
| 3 | 3-way retry classification, not one uniform policy | Network vs server vs client failures need different handling — see below. |
| 4 | Network errors → `Constraints(NetworkType.CONNECTED)`, not timed retries | Don't burn attempts while offline; fire immediately when connectivity returns. |
| 5 | Server errors (5xx/timeout) → exponential backoff 1m/5m/30m, 3 attempts | Roadmap policy; genuinely transient on a reachable server. |
| 6 | Client errors (4xx) → fail fast to queue after one attempt, marked "config error" | Bad URL/token/payload isn't transient; distinct status lets the indicator differentiate config error from unreachable. |
| 7 | Failed-delivery queue unbounded for MVP | Eviction policy is speculative; user explicitly deferred. |
| 8 | `AppDatabase` 2→3 clean bump, no Migration | Pre-launch destructive policy (CLAUDE.md); no installed users. |
| 9 | Queue rows treated like `authValue`: SQLCipher at-rest + never logged | Payloads may carry extracted sensitive fields until retried. |

## Approach

`SendWebhookActionExecutor` builds the JSON payload (field checklist or `{{token}}` template) from the matched notification + extracted fields, then enqueues a WorkManager `CoroutineWorker` (Hilt-injected `WebhookTestClient`/OkHttp). The worker classifies the outcome (network / server / client) per Decisions 4–6 and updates `lastDeliveryStatus`/`lastDeliveryAt`; exhausted or fail-fast deliveries persist to the failed-delivery table. `MyApplication` gains `Configuration.Provider` + a retry-on-app-open sweep (resolving the existing "no WorkManager yet" TODO near `MainActivity` retention sweep). Rule Editor adds the config branch; webhook list reads the new status fields for a tri-state indicator.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/model/RuleAction.kt` (`ActionType`) | Modified | Add `SEND_WEBHOOK` |
| `core/notification/action/WebhookActionConfig.kt` | New | Flat config + readers |
| `core/notification/action/SendWebhookActionExecutor.kt` | New | Executor + payload builder |
| `core/notification/action/*Worker*` (WorkManager) | New | Delivery `CoroutineWorker` |
| `core/data/local/{entity,dao,mapper}` (failed-delivery) | New | Queue table |
| `domain/model/Webhook.kt`, `core/data/local/entity/WebhookEntity.kt` | Modified | `lastDeliveryStatus`, `lastDeliveryAt` |
| `core/data/local/AppDatabase.kt` | Modified | Add entity, bump 2→3 |
| `features/ruleeditor/domain/ActionTypeUi.kt`, `ui/components/ActionConfigSheet.kt` | Modified | Config UI |
| `features/webhook/` (list) | Modified | Tri-state indicator |
| `MyApplication.kt` | Modified | `Configuration.Provider` + retry sweep |
| `MainActivity.kt` | Modified | Drop "no WorkManager yet" TODO |
| `core/di/*` | Modified | Worker/DAO/repo wiring |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modified | WorkManager + work-testing |
| `docs/adr/012-*`, `PRIVACY.md` | Modified | Retry/queue addenda |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| WorkManager + HiltWorkerFactory misconfiguration (first use) | Med | `Configuration.Provider` wiring covered by manual + work-testing checks |
| Queued payloads leak sensitive extracted data | Med | SQLCipher at-rest + no-log rule (decision 9) |
| Retry misclassification retries a permanent 4xx forever | Med | Explicit 3-way classifier, fail-fast on 4xx (decisions 4–6) |
| Unbounded queue growth | Low | Accepted for MVP; app-open retry drains delivered rows |
| Scope creep (eviction, AI payloads) | Med | Hard out-of-scope list |
| Release crash from version bump | Low | No migration on fresh install (decision 8) |

## Rollback Plan

Revert the PR. New files (executor, worker, queue table, config) are self-contained; shared edits (AppDatabase version, DI, `ActionType`, Rule Editor branches, `MyApplication`, manifest, gradle) revert cleanly. No production data exists (pre-launch), so dropping the failed-delivery table and reverting the Webhook field additions is safe.

## Dependencies

- WorkManager (new runtime dep) + `androidx.work:work-testing` (new test dep). Builds on PR1's `webhook-management`.

## Success Criteria

- [ ] A rule with a Send Webhook action fires a real POST on match with the selected/templated payload.
- [ ] Payload preview renders correctly for both field-checklist and `{{token}}` template modes.
- [ ] Network failures wait for connectivity; 5xx retry with backoff; 4xx fail fast to the queue.
- [ ] Failed deliveries persist and retry on app open until delivered.
- [ ] Webhook list shows delivered / config-error / unreachable per webhook with timestamp.
- [ ] Queued payloads never appear in logs.
- [ ] ADR 012 + PRIVACY.md updated for retry/queue storage.
- [ ] `./gradlew spotlessApply detekt architectureCheck test` all green.
