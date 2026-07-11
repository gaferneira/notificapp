# Proposal: Alarm Options Redesign

## Intent

The alarm action config (`AlarmOptionsSelector`) is a minimal set of always-on
toggles: sound always plays, vibration is boolean-only with a hardcoded pattern,
snooze is a fixed 5-minute constant with **no max-count** (users can snooze
infinitely — a latent bug), and full-screen mode paints a hardcoded background.
This change reworks the UI into a card of value-summarizing rows and adds real
backing functionality for four per-alarm controls so users can tailor how each
alarm rings.

## Scope

### In Scope
- **UI**: replace `AlarmOptionsSelector`'s plain list with a card of 3 rows
  (Sound / Vibration / Snooze) — bold title + colored subtitle showing current
  value + trailing switch, divider-separated; tapping a row opens its picker.
- **Sound**: enable/disable toggle (default on); keep `RingtoneManager` picker.
- **Vibration**: named-pattern picker (built-in set, e.g. Basic call / Pulse /
  Long, each a distinct `LongArray`); `AndroidAlarmPlayer.vibrate()` takes it.
- **Snooze**: enable toggle + duration (minutes) + repeat count (max snoozes);
  fixes the infinite-snooze bug via a concrete max-count.
- **Alarm background**: new section shown only when full-screen is on — preset
  color/gradient swatches + custom gallery image via `OpenDocument` +
  `takePersistableUriPermission`; painted as `AlarmCallScreen` surface.

### Out of Scope
- Custom vibration-pattern authoring UI (only built-in presets).
- Animated/video backgrounds; per-preset theming beyond the swatch color itself.
- Global (non-per-rule) alarm defaults; Room schema migration (config is a JSON
  string column — new keys are free).

## Capabilities

### New Capabilities
- None

### Modified Capabilities
- `alarm-playback`: sound becomes optional; vibration becomes a selectable named
  pattern; snooze becomes configurable (enable + duration + bounded max-count).
- `alarm-fullscreen-ui`: full-screen background becomes user-selectable (preset
  swatch or persisted gallery image) instead of hardcoded theme background.

## Approach

Extend the established per-`RuleAction` config-map convention: add `*_KEY` +
`DEFAULT_*` companions and typed accessors on `RuleAction`, thread each new field
through the 5-hop seam (`AlarmOptions` → `RuleAction.createAlarm` →
`AlarmActionExecutor` → `AlarmRequest` → `AlarmService` intent extras). Model
snooze duration/count on the existing `createSnooze` int-minutes convention and
clamp numeric fields (min/max) like `FLASH_COUNT`/`FLASH_DURATION_MS`. Track
snooze-count-so-far in `AlarmService`/`AlarmStateHolder` state (re-ring rebuilds
`AlarmRequest` from the start intent, so count must persist across the extra).

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `domain/model/RuleAction.kt` | Modified | New keys, defaults, accessors, `createAlarm` params |
| `features/ruleeditor/ui/AlarmBottomSheet.kt` + `actionconfig/AlarmConfig.kt` | Modified | Row-card redesign, 3 pickers, background section |
| `features/ruleeditor/.../AlarmOptions.kt` | Modified | New UI fields |
| `.../alarm/AlarmActionExecutor.kt`, `AlarmController.kt` (`AlarmRequest`) | Modified | Thread new fields through seam |
| `.../alarm/AlarmService.kt` | Modified | Optional sound, snooze config + count, extras |
| `.../alarm/AndroidAlarmPlayer.kt` | Modified | `vibrate(pattern)`, skip sound when disabled |
| `.../alarm/AlarmActivity.kt` (`AlarmCallScreen`) | Modified | Paint selected background (gradient / Coil image) |
| `openspec/specs/alarm-playback`, `alarm-fullscreen-ui` | Modified | Delta specs |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Missing a link in the 5-hop config seam | Med | Checklist per field; delta spec scenarios cover each |
| Wrong picker contract loses image at ring time | Med | Mandate `OpenDocument` + `takePersistableUriPermission` |
| Runaway re-ring from bad snooze values | Low | Clamp duration/count min-max |
| Default max-count choice for legacy rules | Med | **Decision point** (see below) |

## Rollback Plan

Feature is additive behind new config keys with backward-compatible defaults;
revert the change set. Legacy `CREATE_ALARM` rules lack the new keys and fall
back to defaults, so no data migration or cleanup is needed on rollback.

## Dependencies

- Coil (`core/di/CoilModule`, already present) for image backgrounds.

## Open Decisions

- **Legacy default max snooze count**: old rules had effectively unlimited
  snooze. Since "unlimited" is being closed off, a concrete default must be
  chosen (proposed: 3). Confirm the number in spec/design — do not silently
  assume.

## Success Criteria

- [ ] Sound can be disabled; disabled alarms ring silently.
- [ ] Vibration uses the selected named pattern; row shows its name.
- [ ] Snooze respects enable/duration/max-count; cannot snooze past the max.
- [ ] Full-screen background renders the chosen preset or persisted image, and
      the image survives app restart/reboot.
- [ ] Existing saved alarm rules keep working unchanged with sensible defaults.
