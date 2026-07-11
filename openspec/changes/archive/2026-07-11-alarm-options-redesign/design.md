# Design: Alarm Options Redesign

## Technical Approach

Extend the established per-`RuleAction` config-map convention (`*_KEY` + `DEFAULT_*`
companions + typed accessors + a `createAlarm(...)` factory) with eight new fields, then
thread each through the existing 5-hop alarm seam so nothing is dropped mid-chain. Named
vibration patterns and background presets become small pure-Kotlin enums in
`domain/model/`, keeping the domain layer Android-free per this project's package-dependency
rules. Snooze gains a bounded counter carried as an intent extra that
survives the `AlarmRequest` re-ring rebuild. The UI redesigns `AlarmConfig.kt` into a
value-summarizing row card with per-row pickers plus a conditional background section. No Room
migration: `RuleActionEntity.config` is already a JSON-string column, so new keys are free and
missing keys resolve to accessor defaults (legacy `snooze-max-count = 3`, confirmed by user).

References: proposal `sdd/alarm-options-redesign/proposal`, exploration
`sdd/alarm-options-redesign/explore`, specs `alarm-playback` + `alarm-fullscreen-ui`.

## 1. Domain model — `domain/model/RuleAction.kt`

New `const val *_KEY` / `DEFAULT_*` + accessor per field, mirroring existing style:

| Field | Key const | Default | Accessor | Clamp |
|-------|-----------|---------|----------|-------|
| Sound enabled | `ALARM_SOUND_ENABLED_KEY` | `true` | `isAlarmSoundEnabled()` | — |
| Vibration pattern id | `ALARM_VIBRATION_PATTERN_KEY` | `VibrationPattern.BASIC_CALL.id` | `getAlarmVibrationPattern(): VibrationPattern` | fallback to default on unknown id |
| Snooze enabled | `ALARM_SNOOZE_ENABLED_KEY` | `true` | `isAlarmSnoozeEnabled()` | — |
| Snooze duration min | `ALARM_SNOOZE_DURATION_MINUTES_KEY` | `5` | `getAlarmSnoozeDurationMinutes()` | `coerceIn(MIN=1, MAX=60)` |
| Snooze max count | `ALARM_SNOOZE_MAX_COUNT_KEY` | `3` | `getAlarmSnoozeMaxCount()` | `coerceIn(MIN=1, MAX=10)` |
| Background type | `ALARM_BACKGROUND_TYPE_KEY` | `AlarmBackgroundType.NONE` | `getAlarmBackgroundType(): AlarmBackgroundType` | fallback NONE on unknown |
| Background preset id | `ALARM_BACKGROUND_PRESET_KEY` | `null` | `getAlarmBackgroundPresetId(): String?` | used when type is `PRESET`; resolved via `AlarmBackgroundPreset.fromId(id)`, fallback to default preset on `null`/unknown id |
| Background image URI | `ALARM_BACKGROUND_IMAGE_URI_KEY` | `null` | `getAlarmBackgroundImageUri(): String?` | used when type is `IMAGE` |

Reuse the `createSnooze` int-minutes-as-string convention for duration/count (distinct
alarm-scoped key, NOT the `SNOOZE_NOTIFICATION` key). `createAlarm(...)` grows the eight params
(defaulted), writing each into `buildMap`. Keep vibration-pattern id resolution on the enum so
the domain stays framework-free (no `LongArray` in domain).

## 2. Vibration patterns — `domain/model/VibrationPattern.kt` (new)

`enum class VibrationPattern(val id: String, val timings: LongArray, val repeatIndex: Int)`
with `BASIC_CALL` (current `[0,800,1000]`), `PULSE` (`[0,200,200]`), `LONG` (`[0,1200,800]`).
`companion fun fromId(id: String?): VibrationPattern` = default on miss. `AlarmPlayer.vibrate()`
becomes `vibrate(pattern: VibrationPattern)`; `AndroidAlarmPlayer` builds
`VibrationEffect.createWaveform(pattern.timings, pattern.repeatIndex)` instead of the constant.
Enum lives in `domain/model/` alongside `RuleAction.kt` (holds only primitives — `id`,
`LongArray`, `Int` — no Android imports), per this project's Package Dependencies rule that
`core/notification` depends on `domain`, never the reverse; it stays unit-testable without an
emulator.

## 3. Snooze bounded behavior

