# Design: Webhook Management (Phase 4 PR1)

## Technical Approach

Webhook CRUD + connection infrastructure only. Data/domain follow the `SelectedApp` conventions (entity/dao/repository/mapper, `internal` visibility per architectureCheck rule 1, `Result<T>` per ADR 006, injected IO dispatcher per ADR 008). A new `core/network/` package hosts a thin OkHttp `Call` wrapper for the "send test payload" action — the app's first network egress. `features/webhook/` mirrors `features/appselection/` MVI. No rule-action wiring, WorkManager, retry, or queue (PR2).

## Architecture Decisions

| Decision | Choice | Rejected | Rationale |
|---|---|---|---|
| Network client | Plain OkHttp `Call` in `core/network/WebhookTestClient.kt` | Retrofit | URL is fully user-entered/dynamic; Retrofit's static `@POST`+converters add machinery for one arbitrary-URL call. Overrides roadmap "Retrofit+OkHttp" phrasing. |
| DB version | Bump `CURRENT_VERSION` 1→2, **no** `Migration` | Hand-written migration | Pre-launch destructive policy; fresh installs create v2 directly, dev DBs use existing debug destructive fallback. |
| Headers persistence | Reuse `RuleTypeConverters.fromStringMap/toStringMap` | New converter | Already JSON-serializes `Map<String,String>`; converters are registered at DB level via `@TypeConverters(RuleTypeConverters::class)` on `AppDatabase`, so `WebhookEntity` inherits it — no per-entity annotation needed. |
| authType | Sealed class `WebhookAuth` (None/ApiKeyHeader/BearerToken) | Enum + nullable fields | Sealed keeps `authHeaderName`/`authValue` scoped to the variants that use them; persisted as a discriminator string + two nullable columns. |
| Mapper | Dedicated `internal object WebhookMapper` in `WebhookMapper.kt` | Inline ext funcs | Matches current `Rule*` convention (ADR 005); `internal` per architectureCheck rule 1, consistent with all existing `Rule*Mapper` objects. |
| authValue crypto | Rely on existing SQLCipher at-rest (DATA-02) | Per-field crypto | Sufficient for MVP. Safeguards: never log, mask in UI. |
| Cleartext HTTP | Allow via `res/xml/network_security_config.xml` (`cleartextTrafficPermitted="true"`) | Force `https://` only | minSdk 26; Android blocks cleartext traffic by default via Network Security Config since API 28, so without an explicit config any `http://` webhook URL would throw `CLEARTEXT_NOT_PERMITTED` — a very common case for self-hosted LAN targets (e.g. Home Assistant). Since destinations are arbitrary user-entered URLs, both `http` and `https` are allowed; validation (see Validation section) only checks scheme is one of the two, no stricter recommendation. |

## Domain Model & Contracts

```kotlin
// domain/model/Webhook.kt
data class Webhook(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val auth: WebhookAuth = WebhookAuth.None,
    val createdAt: Long = System.currentTimeMillis(),
) {
    // Hard requirement (no-log guarantee): the default data class toString()
    // would print `headers`/`auth` verbatim, including any authValue. Override
    // toString() to redact both, e.g.:
    //   override fun toString() = "Webhook(id=$id, name=$name, url=$url, " +
    //       "headers=${headers.mapValues { "REDACTED" }}, auth=$auth)"
    // relying on WebhookAuth's own toString() override (below) for the auth part.
}
sealed interface WebhookAuth {
    data object None : WebhookAuth
    data class ApiKeyHeader(val headerName: String = "X-API-Key", val value: String) : WebhookAuth {
        // MUST override toString() to print "ApiKeyHeader(headerName=$headerName, value=REDACTED)" —
        // never interpolate `value` directly.
    }
    data class BearerToken(val value: String) : WebhookAuth {
        // MUST override toString() to print "BearerToken(value=REDACTED)" — never interpolate `value` directly.
    }
}
```

