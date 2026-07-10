# Rule Export Format

Notificapp rules can be exported to a JSON file (or clipboard text) and imported back — by you, on another device, or by anyone using a rule you shared. This document specifies that format.

See `docs/adr/011-rule-definition-storage.md` for why this is a wire format only: storage stays the normalized Room tables it always was. The DTOs in `core/rulesharing/dto/` (not the domain models) are the canonical definition of this format — every field carries an explicit `@SerialName`, so a domain-model rename never changes exported JSON. `core/rulesharing/RuleWireMapper.kt` maps between the two; `core/rulesharing/RuleJsonCodec.kt` encodes/decodes. A golden-file test (`app/src/test/resources/rule-export-v1.json`) locks this exact shape — any change to it that isn't a deliberate `schemaVersion` bump is normally a test failure, not a silent break for every rule file already exported by users. The move of `fields` from the rule level onto the `save_data` action (below) is an exception made while the app has no real users yet: it's a deliberate breaking change to the shape without a version bump, and no back-compat decoding for the old shape exists — see "No back-compat" below.

## Envelope

Every exported file is a single JSON object:

```json
{
  "schemaVersion": 1,
  "rule": { ... }
}
```

- `schemaVersion` — an integer, currently `1`. A future breaking change to the `rule` shape should bump this; `RuleJsonCodec.decode` rejects any `schemaVersion` newer than the app understands with a clear error, rather than silently misinterpreting it.
- `rule` — the exported rule, matching the `Rule` domain model (`domain/model/Rule.kt`) field-for-field.

## `rule` object

| Field | Type | Notes |
|---|---|---|
| `id` | string | Ignored on import — a fresh ID is always generated, so importing the same file twice never collides with itself. |
| `name` | string | Required; import is rejected if blank. |
| `description` | string? | Optional. |
| `category` | string? | Optional. |
| `isActive` | boolean | Ignored on import — imported rules are always activated. |
| `isDryRun` | boolean | Ignored on import — **imported rules always start in dry-run mode**, regardless of what's in the file. This is a deliberate safety rule: you review what an imported rule would have done (via "Test against history" and its dry-run execution log) before trusting it to act on real notifications. See `docs/adr/` and the roadmap's Backtesting and Dry-Run section. |
| `targetApps` | array of `{packageName, name}` \| `null` | `null` or an empty array means "all apps". |
| `conditions` | array of condition objects | See below. IDs are regenerated on import. |
| `actions` | array of action objects | See below. IDs are regenerated on import. Extraction fields are nested under the `save_data` action (see below) - there is no rule-level `fields` array. |
| `createdAt` / `updatedAt` | number (epoch ms) | Ignored on import — reset to the import time. |

## `conditions[]` — `RuleCondition`

```json
{ "id": "...", "condition": "text_content", "operator": "contains", "value": "Total" }
```

`condition` is one of: `text_content`, `title`, `app_name`, `package_name`, `raw_content`.
`operator` is one of: `contains`, `starts_with`, `ends_with`, `equals`, `regex_match`, `not_contains`.

## `actions[]` — `RuleAction`

```json
{ "id": "...", "type": "save_data", "isEnabled": true, "config": {}, "fields": [] }
```

`type` is one of: `save_data`, `dismiss_notification`, `snooze_notification`, `create_alarm`, `flash_alert`. Unlike `conditions[].condition`/`.operator` and `fields[].method.type`, an unrecognized `type` here does not fail the import — that one action (and any `fields` it nests) is dropped and reported to the user, and the rest of the rule still imports. This lets a rule exported from a newer app version (with an action type this version doesn't have yet) still import in a degraded but usable form.

`config` is a free-form `Map<String, String>` whose keys depend on `type` (e.g. `snooze_duration_minutes` for `snooze_notification`, `flash_count`/`flash_duration_ms` for `flash_alert`) — see the constants in `domain/model/RuleAction.kt`.

`fields` is only ever non-empty on the `save_data` action - it carries that action's extraction fields (see below). Every other action type has an empty `fields` array.

## `fields[]` — `RuleField` (nested under the `save_data` action)

```json
{
  "id": "...",
  "name": "Amount",
  "fieldType": "currency",
  "isRequired": false,
  "method": { "type": "text_after_keyword", "keyword": "Total: ", "maxLength": null }
}
```

`fieldType` is one of: `string`, `number`, `date`, `currency`, `boolean`.

`method` is a tagged union on its `type` field — one of:

| `type` | Extra fields |
|---|---|
| `fixed_position` | `startIndex`, `endIndex` |
| `text_between_anchors` | `startAnchor`, `endAnchor` |
| `regex` | `pattern`, `captureGroup` |
| `text_after_keyword` | `keyword`, `maxLength?` |
| `text_before_keyword` | `keyword` |
| `line_extraction` | `lineNumber` |
| `split_by_delimiter` | `delimiter`, `takeIndex` |
| `json_path` | `path` |
| `smart_amount` | (none) |
| `smart_date` | (none) |

## Full example

A rule that extracts a payment amount from bank notifications and saves it, exported from the app:

```json
{
  "schemaVersion": 1,
  "rule": {
    "id": "b3f1e2b0-6c7b-4b8e-9b0a-000000000000",
    "name": "Bank payment received",
    "description": null,
    "category": "Finance",
    "isActive": true,
    "isDryRun": false,
    "targetApps": [
      { "packageName": "com.bank.example", "name": "Example Bank" }
    ],
    "conditions": [
      { "id": "c1", "condition": "text_content", "operator": "contains", "value": "Payment received" }
    ],
    "actions": [
      {
        "id": "a1",
        "type": "save_data",
        "isEnabled": true,
        "config": {},
        "fields": [
          {
            "id": "f1",
            "name": "Amount",
            "fieldType": "currency",
            "isRequired": false,
            "method": { "type": "smart_amount" }
          }
        ]
      }
    ],
    "createdAt": 1751500000000,
    "updatedAt": 1751500000000
  }
}
```

## Import safety

Three rules apply on every import, regardless of what the source file contains:

1. **Fresh identity** — the rule and every nested condition/action (and its fields) get a newly generated ID. Importing the same file twice creates two independent rules, never a collision.
2. **Dry-run by default** — `isDryRun` is always forced to `true`. You get to see what an imported rule would have matched and extracted before it can dismiss, snooze, alert, or otherwise act on a real notification.
3. **Schema version check** — if `schemaVersion` is newer than this app version understands, import is rejected with an explicit error rather than guessing at an unfamiliar shape. This does **not** protect against the pre-`save_data`-nesting shape (see "No back-compat" above) since that change didn't bump the version.
4. **Unrecognized actions are skipped, not fatal** — see the `actions[]` section above. Everything else in the rule (conditions, remaining actions and their fields) still imports.
