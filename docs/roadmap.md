# Notificapp Roadmap

## Vision

Notificapp lets anyone take control of their Android notifications. Users create rules that match notifications and trigger actions — dismiss, snooze, flash alerts, alarms, webhook delivery — including the one no competitor offers: **extracting the data inside notifications** into a structured, reusable dataset, entirely on-device.

## Positioning

**Category: open-source, local-first notification automation** — "if this notification, then that" — **with data extraction as the flagship capability.**

- **BuzzKill** is the polished incumbent for notification rules — but paid and closed-source. Notificapp is the open alternative (the NewPipe/Aegis playbook: F-Droid thrives on open replacements for closed paid apps), and BuzzKill cannot extract data.
- **Tasker/MacroDroid** can technically do everything, but with power-user-hostile UX. Notificapp is focused and simple: one domain (notifications), one mental model (rules).
- **Notification history apps** capture but don't act on or structure anything.

Extraction is one action among many in the rules engine — but it is the **differentiator**: the action nobody else has, and the foundation of the moat. The moat itself is **community-contributed rules**. Every roadmap decision that makes rules easier to create, test, and share compounds; everything else is table stakes.

**Hero use cases** (optimize onboarding, README, and rule templates around these):

1. **Notification noise control** — auto-dismiss/snooze the junk, get alerted for what matters
2. **Expense tracking** — bank/payment notifications become a spending dataset
3. **Package tracking** — carrier notifications become delivery status + tracking numbers
4. **Home automation bridging** — extracted data delivered to Home Assistant & co. via webhooks

**Target audience**: any Android user who wants their notifications under control — automation is the broad on-ramp. The OSS crowd (self-hosters, Home Assistant users, data-ownership people) is the community core that contributes rules and drives F-Droid distribution.

## Guiding Principles

- **Rule-creation friction is the make-or-break UX** — a normal person must go from "annoying notification" to "working rule" in under a minute (templates → create-from-notification → raw editor, in that order of prominence)
- **Never fail silently** — automation that eats notifications or drops webhooks without telling the user destroys trust
- **Community-first** — shared rules are the growth engine; the repo is part of the product

---

## Current Status

### Done

