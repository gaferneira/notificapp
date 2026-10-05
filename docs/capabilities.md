# Application Functional Capabilities

Notificapp lets users create automation rules that act on the notifications their phone receives — dismissing noise, snoozing, sounding an alarm, or flashing a visual alert — and, uniquely, extract structured data out of a notification's text (like turning a bank alert into a spending record). Everything runs locally on the device, with no account, cloud sync, or network access required.

---

## Core Automation

### Rule Creation & Editing
* **User Experience:** One editor with two presentations of the same sections (When, Do, Name, Settings), chosen once when it opens. **Creating from scratch** is a guided 3-step flow with a labeled step indicator (When, Do, Review; completed steps are tappable) and a sticky bottom bar with Next/Back and Save: step 1 picks apps, conditions, match logic and can test the draft against history; step 2 lists the actions (including Extract data); step 3 names the rule (optional description/category), shows a plain-language summary ("When a notification from X matches Y, then Z") and the settings: dry-run (off by default for new rules; when on, matches are only recorded and no actions run) and, only when the rule extracts data, "delete raw content after extraction". **Editing an existing rule, or creating one from a template or a captured notification** (content prefilled) is a single scrollable page with Name, When, Do and Settings cards, a sticky Save/Cancel bar, a "Needs attention" card that jumps to the section with the problem, a one-line hint on templates that still need apps chosen, a dismissible hint when a condition was pre-filled from the source notification (its title, or the first line of its text; the source app is preselected), and an overflow menu holding Delete. Back steps through the guided flow, then asks to discard unsaved changes. **Validation** (`RuleValidation`): saving requires a name and at least one enabled action, and a rule with no conditions that applies to all apps (or all apps except some) is blocked as too broad; in the guided flow Next is disabled with a short reason while the current step is invalid, and the review step lists earlier-step problems with a button to jump back. A rule with no conditions on specific included apps is allowed with a non-blocking warning.
* **System Trigger:** User taps "New rule" on the Rules screen, which opens the Rule Templates screen (curated starter templates first, "Start from scratch" as a pinned secondary action); or taps "Create rule" from a notification's detail view (the single entry point: the button in the empty "No rules matched" state, or the floating button once rules matched; goes straight to a pre-filled blank editor).
* **Technical Spec Reference:** `openspec/specs/rule-action-authoring/`, `openspec/specs/rule-storage/`

### Rule Templates
* **User Experience:** A full-screen gallery of curated starter rules with single-select category filter chips (All plus each category) and each template's full description. Tapping a template opens the rule editor pre-populated and unsaved (nothing persists until Save; templates start live, dry run off, while rules imported from a file or the clipboard always start in dry-run); "Start from scratch" is pinned at the bottom. The gallery is removed from the back stack on selection, so saving or backing out of the editor returns to the originating screen.
* **System Trigger:** "See more templates" on Home, or "New rule" / "Import from templates" on the Rules screen.

### Matching Conditions
* **User Experience:** The user specifies what a notification must look like to match a rule by adding one or more conditions, drawn from three editable families. Multiple conditions on the same rule are combined with a per-rule combinator (`ALL` = every condition must match, `ANY` = at least one condition must match). The three condition families are:
  * **Content match** — a notification property compared against a value with an operator:
    * Properties that can be checked: Title, Main text/content, Raw content (the full raw notification text), App name, Package name
    * Operators available: Contains, Does not contain, Starts with, Ends with, Equals (exact match), Matches regex (pattern match)
  * **Day of week** — matches when the current day is one of a chosen set of weekdays. Choosing zero days matches no day (fail-closed), not every day.
  * **Time range** — matches when the current time falls within a start/end time, inclusive. A range where the end is earlier than the start wraps across midnight (e.g. 22:00–06:00); a range where start equals end matches only that exact instant.
  * **Group** (`RuleCondition.Group`) — a nested, recursively-structured set of conditions combined by its own ALL/ANY combinator, letting a rule express `(A AND B) OR C`-style trees. Not creatable or editable in this app version's editor — it exists so a rule imported from a shared file (or a future version's group editor) round-trips without silently losing conditions. The rule editor shows it as a read-only summary row ("Group: N conditions (ALL/ANY)") that can be removed like any other condition but not opened for editing. Nesting is capped at 5 levels deep on import (`RuleWireMapper.MAX_CONDITION_DEPTH`) to prevent a hand-crafted file from being a stack-overflow DoS.
