# Technical Debt Roadmap

This document details every technical debt item identified in the July 6, 2026 architecture review (post-Phase-2), with the concrete solution for each.

> The previous review's items (TD-1..TD-8, July 5, 2026) are all resolved or converted to policy/ADRs — see git history of this file, `docs/adr/011-rule-definition-storage.md`, and the Detekt/test infrastructure now in place. Numbering continues from there so ADR and commit references stay unambiguous.

## Summary

| ID    | Item | Priority | Effort | Status |
|-------|------|----------|--------|--------|
| TD-9  | Rule-sharing wire format is coupled to domain models (and to DB columns) | P0 | Medium | Resolved |
| TD-10 | `fallbackToDestructiveMigration()` active in release builds | P0 | Small | Resolved |
| TD-11 | Backtesting loads the entire notification table into memory | P1 | Small | Resolved |
| TD-12 | No CI pipeline (blocking for community contributions) | P1 | Small | Resolved |
| TD-13 | `ActionBottomSheet` / `AddFieldBottomSheet` are per-type config monoliths | P2 | Medium | Resolved |
| TD-14 | `NotificationNormalizer` has zero test coverage | P2 | Medium | Resolved |
| TD-15 | Release hygiene: static `versionCode`, no Fastlane changelogs | P3 | Small | Resolved |
| TD-16 | Detekt baseline holds 299 grandfathered findings | P3 | Policy | Resolved (policy documented; baseline count now tracked as it shrinks) |

All eight items resolved 2026-07-06 on `refactor/tech-debt`. Notably, implementing TD-9's golden-file test surfaced and fixed a real pre-existing bug: `RuleJsonCodec.encode()` threw for every `ExtractionMethod` except the two zero-argument "smart" ones, because the domain sealed class's self-declared `type` property collided with kotlinx's default polymorphic JSON discriminator — undetected because no prior test encoded any other extraction method through the codec.

---

## TD-9: Rule-sharing wire format is coupled to domain models (P0)

**Status: Resolved** — commit `c2f6db5`.

### Context

`RuleJsonCodec.encode()` (`core/rulesharing/RuleJsonCodec.kt:23`) serializes the domain `Rule` directly: `json.encodeToString(RuleExport(rule = rule))`. The `@Serializable` annotations live on the domain models themselves (`Rule`, `RuleCondition`, `RuleField`, `RuleAction`, `AppInfo`). The enums and the `ExtractionMethod` sealed hierarchy are already pinned with `@SerialName` (good), but the **data-class property names are not** — `isDryRun`, `targetApps`, `captureGroup`, etc. are the wire format by accident of being Kotlin property names.

This creates three concrete problems:

1. **Domain refactors break shared rules.** Rename any property on `Rule` or its children and every exported rule file, every community gallery rule, and every user's clipboard paste stops importing. The `schemaVersion` envelope doesn't protect against this — the payload shape is implicit.
2. **The coupling is double.** The same serializers also feed Room columns: `RuleFieldMapper` stores `ExtractionMethod` as serialized JSON in `rule_fields.methodConfig`, and `RuleActionMapper` serializes `config`. So today one Kotlin class shape simultaneously defines (a) the domain model, (b) the public wire format, and (c) persisted DB column content. Changing any one silently breaks the others.
3. **No forward compatibility within schemaVersion 1.** kotlinx decodes enums strictly by `@SerialName`. When Phase 4 adds `SEND_WEBHOOK` to `ActionType`, a rule exported from the new version **fails to import entirely** on older versions — a hard `SerializationException`, not a graceful "this rule uses an action your version doesn't support."

Once the community gallery (Phase 6) and starter templates exist, this format is frozen forever. The window to fix it cheaply is *now*, before any rule file exists outside the repo.

### Solution

**1. Introduce a DTO layer in `core/rulesharing/dto/` that pins today's JSON shape exactly** (so `schemaVersion` stays 1 and existing exports keep working):