- **Core Architecture** — MVI + feature-first packaging, Hilt DI, Room DB with 9 entities, Navigation3
- **Onboarding** — Notification listener permission request flow
- **App Selection** — Browse installed apps, select which to monitor
- **Notification Capture** — Background `NotificappListenerService` with deduplication and normalization
- **Inbox** — Paginated notification list with filters (app, status) and search, time-grouped headers
- **Notification Detail** — View full notification content + rule execution results and extracted fields
- **Rule Engine** — Condition matching (6 operators) + field extraction (10 methods); `RuleMatcher` and `FieldExtractor` are pure Kotlin
- **Rules List** — View, filter, search, and toggle rules active/inactive
- **Rule Editor** — Multi-step form: name, conditions, data extraction fields, actions, app targets; supports pre-filling from a sample notification
- **Settings** — Monitored apps, data collection toggle, app icons, notification listener status
- **Snooze/Dismiss Actions** — `SNOOZE_NOTIFICATION` (configurable duration picker) and `DISMISS_NOTIFICATION`, each as a pluggable `ActionExecutor`, with per-action outcome shown in Notification Detail
- **Notification Content Filtering** — `NotificappListenerService.shouldSkipNotification` skips notifications without title/content, ongoing system notifications, and stale (>`MAX_AGE_MS`) notifications
- **Alarm Action** — `CREATE_ALARM` plays an alarm sound (system ringtone picker, defaults to the device's default alarm sound) and optionally vibrates, as a pluggable `ActionExecutor`
- **Flash Alert Action** — `FLASH_ALERT` blinks the camera torch a configurable number of times, as a pluggable `ActionExecutor`; skipped without a flash or in battery saver, with photosensitivity-safe clamps on count/duration — **Phase 1 (Complete Action System) is now fully done**
- **Backtest Against History** — Rule Editor can test an unsaved draft rule against previously captured notifications and preview matches + extracted fields before saving
- **Dry-Run Mode** — Per-rule toggle: matches are logged as `RuleExecution`s but no actions execute; surfaced via a `DryRunBadge` in Rules List and Notification Detail — **Backtesting and Dry-Run (Phase 2) is now fully done**
- **Rule Import/Export** — Versioned JSON format (`docs/rule-format.md`), export via the Android share sheet (`FileProvider`), import from file/clipboard with a validation + preview dialog; imported rules always get fresh IDs and start in dry-run mode
- **Tech-debt hardening before Phase 3** rule-sharing wire format decoupled from domain models via DTOs (`core/rulesharing/dto/`), destructive Room migration fallback gated to debug builds, backtesting query bounded, GitHub Actions CI added, `ActionBottomSheet`/`AddFieldBottomSheet` split into per-type config composables, `NotificationNormalizer` made pure Kotlin with test coverage, release/Fastlane scaffolding added, Detekt baseline shrink policy documented
- **Snooze Throttle Mode** — `SnoozeMode.THROTTLE`: lets the first notification through per rule+app within a configurable window (1-1440 min, default 10) and suppresses (`ActionOutcome.SUPPRESSED`) the rest until the window elapses or the user changes the window/re-enables the action; backed by `NotificationThrottleTracker` (in-memory, mutex-guarded, DB fallback via `RuleExecutionRepository.lastThrottleDeliveryAt` on cold start) and a `CurrentTimeProvider` clock seam; UI via `ThrottleWindowSelector` in the Snooze bottom sheet

### In Progress

Nothing currently in progress — ready to start Phase 3.

---

## Roadmap Phases

### Phase 1: Complete Action System

**Goal:** Round out the action system on top of the refactored pipeline. All four actions (Snooze, Dismiss, Alarm, Flash Alert) and per-action outcome feedback are done - Phase 1 is complete.

#### Snooze and Dismiss — **Done**

- [x] Add action configuration UI (snooze duration picker in `ActionBottomSheet`)
- [x] Implement `SNOOZE_NOTIFICATION` handler as an `ActionExecutor`
- [x] Add action configuration UI to dismiss notification
- [x] Implement `DISMISS_NOTIFICATION` handler as an `ActionExecutor`

#### Alarms — **Done**

- [x] Create form to add an alarm with options (alarm sound via system ringtone picker, vibration toggle)
- [x] Implement `CREATE_ALARM` handler as an `ActionExecutor` (plays sound + vibrates through an `AlarmPlayer` abstraction, kept testable per ADR 010's pattern)

#### Flash Alerts — **Done**

- [x] Add `FLASH_ALERT` action type: blink the camera torch when a rule matches (`CameraManager.setTorchMode` — no runtime permission required)
- [x] Configuration UI in `ActionBottomSheet`: number of flashes and flash duration (stored in `RuleAction.config`)
- [x] Implement `FlashAlertActionExecutor`; return `SKIPPED` on devices without a torch
- [x] Safety: flash count/duration are clamped server-side (not just in the UI) to a photosensitivity-safe range (max 10 flashes, min 200ms per phase -> capped at 2.5Hz); skipped entirely when battery saver is on

#### Feedback and Safety

- [x] Show per-action execution outcomes in Notification Detail screen (which actions ran, succeeded, failed) — landed early as part of Phase 0's TD-5
- [x] Unit tests for each action handler (Dismiss, Snooze, SaveData, plus the dispatcher) — landed early as part of Phase 0's TD-6

### Phase 2: Rule Trust & Community Sharing

**Goal:** Make rules easy to trust and easy to share. This is the OSS growth engine — users who can't write regex can still contribute and benefit, and every shared rule is marketing (the uBlock filter-list / TaskerNet model). Ranked ahead of webhooks because webhooks serve users who already succeeded at making rules; sharing gets people to their *first* working rule.

#### Backtesting and Dry-Run — **Done**

- [x] "Test against history" in Rule Editor: run the draft rule against captured notifications and preview matches + extracted fields before saving — `RuleEditorViewModel.testAgainstHistory()` runs the unsaved draft rule (via the existing pure `RuleEngine`) against `NotificationRepository.getNotificationsForBacktest()`, bounded to the 500 most recent and filtered to the rule's target apps at the SQL level (TD-11); results shown in `BacktestResultsBottomSheet`. Purely a preview — nothing is persisted.
- [x] Per-rule **dry-run mode**: log matches without executing actions — essential safety once dismiss exists (a bad condition silently eating notifications is the worst possible first impression). `Rule.isDryRun` gates `ProcessNotificationUseCase.evaluateAndPersist`: dry-run rules never reach `ActionDispatcher`, but the match is still recorded via `RuleExecution.wasDryRun` (also migrated in, `RuleExecutionEntity.was_dry_run`) so the flag is a snapshot at match time, not derived from the rule's current state. Toggle lives in the Rule Editor's metadata step.
- [x] Surface dry-run results in Rules List / Notification Detail so users can promote a rule to live with confidence — shared `DryRunBadge` composable (`core/ui/components/`) shown next to the rule name in the Rules list, and next to the rule name + as an explanatory note on each dry-run `ExecutionCard` in Notification Detail.

#### Rule Import/Export — **Done**

- [x] Versioned JSON rule format via a dedicated DTO layer decoupled from the domain models (TD-9); documented in `docs/rule-format.md` — `core/rulesharing/RuleJsonCodec.kt` wraps a `RuleDto` in a `{schemaVersion, rule}` envelope; decode rejects a newer-than-supported `schemaVersion` explicitly rather than guessing
- [x] Export a rule via Android share sheet — writes the JSON to a cache file, shared through a `FileProvider` (`android:authorities="${applicationId}.fileprovider"`) so no broader file access is granted
- [x] Import a rule from file/clipboard with validation + preview before saving — `RulesScreen`'s import menu (`ActivityResultContracts.OpenDocument` / clipboard read) feeds decoded text through `RuleJsonCodec.decode`, showing a preview dialog (name, condition/field/action counts, target apps) before the user confirms
- [x] Import safety: imported rules start in dry-run mode by default — `RuleJsonCodec.withFreshIdentityForImport()` also regenerates the rule's and every nested condition/field/action's ID, so importing the same file twice never collides with itself

#### Improve conditionals
- [x] Add Day of week
- [x] Add Time Range
- [x] Option to decide whether the app condition is to include or exclude the selected app(s) (e.g, trigger this rule for all the apps except the selected one)

### Phase 3: Starter Rules, Data Browser & Data Lifecycle

**Goal:** Ship the first rung of the rule-creation ladder (templates), make live rules safe against noisy apps, and build the dedicated screen for browsing, filtering, and exporting extracted data — plus the retention and backup features people need before investing hours in rules.

#### Starter Rule Templates

Guiding principle #1 puts **templates first** in the rule-creation ladder (templates → create-from-notification → raw editor), but nothing on the roadmap built them before the Phase 6 gallery. They're mostly content on top of Phase 2's import machinery, and they seed the gallery.

- [ ] Curate 5–10 starter rules for the hero use cases (bank transaction → spending row, carrier tracking, OTP auto-dismiss, noisy-app dismiss), stored as raw JSON assets in the APK using the `RuleJsonCodec` wire format — **requires the frozen DTO format from TD-9 first**
- [ ] "Start from a template" entry point in the Rules empty state and as the first option when creating a rule; template import follows the same fresh-ID + dry-run path as Phase 2 imports
- [ ] Each template doubles as a seed rule for the Phase 6 community gallery (same file format, same validation)

#### Rule Safety: Per-Rule Cooldown

With Alarm and Flash Alert live, a chatty app matching an alarm rule every 30 seconds is an uninstall-level bug — this can't wait for someone to hit it.

- [ ] `Rule.cooldownSeconds` (0 = disabled); editor UI in the metadata step next to the dry-run toggle
- [ ] `ProcessNotificationUseCase.evaluateAndPersist` skips action dispatch when the rule's latest `RuleExecution` is more recent than the cooldown — the match is still recorded, flagged as cooldown-suppressed (never fail silently, even when suppressing on purpose)

#### Data Screen (New Bottom Nav Tab)

- [ ] Add "Data" tab to bottom navigation: **Inbox → Data → Rules → Settings** (chart icon)
- [ ] Navigation route: `Screen.Data`
- [ ] Feature module: `features/databrowser/` with contract + viewmodel + UI

#### Browse and Filter

- [ ] Paginated list of extracted data entries (field name, value, source notification, rule, timestamp)
- [ ] Filter by: rule, source app, date range, field type
- [ ] Search across all extracted values (text search)
- [ ] Sort by: date, rule name, app, field name

#### Statistics

- [ ] Summary header: total extractions count, extractions this week, most active rule
- [ ] Per-rule and per-app extraction counts
- [ ] Trend: extractions over time (simple bar/line chart — last 7/30 days)

#### Export and Delete

- [ ] Delete individual entries or bulk delete with filters applied
- [ ] Export: CSV or JSON format via Android share sheet with current filters applied

#### Data Lifecycle

- [ ] **Interim retention sweep — do this first**: delete captured notifications older than 90 days on app start. The table currently grows unboundedly and stores OTPs/private messages forever, which contradicts the privacy story and is the root cause of the backtest memory issue (TD-11); the full settings UI below can follow later
- [ ] Retention settings: auto-delete captured notifications after 30/90 days/never
- [ ] Storage usage view in Settings (DB size, counts per table)
- [ ] Local backup/restore: export/import rules — and optionally extracted data — to a local file (people won't invest in rules that vanish with a lost phone; stays local-first)

### Phase 4: Webhooks

**Goal:** Allow rules to send extracted data to external services via user-managed webhooks. Primary audience: Home Assistant / self-hosting users.

#### Webhook Management

- [ ] Domain model: `Webhook` (id, name, url, headers, auth config)
- [ ] Room entity + DAO + `WebhookRepository` interface and implementation
- [ ] Webhook form screen: name, URL, custom headers (key/value pairs), optional auth (API key header, bearer token)
- [ ] Webhook list screen accessible from Settings
- [ ] Edit and delete existing webhooks
- [ ] "Send test payload" button on the webhook form

#### Webhook as Rule Action

- [ ] Add `SEND_WEBHOOK` action type, implemented as an `ActionExecutor`
- [ ] In Rule Editor action step: "Send Webhook" option opens a picker to select from saved webhooks **or** create a new one inline
- [ ] Payload customization: checkbox list to select included fields (title, content, app name, package name, timestamp, raw content, each extracted field)
- [ ] Store selected payload fields in action config

#### Webhook Delivery

- [ ] Fire webhook POST on rule match with selected payload as JSON
- [ ] Retry policy: 3 attempts with exponential backoff (1min, 5min, 30min) using WorkManager
- [ ] After 3 failures: persist the failed event in a local queue; retry queued events on app open until delivered
- [ ] **Delivery visibility**: per-webhook last-delivery status indicator (✓/✗ + timestamp) on the webhook list — silent failure is the most trust-destroying behavior an automation tool can have; the user's Home Assistant stops updating and they must be able to see why
- [ ] Unit tests for delivery, retry, and queue logic

*Note: webhooks introduce the app's first network access.*

- [ ] Disclose the change prominently (release notes) and update `PRIVACY.md` in the same PR — webhook delivery to user-configured URLs must remain the app's only network egress

### Phase 5: On-Device AI Extraction (Optional Flavor)

**Goal:** Offer on-device AI extraction as an alternative to manual field configuration. Deliberately last: it runs on a narrow device set (recent Pixels/flagships), adds a proprietary dependency, and doesn't help the median user — community rules and backtesting improve rule creation for *everyone* first.

#### Abstraction and Packaging

- [ ] `AiExtractor` interface in the core so any on-device model can plug in later — don't couple the engine to Gemini Nano
- [ ] Gemini Nano (Google AI Edge / ML Kit) as the first implementation, in a **separate build flavor** — AICore is proprietary and F-Droid won't accept it in the main flavor
- [ ] Detect availability at runtime; hide AI extraction UI on unsupported devices/flavors; manual extraction remains the universal path

#### Rule Editor Integration

- [ ] In the "Data Extraction" step, add a toggle: **Manual** | **AI Extract**
- [ ] **Manual mode**: current behavior (10 extraction methods)
- [ ] **AI Extract mode**: single `TextField` for a natural-language prompt (e.g., *"Extract the tracking number, store name, and estimated delivery time"*)
- [ ] "Test" button: runs the model against a sample notification and previews extracted fields
- [ ] AI-extracted fields mapped to existing field types (STRING, NUMBER, DATE, CURRENCY, BOOLEAN) — inferred by the model, overridable by user
- [ ] Store the prompt in the rule for re-execution on future notifications

#### Execution

- [ ] When a rule with AI extraction matches, run the stored prompt with the notification content through `AiExtractor`
- [ ] Parse model output into typed field values, persisted through `RuleExecutionRepository`
- [ ] Unit tests for prompt output parsing and field mapping

---

### Phase 6: Extra features

#### Community Rules Gallery

- [ ] `rules/` directory in the GitHub repo with community-contributed, tested rules for popular apps (e.g., "Revolut transaction", "DHL tracking", "Amazon delivery"), organized by app/category
- [ ] Contribution guide + PR template for submitting rules
- [ ] In-app: "Browse community rules" entry point that explains how to import gallery rules (no network access in-app — user downloads/pastes JSON, keeping the local-first promise)
- [ ] CI validation of `rules/*.json`: a JVM test decodes every gallery file through `RuleJsonCodec` so a malformed contribution can't merge (TD-9's wire format and TD-12's pipeline are both done, so this is now unblocked)

### Repo Infrastructure

- [x] README (privacy-first pitch, hero use cases, build instructions, docs index)
- [ ] Record the 30-second demo GIF (bank notification → spending row) and embed it at the marked TODO in `README.md`
- [x] `CONTRIBUTING.md` (code contributions + rule-gallery contributions)
- [x] Issue templates (bug, feature, rule request) and PR template in `.github/`
- [x] `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1)
- [x] `SECURITY.md` (private vulnerability reporting policy)
- [x] CI: Spotless + Detekt + unit tests + debug build on every PR (`.github/workflows/ci.yml`, TD-12)
- [x] License decision — **GPL-3.0** (`LICENSE`): keeps forks open-source, fits the F-Droid audience and trust story

### Trust & Privacy

- [x] `PRIVACY.md`: exactly what the notification-listener permission is used for, what is stored, and that nothing leaves the device (except user-configured webhooks from Phase 4) — the permission sees OTPs and private messages; proactive transparency separates "trusted OSS tool" from "sketchy notification app"
- [ ] Link the privacy statement from onboarding and Settings
- [ ] Quick Settings tile to pause/resume all monitoring — cheap to build, gives privacy-conscious users a visible kill switch

### Distribution

- [ ] **F-Droid as the primary channel** — local-first, no trackers, notification-listener-based is an F-Droid darling profile, and it's where the target audience lives
- [ ] Reproducible builds; keep proprietary deps (AICore) out of the main flavor
- [ ] Fastlane metadata for the F-Droid listing — descriptions and versioning/release-checklist scaffolding done (`fastlane/metadata/`, `docs/RELEASING.md`, TD-15); screenshots still needed

---

## Idea Backlog (unscheduled)

Recorded so they aren't lost; none are committed. Promote to a phase only when there's a clear user story and it survives the guiding principles (local-first, simplicity, never fail silently).

### Actions
- **Read-aloud (TTS)**: speak extracted fields on match ("Received 45 euros from Maria") — hands-free/accessibility value, fully local
- **Copy field to clipboard**: e.g., tracking numbers ready to paste (exclude sensitive categories by default; Android 13+ clipboard redaction)
- **Post a summary notification**: replace a noisy notification with a clean, renamed one built from extracted fields ("re-writer" use case)
- **Add to calendar**: extracted date/time → calendar event via `Intent` (no permission needed with the insert intent)
- **Broadcast intent for automation apps**: fire a local broadcast/intent with extracted data as extras so Tasker/MacroDroid/Automate users can chain workflows — cheap to build, huge for the power-user audience, zero network
- **Vibration pattern** per rule (subtle alternative to flash/sound)
- **Notification reply/interaction** — Send replies or interacting with notification actions

### Data & insights
- **Daily/weekly digest notification**: "This week: 23 transactions, €412 total" — computed locally from extracted values
- **Home screen widget**: latest extracted values or per-rule counters
- **Auto-export to a folder (SAF)**: append extracted data to a CSV/JSON file in a user-chosen directory — pairs perfectly with Syncthing/Obsidian users without the app ever touching the network

### Rules
- **Per-rule health stats**: "matched 0 times in 30 days" indicator in the Rules list — a dead rule silently not firing violates "never fail silently", and the `RuleExecution` data to compute it already exists
- **Time-window conditions**: rule active only during set hours/days
- **OR condition groups**: currently conditions are AND-only (feeds the ADR 011 schema decision)

### Privacy
- **Raw-content retention per rule**: keep only extracted fields, auto-delete the raw notification after extraction — minimizes stored sensitive text
- **Biometric app lock**: gate the app behind fingerprint/face unlock given the sensitivity of stored data

---

## Out of Scope (MVP)

Explicitly **not** part of the MVP; may be revisited post-launch:

- **Cloud sync / multi-device** — app is local-first only
- **User accounts / login** — no authentication system
- **Cloud-based AI** — no Gemini Pro API or cloud fallback for unsupported devices
- **Play Store release** — F-Droid first; Play Store distribution deferred
- **Advanced analytics** — no ML-based insights, trend predictions, or anomaly detection beyond basic statistics

---

## Technical Notes

| Area | Decision |
|---|---|
| Notification pipeline | `ProcessNotificationUseCase` orchestrates normalize → dedupe → persist → match → extract → act; listener service is a thin adapter |
| Action execution | `ActionExecutor` per `ActionType` via Hilt multibindings; system actions behind `SystemNotificationController` implemented by the listener service |
| Execution records | Per-action outcome (`SUCCESS`/`FAILED`/`SKIPPED`) stored on `RuleExecution` |
| Rule sharing | Versioned JSON wire format defined by dedicated DTOs in `core/rulesharing` (TD-9), decoupled from domain models and pinned by a golden-file test; spec in `docs/rule-format.md`; imported rules default to dry-run |
| Rule storage | If rule shape keeps churning (OR-groups, nesting), consider JSON-column rule definition + thin queryable metadata instead of 5 normalized tables — reevaluate before Phase 2 |
| Webhook delivery | WorkManager retries; Room table for failed event queue; per-webhook last-status indicator |
| On-device AI | `AiExtractor` abstraction; Gemini Nano impl in separate build flavor (F-Droid-safe main flavor) |
| Data browser | Paging3 consistent with Inbox; reuse extracted-field storage via `RuleExecutionRepository` |
| Export | Android `ShareCompat` / share sheet intent with temp file URI |
| Bottom nav | 4 tabs: Inbox, Data, Rules, Settings |
| Payload customization | Checkbox field selection stored as list in `RuleAction.config` |
| Action config | Keep `Map<String, String>` (schemaless = no migrations for new action types); wrap access in small typed readers per action type |