**No-log guarantee (hard design requirement, not a testing footnote):** `Webhook` and the `WebhookAuth.ApiKeyHeader`/`BearerToken` variants MUST override `toString()` to substitute the literal string `"REDACTED"` for `authValue`/`value`. `Webhook.toString()` MUST ALSO redact `headers` values — print header names but mask values, e.g. `"headers={key1=REDACTED, key2=REDACTED}"` — so a secret pasted directly into a custom header row (e.g. `Authorization: Bearer <token>`, instead of the dedicated auth mechanism) gets the same defense-in-depth as `authValue`. `WebhookEntity` (the Room entity holding `auth_value` as a plain column) MUST override `toString()` with the same redaction as `Webhook` — the no-log guarantee and its automated test (see Testing Strategy) cover `WebhookEntity` as well, not just the domain type, since a future maintainer logging the entity instead of a scalar field in a DAO/repository catch block would otherwise leak the plaintext secret via its default `toString()`. This is required because the default data-class-generated `toString()` would otherwise print the plaintext secret; the redaction is defense-in-depth against a future maintainer deviating from the existing per-field logging convention (e.g. `Timber.e(e, "Failed to get app: $packageName")` in `SelectedAppRepositoryImpl`, which logs a scalar field, never a whole object) — should a future change log a whole `Webhook`/`WebhookEntity` instead of a scalar field, redaction prevents a leak. In addition, `WebhookTestClient` MUST catch `IllegalArgumentException` thrown by OkHttp's `Headers.Builder.add()` (raised when a header value contains disallowed characters, e.g. a stray newline/tab in a pasted token) as its own case, logging a fixed, scrubbed message (e.g. `Timber.e("Invalid header value for webhook %s", webhook.id)`, never `Timber.e(cause)` or the exception's `message`) and returning `WebhookTestResult.InvalidHeaderValue` — see the sealed `WebhookTestResult` definition and Data Flow section below for why this never touches `Failure.analyzeCause`.

`WebhookEntity` columns: `id`(PK), `name`, `url`, `headers`(String via converter), `auth_type`(String: NONE/API_KEY/BEARER), `auth_header_name`(String?), `auth_value`(String?), `created_at`. `WebhookMapper` flattens/reconstructs the sealed `WebhookAuth`.

`WebhookDao` mirrors `SelectedAppDao`: `getAll(): Flow<List<WebhookEntity>>`, `getById(id): WebhookEntity?`, `insert`(REPLACE), `delete`, `deleteById(id)`. No separate `update` method: `SelectedAppRepositoryImpl` (the pattern being mirrored) never calls `.update()` on its DAO in practice, so it would be dead code here too — `saveWebhook` always calls `dao.insert()` with `OnConflictStrategy.REPLACE` for both create and edit.

```kotlin
// domain/repository/WebhookRepository.kt
interface WebhookRepository {
    fun observeWebhooks(): Flow<List<Webhook>>
    suspend fun getWebhook(id: String): Result<Webhook?>
    suspend fun saveWebhook(webhook: Webhook): Result<Unit>   // insert-or-update
    suspend fun deleteWebhook(id: String): Result<Unit>
    suspend fun sendTestPayload(webhook: Webhook): Result<WebhookTestResult>
}
```

`WebhookRepositoryImpl` (core/data/repository, `internal`, `@Inject`, IO dispatcher, `try/catch → e.toFailureResult()`) delegates persistence to `WebhookDao` and `sendTestPayload` to `WebhookTestClient`.

```kotlin
// core/network/WebhookTestClient.kt (internal)
internal class WebhookTestClient @Inject constructor(
    private val client: OkHttpClient,
    @Dispatcher(DispatcherType.IO) private val io: CoroutineDispatcher,
) {
    // post() catches ALL relevant exceptions locally in its own try/catch and NEVER
    // throws or delegates to Result<T>/Failure.analyzeCause — it always returns a
    // WebhookTestResult directly, so the Failure hierarchy is not involved in this
    // flow at all:
    //   - IllegalArgumentException from Headers.Builder.add() (disallowed characters
    //     in a header value) -> WebhookTestResult.InvalidHeaderValue, without logging
    //     the raw exception message (see no-log guarantee above)
    //   - IOException (no connection/timeout) -> WebhookTestResult.NetworkError
    //   - non-2xx HTTP status -> WebhookTestResult.ServerError
    //   - 2xx response with a non-empty body that fails to parse as JSON ->
    //     WebhookTestResult.MalformedBody (a 2xx response with an EMPTY body is
    //     WebhookTestResult.Success — see below)
    suspend fun post(webhook: Webhook): WebhookTestResult
}

// core/network/WebhookTestResult.kt
sealed interface WebhookTestResult {
    data class Success(val httpCode: Int) : WebhookTestResult
    data object MalformedBody : WebhookTestResult // 2xx status, non-empty body that fails to parse as JSON
    data class ServerError(val httpCode: Int) : WebhookTestResult // non-2xx status
    data object NetworkError : WebhookTestResult // IOException, e.g. no connection/timeout
    data object InvalidHeaderValue : WebhookTestResult // IllegalArgumentException from Headers.Builder.add()
}
```

