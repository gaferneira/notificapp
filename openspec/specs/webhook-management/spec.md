# Webhook Management Specification

## Purpose

Let users define, edit, delete, and validate webhooks (name, URL, headers, auth) that a future PR will wire into rule actions. This spec covers CRUD, listing, and a one-shot test-payload send — no rule-action integration, retry, or queueing.

## Requirements

### Requirement: Webhook domain model and validation
The system SHALL model a `Webhook` with `id`, `name`, `url`, `headers: Map<String,String>`, `authType` (None / ApiKeyHeader / Bearer), `authValue`, `authHeaderName` (defaults to `X-API-Key`), `lastDeliveryStatus` (nullable — delivered / config-error / unreachable, absent until a real delivery or retry has occurred), and `lastDeliveryAt` (nullable timestamp of the last delivery attempt outcome). The system MUST reject a webhook with a blank `name` or a `url` that is not a well-formed `http`/`https` URL.

#### Scenario: Valid webhook passes validation
- GIVEN a name "Home Assistant" and url "https://ha.local/api/hook"
- WHEN the user saves the webhook
- THEN it is persisted successfully

#### Scenario: Malformed URL is rejected
- GIVEN a url "not-a-url"
- WHEN the user attempts to save
- THEN save is blocked and a URL validation error is shown

#### Scenario: Blank name is rejected
- GIVEN an empty name field
- WHEN the user attempts to save
- THEN save is blocked and a name validation error is shown

#### Scenario: New webhook has no delivery status
- GIVEN a newly created webhook that has never been used in a rule action
- WHEN the webhook is saved
- THEN its `lastDeliveryStatus` and `lastDeliveryAt` are absent (null)

### Requirement: Webhook creation with headers and auth
The system SHALL let a user add zero or more header key/value rows and select one auth type: None, API-key header (with an editable `authHeaderName`, default `X-API-Key`), or Bearer token.

#### Scenario: Create webhook with custom headers
- GIVEN a new webhook form with two header rows filled in
- WHEN the user saves
- THEN the webhook is persisted with both headers in its `headers` map

#### Scenario: Create webhook with API-key auth and custom header name
- GIVEN auth type "API-key header" with `authHeaderName = "X-Custom-Key"` and a value
- WHEN the user saves
- THEN the webhook is persisted with `authType = ApiKeyHeader`, that header name, and the value

#### Scenario: Create webhook with Bearer auth
- GIVEN auth type "Bearer" with a token value
- WHEN the user saves
- THEN the webhook is persisted with `authType = Bearer` and the token as `authValue`

#### Scenario: Create webhook with no auth
- GIVEN auth type "None"
- WHEN the user saves
- THEN the webhook is persisted with `authType = None` and no `authValue`

### Requirement: Webhook editing
The system SHALL let a user open an existing webhook in the editor pre-filled with its current fields and save changes as an update, not a new record.

#### Scenario: Edit updates existing webhook
- GIVEN an existing webhook with url "https://old.example.com"
- WHEN the user changes the url to "https://new.example.com" and saves
- THEN the same webhook `id` now has url "https://new.example.com"

### Requirement: Webhook listing and deletion
The system SHALL show all saved webhooks in a list reachable from Settings, and SHALL let a user delete a webhook after confirmation. Each list row SHALL show a tri-state delivery indicator — delivered, config error, or unreachable — reflecting the webhook's `lastDeliveryStatus`, alongside the `lastDeliveryAt` timestamp when present. A webhook with no delivery history SHALL show no indicator.

#### Scenario: List shows all saved webhooks
- GIVEN three saved webhooks
- WHEN the user opens the Webhook List screen
- THEN all three appear with their names

#### Scenario: Delete removes a webhook
- GIVEN an existing webhook in the list
- WHEN the user deletes it and confirms
- THEN it no longer appears in the list or in storage

#### Scenario: List shows delivered indicator
- GIVEN a webhook whose last delivery succeeded
- WHEN the user opens the Webhook List screen
- THEN that webhook's row shows a "delivered" indicator with the last delivery timestamp

#### Scenario: List shows config-error indicator
- GIVEN a webhook whose last delivery failed with a client (4xx) classification
- WHEN the user opens the Webhook List screen
- THEN that webhook's row shows a "config error" indicator, distinct from "unreachable"

#### Scenario: List shows unreachable indicator
- GIVEN a webhook whose last delivery failed with a network or exhausted server classification
- WHEN the user opens the Webhook List screen
- THEN that webhook's row shows an "unreachable" indicator

#### Scenario: List shows no indicator for unused webhook
- GIVEN a webhook that has never had a rule action send through it
- WHEN the user opens the Webhook List screen
- THEN that webhook's row shows no delivery indicator

### Requirement: Send test payload
The system SHALL let a user trigger a single, immediate POST of a fixed sample JSON payload to the webhook's URL (with its configured headers and auth applied), with no retry or queueing, and SHALL report success or failure to the user.

#### Scenario: Test payload succeeds
- GIVEN a webhook pointing at a reachable endpoint that returns HTTP 200
- WHEN the user taps "Send test payload"
- THEN a success indicator is shown to the user

#### Scenario: Test payload fails on network error
- GIVEN a webhook pointing at an unreachable host
- WHEN the user taps "Send test payload"
- THEN a failure indicator is shown, distinguishing a network error from a server error

#### Scenario: Test payload fails on non-2xx or malformed response
- GIVEN a webhook endpoint that returns HTTP 500 or a malformed/non-JSON body
- WHEN the user taps "Send test payload"
- THEN a failure indicator is shown reflecting the server-side error, with no retry attempted

### Requirement: Auth value confidentiality
The system MUST NOT write `authValue` to logs at any point (creation, edit, test-payload send, or failure paths), and MUST mask `authValue` in the UI (e.g. displayed as dots/asterisks) with an explicit user action required to reveal it, if reveal is offered at all.

#### Scenario: Auth value is masked in the editor
- GIVEN a webhook with `authType = Bearer` and a saved token
- WHEN the user reopens the webhook in the editor
- THEN the token field displays masked characters, not the plaintext value

#### Scenario: Auth value never appears in logs on test-payload failure
- GIVEN a webhook with a Bearer token and an endpoint that returns HTTP 500
- WHEN the user sends a test payload and it fails
- THEN the failure log output contains no occurrence of the `authValue` string
