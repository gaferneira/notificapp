# Delta for action-execution

## ADDED Requirements

### Requirement: Send-webhook action execution
The system SHALL enqueue a webhook delivery when a matched rule has an enabled `SEND_WEBHOOK` action, via a `SendWebhookActionExecutor` bound into the `ActionExecutor` Hilt multibinding alongside the other per-type executors (mirrors `FlashAlertActionExecutor`). The executor SHALL build the payload from the action's `WebhookActionConfig` and enqueue a WorkManager `CoroutineWorker` for delivery; it SHALL NOT perform the HTTP call synchronously on the calling thread.

#### Scenario: Enabled webhook action enqueues delivery
- WHEN a notification matches a rule with an enabled `SEND_WEBHOOK` action
- THEN the executor builds the payload from the action's config
- AND enqueues a WorkManager delivery worker for that webhook

#### Scenario: Disabled webhook action is not executed
- WHEN a notification matches a rule that has a disabled `SEND_WEBHOOK` action
- THEN the executor does not build a payload or enqueue any delivery work

#### Scenario: Executor initiation returns success independent of delivery outcome
- WHEN the `SendWebhookActionExecutor` successfully enqueues the delivery work
- THEN it returns `ActionOutcome.SUCCESS`, meaning the action was successfully initiated
- AND the eventual HTTP delivery result (delivered, retried, queued) is tracked separately via the webhook's `lastDeliveryStatus`, not via this executor's return value

#### Scenario: Webhook action executes alongside other actions on the same match
- WHEN a notification matches a rule with both `SEND_WEBHOOK` and `DISMISS_NOTIFICATION` actions enabled
- THEN the system executes both enabled actions for that rule match