`WebhookTestClient.post()` MUST NOT rely on HTTP status code alone: spec.md has a MUST-level scenario requiring a malformed/non-JSON response body to be treated as a distinct failure from a bare success. "Empty" MUST be determined by reading the response body once as a string (e.g. `response.body?.string()`) and checking `.isNullOrBlank()`/`.isEmpty()` on that string — never by trusting `Content-Length`/`response.body?.contentLength()`, since a chunked response (`contentLength() == -1`, common with reverse proxies in front of Home Assistant) would otherwise bypass this check and be misrouted into a parse attempt. After receiving a 2xx response, the client reads the body string once: if it is blank, the result is `WebhookTestResult.Success`; otherwise it attempts to parse the string as JSON, and a parse failure maps to `WebhookTestResult.MalformedBody`, separate from `NetworkError`/`ServerError`. A 2xx response with a genuinely **empty** body is treated as `WebhookTestResult.Success` — many real webhook receivers (including Home Assistant, this feature's stated primary target) respond `200 OK` with an empty body by design, and misclassifying that as `MalformedBody` would report a correctly-working webhook as failed.

`core/di/NetworkModule.kt` (`@Module @InstallIn(SingletonComponent)`) `@Provides @Singleton` a single `OkHttpClient` (10s connect/read/write timeouts, **no logging interceptor** — never dump headers/body). `WebhookTestClient` is constructor-injected (no binding needed). `NetworkModule` is placed alongside existing DI modules.

## Data Flow — Send Test Payload

```
Editor "Send test" tap
  → UiEvent.OnSendTestClicked
  → WebhookEditorViewModel (build Webhook from current state, set isSending)
  → repository.sendTestPayload(webhook)
      → WebhookTestClient.post: POST url, apply headers + auth header, body = sample JSON
        (an IllegalArgumentException from Headers.Builder.add() is caught inside post()
        itself and mapped to WebhookTestResult.InvalidHeaderValue — it never reaches
        Result<T>/Failure.analyzeCause)
  → Result<WebhookTestResult>
  → setState(isSending=false); sendEffect(UiEffect.ShowTestResult(result: WebhookTestResult))
  → Screen collects via CollectOneOffEffects → Snackbar, message driven by the `WebhookTestResult` variant
    ("Test sent: HTTP 200" / "Failed: no connection" / "Failed: malformed response" / "Failed: HTTP 500" /
    "Failed: invalid header value")
```

Fixed sample payload:
```json
{"event":"test","source":"Notificapp","timestamp":<epochMillis>,"data":{"message":"Test payload from Notificapp"}}
```
`Content-Type: application/json`. Auth applied: `ApiKeyHeader`→`headerName: value`; `BearerToken`→`Authorization: Bearer <value>`.

## Validation

spec.md has a MUST-level requirement to reject malformed URLs. Validation lives at the domain level: `Webhook.validate(): List<ValidationError>` in `domain/model/Webhook.kt`, pure Kotlin. It checks:
- `url` parses via `java.net.URI` and its scheme is `http` or `https` (basic scheme+host well-formedness only; no stricter format checks).
- `name` is non-blank.

`Webhook.validate()` operates on the already-constructed `Webhook`, whose `headers: Map<String, String>` cannot structurally contain duplicate keys by the time it exists — so duplicate-key detection does NOT belong here; it would be dead code. Header/auth-name collision detection, however, is NOT subject to that limitation: a constructed `Webhook` already carries both `headers` and `auth`, so it CAN be checked directly. Split accordingly:
- **Duplicate header key detection** runs earlier, in `WebhookEditorViewModel`/`WebhookEditorContract` against the pre-collapse ordered `headerRows: List<Pair<String, String>>` (or equivalent), BEFORE it is converted into `Webhook.headers`. The header-row editor UI structurally allows entering the same key twice, and this MUST be rejected as a validation error rather than silently collapsed by last-write-wins map semantics. The comparison is case-insensitive (trim + lowercase compare): HTTP header names are case-insensitive per RFC 7230, while `Map<String, String>` keys are case-sensitive in Kotlin, so two rows differing only in casing would otherwise both survive and produce two conflicting header lines.
- **Header/auth collision detection** runs inside `Webhook.validate()` itself: a `headers` entry whose key equals (case-insensitively) the active auth header name (`authHeaderName` for `ApiKeyHeader`, or the literal `Authorization` for `BearerToken`) is rejected. Running this check in `Webhook.validate()` — rather than only in the ViewModel — means `WebhookRepositoryImpl.saveWebhook`'s defense-in-depth guard actually covers it for any caller, not just the editor ViewModel. Without this, both the custom header and the auth mechanism get added via `Headers.Builder.add()` (which appends, not replaces), producing two conflicting header lines with undefined server-side precedence — e.g. a user adding a header named `X-API-Key` while also using `ApiKeyHeader` auth with its default `X-API-Key` header name.

`Webhook.validate()` is called from two places: `WebhookEditorViewModel` before `OnSave` (surfacing errors in `WebhookEditorContract.State.errors`), and `WebhookRepositoryImpl.saveWebhook` as a defense-in-depth guard returning `Result.failure` on violation, using `Failure.ApplicationException` (see `core/common/Failure.kt`) carrying a validation-specific message — the closest existing fit in the ADR 006 hierarchy for a rejected precondition rather than an I/O or unexpected error.

## Feature Structure (features/webhook/)

- `contract/WebhookListContract.kt` — State(webhooks, isLoading, error); Event(OnAdd, OnEdit(id), OnDelete(id)); Effect(NavigateToEditor(id?)).
- `contract/WebhookEditorContract.kt` — State(id?, name, url, headerRows, authType, authHeaderName, authValue, isSending, errors); Event(field changes, OnAddHeaderRow, OnSave, OnSendTestClicked); Effect(ShowTestResult(result: WebhookTestResult), ShowError, NavigateBack).
- `viewmodel/WebhookListViewModel`, `viewmodel/WebhookEditorViewModel` — `@HiltViewModel`, extend `MviViewModel`, inject `WebhookRepository` + `NavigationHandler` + IO dispatcher. `WebhookEditorViewModel` MUST reuse the loaded id (`WebhookEditorContract.State.id`) when constructing the `Webhook` for `OnSave` in edit mode — never call the `Webhook` constructor without an explicit `id`, or the default `UUID.randomUUID()` will silently turn an edit into a duplicate insert. `OnSendTestClicked` is a no-op in `onEvent` while `State.isSending == true` — a debounce/re-entrancy guard against repeated "Send test" taps firing overlapping requests.
- `ui/WebhookListScreen.kt`, `ui/WebhookEditorScreen.kt` — Compose/M3; `authValue` field uses `visualTransformation = PasswordVisualTransformation()` + `KeyboardType.Password`; a visibility toggle is allowed but defaults masked. `authValue` is never placed in a log or content-description string.

## File Changes

| File | Action |
|---|---|
| `domain/model/Webhook.kt`, `domain/repository/WebhookRepository.kt` | Create |
| `core/data/local/entity/WebhookEntity.kt`, `dao/WebhookDao.kt`, `mapper/WebhookMapper.kt` | Create |
| `core/data/repository/WebhookRepositoryImpl.kt` | Create |
| `core/network/WebhookTestClient.kt`, `core/network/WebhookTestResult.kt` | Create |
| `core/di/NetworkModule.kt` | Create |
| `core/data/local/AppDatabase.kt` | Modify — add `WebhookEntity`, `webhookDao()`, `CURRENT_VERSION = 2` |
| `core/di/DatabaseModule.kt` | Modify — `provideWebhookDao` |
| `core/di/RepositoryModule.kt` | Modify — `bindWebhookRepository` |
| `core/ui/navigation/Screen.kt` | Modify — `WebhookList`, `WebhookEditor(webhookId: String? = null)` |
| `core/ui/navigation/Routes.kt` | Modify — `webhookList()`, `webhookEditor(id?)` |
| `MainActivity.kt` | Modify — two `entry<>` blocks |
| `features/settings/{contract,viewmodel,ui}` | Modify — `OnWebhooksClicked` event + `NavigateToWebhookList` effect + Webhooks card |
| `AndroidManifest.xml` | Modify — `<uses-permission android:name="android.permission.INTERNET"/>`; add `android:networkSecurityConfig="@xml/network_security_config"` on `<application>` |
| `res/xml/network_security_config.xml` | Create — `<base-config cleartextTrafficPermitted="true">` (broadly allow cleartext) since destinations are arbitrary user-entered URLs, commonly self-hosted LAN targets (e.g. Home Assistant) reachable only over `http://` |
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | Modify — add OkHttp dependency |
| `docs/adr/012-*.md`, `docs/adr/005-*.md`, `PRIVACY.md` | Modify (see Docs) |

## Docs / Policy Changes

- **ADR 012**: change status note — decision #1's "webhook delivery (Phase 4) is the *only* network egress" is now *realized*; add a line: "Webhook test payload (PR1, `WebhookTestClient`) is the first shipped egress; triggered only on explicit user tap, no background traffic." No policy reversal.
- **ADR 005**: decision #3 line "the app is local-first with no remote sources" is now stale — add amendment note: "As of webhook-management (Phase 4), `WebhookRepository` coordinates a remote sink (`core/network/WebhookTestClient`) in addition to local sources; the local-first default still holds — egress is user-initiated only."
- **PRIVACY.md**: add a "Network access" section — INTERNET permission added; the app connects only to user-configured webhook URLs, only on explicit test/delivery action; auth secrets are stored in the SQLCipher-encrypted DB and never logged. This change also **replaces/rewrites** PRIVACY.md's existing "Future changes" paragraph ("The roadmap includes an optional webhooks feature. When that ships...") — that paragraph describes webhooks as a future roadmap item and must not be left alongside the new "Network access" section describing it as shipped. In the same PR, also update PRIVACY.md's "What we never do" section: the absolute bullet "No data transmission off your device" becomes false once webhook test-payload egress ships and MUST be qualified, e.g. "No data transmission off your device, except to webhook URLs you explicitly configure and trigger."

## Testing Strategy

| Layer | What | Approach |
|---|---|---|
| Unit — repository | `WebhookRepositoryImplTest` mirrors `SelectedAppRepositoryImplTest`: CRUD success maps entity↔domain, dao exception → `Result.failure`, `sendTestPayload` delegates to a mocked `WebhookTestClient` | MockK dao+client, `StandardTestDispatcher`, Kotest `shouldBe` |
| Unit — mapper | `WebhookMapperTest`: round-trip each `WebhookAuth` variant (None/ApiKeyHeader with custom+default header name/BearerToken) entity↔domain, headers map preserved | Pure JVM |
| Unit — list VM | `WebhookListViewModelTest`: observe emits mapped list, delete calls repo, add/edit emit navigate effect | Fake/mock repo + `NavigationHandler`, `advanceUntilIdle` |
| Unit — editor VM | `WebhookEditorViewModelTest`: field edits update state, save persists+navigates back, send-test success/failure emit `ShowTestResult`, validation blocks empty name/url, **edit preserves id** (save in edit mode never generates a new `UUID`), **duplicate header key rejected** (case-insensitive, including a collision with the active auth header name) | Mock repo returning `Result.success/failure`, Turbine for effects |

A `FakeWebhookRepository` in `testutil/fakes/` mirrors `FakeSelectedAppRepository`.

**No-log guarantee test (automated, not manual review):** given the CRITICAL leak paths identified above (data-class `toString()`, OkHttp `IllegalArgumentException` messages), a manual review check is insufficient for a MUST-level spec requirement. Add a test that installs a probe `Timber.Tree` capturing all logged messages/throwables, exercises `WebhookRepositoryImpl` and `WebhookTestClient` failure paths (dao exception, malformed header value, network error) using a fixed sample `authValue` (e.g. `"sk-test-secret-should-never-log"`), and asserts that string never appears in any captured log message, throwable message, or throwable `toString()` across the run.

## Migration / Rollout

No data migration (pre-launch, destructive bump). `applyDebugOnlyFallback()` only applies `fallbackToDestructiveMigration()` under `BuildConfig.DEBUG`, and `APP_DATABASE_MIGRATIONS` is empty, so a non-debug (release/signed) build with an existing v1-schema DB will crash with `IllegalStateException` on open after this `CURRENT_VERSION` 1→2 bump — it does not silently wipe data. This is an accepted, pre-first-release-only tradeoff per CLAUDE.md's Development Status policy (no installed user base, no production data to protect); any non-debug install that already has a v1 DB (e.g. an internal tester build) requires a clean reinstall/data clear after this change ships. **This tradeoff must not ship after the app's first public release** — once there is an installed user base, a real `Migration` becomes mandatory.

Rollback = revert PR; shared edits (DB version, DI, nav, manifest, Settings) revert cleanly; dropping the `webhooks` table is safe.

## Open Questions

- [x] URL validation strictness (require `https://`?) — **Resolved**: allow both `http` and `https`; rely on the user-entered URL only (no host allowlist/denylist), given the network security config change above already permits cleartext traffic broadly for LAN self-hosters.
- [ ] Header-row UI editing model (list of key/value pairs) — leave to tasks/implementation detail; must enforce the no-duplicate-key validation rule from the Validation section regardless of the chosen UI shape.
