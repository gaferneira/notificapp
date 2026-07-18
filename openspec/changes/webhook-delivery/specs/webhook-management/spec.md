# Delta for webhook-management

## MODIFIED Requirements

### Requirement: Webhook domain model and validation
The system SHALL model a `Webhook` with `id`, `name`, `url`, `headers: Map<String,String>`, `authType` (None / ApiKeyHeader / Bearer), `authValue`, `authHeaderName` (defaults to `X-API-Key`), `lastDeliveryStatus` (nullable — delivered / config-error / unreachable, absent until a real delivery or retry has occurred), and `lastDeliveryAt` (nullable timestamp of the last delivery attempt outcome). The system MUST reject a webhook with a blank `name` or a `url` that is not a well-formed `http`/`https` URL.
(Previously: model had no `lastDeliveryStatus`/`lastDeliveryAt` fields — those are added by webhook-delivery (Phase 4 PR2).)

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

### Requirement: Webhook listing and deletion
The system SHALL show all saved webhooks in a list reachable from Settings, and SHALL let a user delete a webhook after confirmation. Each list row SHALL show a tri-state delivery indicator — delivered, config error, or unreachable — reflecting the webhook's `lastDeliveryStatus`, alongside the `lastDeliveryAt` timestamp when present. A webhook with no delivery history SHALL show no indicator.
(Previously: list showed only webhook names, with no delivery status indicator.)

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
