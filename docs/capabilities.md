# Application Functional Capabilities

Notificapp lets users create automation rules that act on the notifications their phone receives — dismissing noise, snoozing, sounding an alarm, or flashing a visual alert — and, uniquely, extract structured data out of a notification's text (like turning a bank alert into a spending record). Everything runs locally on the device, with no account, cloud sync, or network access required.

---

## Core Automation

### Rule Creation & Editing
* **User Experience:** The user builds a rule in a two-step wizard: first defining conditions (when a notification's title, text, app, or package matches something) and one or more actions to run when it matches, then naming the rule and optionally marking it as "dry-run" (log matches without ever acting) for safe trialing. Rules can also be started pre-filled from a real captured notification, or built from scratch.
* **System Trigger:** User taps "+" on the Rules screen, or taps "Create rule" from a notification's detail view.
* **Technical Spec Reference:** `openspec/specs/rule-action-authoring/`, `openspec/specs/rule-storage/`

### Matching Conditions
* **User Experience:** The user specifies what a notification must look like to match a rule by adding one or more conditions, drawn from three families. Multiple conditions on the same rule are combined with a per-rule combinator (`ALL` = every condition must match, `ANY` = at least one condition must match). The three condition families are:
  * **Content match** — a notification property compared against a value with an operator:
    * Properties that can be checked: Title, Main text/content, Raw content (the full raw notification text), App name, Package name
    * Operators available: Contains, Does not contain, Starts with, Ends with, Equals (exact match), Matches regex (pattern match)
  * **Day of week** — matches when the current day is one of a chosen set of weekdays. Choosing zero days matches no day (fail-closed), not every day.
  * **Time range** — matches when the current time falls within a start/end time, inclusive. A range where the end is earlier than the start wraps across midnight (e.g. 22:00–06:00); a range where start equals end matches only that exact instant.
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
* **System Trigger:** User taps "Export" on a rule in the Rules list, or "Import" and selects a shared file/clipboard text.

---

## User Interface & Setup

### Onboarding
* **User Experience:** On first launch, the user sees a short explanation of what the app does, then is guided to grant notification access via the system settings screen, returning automatically once permission is granted.
* **System Trigger:** App opened for the first time; permission status is re-checked when the user returns from system settings.

### App Selection
* **User Experience:** The user picks which installed apps Notificapp should monitor, searching and toggling apps in a list. Only notifications from selected apps are captured and can be used in rules.
* **System Trigger:** Shown right after onboarding, or reopened anytime from Settings.

### Notification Inbox & Detail
* **User Experience:** The user browses a time-grouped list of every captured notification, with:
  * Search by text
  * Filter by app
  * Filter by processed-status (All / Processed / Unprocessed)
  * A warning banner if notification access has been revoked
  * Tapping an item opens its full content plus a history of which rules matched it, what data was extracted, and what actions ran (with outcome: Success, Failed, Skipped, or Suppressed)
  * From detail view: "Create rule" from this notification, or "Re-run rules" to manually recompute matches
* **System Trigger:** User opens the app to the Inbox (its home screen); taps a notification to see details; taps "Re-run rules" to recompute matches manually.

### Rules Management
* **User Experience:** The user views all their rules in one list, with:
  * Search by name
  * Filter by category, target app, or status (bottom sheet)
  * Sort by: category, name A-Z/Z-A, newest/oldest created, recently updated, or status-first
  * Inline enable/disable toggle without deleting the rule
  * Export/Import (see "Rule Sharing" below)
* **App Scope:** Each rule can target apps in one of three modes:
  * All apps — no app restriction
  * Include-list — rule fires only for the listed apps
  * Exclude-list — rule fires for every app except the listed ones
* **System Trigger:** User navigates to the Rules tab.
* **Technical Spec Reference:** `openspec/specs/rule-app-scope/`

### Settings
* **User Experience:** The user reviews and manages which apps are monitored, checks whether notification access is still granted (re-enabling it if revoked), toggles data collection and app-icon display preferences, sets a notification retention period (30 days / 90 days / Never, auto-deleting older notifications on app start), and views a storage usage summary (database size, row counts per data type).
* **System Trigger:** User navigates to the Settings tab.

### Data Browser
* **User Experience:** The user browses every piece of data their rules have extracted (field name, value, source app, rule, timestamp), in a paginated list newest-first by default. They can:
  * Filter by any combination of rule, source app, date range, and field type
  * Search extracted values with free-text search (FTS4-backed)
  * Sort by date, rule name, app, or field name
  * See a plain-text stats header: total extractions, extractions this week, most active rule (no chart rendering yet — trend data is computed but not visualized, see `docs/roadmap.md`)
  * Delete a single entry directly from the list
  * Bulk-delete everything matching the current filters, after a confirmation dialog showing the exact affected count — the delete always targets the previewed ID set, so data arriving between preview and confirmation is never swept in
  * Export the currently filtered set as CSV or JSON via the Android share sheet; export streams in fixed-size batches so it never materializes the full result set in memory, even for tens of thousands of rows
  * Dry-run rule executions (test/preview matches) are excluded from every Data Browser view by default: browsing, search, statistics, export, and deletion
* **System Trigger:** User navigates to the Data tab (bottom navigation, between Inbox and Rules).
* **Technical Spec Reference:** `openspec/changes/data-browser/specs/data-browsing/spec.md`, `data-statistics/spec.md`, `data-export/spec.md`, `data-deletion/spec.md`

---

## Background Data Handling

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