```kotlin
@Serializable
data class RuleExportDto(
    @SerialName("schemaVersion") val schemaVersion: Int = RULE_EXPORT_SCHEMA_VERSION,
    @SerialName("rule") val rule: RuleDto,
)

@Serializable
data class RuleDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("description") val description: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("isActive") val isActive: Boolean = true,
    @SerialName("isDryRun") val isDryRun: Boolean = false,
    @SerialName("targetApps") val targetApps: List<AppInfoDto>? = null,
    @SerialName("conditions") val conditions: List<ConditionDto> = emptyList(),
    @SerialName("fields") val fields: List<FieldDto> = emptyList(),
    @SerialName("actions") val actions: List<ActionDto> = emptyList(),
    @SerialName("createdAt") val createdAt: Long = 0L,
    @SerialName("updatedAt") val updatedAt: Long = 0L,
)

@Serializable
data class ActionDto(
    @SerialName("id") val id: String,
    // Deliberately a String, not ActionType: unknown values must not fail the whole import.
    @SerialName("type") val type: String,
    @SerialName("isEnabled") val isEnabled: Boolean = true,
    @SerialName("config") val config: Map<String, String> = emptyMap(),
)
```

Every field carries an explicit `@SerialName`, even when it matches the property name — that is the point: renaming the Kotlin property no longer changes the JSON. Enums cross the wire as `String` (conditions/operators/field types too); the `ExtractionMethod` DTO mirrors the sealed hierarchy with the same `@SerialName` discriminators it has today.

**2. Add a `RuleWireMapper` (pure Kotlin, same package)** with `Rule.toDto(): RuleExportDto` and `RuleExportDto.toDomain(): RuleImportResult`. The domain-ward direction maps enum strings explicitly and handles unknowns *leniently*:

```kotlin
data class RuleImportResult(
    val rule: Rule,
    /** Wire values this app version doesn't understand, dropped from the imported rule. */
    val skippedActions: List<String>,
    val skippedFields: List<String>,
)
```

An unknown `ActionDto.type` (e.g. `"send_webhook"` on a pre-Phase-4 install) drops that action and reports it; the rest of the rule imports. `RulesViewModel`'s import preview dialog surfaces the skips ("1 action requires a newer version of Notificapp"). An unknown *condition operator* should still fail the import — a rule that can't evaluate its conditions is meaningless — but with a clear message.

**3. Lock the format with a golden-file test.** This is the single most valuable deliverable of the item: check in `app/src/test/resources/rule-export-v1.json` containing a maximally-populated rule (every action type, every extraction method, every operator), and assert:

- `RuleJsonCodec.encode(fixtureRule)` matches the golden file byte-for-byte (modulo the pretty-print settings), and
- decoding the golden file yields `fixtureRule` back.

From then on, *any* accidental wire-format change — a rename, a default change, a new required field — is a failing unit test instead of a broken community.

**4. Afterwards, remove `@Serializable` from the rule domain models where possible.** `Rule`, `RuleCondition`, `AppInfo` become plain data classes. Note the caveat from problem 2: `RuleField.ExtractionMethod` and `RuleAction.config` serialization is still used by the Room mappers for column content — either keep those annotations documented as *internal storage format* (device-local, migratable via Room migrations, acceptable), or give the mappers their own column DTOs in a follow-up. Do not block this item on the follow-up.

**5. Update `docs/rule-format.md`** to state that `core/rulesharing/dto/` is the canonical definition of the format and that changes there require a `schemaVersion` decision.

### Acceptance

- Golden-file round-trip test passes and is the only place the wire shape is asserted.
- Renaming a domain property compiles without touching any JSON output (proven by the golden test).
- Importing a rule containing an unknown action type succeeds with a visible "skipped" notice.