* **System Trigger:** User adds/edits a condition inside the Rule Editor's "Matching Logic" step.
* **Technical Spec Reference:** `openspec/specs/rule-conditions/`

### Data Extraction
* **User Experience:** As one of a rule's actions, the user can configure it to pull specific named pieces of information out of a notification and save them for later viewing. The editor offers a live preview against sample text and can auto-suggest fields from a sample notification.
  * Extraction methods available:
    * Fixed position — characters between a start and end index
    * Text between anchors — whatever sits between two marker strings
    * Regex pattern — a capture group from a regular expression
    * Text after keyword — everything after a given keyword (optionally length-capped)
    * Text before keyword — everything before a given keyword
    * Line extraction — a specific line by line number
    * Split by delimiter — split the text and take the Nth part
    * JSON path — a value from structured/JSON-shaped content by dot-notation path
    * Smart amount detection — automatically finds a currency amount, no configuration needed
    * Smart date detection — automatically finds a date, no configuration needed
  * Each extracted field also has a data type: String, Number, Date, Currency, or Boolean.
* **System Trigger:** User adds an "Extract data" action while building or editing a rule; the extraction itself runs automatically in the background whenever a matching notification arrives.
* **Technical Spec Reference:** `openspec/specs/rule-action-authoring/`

### Post-Extraction Privacy (Delete Raw Content)
* **User Experience:** A per-rule "Delete raw content after extraction" toggle in the Rule Editor's options section. When enabled, once the rule matches and actually extracts data, the source notification's original text (raw content, main text, and title) is scrubbed - only the extracted fields remain. The notification row itself and its rule executions are kept, so history and extracted data stay intact; only the free-text OTP/message content is removed.
* **System Trigger:** Runs automatically, right after a matching rule's executions are persisted, but only when the rule is non dry-run, the toggle is enabled, and at least one field was actually extracted (a match that extracted nothing never scrubs, since there'd be nothing left to explain the notification).
* **Technical Spec Reference:** `domain/model/Rule.kt`, `core/notification/ProcessNotificationUseCase.kt`, `domain/repository/NotificationRepository.kt`

