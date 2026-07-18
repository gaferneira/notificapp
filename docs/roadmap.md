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

## Shipped

Areas already built and in the app, one line each. **For the full functional detail of what each does, see [`capabilities.md`](capabilities.md)** — that is the present-tense record of the app's behavior and the doc kept in sync with the code. This roadmap stays forward-looking; it does not duplicate feature detail here (that duplication had already drifted out of sync).

- **Foundation** — MVI + feature-first architecture, Hilt DI, Room (13 entities, incl. FTS4 search tables), Navigation3; onboarding, app selection, notification capture (dedupe + normalization), inbox, notification detail, rules list, multi-step rule editor, settings
- **Rule engine** — condition matching (6 operators) + field extraction (10 methods), pure-Kotlin `RuleMatcher`/`FieldExtractor`
- **Action system** — dismiss, snooze (duration / scheduled / throttle), alarm (with full-screen UI + built-in snooze), flash alert, per-action cooldowns, extract-data; per-action outcomes (`SUCCESS`/`FAILED`/`SKIPPED`/`SUPPRESSED`) surfaced in detail
- **Rule trust & sharing** — backtest against history, dry-run mode, versioned JSON import/export (`RuleJsonCodec`, golden-file-pinned), improved conditionals (day-of-week, time-range, include/exclude app scope)
- **Starter templates & data browser** — curated starter rules; Data tab with browse/filter/search, statistics (trend computed, chart pending), CSV/JSON export, single + bulk delete, retention sweep + settings, storage usage view
- **Webhooks** — webhook library (methods, headers, auth, query params, test payload), `SEND_WEBHOOK` action (fields + template payload modes), WorkManager delivery with retry/backoff + local queue + per-webhook status
- **Repo, trust & privacy** — README + `CONTRIBUTING`/issue+PR templates/`CODE_OF_CONDUCT`/`SECURITY`, CI (Spotless + Detekt + tests + debug build), GPL-3.0 license, `PRIVACY.md`
- **Tech-debt hardening** — see the Detekt/architecture baseline policies in `CLAUDE.md` and git history for the TD-1..16 record

---

## v1 — First Release

Everything committed to the first public (F-Droid) release ships together here. This is the single source of truth for what's left before launch.

### Structural locks (decide the shape before users have data to migrate)

- [ ] **Nested condition groups** — commit the recursive condition shape in the domain model + wire format, modeled as a group node inside the existing `payload` JSON so **no Room migration** is needed. UI may expose only a single level of grouping — or stay hidden — at first; the point is to freeze the *shape* so v1 shared rules never need a format migration later. *(Was tracked as the "condition groups" idea; resolves the "reevaluate before Phase 2" Technical Note.)*
- [ ] **Per-rule raw-content retention flag** — boolean on `Rule` ("keep only extracted fields, delete the raw notification after extraction"). Rule-model + wire field and a privacy win.

### Rule creation & trust (the differentiator)

- [ ] **"Start from a template" as the first option in the create-rule flow** (FAB/RuleEditor) — Guiding Principle #1's "templates first"; seed templates already exist, this only wires the entry point. Highest activation ROI on the board.
- [ ] **Read-aloud (TTS) action** — speak extracted fields on match ("Received 45 euros from Maria"); fully local, accessibility/hands-free differentiator. Additive (schemaless action config), committed to v1 by product choice rather than structural necessity — first candidate to cut if the timeline slips.
- [ ] **Notification reply/interaction** — send replies or trigger a notification's own actions. RemoteInput + per-app fragility; not core.

### Correctness (blocks the flagship use case)

- [ ] **`extractCurrencyValue` drops thousands-separated amounts** — `ExtractedFieldValueMapper.extractCurrencyValue` replaces every comma with a dot and then parses the first `[\d.]+` run, so `"$1,234.56"` becomes `"1.234.56"` → `toDoubleOrNull()` returns null and the CURRENCY field silently degrades to text-only (no queryable `value_number`). Same failure for European `"1.234,56 EUR"`. Needs locale-aware separator handling. Pure parser fix — no schema change, no migration — but it silently breaks the **expense-tracking hero use case** on the most common bank-notification format, and does so precisely in the `1.234,56` locale we ship at launch (Spanish). Not post-v1 polish.

### UI, design system & i18n

- [ ] **Design System & UI consistency pass** — centralize spacing/typography/color-role tokens in the theme, extract repeated composables (cards, badges, list rows, bottom sheets) into one component set, standardize empty/loading/error states across all four tabs, finish edge-to-edge + Material 3 dynamic color, one accessibility/touch-target + light-dark parity pass. Touches no schema.
- [ ] **Full internationalization** — extract every hardcoded UI string into `strings.xml` (enforce "no literal UI strings" during the design-system pass, since it already visits every screen), make layouts locale-safe (RTL readiness, no fixed-width truncation), and ship at least one complete translation (**Spanish**) alongside English at launch.

### Privacy & safety

- [ ] **Link the privacy statement from onboarding and Settings** — cheap trust win for a listener app that sees OTPs and private messages.
- [ ] **Quick Settings tile to pause/resume all monitoring** — cheap to build, gives privacy-conscious users a visible kill switch.

### Release mechanics

