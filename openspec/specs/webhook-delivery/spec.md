# Webhook Delivery Specification

## Purpose

Wire webhooks into the rule engine as a `SEND_WEBHOOK` action: build a JSON payload from the matched notification, POST it, classify failures (network / server / client), retry via WorkManager per a 3-way policy, and persist undelivered attempts to a failed-delivery queue that drains on app open.

## Requirements

### Requirement: Webhook fires on rule match
The system SHALL POST a JSON payload to the configured webhook's URL (with its headers and auth applied) when a matched rule has an enabled `SEND_WEBHOOK` action, using the payload built per the rule's configured payload mode.

#### Scenario: Rule with webhook action sends a real POST
- GIVEN a rule with an enabled `SEND_WEBHOOK` action pointing at a reachable webhook
- WHEN a notification matches the rule
- THEN the system sends an HTTP POST to the webhook's URL with the built payload, headers, and auth applied

### Requirement: Failure classification
The system SHALL classify a delivery failure into exactly one of three categories: network (no connectivity / connection failure), server (5xx response or timeout), or client (4xx response). Each category SHALL drive a distinct retry policy.

#### Scenario: Connection failure classified as network
- GIVEN a webhook pointing at an unreachable host
- WHEN the delivery attempt fails to connect
- THEN the failure is classified as network

#### Scenario: HTTP 500 classified as server
- GIVEN a webhook endpoint that returns HTTP 500
- WHEN the delivery attempt completes
- THEN the failure is classified as server

#### Scenario: HTTP 401 classified as client
- GIVEN a webhook endpoint that returns HTTP 401
- WHEN the delivery attempt completes
- THEN the failure is classified as client

### Requirement: Network failure retry policy
When a delivery fails with a network classification, the system SHALL enqueue the retry with a `Constraints(NetworkType.CONNECTED)` constraint rather than a timed backoff, so the attempt fires as soon as connectivity returns instead of burning attempts while offline.

#### Scenario: Network failure waits for connectivity
- GIVEN a webhook delivery fails because the device has no network connection
- WHEN the retry is scheduled
- THEN the retry work is constrained to run only once network connectivity is available
- AND no fixed-delay backoff timer is used for this retry

### Requirement: Server failure retry policy
When a delivery fails with a server classification, the system SHALL retry with exponential backoff at 1 minute, 5 minutes, and 30 minutes, for a maximum of 3 attempts. If all attempts are exhausted, the delivery SHALL be persisted to the failed-delivery queue.

#### Scenario: 5xx response retried with backoff
- GIVEN a webhook endpoint that returns HTTP 503
- WHEN the delivery is attempted
- THEN the system retries at approximately 1 minute, then 5 minutes, then 30 minutes after each failure
- AND after the third failed attempt the delivery is persisted to the failed-delivery queue

#### Scenario: Timeout retried as server failure
- GIVEN a webhook endpoint that does not respond before the request times out
- WHEN the delivery is attempted
- THEN the failure is classified as server and follows the same backoff schedule as a 5xx response

### Requirement: Client failure fails fast
When a delivery fails with a client classification, the system SHALL NOT retry. After the single failed attempt, the delivery SHALL be persisted to the failed-delivery queue marked as a config error, distinct from an unreachable/server status.

#### Scenario: 4xx response fails without retry
- GIVEN a webhook endpoint that returns HTTP 400
- WHEN the delivery is attempted
- THEN the system does not schedule a retry
- AND the delivery is persisted to the failed-delivery queue with a config-error status after the single attempt

### Requirement: Failed-delivery queue persistence
The system SHALL persist deliveries that exhaust retries or fail fast to a Room-backed failed-delivery table, storing enough data (webhook id, built payload, failure classification) to retry the exact same attempt later. The queue SHALL be unbounded for MVP — no max-size or max-age eviction.

#### Scenario: Exhausted server retry is queued
- GIVEN a webhook delivery that exhausts all 3 server-failure retry attempts
- WHEN the final attempt fails
- THEN a row is persisted to the failed-delivery table with the webhook id, payload, and classification

#### Scenario: Queue accepts unbounded rows
- GIVEN the failed-delivery table already has many undelivered rows
- WHEN another delivery fails and is queued
- THEN the new row is persisted without any eviction of existing rows

### Requirement: Retry on app open
The system SHALL attempt to redeliver every row currently in the failed-delivery queue when the app starts (`MyApplication.onCreate()`). A row that succeeds on retry SHALL be removed from the queue and update the owning webhook's `lastDeliveryStatus`/`lastDeliveryAt`. A row that fails again SHALL remain queued, reclassified per its new outcome.

#### Scenario: Queued item retried and delivered on app open
- GIVEN a failed-delivery row for a webhook that is now reachable
- WHEN the app is opened
- THEN the system retries the queued delivery
- AND on success the row is removed from the queue and the webhook's delivery status is updated to delivered

#### Scenario: Queued item still failing remains queued
- GIVEN a failed-delivery row for a webhook that is still unreachable
- WHEN the app is opened and the retry sweep runs
- THEN the delivery attempt fails again
- AND the row remains in the failed-delivery queue

### Requirement: Payload builder supports both modes
The system SHALL build the webhook payload from the matched notification and its extracted field values, using one of two modes selected on the action's config: a raw field checklist (each checked field emitted as a JSON key/value) or a custom JSON template with `{{field}}` tokens substituted from extracted field values.

#### Scenario: Field-checklist payload includes only checked fields
- GIVEN a `SEND_WEBHOOK` action configured with field-checklist mode and two fields checked out of four extracted
- WHEN the payload is built
- THEN the JSON payload contains only the two checked fields

#### Scenario: Template payload substitutes tokens
- GIVEN a `SEND_WEBHOOK` action configured with a custom JSON template containing `{{amount}}` and `{{merchant}}`
- WHEN the payload is built from a match with extracted values `amount = "42.00"` and `merchant = "Acme"`
- THEN the built payload has those tokens replaced with the extracted values

### Requirement: Sensitive payload data is never logged
The system MUST NOT write queued payload contents, webhook `authValue`, or other sensitive extracted field data to logs at any point — enqueue, retry attempt, success, or failure.

#### Scenario: Queued payload absent from logs
- GIVEN a delivery that fails and is persisted to the failed-delivery queue
- WHEN the failure is logged for diagnostics
- THEN the log output contains no occurrence of the payload body or the webhook's `authValue`

#### Scenario: Retry-sweep logs omit payload content
- GIVEN the app-open retry sweep processes a queued row
- WHEN the retry attempt is logged (success or failure)
- THEN the log output contains no occurrence of the queued payload body