`AlarmRequest` gains `snoozeEnabled: Boolean`, `snoozeDurationMinutes: Int`,
`snoozeMaxCount: Int`, `snoozeCount: Int` (count-so-far, default 0). `AlarmService`:
`EXTRA_SNOOZE_*` extras added to `startIntent`/`toAlarmRequest`. `scheduleReRing` rebuilds the
start intent with `snoozeCount + 1`; the re-rung `handleStart` reads it back into `current`, so
the counter survives the rebuild via the intent extra (no `AlarmStateHolder` mutation needed —
keeps the holder a pure ring-flag). `handleSnooze` guards:
`if (!current.snoozeEnabled || current.snoozeCount >= current.snoozeMaxCount) { stopAlarm(); return }`.
`buildNotification()` only adds the Snooze action when snooze is still available; `AlarmActivity`
receives `snoozeEnabled`/`snoozeDurationMinutes`/`snoozeCount`/`snoozeMaxCount` extras (added to
`AlarmActivity.intent(...)`, which today only carries `EXTRA_TITLE`/`EXTRA_TEXT`/`EXTRA_APP_NAME`),
hides the Snooze button when exhausted, and uses `snoozeDurationMinutes` to label the button
(e.g. "Snooze (5 min)"). Duration replaces the hardcoded `SNOOZE_DELAY_MS` with
`current.snoozeDurationMinutes * 60_000L`. Clamping lives in the `RuleAction` accessors (defense
in depth, like `FLASH_COUNT`).

## 4. Background picker + persistence