**Deviations from the plan above:** `RuleImportResult` shipped without `skippedFields` — only actions get lenient unknown-value handling (matching the acceptance criteria and the near-term motivation, Phase 4's webhook action). An unrecognized extraction method fails the whole import, same as an unrecognized condition operator, since a field that can't extract is as meaningless as a condition that can't evaluate; there's no equivalent near-term driver for field-level leniency. `RuleImportResult` lives in its own file (`RuleImportResult.kt`) rather than inline in the mapper, to satisfy Detekt's one-top-level-declaration-per-file convention. Implementing the golden-file test surfaced and fixed a real bug — see the note at the top of this document.

---

## TD-10: `fallbackToDestructiveMigration()` active in release builds (P0)

**Status: Resolved** — commit `337b393`.

### Context

`DatabaseModule.kt:43` still calls `.fallbackToDestructiveMigration()` alongside the real migration chain (`MIGRATION_1_2`, `MIGRATION_2_3`, exported schemas 1–3, and `AppDatabaseMigrationTest` covering both — all good). The old TD-2 kept the fallback as a pre-release safety net, but its failure mode inverted the moment real migrations started shipping: if a future schema bump forgets its `Migration`, Room won't crash — it will **silently wipe every rule, notification, and extraction** on user devices. For an app whose pitch is "invest hours building rules on a dataset you own," a silent wipe on update is the single worst bug possible, and it's undetectable in testing because dev installs are always fresh.

### Solution

1. Gate the fallback to debug builds in `DatabaseModule`:

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
    .addMigrations(*APP_DATABASE_MIGRATIONS)
    .apply {
        // Dev installs may skip migrations; release builds must crash loudly on a
        // missing migration rather than silently wiping user data.
        if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
    }
    .build()
```

2. Keep the existing per-bump discipline (already established): every `@Entity` change ships its `Migration` + a `MigrationTestHelper` test in the same PR. `AppDatabaseMigrationTest` already models this — extend it for each new version.
3. Add a release-checklist line (F-Droid prep, TD-15): verify `addMigrations` covers a contiguous chain from every previously-shipped schema version.

### Acceptance

- A release build with a missing migration crashes with Room's `IllegalStateException` instead of wiping data (verifiable by temporarily bumping the version in a release build without a migration).

---

## TD-11: Backtesting loads the entire notification table into memory (P1)

**Status: Resolved** — commit `16996b4`.

### Context

`RuleEditorViewModel.testAgainstHistory()` (`RuleEditorViewModel.kt:412`) calls `NotificationRepository.getAllNotifications()` — backed by `NotificationDao.getAllSync()`, an unbounded `SELECT * FROM notifications` — then filters by target package **in memory**. There is no retention sweep yet (Phase 3), so this list grows without bound. After a few months of captured notifications on a mid-range device, tapping "Test against history" is a multi-second freeze at best, an OOM at worst. The package filter belonging in SQL is the textbook part; the missing `LIMIT` is the dangerous part.

### Solution

1. Add bounded, filtered queries to `NotificationDao` (the table and column names already exist):

```kotlin
@Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT :limit")
suspend fun getRecent(limit: Int): List<NotificationEntity>

@Query(
    "SELECT * FROM notifications WHERE package_name IN (:packageNames) " +
    "ORDER BY timestamp DESC LIMIT :limit",
)
suspend fun getRecentByPackageNames(packageNames: List<String>, limit: Int): List<NotificationEntity>
```

2. Add `NotificationRepository.getNotificationsForBacktest(targetPackages: List<String>?, limit: Int): Result<List<Notification>>` that picks the right query (`null`/empty → `getRecent`). Keep the existing `Result` + mapper conventions.
3. In `testAgainstHistory()`, replace the `getAllNotifications()` + in-memory `filter` with the new call. A constant like `BACKTEST_NOTIFICATION_LIMIT = 500` is plenty for a preview; the newest-first ordering means the preview reflects what the user currently receives.
4. UI honesty (never fail silently, even in previews): `backtestTestedCount` already exists in the contract — change the results-sheet copy to "Tested against the most recent N notifications" so a capped run doesn't masquerade as an exhaustive one.
5. Delete `getAllNotifications()` / `getAllSync()` if this was the last caller — an unbounded full-table read shouldn't survive as an attractive nuisance. (Check callers first; `rg getAllNotifications` currently shows only the backtest path.)

### Acceptance

- Backtest issues a single `LIMIT`-bounded query; no full-table list is ever materialized.
- Results sheet states the tested window size.

---

## TD-12: No CI pipeline (P1 — blocking for community contributions)

**Status: Resolved** — commit `684787b`.

### Context

Phase 2 shipped rule import/export; the roadmap's growth engine is community contributions (rules and code). The repo has quality gates locally (Spotless, Detekt, 211 unit tests, git hooks) but nothing enforces them on PRs — meaning the maintainer is the CI, and the first community PR that breaks the build merges silently. The roadmap lists CI as an unchecked Repo Infrastructure item; it stopped being optional the moment sharing landed.

### Solution

Add `.github/workflows/ci.yml`:

```yaml
name: CI

on:
  pull_request:
  push:
    branches: [main]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17   # matches app/build.gradle.kts sourceCompatibility
      - uses: gradle/actions/setup-gradle@v4   # Gradle + dependency caching
      - run: ./gradlew spotlessCheck detekt test assembleDebug
```

Notes:

- One job, one Gradle invocation — task order puts the fast failures (formatting, static analysis) before tests and assembly.
- `gradle/actions/setup-gradle` handles wrapper validation and build caching; no manual cache keys.
- Instrumented tests (`AppDatabaseMigrationTest`) need an emulator — defer to a separate, manually-triggered or nightly workflow (`reactivecircus/android-emulator-runner`) rather than slowing every PR.
- When the Phase 6 gallery lands, extend with a job that decodes every `rules/*.json` through `RuleJsonCodec` (a plain JVM test iterating the directory) so a malformed community rule cannot merge. This depends on TD-9's golden format.

### Acceptance

- A PR that fails formatting, Detekt, or any unit test shows a red check and cannot be merged blind.

---

## TD-13: `ActionBottomSheet` / `AddFieldBottomSheet` are per-type config monoliths (P2)

**Status: Resolved** — commit `0eba254`.

### Context

`ActionBottomSheet.kt` is 806 lines: the sheet scaffolding plus `SnoozeDurationSelector`, `AlarmOptionsSelector`, `AlarmSoundPickerButton`, `FlashOptionsSelector`, `FlashCountSlider`, `FlashDurationSlider` — every action's configuration UI inlined. `AddFieldBottomSheet.kt` is 857 lines with the same pattern across 10 extraction methods. Phase 4's webhook action brings the largest config UI yet (saved-webhook picker + inline creation + payload field checkboxes); dropped into this file it lands well past 1,200 lines. This also degrades the documented "Adding a New Action Type" recipe: step 3 currently means "grow the monolith."

The old TD-6 set a 700-line soft budget for *new* screens; these two files are where the budget meets reality.

### Solution

1. Create `features/ruleeditor/ui/components/actionconfig/` with one file per action's configuration composable, moved verbatim (no behavior change — this is a mechanical extraction):
   - `SnoozeConfig.kt` (duration selector)
   - `AlarmConfig.kt` (options selector + sound picker button)
   - `FlashAlertConfig.kt` (options selector + both sliders)
   Each keeps its own `@Preview`s, which move with it.
2. `ActionBottomSheet.kt` keeps: sheet scaffolding, the `ActionTypeCard` grid, and a single `when (selectedType)` that delegates to the config composables. Target: under 350 lines.
3. Establish the signature convention so future actions (webhooks!) slot in without touching anything else:

```kotlin
@Composable
fun SnoozeConfig(
    config: Map<String, String>,
    onConfigChange: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier,
)
```

4. Apply the same extraction to `AddFieldBottomSheet` → `ui/components/fieldconfig/`, one file per `ExtractionMethod` group. This can be a separate PR; the action sheet is the urgent one because Phase 4 grows it next.
5. Update the "Adding a New Action Type" section in `CLAUDE.md`: step 3 becomes "add a config composable in `actionconfig/` and one `when` branch."

### Acceptance

- Adding a new action type touches: domain enum, executor + DI binding, one new `actionconfig/` file, one `when` branch. No file grows past the 700-line budget.

---

## TD-14: `NotificationNormalizer` has zero test coverage (P2)

**Status: Resolved** — commit `04ff915`.

### Context

`NotificationNormalizer` (`features/notification/NotificationNormalizer.kt`) is the funnel every captured notification passes through, and it faces the wildest input in the app — every third-party app's title/text/bigText/textLines/subText conventions. It has zero tests, while the pure engine *behind* it (matcher, extractor, engine — 211 tests) is thoroughly covered: the best-tested pipeline in the app sits downstream of its least-tested component. It's untested because it consumes `StatusBarNotification` + `PackageManager` directly, which don't exist on the JVM. There are also behaviors worth locking in (or reconsidering) under test, e.g. the `extras.toString()` last-resort in `extractContent()` — which leaks a `Bundle` debug string into user-visible content and extraction input.

### Solution

Split the Android boundary from the logic (same move ADR 009 made for the pipeline):

1. Introduce a plain data holder capturing everything the normalizer reads:

```kotlin
data class RawNotificationData(
    val packageName: String,
    val notificationId: Int,
    val postTime: Long,
    val key: String?,
    val title: String?,          // EXTRA_TITLE / EXTRA_TITLE_BIG, resolved
    val text: String?,           // EXTRA_TEXT
    val bigText: String?,        // EXTRA_BIG_TEXT
    val textLines: List<String>, // EXTRA_TEXT_LINES
    val subText: String?,        // EXTRA_SUB_TEXT
    val tickerText: String?,
)
```

2. A thin Android-side reader (`StatusBarNotification.toRawData()` extension next to the service, or a small `RawNotificationReader` class) does *only* the `extras.get*` calls and app-name resolution via `PackageManager`. No logic, no branching beyond null-safety — nothing worth unit-testing lives here.
3. `NotificationNormalizer.normalize(raw: RawNotificationData, appName: String): Notification` becomes pure Kotlin: the source-priority cascade (bigText → text → textLines → subText), raw-content assembly, ID generation. Move it out of `features/notification` into `core/notification/` if desired — it no longer imports Android.
4. Add JVM tests (JUnit5/Kotest, consistent with the suite) covering: title fallback order, each content source in priority order, multi-line joining, ticker dedup in `buildRawContent`, ID format stability (`packageName_id_postTime` — dedup and DB identity depend on it), and the empty-extras edge. While writing them, decide deliberately whether the `extras.toString()` fallback should survive; recommendation: drop it and return null — `shouldSkipNotification` already filters contentless notifications, and a `Bundle[...]` dump as extraction input is noise, not signal.

### Acceptance

- Normalization logic runs and is tested on the JVM with zero Android imports; the Android-facing reader is ≤ ~40 lines of glue.

---

## TD-15: Release hygiene — static `versionCode`, no Fastlane changelogs (P3)

**Status: Resolved** — commit `c79720b`.

### Context

`app/build.gradle.kts` has `versionCode = 1`, `versionName = "1.0"`, unchanged since project start. The roadmap's Distribution section already plans Fastlane metadata for F-Droid, but version history can't be retrofitted — F-Droid builds are keyed to tagged versions and changelog files are keyed to `versionCode`.

### Solution

1. Adopt the scheme now: bump `versionCode` monotonically (+1) and `versionName` (semver-ish `0.x` until first stable) on every tagged release; tag format `v<versionName>`.
2. Create the Fastlane structure F-Droid reads:

```
fastlane/metadata/android/en-US/
├── short_description.txt
├── full_description.txt
├── changelogs/
│   └── 2.txt        # one file per versionCode, plain text
└── images/          # screenshots for the listing (roadmap item)
```

3. Add the release checklist (`docs/RELEASING.md` or a section in `CONTRIBUTING.md`): bump versions, write `changelogs/<versionCode>.txt`, verify migration chain (TD-10), tag. Five lines that prevent every "forgot the changelog" release.

---

## TD-16: Detekt baseline holds 299 grandfathered findings (P3 — policy)

**Status: Resolved** — commit `094132c`.

### Context

Detekt is wired and gates new code (old TD-7), but `config/detekt/baseline.xml` grandfathers 299 findings. A baseline that only ever grows stale becomes permanent debt with a paper trail.

### Solution

Boy-scout policy, not a big-bang cleanup:

1. When a PR meaningfully touches a file, fix that file's baseline entries in the same PR and regenerate the baseline (`./gradlew detektBaseline`) — the diff must show the baseline *shrinking*.
2. Never regenerate to *add* entries except by the existing explicit rule (intentionally accepted debt, called out in the PR description).
3. TD-13's extractions are the natural first big bite — splitting the two bottom sheets should retire their `LongMethod`/`LargeClass` entries for free.

No acceptance gate; track the count direction (299 → down) in review.
