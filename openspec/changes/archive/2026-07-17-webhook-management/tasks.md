# Tasks: Webhook Management (Phase 4 PR1)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | ~1300-1600 (mostly new files) |
| 400-line budget risk | High |
| Chained PRs recommended | Yes (but overridden by delivery strategy) |
| Suggested split | PR1a: domain+data+network; PR1b: UI+nav; PR1c: tests+docs |
| Delivery strategy | single-pr |
| Chain strategy | size-exception |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: size-exception
400-line budget risk: High

### Suggested Work Units (not used — single-pr chosen; kept for reference only)

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Domain + Room + Repository + Network client | PR 1 | Foundation, no UI |
| 2 | Feature UI (List + Editor) + Navigation + Settings entry | PR 2 | Depends on Unit 1 |
| 3 | Tests + docs (PRIVACY.md/ADR) + manual verification | PR 3 | Depends on Unit 1-2 |

Per `single-pr`, orchestrator must require `size:exception` from the maintainer before `sdd-apply` proceeds, given the High risk above.

## Phase 1: Foundation

- [x] 1.1 Add OkHttp to `gradle/libs.versions.toml` and `app/build.gradle.kts` (no Retrofit).
- [x] 1.2 Add `<uses-permission android:name="android.permission.INTERNET"/>` and `android:networkSecurityConfig` attribute to `AndroidManifest.xml`.
- [x] 1.3 Create `res/xml/network_security_config.xml` with `cleartextTrafficPermitted="true"` base-config.
- [x] 1.4 Create `domain/model/Webhook.kt`: `Webhook` data class + `WebhookAuth` sealed interface (None/ApiKeyHeader/BearerToken), each with redacting `toString()` overrides per design.md.
- [x] 1.5 Add `Webhook.validate(): List<ValidationError>` (blank name, malformed URL via `java.net.URI`, header/auth-name collision case-insensitive).
- [x] 1.6 Create `domain/repository/WebhookRepository.kt` interface (observeWebhooks/getWebhook/saveWebhook/deleteWebhook/sendTestPayload).

## Phase 2: Data Layer

- [x] 2.1 Create `core/data/local/entity/WebhookEntity.kt` (`internal`, columns per design.md, redacting `toString()` override).
- [x] 2.2 Create `core/data/local/dao/WebhookDao.kt` (`internal`, getAll Flow, getById, insert REPLACE, delete, deleteById).
- [x] 2.3 Create `core/data/local/mapper/WebhookMapper.kt` (`internal object`, entity↔domain, `WebhookAuth` flatten/reconstruct).
- [x] 2.4 Modify `core/data/local/AppDatabase.kt`: add `WebhookEntity`, `webhookDao()`, no `Migration`.
- [x] 2.5 Modify `core/di/DatabaseModule.kt`: `provideWebhookDao`.
- [x] 2.6 Create `core/data/repository/WebhookRepositoryImpl.kt` (`internal`, IO dispatcher, `Result<T>`, `saveWebhook` runs `Webhook.validate()` defense-in-depth via `Failure.ApplicationException`).
- [x] 2.7 Modify `core/di/RepositoryModule.kt`: `bindWebhookRepository`.

## Phase 3: Network Layer

- [x] 3.1 Create `core/network/WebhookTestResult.kt` (sealed interface: Success/MalformedBody/ServerError/NetworkError/InvalidHeaderValue).
- [x] 3.2 Create `core/network/WebhookTestClient.kt`: POST via OkHttp `Call`, apply headers+auth, fixed sample JSON body, read body once for empty-vs-parse-failure distinction, local try/catch for `IllegalArgumentException`/`IOException`/non-2xx (never touches `Failure.analyzeCause`).
- [x] 3.3 Create `core/di/NetworkModule.kt`: `@Provides @Singleton OkHttpClient` with 10s timeouts, no logging interceptor.

## Phase 4: Feature UI

- [x] 4.1 Create `features/webhook/contract/WebhookListContract.kt` (State/Event/Effect per design.md).
- [x] 4.2 Create `features/webhook/contract/WebhookEditorContract.kt` including `headerRows`, duplicate-key validation error field.
- [x] 4.3 Create `features/webhook/viewmodel/WebhookListViewModel.kt` (`@HiltViewModel`, `MviViewModel`, injects `WebhookRepository`+`NavigationHandler`+IO dispatcher).
- [x] 4.4 Create `features/webhook/viewmodel/WebhookEditorViewModel.kt`: preserve loaded `id` on save, case-insensitive duplicate-header-key check against `headerRows` before `OnSave`, `isSending` re-entrancy guard on `OnSendTestClicked`.
- [x] 4.5 Create `features/webhook/ui/WebhookListScreen.kt` (Compose/M3, edit/delete with confirmation).
- [x] 4.6 Create `features/webhook/ui/WebhookEditorScreen.kt`: header rows, auth type selector, `authValue` field with `PasswordVisualTransformation`, "Send test payload" button, `CollectOneOffEffects` for `ShowTestResult`.

## Phase 5: Navigation Wiring

- [x] 5.1 Modify `core/ui/navigation/Screen.kt`: add `WebhookList`, `WebhookEditor(webhookId: String? = null)`.
- [x] 5.2 Modify `core/ui/navigation/Routes.kt`: add `webhookList()`, `webhookEditor(id?)` factories.
- [x] 5.3 Modify `MainActivity.kt`: add two `entry<>` blocks for the new screens.
- [x] 5.4 Modify `features/settings/{contract,viewmodel,ui}`: add `OnWebhooksClicked` event, `NavigateToWebhookList` effect, "Webhooks" settings card.

## Phase 6: Testing

- [x] 6.1 Create `testutil/fakes/FakeWebhookRepository.kt` mirroring `FakeSelectedAppRepository`.
- [x] 6.2 Write `WebhookRepositoryImplTest`: CRUD success mapping, dao exception → `Result.failure`, `sendTestPayload` delegates to mocked `WebhookTestClient`.
- [x] 6.3 Write `WebhookMapperTest`: round-trip each `WebhookAuth` variant (None/ApiKeyHeader default+custom header name/BearerToken), headers map preserved.
- [x] 6.4 Write `WebhookListViewModelTest`: observe emits mapped list, delete calls repo, add/edit emit navigate effect.
- [x] 6.5 Write `WebhookEditorViewModelTest`: field edits, save persists+navigates back, send-test success/failure emit `ShowTestResult`, validation blocks blank name/malformed URL, edit preserves `id` (no new UUID), duplicate header key rejected case-insensitively including collision with active auth header name.
- [x] 6.6 Write the automated no-log Timber-probe test: install a probe `Tree`, exercise `WebhookRepositoryImpl`/`WebhookTestClient` failure paths (dao exception, malformed header value, network error) with a fixed sample `authValue`, assert it never appears in any captured message/throwable/`toString()`.

## Phase 7: Docs Verification

- [x] 7.1 Confirm `PRIVACY.md` has the new "Network access" section, rewritten "Future changes" paragraph, and qualified "No data transmission off your device" bullet (design.md already specifies exact text — verify it's applied, don't re-author).
- [x] 7.2 Confirm `docs/adr/012-*.md` has the realized-egress status note and `docs/adr/005-*.md` has the local-first amendment note.

## Phase 8: Manual Verification

- [x] 8.1 Run `./gradlew spotlessApply detekt architectureCheck test` and confirm all green.