New `domain/model/AlarmBackground.kt` (new) alongside `VibrationPattern`, holding two top-level
enums (mirroring `VibrationPattern`'s flat, framework-free shape — no nested container type):
`enum class AlarmBackgroundPreset(val id: String, val colorHexStops: List<String>)` (a small
swatch/gradient set, e.g. `SUNRISE`, `OCEAN`, `MIDNIGHT`, each carrying only primitive ARGB hex
strings for its gradient stops — no `Brush`/`Color` or other Compose type, keeping the domain
Android-free per this project's Domain Layer rule) plus `enum class AlarmBackgroundType { NONE,
PRESET, IMAGE }` — both live in `domain/model/`, not `core/notification/action/`, so
`RuleAction`'s accessors can return them without inverting the domain → core/notification
dependency direction. `AlarmBackgroundPreset` gets a `companion fun fromId(id: String?):
AlarmBackgroundPreset`, mirroring `VibrationPattern.fromId`'s shape: falls back to a default
preset when `id` is `null` or doesn't match a known preset (stale id from an old export, or a
preset removed in a later version), or when `backgroundType == PRESET` but no preset was ever
picked — the rendering layer always calls `fromId()` before resolving a `Brush`, so there's no
unresolved-id case left unhandled. Custom image uses
`ActivityResultContracts.OpenDocument()` (matching `RulesScreen.kt`) — but unlike JSON import,
the URI is read later at ring time, so the picker callback in `AlarmBottomSheet` MUST call
`contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`
immediately (via `LocalContext.current`, the same pattern the sheet already uses for
`rememberNotificationPermissionRequest`) so the grant survives process death/reboot — this take
call is *local UI state only* at this point, not yet persisted to any `RuleAction`.

URI-permission lifecycle (take, release-on-replace, release-on-cancel) all needs a `Context`,
which `RuleEditorViewModel` does not have and should not gain (staying consistent with this
project's MVI pattern, where Android-framework calls live in the Composable, not the ViewModel).
So the full flow is:
- **On pick**: `AlarmBottomSheet` takes the grant immediately (above), before the sheet is even saved.
- **On replace-within-sheet**: if the user picks a second image before saving, `AlarmBottomSheet`
  releases the *previous* picked-but-unsaved URI's grant directly (still local state, still a
  Composable-only concern — no repository check needed since that URI was never persisted).
- **On sheet dismiss without saving**: `AlarmBottomSheet`'s `onDismiss` path releases the
  currently-picked URI's grant if it differs from `initial`'s already-persisted URI (if any) —
  this closes the cancel-path leak where a picked-but-never-saved URI would otherwise hold its
  grant forever.
- **On save, when the newly-saved URI differs from the action's previous persisted URI** (edit
  case) or **type changes away from `IMAGE`**: `AlarmBottomSheet`'s `onSave` callback (still in the
  Composable, still holding `Context`) calls a suspend function on `RuleEditorViewModel` —
  `viewModelScope.launch { ruleRepository.isImageUriReferencedByOtherAlarmAction(uri, excludingActionId) }`
  wrapping the ViewModel's new repository method — and only once that returns `false` does
  `AlarmBottomSheet` call `contentResolver.releasePersistableUriPermission(oldUri, ...)` itself.
  `onActionSaved` in `RuleEditorViewModel` already needs to become `viewModelScope.launch`-wrapped
  for this repository read, mirroring the existing `saveRule`/`deleteRule` pattern in the same
  ViewModel — the check is exposed to `AlarmBottomSheet` as a suspend callback parameter (e.g.
  `onCheckUriStillReferenced: suspend (uri: String, excludingActionId: String) -> Boolean`) so the
  actual `contentResolver` call stays entirely in the Composable, and the ViewModel never touches
  `Context`.

This keeps the split clean: `RuleEditorViewModel`/`RuleRepository` only ever answer "is this URI
still referenced elsewhere" (pure data question); `AlarmBottomSheet` is the only place that ever
calls `contentResolver.take/releasePersistableUriPermission`, whether on pick, replace-within-sheet,
dismiss-without-save, or save. Because `RuleActionEntity.config` is a JSON-string column (line 13),
`isImageUriReferencedByOtherAlarmAction` has no indexed column to query against; it linear-scans
stored `CREATE_ALARM` actions' configs and deserializes each to compare the background image URI,
which is acceptable given the expected low number of alarm actions per install. `AlarmRequest` gains `backgroundType`/`backgroundPresetId`/`backgroundImageUri`;
threaded to `AlarmActivity` via extras. `AlarmCallScreen`'s `Surface` renders by type: `NONE` →
today's `MaterialTheme.colorScheme.background`; `PRESET` → `AlarmBackgroundPreset.fromId(presetId)`
resolves the id to a preset, then a small mapper function local to the UI
layer (e.g. an `AlarmBackgroundPreset.toBrush()` extension defined next to `AlarmCallScreen`, NOT
in domain) converts `colorHexStops` into a Compose `Brush.linearGradient(...)`, applied via
`Modifier.background(brush)` — mirroring how `VibrationPattern.timings: LongArray` is only
converted to `VibrationEffect` inside `AndroidAlarmPlayer`; `IMAGE` → Coil `AsyncImage` fill behind
the content (Coil already wired via
`core/di/CoilModule`), with a scrim for text contrast. The `AsyncImage` call uses Coil's error
slot (e.g. `onError = { ... }`, or `SubcomposeAsyncImage`'s error composable) to detect a failed
load (revoked permission, deleted file) and falls back to rendering the same theme background as
the `NONE` case, so the alarm keeps ringing and stays dismissible/snoozable even when the image
can't be loaded.

## 5. UI redesign — `AlarmConfig.kt` / `AlarmOptions.kt` / `AlarmBottomSheet.kt`

Replace `AlarmToggleRow` for the three feature rows with `AlarmValueRow` (bold title + colored
subtitle summarizing current value + trailing `Switch`; whole row tappable to open its picker).
Sound row reuses the existing `RingtoneManager` picker for its tap action, gated by the new
enabled switch. Vibration row opens a radio-list picker of `VibrationPattern` values. Snooze row
opens an inline expandable section with duration + max-count number steppers. A new
`AlarmBackgroundSection` composable renders only when `fullScreenEnabled` is true: preset swatch
grid + "Choose image" `OpenDocument` button. `AlarmOptions` grows the eight fields;
`AlarmBottomSheet` owns the new state (`remember { mutableStateOf(...) }` seeded from `initial`
accessors) and passes all eight to `RuleAction.createAlarm(...)`.

## 6. Full 5-hop threading (per field, top implementation risk)

Chain: `RuleAction` accessor → `AlarmOptions` → `AlarmActionExecutor` (`AlarmRequest(...)`
construction) → `AlarmRequest` field → `AlarmService` `EXTRA_*` (`startIntent` write +
`toAlarmRequest` read). Checklist — every field must appear at least at these five stops; the
snooze and background fields also need a 6th `AlarmActivity.intent(...)` stop (sound/vibration
stay service-only, since they only affect ringing, not the full-screen UI):

| Field | RuleAction | AlarmOptions | Executor | AlarmRequest | Service extra | AlarmActivity.intent |
|-------|:---:|:---:|:---:|:---:|:---:|:---:|
| soundEnabled | ✓ | ✓ | ✓ | ✓ | ✓ (skip `play` when false) | — |
| vibrationPattern | ✓ | ✓ | ✓ | ✓ | ✓ (`vibrate(pattern)`) | — |
| snoozeEnabled | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ (hide Snooze button when exhausted) |
| snoozeDurationMinutes | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| snoozeMaxCount | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| backgroundType | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| backgroundPresetId | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| backgroundImageUri | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

`EMPTY_REQUEST` and `AlarmActionExecutorTest` fixtures updated in lockstep.

## 7. Legacy migration

No Room schema migration. Legacy `CREATE_ALARM` rules lack the new keys; accessors resolve to
defaults — critically `getAlarmSnoozeMaxCount() = 3`, closing the infinite-snooze bug for old
rules without touching stored data. Rollback = revert code; old rows still parse.

## Architecture Decisions

### Decision: Bottom-sheet-per-row vs inline-expand for pickers
**Choice**: Mixed — reuse the system pickers for Sound (ringtone) and image (`OpenDocument`);
inline expand for Snooze (two number steppers); radio-list picker for Vibration.
**Alternatives**: a separate modal bottom sheet per row.
**Rationale**: Snooze is two tiny numeric inputs — a nested sheet inside the already-modal
`ActionConfigSheet` is heavier than the interaction warrants and risks nested-sheet focus bugs.
Sound/image already have canonical system pickers; reusing them is less code and matches user
expectation. Inline keeps all alarm config in one scroll, preserving the card's summary model.

### Decision: enum vs sealed class for vibration patterns & background presets
**Choice**: `enum class` for both.
**Alternatives**: sealed class hierarchy.
**Rationale**: The set is closed, built-in, and each case carries only flat primitive data
(`id`, `LongArray`/`List<String>` hex color stops, repeat index) with no per-case behavior or
varying shape — the exact case an enum models best. `enum.values()`/`entries` gives the picker
list for free and `fromId` gives cheap, exhaustive-safe config round-tripping. A sealed class
would add ceremony with no polymorphism benefit; custom-image is already modeled by the
`AlarmBackgroundType` discriminator + URI string, not a preset case.

### Decision: snooze counter as intent extra vs AlarmStateHolder
**Choice**: Carry `snoozeCount` as an `AlarmRequest`/intent extra, incremented in `scheduleReRing`.
**Alternatives**: mutable counter in the `@Singleton AlarmStateHolder`.
**Rationale**: The re-ring already reconstructs the full request from `startIntent`, so the
intent is the natural carrier and keeps count correctly scoped to a single alarm episode.
`AlarmStateHolder` stays a pure ring-flag (single responsibility); a holder counter would need
manual reset on dismiss/new-alarm and could leak state across episodes.

## Data Flow

    AlarmConfig UI ──sets──▶ AlarmOptions ──▶ RuleAction.createAlarm ──▶ config map (JSON)
                                                                              │
    RuleAction accessors ◀────────────────────────────────────────────── persisted
        │
        ▼
    AlarmActionExecutor ──▶ AlarmRequest ──▶ AlarmService (EXTRA_* intent)
                                                 │  ├─▶ AlarmPlayer.play / vibrate(pattern)
                                                 │  └─▶ scheduleReRing (count+1, bounded)
                                                 └─▶ AlarmActivity (bg + snooze extras)

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | `RuleAction` accessors: defaults, clamps, legacy `maxCount=3`, unknown-id fallback | JUnit5/Kotest, extend existing `RuleAction` tests |
| Unit | `VibrationPattern.fromId` / `AlarmBackgroundType`+`AlarmBackgroundPreset` round-trip | pure JVM |
| Unit | `AlarmBackgroundPreset.fromId`: null id, unrecognized id, and `PRESET` type with no id selected all fall back to default preset | pure JVM, mirrors "Unrecognized pattern id falls back to default" |
| Unit | `AlarmActionExecutor` maps all 8 fields into `AlarmRequest` | extend `AlarmActionExecutorTest` with fake controller |
| Unit | Snooze bound: exhausted count stops instead of re-ringing | fake `AlarmManager`/state assertion where feasible |
| Manual | Full-screen background render (preset/image/none); image survives reboot | on-device |
| Manual | `IMAGE` background fails to load (revoked permission/deleted file) falls back to theme background; alarm keeps ringing and stays dismissible/snoozable | on-device |

## Migration / Rollout

No data migration (JSON config column, additive keys, default-backed accessors). Additive and
backward compatible; rollback = revert change set.

## Open Questions

- [ ] Exact preset swatch set + `colorHexStops` values (names/colors) — cosmetic, resolve in apply.
- [ ] Snooze duration/count clamp bounds (proposed 1–60 min / 1–10) — confirm in tasks.