### Notification Actions
* **User Experience:** For a matching notification, the user picks one or more actions to run. Each action can be turned on or off independently within a rule.
  * **Dismiss notification** — silently removes it from the system tray (good for noise like OTP codes or spam).
  * **Snooze notification** — hides it and re-delivers it later, in one of three modes:
    * Duration — snooze for a fixed number of minutes from the time of the match
    * Scheduled — deliver at specific times of day, or on a recurring interval within a time window, optionally restricted to certain weekdays
    * Throttle — let the first match through, then suppress further matches from that rule+app until a configurable window elapses
  * **Create alarm** — plays a sound and/or vibrates, with options:
    * Custom or default alarm sound (via the system ringtone picker)
    * Choice of vibration pattern
    * Optional full-screen alarm UI (can wake/unlock-prompt the screen) with a customizable background
    * Its own built-in snooze (duration + max snooze count)
    * Optional cooldown (in seconds, 0 = disabled): a chatty source app re-matching this rule within the window is suppressed instead of re-ringing
    * Automatically stops if the user dismisses the source notification (swipe, clear-all, or tap-to-open) — but not if a rule's own Dismiss action removes it, so a rule can pair Dismiss + Create Alarm without the alarm instantly silencing itself
  * **Flash alert** — blinks the camera flash/torch a configurable number of times as a visual alert; automatically skipped on devices with no flash or when battery saver is on, and safety-clamped to avoid photosensitivity risk. Also supports an optional cooldown (in seconds, 0 = disabled), same suppression behavior as the alarm's.
  * **Read aloud** — speaks a short, user-authored text template out loud via on-device text-to-speech (no cloud, no network) when the rule matches. The template uses the same `{{token}}` placeholder syntax as the Send Webhook TEMPLATE mode (built-in notification fields plus any extracted data fields), e.g. `Received {{field.amount}} from {{field.sender}}`. A blank template, or one whose placeholders all resolve to nothing, is skipped rather than speaking silence.
  * **Send reply (BETA)** — replies to the source notification via its Android direct-reply (`RemoteInput`) action, with a user-authored text template using the same `{{token}}` placeholder syntax as Read Aloud/Send Webhook. Marked BETA and shown with a Beta badge in the UI because it only works on apps that expose a RemoteInput reply action on their notification (many don't); on any other app it silently does nothing rather than failing — a `SKIPPED` outcome visible in the notification detail's per-action outcomes. Exempt from the starter-template coverage guarantee (`domain/model/BETA_ACTION_TYPES`).
  * **Extract data** — see "Data Extraction" above.
* **System Trigger:** Runs automatically in the background the moment a monitored notification matches an enabled (non dry-run) rule.
* **Technical Spec Reference:** `openspec/specs/action-execution/`, `openspec/specs/snooze-scheduling/`, `openspec/specs/alarm-playback/`, `openspec/specs/alarm-fullscreen-ui/`

### Rule Testing & Safety
* **User Experience:** Before trusting a new rule, the user has two safety nets:
  * **Test against history** — preview which previously captured notifications would have matched and what data would have been extracted, without anything actually running or saving.
  * **Dry-run mode** — flag the whole rule so it logs matches without ever performing its actions, letting the user validate it safely before turning it fully on.
* **System Trigger:** User taps "Test against history" in the Rule Editor, or toggles the Dry-run switch when saving a rule.

### Rule Sharing (Import/Export)
* **User Experience:** The user can export any rule as a shareable file (via the standard Android share sheet) and import a rule shared by someone else, previewing and confirming it before it's added. Imported rules always start disabled from acting until reviewed.
* **System Trigger:** User opens a rule from the Rules list and taps "Share" in the Rule Details overflow menu, or taps "Import" on the Rules screen and selects a shared file/clipboard text.

---

## User Interface & Setup

### Onboarding
* **User Experience:** On first launch, the user sees a short explanation of what the app does (step 1 of 3), then a permission step (step 2 of 3) that explains why notification access is needed, discloses that notification content stays on the device (no analytics; only webhooks the user sets up send data out) and links to the Privacy Policy, and opens the app's own notification-access screen in system settings (API 30+ deep link, with fallbacks). Returning with access granted moves the user to App Selection (step 3 of 3). Setup is only "complete" once access is on AND at least one app is saved: app selection is opt-in and starts with nothing selected, so a user who quits on App Selection returns there on the next launch.
* **Back / resume behavior:** System and predictive back on step 2 returns to step 1 instead of exiting the app. The current step and the "opened system settings" flag survive process death. If the user returns from settings without granting access, an inline hint explains it.
* **Routing:** `MainViewModel` is the single owner of the onboarding -> App Selection -> Home transition. First-time users (no saved apps) see onboarding; users who already saved apps go straight to Home even if access was later revoked, where Home's access-off banner offers re-enabling.
* **System Trigger:** App opened with no saved apps; permission status is re-checked each time the app resumes.

### App Selection
* **User Experience:** The user picks which installed apps Notificapp should monitor, searching and toggling apps in a list. Only notifications from selected apps are captured and can be used in rules.
* **System Trigger:** Shown right after onboarding, or reopened anytime from Settings.

### Home Dashboard
* **User Experience:** The user opens the app to a launch summary: a monitoring status banner
  (listener active/inactive, monitored-app count, rule count); for zero-rule users, a "Get started"
  checklist (notification access, monitored apps, first rule) whose active step hosts curated
  templates ("Create rule from this" / "Create from scratch" / "See more templates") and replaces
  the banner; for users with rules, a "Recurring Notifications" section suggesting rules for
  repeated, un-automated notification patterns ("Create rule from this" / "Skip similar"); a "This
  Week" summary (records / rules fired); a persistent "New rule" button (opens the templates screen; hidden while loading, on error, and during first run); and a "Recent Activity" feed of recent
  rule executions, each opening notification detail, with a "See all" action that pushes Inbox as a
  stacked screen. While data loads Home shows a centered progress indicator; if observation fails
  it shows an error message with a "Retry" button instead of a blank screen. The banner turns into a
  warning with an "Enable access" action when notification access is off, or a "Choose apps" action
  when access is on but no app is monitored, a "Monitoring paused" banner with a "Resume" action
  while global monitoring is paused, and a battery
  optimization hint (opens the system battery-optimization list) appears when access is on, past
  first run, and the app is not exempt from battery optimization.
* **System Trigger:** User opens the app.
* **Technical Spec Reference:** `openspec/changes/home-screen-nav-replacement/specs/home-dashboard/`

### Recurring Notification Suggestions
* **User Experience:** When the same kind of notification arrives at least 4 times over 2 weeks on
  at least 2 different days from an app no rule covers, Home offers to turn it into a rule;
  tapping "Skip similar" hides that pattern permanently.
* **System Trigger:** User opens the app (Home dashboard); the suggestion is recomputed on each
  app launch/resume.
* **Technical Spec Reference:** `openspec/changes/home-screen-nav-replacement/specs/recurring-notification-suggestions/`

### Notification Inbox & Detail
* **User Experience:** The user browses a time-grouped list of every captured notification, with:
  * Search by text
  * Filter bottom sheet (scrollable, with a pinned footer: Clear all + "Show N notifications" live match count; edits are a draft that only applies on confirm, and the applied filter is persisted):
    * Status: single-select All / Processed / Unprocessed
    * Apps: pick one or more source apps from a searchable list showing each app's notification count (notifications from ANY selected app)
  * Active filters shown as dismissible chips under the search field (tap to remove one), with a count badge on the filter button
  * When a filter or search hides every notification, a "Nothing matches your filters" state with a "Clear filters" action that resets the filters and the search at once (distinct from the "No notifications yet" state)
  * A warning banner if notification access has been revoked
  * Tapping an item opens Notification Detail: the source app, absolute and relative time, and the full selectable text (long content collapses with Show more). If a rule removed the content after extraction, the card says which rule did so instead
  * Below it, a "Matched rules" history: each stored match opens its rule, shows a dry-run note when actions did not run, the extracted fields (name, type, value) and the actions that ran with a labeled outcome (Succeeded, Failed, Skipped, Suppressed, or "No result recorded" for old rows); deleted rules, fields and actions show "Deleted rule" / "Removed field" / "Removed action"
  * "Test current rules" re-evaluates the active rules against the stored notification without saving anything or running actions, and shows the result in a bottom sheet: per rule, each field as Unchanged / Changed (was, now) / New / Removed, the actions it would run, rules that match now but were never recorded, and recorded rules that no longer match. "Update extracted data" (after a confirmation that actions won't run) replaces the stored values of existing matches only; it is unavailable when the content was removed
  * Top-bar menu: Open app (only if the app is launchable) and Delete (with confirmation). A notification that is missing or deleted while open shows a message with "Go back"; a read failure offers Retry
  * Create rule from this notification
* **System Trigger:** User opens the app to the Home dashboard; the Inbox opens from Home's "See all" or from a recent-activity row; taps a notification to see details; taps "Test current rules" to preview what the current rules would extract.

### Rules Management
* **User Experience:** The user views all their rules in one list, with:
  * Search by name, description, or category (with a clear button)
  * "Filter & sort" bottom sheet (scrollable, with a pinned footer: Clear all + "Show N rules" live match count):
    * Status: single-select All / Enabled / Disabled
    * Categories: multi-select chips with rule counts, plus an "Uncategorized" option for rules without a category
    * Apps: selected apps shown as removable chips; "Choose apps" opens a searchable list (icon, name, rule count) grouped into "Used by rules" and "Other monitored apps". An app filter matches rules that **mention** the app in their target list (include or exclude mode); several apps combine with OR. Global rules do not match by default. The toggle "Also show rules that apply to all apps" (shown once an app is selected) additionally includes every rule that would run for the app: global rules and exclude-mode rules that do not exclude it
    * Sort by: category (default; uncategorized last), name A-Z/Z-A, newest/oldest created, recently updated, or enabled-first. Sort is not a filter: it is not counted in the filter badge and "Clear all" / "Clear filters" keep it
  * Active filters appear as dismissible chips under the search bar (tap one to remove just that filter); the toolbar badge shows the number of active filter dimensions
  * The applied filter and sort are remembered across app restarts
  * Compact rows: icon, name (with a dry-run badge when applicable), one-line description, and app scope plus category; the inline enable/disable toggle works without deleting the rule
  * Distinct empty states: "No rules yet" (with a link to templates) versus "No matching rules" when a search or filter hides everything (with "Clear filters")
  * Tapping a rule opens its read-only Rule Details screen (see "Rule Details" below); Import is available from the top bar (see "Rule Sharing" below)
* **App Scope:** Each rule can target apps in one of three modes:
  * All apps — no app restriction
  * Include-list — rule fires only for the listed apps
  * Exclude-list — rule fires for every app except the listed ones
* **System Trigger:** User navigates to the Rules tab.
* **Technical Spec Reference:** `openspec/specs/rule-app-scope/`

### Rule Details
* **User Experience:** Tapping a rule in the Rules list opens a read-only details screen instead of the editor, so an accidental tap cannot start editing. It shows the rule's name, category, description and dry-run state, an enable/disable switch, a compact Statistics card (matches in the last 7 / 30 days and in total, when the rule last triggered, and the test-mode vs live split, emphasizing test-mode matches while in dry run; an empty state when it has never matched; a caption notes counts only cover notifications still stored, so retention auto-delete can lower them), and created/updated dates. A plain-language Summary card sits after the banner and statistics: one sentence ("When a notification from <apps> matches <conditions>, then <actions>", built by the same shared summary the rule editor's review step uses, but listing every action and marking disabled ones "(disabled)") plus the app scope as chips (All apps / Only these apps / All apps except, each app with its icon, up to 6 then a "+N more" chip); it replaces the former separate Apps card. Below it, the WHEN section (conditions with ALL/ANY logic and exact operators/values) and the DO section (actions with disabled ones marked and the extracted fields of "Extract data") remain as detail, each behind an accessible expandable header with a count: expanded by default, collapsed by default only for long rules (more than 3 conditions or more than 3 actions, disabled ones included), and the user's toggle survives rotation. While the rule is in dry-run (test) mode, a prominent banner explains that matches are logged but no actions run (with the number of test-mode matches so far) and offers a "Go live" button; it opens a confirmation dialog listing the rule's enabled actions (and noting raw-content deletion when enabled), and confirming saves the rule with dry run off and shows a confirmation message (turning dry run back on is done in the editor). The top bar offers "Edit" (opens the Rule Editor) and an overflow menu with "Share" (Android share sheet with the rule's JSON) and "Delete" (with a confirmation dialog). The screen observes the rule, so edits made in the editor appear on return, and it closes itself if the rule is deleted (from here or from the editor).
* **System Trigger:** User taps a rule row on the Rules screen.

### Settings
* **User Experience:** A sectioned screen (Monitoring, Integrations, Appearance, Data, About). Monitoring: a **Pause monitoring** switch (same global state as the Quick Settings tile; Home shows a "Monitoring paused" banner with Resume), notification-access status (re-enable if revoked), the monitored-apps summary with a link to manage them, and a battery-optimization row that opens the system exemption prompt. Integrations: webhook management. Appearance: **Theme** (System / Light / Dark) and **Language** (System / English / Spanish), both applied live. Data: notification retention (30 days / 90 days / Forever, swept on app start and whenever the setting changes), a storage usage summary (database size, row counts per data type), and **Clear all data** behind a confirmation dialog. About: version, privacy policy link, and open-source licenses link.
* **System Trigger:** User navigates to the Settings tab.

### Data Browser
* **User Experience:** The user browses every piece of data their rules have extracted (field name, value, source app, rule, timestamp), in a paginated list newest-first by default. They can:
  * Filter by any combination of rule, source app, and date range from a filter bottom sheet (edits are a draft that only applies on Apply; no live result count). The rule picker lists only rules that extract at least one field (Extract data), so every choice can return results; dates are chosen with a Material date range picker, inclusive of the first and last selected day in local time. Field type remains a domain-level filter and is not exposed in the UI
  * See the active filters as dismissible chips under the search field (one per rule, per app, and the date range; tap to remove just that value) with a count badge on the filter button, so the scope of Export, bulk delete, and stats is always visible
  * The filter and search are session-only: held in the Data screen state, lost when leaving the tab, never persisted
  * Search extracted values with free-text search (FTS4-backed)
  * Sort by date, rule name, app, or field name
  * See a plain-text stats header: total extractions, extractions this week, most active rule (no chart rendering yet — trend data is computed but not visualized, see `docs/roadmap.md`)
  * Delete a single entry directly from the list
  * Bulk-delete everything matching the current filters, after a confirmation dialog showing the exact affected count — the delete always targets the previewed ID set, so data arriving between preview and confirmation is never swept in
  * Export the currently filtered set as CSV or JSON via the Android share sheet; export streams in fixed-size batches so it never materializes the full result set in memory, even for tens of thousands of rows
  * Two distinct empty states: "No extracted data yet" (explains Extract data fields, with a "Go to Rules" action) when nothing was ever extracted, versus "Nothing matches your filters" with "Clear filters" (resets filters and search, keeps the sort) when a filter or search hides every row. The stats card no longer shows its own "No data yet"
  * Dry-run rule executions (test/preview matches) are excluded from every Data Browser view by default: browsing, search, statistics, export, and deletion
* **System Trigger:** User navigates to the Data tab (bottom navigation, between Home and Rules).
* **Technical Spec Reference:** `openspec/changes/data-browser/specs/data-browsing/spec.md`, `data-statistics/spec.md`, `data-export/spec.md`, `data-deletion/spec.md`

---

## Background Data Handling

### Global Monitoring Pause (Quick Settings Tile, Settings, Home)
* **User Experience:** The user adds a Notificapp tile to their Android Quick Settings panel and taps it to instantly pause or resume all notification monitoring — a privacy kill switch reachable without opening the app. The same global state is also controlled by the Pause monitoring switch in Settings, and Home shows a "Monitoring paused" banner with a Resume action. While paused, the tile shows "Paused" and freshly posted notifications are not captured, processed, or acted on at all (existing rules stay configured; they simply see nothing new until monitoring resumes).
* **System Trigger:** User taps the Quick Settings tile, flips the Settings switch, taps Resume on Home, or the tile is displayed (it reads the current state on `onStartListening`).
* **Technical Spec Reference:** `features/notification/MonitoringTileService.kt`, gated in `core/notification/ProcessNotificationUseCase.kt`.

### Automatic Notification Capture
* **User Experience:** The user does nothing — notifications from monitored apps are captured automatically the moment they arrive, ready to browse in the Inbox.
* **System Trigger:** Android system notification broadcast, received continuously while notification access is granted.

### Automatic Rule Evaluation & Execution
* **User Experience:** The user experiences the outcome directly — a notification is dismissed, snoozed, or triggers an alarm/flash — without taking any action themselves, because a rule they set up matched it and ran automatically.
* **System Trigger:** A new notification is captured from a monitored app; it is deduplicated, checked against every active rule, and each matching rule's enabled actions are executed and logged.
* **Technical Spec Reference:** `openspec/specs/action-execution/`

### Local Secure Storage
* **User Experience:** All captured notifications, rules, and extracted data remain on the user's device and are available offline; nothing is uploaded anywhere.
* **System Trigger:** Runs continuously as part of every capture, rule match, and extraction.

---

## Network Actions

### Webhook Management
* **User Experience:** The user builds a library of webhooks (external service endpoints) in the app, then targets them from rule actions. Each webhook includes:
  * **Name:** User-friendly label
  * **URL:** The HTTPS or HTTP endpoint
  * **HTTP Method:** GET, POST, PUT, PATCH, or DELETE
  * **Custom Headers:** Optional header key-value pairs (e.g. `X-Custom-Header: value`)
  * **Authentication:** None, API Key Header (with customizable header name, default `X-API-Key`), or Bearer Token
  * **Query Parameters:** Optional URL query parameters (key-value pairs)
  * **Connection Testing:** Send a test payload to validate the URL, auth, headers, and connectivity before using it in a rule
  * **Delivery Status Indicator:** At-a-glance status of the most recent delivery attempt (Never attempted, Delivered, Configuration error, or Unreachable)
* **System Trigger:** User navigates to Settings → Webhooks, or clicks to add/edit a webhook while configuring a rule action.
* **Technical Spec Reference:** `openspec/specs/webhook-management/`

### Send Webhook Action
* **User Experience:** As one of a rule's actions, the user can configure it to send a JSON payload to a pre-configured webhook whenever the rule matches. The author chooses one of two payload modes:
  * **Fields Mode:** A fixed-schema JSON object built from a checklist of tokens (predefined notification fields plus any extracted data fields defined by the rule's "Extract data" action). Available tokens include:
    * Built-in notification fields: `title`, `content`, `app_name`, `package_name`, `timestamp`, `raw_content`
    * Any extracted fields (by field name) defined in the same rule
  * **Template Mode:** Author writes a custom JSON structure with `{{token}}` placeholders, substituted at delivery time. Same tokens available as in Fields Mode.
  * **Delivery Tracking:** Each delivery is queued, retried on transient failures (network errors, 5xx responses), and logged with outcome (Success, Configuration error, Unreachable after retries). The webhook's "Delivery Status Indicator" surface shows the most recent result.
  * **Multi-Webhook Rule:** A single rule can include multiple "Send webhook" actions, targeting different endpoints.
* **System Trigger:** Runs automatically in the background the moment a monitored notification matches an enabled (non dry-run) rule, for every enabled "Send webhook" action in that rule.
* **Technical Spec Reference:** `openspec/specs/webhook-delivery/`, `openspec/specs/rule-action-authoring/`

---

## Status Reference (for planning what to build next)

* **Planned, not yet built:**
  * Trend chart rendering for the Data Browser's computed trend series (bar/line chart, last 7/30 days) — the Data Browser itself (browse/filter/search/stats/export/delete) is done, see "Data Browser" above
  * Local backup/restore of rules and extracted data (retention settings + storage usage are already shipped — see "Settings" above)
  * Optional on-device AI extraction (separate build flavor)
  * Community rule gallery
  * F-Droid distribution