- [ ] **fastlane + CI for release** — automate build/sign/publish.
- [ ] **F-Droid listing** — primary channel; local-first, no-tracker, notification-listener profile is an F-Droid darling. Fastlane metadata descriptions and versioning/release-checklist scaffolding are done (`fastlane/metadata/`, `docs/RELEASING.md`, TD-15); **screenshots still needed**.
- [ ] **30-second demo GIF** (bank notification → spending row) embedded at the marked TODO in `README.md`.

---

## Post-v1

Additive and migration-safe by design (new action types use schemaless config; new condition families use the `payload` JSON column). No launch payoff to pulling these forward — drip them out after v1.

### More actions

- **Copy field to clipboard** — e.g. tracking numbers ready to paste (exclude sensitive categories by default; Android 13+ clipboard redaction)
- **Add to calendar** — extracted date/time → calendar event via the insert `Intent` (no permission needed)
- **Broadcast intent for automation apps** — fire a local broadcast/intent with extracted data as extras so Tasker/MacroDroid/Automate users can chain workflows; cheap, big for the power-user audience, zero network
- **Post a summary notification** — replace a noisy notification with a clean, renamed one built from extracted fields (the "re-writer" use case)
- **Vibration pattern** per rule — subtle alternative to flash/sound

### Data & insights

- **Trend chart rendering** — visualize the already-computed 7/30-day trend series as a bar/line chart in the Data Browser
- **Daily/weekly digest notification** — "This week: 23 transactions, €412 total", computed locally from extracted values
- **Per-rule health stats** — "matched 0 times in 30 days" indicator in the Rules list; a dead rule silently not firing violates "never fail silently", and the `RuleExecution` data already exists

### Backup & data model

- **Local backup/restore** — export/import rules (and optionally extracted data) to a local file; people won't invest in rules that vanish with a lost phone. Stays local-first — keep the backup file format stable from the first version.
- **Optional `value_currency` column** — only if cross-currency grouping is ever built; additive nullable column, back-fillable from `value_text`.

### Community Rules Gallery

The long-term moat, but it compounds *after* v1 has users making rules; the shipped starter templates bridge the gap until then.

- `rules/` directory in the GitHub repo with community-contributed, tested rules for popular apps (e.g. "Revolut transaction", "DHL tracking", "Amazon delivery"), organized by app/category
- Contribution guide + PR template for submitting rules
- In-app "Browse community rules" entry point that explains how to import gallery rules (no in-app network — user downloads/pastes JSON, keeping the local-first promise)
- CI validation of `rules/*.json`: a JVM test decodes every gallery file through `RuleJsonCodec` so a malformed contribution can't merge (wire format and pipeline are both done, so this is unblocked)

---

## Later / Not planned

### Deferred features

- **Home screen widget** — latest extracted values or per-rule counters. Real surface-area cost (RemoteViews, widget lifecycle) for low leverage vs. templates and sharing.
- **Auto-export to a folder (SAF)** — append extracted data to a CSV/JSON file in a user-chosen directory; niche, and the manual share-sheet export already covers the need.

### On-Device AI Extraction (future optional flavor)

Deliberately last: it runs on a narrow device set (recent Pixels/flagships), adds a proprietary dependency, and doesn't help the median user — community rules and backtesting improve rule creation for *everyone* first.

- `AiExtractor` interface in the core so any on-device model can plug in later — don't couple the engine to Gemini Nano
- Gemini Nano (Google AI Edge / ML Kit) as the first implementation, in a **separate build flavor** — AICore is proprietary and F-Droid won't accept it in the main flavor
- Runtime availability detection; hide AI extraction UI on unsupported devices/flavors; manual extraction stays the universal path
- Rule Editor: a **Manual | AI Extract** toggle in the Data Extraction step; AI mode is a single natural-language prompt `TextField`, a "Test" button that previews against a sample, fields mapped to existing types (inferred, user-overridable), prompt stored in the rule
- Execution: run the stored prompt through `AiExtractor` on match, parse output into typed field values persisted via `RuleExecutionRepository`; unit tests for parsing and field mapping

### Out of scope (architectural non-goals)

Explicitly **not** part of the MVP; may be revisited post-launch:

- **Cloud sync / multi-device** — app is local-first only
- **User accounts / login** — no authentication system
- **Cloud-based AI** — no Gemini Pro API or cloud fallback for unsupported devices
- **Play Store release** — F-Droid first; Play Store distribution deferred
- **Advanced analytics** — no ML-based insights, trend predictions, or anomaly detection beyond basic statistics

---

## Technical Notes

Only the decisions that **constrain not-yet-built work** live here. For how already-shipped internals are wired (pipeline, action execution, webhook delivery, data browser, export, nav), see `docs/ARCHITECTURE.md`, the ADRs, and `capabilities.md`.

| Area                     | Constraint on future work                                                                                                                                                                                                                                                            |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Rule / condition storage | Conditions live in a JSON polymorphic `rule_conditions.payload` column, so new condition families never need a migration. **Nested condition groups (v1 structural lock)** must be modeled as a recursive group node inside that same payload, freezing the shape before launch so shared rules stay forward-compatible. |
| Action config            | Keep `RuleAction.config` schemaless (`Map<String, String>`) so **post-v1 action types add no migration**; wrap access in small typed readers per action type. This is what makes the "drip new actions after launch" plan safe.                                                        |
| On-device AI             | Future optional flavor must go through an `AiExtractor` abstraction (don't couple the engine to Gemini Nano); the Gemini Nano impl ships in a **separate build flavor** to keep the main flavor F-Droid-safe.                                                                          |
