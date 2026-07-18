# Proposal: Webhook Management (Phase 4 PR1)

## Intent

Home Assistant / self-hosting users want rules to push extracted data to their own services. This first slice delivers webhook CRUD + the network foundation so a user can define, edit, delete, and *validate* a webhook before any rule wiring exists. It is also the point where the app makes its first network egress, so the local-first privacy contract must be updated here — not deferred.

## Scope

### In Scope
- `Webhook` domain model: id, name, url, headers `Map<String,String>`, authType, authValue, `authHeaderName`.
- Room entity + DAO + `WebhookRepository` (interface/impl), mirroring SelectedApp; `AppDatabase` version bump.
- Webhook Editor screen (name, URL, header key/value rows, auth: None / API-key header / Bearer).
- Webhook List screen with edit/delete, reachable from a new Settings entry point.
- "Send test payload" button: fixed generic sample JSON via one live network call (no retry/queue).
- `INTERNET` permission, ADR 012 disclosure + PRIVACY.md update, ADR 005 amendment note.
- Unit tests: repository, mapper, both ViewModels.

### Out of Scope
- Rule-action integration, `ActionType.SEND_WEBHOOK`, payload field selection, WorkManager, retry/backoff, delivery queue, per-webhook last-status — all reserved for PR2.

## Capabilities

### New Capabilities
- `webhook-management`: define/list/edit/delete user webhooks with headers + auth, and validate them via a test payload.

### Modified Capabilities
- None (spec-level). ADR 005/012 doc notes are non-spec edits.

## Decisions (product/architecture calls)

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Plain OkHttp `Call`, no Retrofit | URL is fully dynamic/user-entered; Retrofit's static `@POST` + converters add machinery for a single arbitrary-URL call. Overrides roadmap's "Retrofit+OkHttp" phrasing. |
| 2 | ADR 012 disclosure + PRIVACY.md + `INTERNET` land in THIS PR; add ADR 005 amendment note | Test-payload button is the app's first real egress; disclosure must ship with the capability that introduces it. |
| 3 | Add `authHeaderName` field (defaults `X-API-Key`) | Self-hosters use varied header names; a field costs little and avoids a breaking change in PR2. |
| 4 | Bump `AppDatabase.CURRENT_VERSION` 1→2, no Migration | Pre-launch destructive policy; fresh release installs create v2 directly (no migration runs), dev v1 DBs handled by debug destructive fallback. |
| 5 | Dedicated `WebhookMapper.kt` | Matches the current `Rule*` convention per ADR 005/CLAUDE.md; overrides the older inline SelectedApp style. |
| 6 | Reuse `RuleTypeConverters` map converter for headers | Already JSON-serializes `Map<String,String>`; a separate converter adds no isolation value. |
| 7 | `authValue` relies on existing SQLCipher at-rest encryption (DATA-02); no per-field crypto | Sufficient for MVP. Add safeguards: never log `authValue`, mask it in the UI field. |

## Approach

New `core/network/` package (first network layer) hosts a thin OkHttp client + `NetworkModule`. Data/domain follow SelectedApp conventions with `internal` visibility (architectureCheck rule 1) and injected IO dispatcher (ADR 008). `features/webhook/` mirrors `features/appselection/` MVI structure. Settings gains a "Webhooks" card + `NavigateToWebhookList` effect; navigation adds `Screen.WebhookList` / `Screen.WebhookEditor(webhookId?)`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/model/Webhook.kt`, `domain/repository/WebhookRepository.kt` | New | Model + contract |
| `core/data/local/{entity,dao,mapper}` | New | Entity, DAO, mapper |
| `core/data/local/AppDatabase.kt` | Modified | Add entity, bump version 1→2 |
| `core/data/repository/WebhookRepositoryImpl.kt` | New | Result-based impl |
| `core/network/` + `core/di/NetworkModule.kt` | New | OkHttp client + Hilt |
| `core/di/{Database,Repository}Module.kt` | Modified | DAO provide + binding |
| `features/webhook/` | New | Editor + List MVI |
| `core/ui/navigation/{Screen,Routes}.kt`, `MainActivity.kt` | Modified | Routes + entries |
| `features/settings/*` | Modified | Webhooks entry point |
| `AndroidManifest.xml` | Modified | `INTERNET` permission |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modified | OkHttp dep |
| `docs/adr/012-*`, `docs/adr/005-*`, `PRIVACY.md` | Modified | Network disclosure |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| First network dep expands attack surface / F-Droid concerns | Med | Egress only on explicit user tap; disclosed in ADR 012 + PRIVACY.md |
| Secret leakage via logs/UI | Med | No-log rule + masked field (decision 7) |
| Release crash from version bump | Low | No migration runs on fresh install; confirmed decision 4 |
| Scope creep into PR2 | Med | Hard out-of-scope list |

## Rollback Plan

Revert the PR. New files are self-contained; the only shared edits (AppDatabase version, DI modules, navigation, manifest, Settings) revert cleanly. No production data exists (pre-launch), so dropping the `webhook` table is safe.

## Dependencies

- OkHttp (new). No other prerequisites.

## Success Criteria

- [ ] User can create, list, edit, delete a webhook with headers + auth.
- [ ] "Send test payload" performs a real POST and reports success/failure.
- [ ] `authValue` never appears in logs and is masked in UI.
- [ ] ADR 012 + PRIVACY.md disclose network egress; ADR 005 note added.
- [ ] Repository, mapper, and both ViewModels covered by unit tests.
- [ ] `./gradlew spotlessApply detekt architectureCheck test` all green.
