# ADR 012 – Local-First Network Policy and Distribution

## Status
Accepted

## Context
Notificapp's notification-listener permission grants access to extremely sensitive data (OTPs, private messages, financial notifications). The product is **open-source, local-first notification automation with data extraction as its flagship capability** (see `docs/roadmap.md` Positioning), and its community core — self-hosters, Home Assistant users, privacy-conscious OSS users — evaluates apps by their permission and network footprint. Architectural choices that quietly add network access or proprietary dependencies would undermine the product's trust story.

## Decision
1. **All data stays on the device; user-configured webhook delivery (Roadmap Phase 4) is the *only* network egress, ever.** There is no backend, no sync, no cloud. The introduction of network access is disclosed prominently (release notes), and `PRIVACY.md` is updated in the same PR.

2. **No telemetry, analytics, or crash reporting** without explicit user opt-in. Nothing in the architecture may assume a backend exists.

3. **Proprietary dependencies stay out of the main build flavor.** On-device AI (Gemini Nano via AICore, Roadmap Phase 5) ships in a separate flavor behind an `AiExtractor` abstraction, keeping the main flavor F-Droid-compliant and the engine open to alternative local models.

4. **F-Droid is the primary distribution channel**, which requires reproducible builds and a tracker-free main flavor. Play Store distribution is deferred.

5. **Community features avoid in-app network access**: the rules gallery (Roadmap Phase 2) lives in the GitHub repository; import is manual (file/clipboard) rather than fetched, keeping the app fully local pre-webhooks.

## Status update (webhook-management, Phase 4 PR1)

Decision #1's "user-configured webhook delivery (Roadmap Phase 4) is the *only* network egress" is now **realized**: the webhook test-payload button (`WebhookTestClient`, `core/network/`) is the first shipped egress. It is triggered only on an explicit user tap ("Send test payload" in the webhook editor) — no background traffic, no automatic retries. `PRIVACY.md` and the `INTERNET` permission landed in the same PR, per decision #1's disclosure requirement. This is not a policy reversal.

## Status update (webhook-delivery, Phase 4 PR2)

Rule-action wiring for webhooks has now shipped: a `SEND_WEBHOOK` rule action enqueues a delivery instead of sending synchronously, so a real retry/queue mechanism exists in addition to the manual "Send test payload" button from PR1.

- **Queue is a delivery journal, not a new persistent egress channel.** `webhook_deliveries` (SQLCipher-encrypted, same at-rest guarantee as every other table) holds one row per unresolved delivery — `PENDING` while awaiting/retrying, transiently `FAILED` (with a `failureType` of `NETWORK`/`SERVER`/`CLIENT`) after a terminal non-2xx outcome. A row is deleted on success, so the table only ever holds work still in flight; it never becomes a permanent history log of egress traffic.
- **Classify → retry/fail-fast rules.** `WebhookDeliveryClient` classifies every HTTP outcome into `Delivered` (2xx), `NetworkError` (`IOException`/timeout), `ServerError` (5xx, 408, 429), or `ClientError` (other 4xx — bad URL/auth/payload, not retriable). Network errors retry via WorkManager's own `Result.retry()` gated by `Constraints(NetworkType.CONNECTED)` — they never burn a server-retry attempt, since a temporary connectivity gap isn't evidence the destination is failing. Server errors get up to 3 manually re-enqueued attempts on a fixed `[1m, 5m, 30m]` schedule (the persisted row's `attemptCount` is the sole source of truth, not WorkManager's own attempt counter, because each retry is a fresh `WorkRequest`); attempt 3 exhausting marks the webhook `UNREACHABLE`. Client errors fail fast on the first attempt and mark the webhook `CONFIG_ERROR` — retrying a 401/404/malformed-payload response would just repeat the same failure.
- **WorkManager wiring is on-device only, no new remote dependency.** `MyApplication` supplies its own `Configuration.Provider` (via `HiltWorkerFactory`), replacing the default WorkManager initializer. `WebhookDeliveryWorker` is enqueued per delivery attempt with a `Constraints(NetworkType.CONNECTED)` gate; nothing here talks to a Google-hosted or any other third-party service — WorkManager only schedules local coroutine work.
- **App-open sweep, not a background poller.** `WebhookRetrySweepWorker` runs once, guarded per-process, from `MyApplication.onCreate` — it drains any `FAILED` rows left over from a previous process (e.g. the app was killed mid-retry-window) by resetting them to `PENDING` and re-enqueuing. There is no periodic/scheduled network polling; delivery only happens in direct response to a rule firing or the app being reopened.

This still satisfies decision #1: webhook delivery (now including its retry queue) remains the *only* network egress, and it only ever talks to the destination URL the user configured.

## Consequences

**Positive:**
- The privacy claims are auditable in the open source code — a strong trust signal for the target audience
- F-Droid compliance is designed-in rather than retrofitted
- The `AiExtractor` abstraction prevents vendor lock-in on the extraction core

**Negative:**
- No crash reporting makes field debugging harder (mitigation: users can share logs manually; Timber logging retained)
- Manual rule import has more friction than an in-app gallery fetch
- Maintaining a build flavor split (main vs AI) adds CI and release complexity from Phase 5 onward
- Webhook delivery cannot fall back to any cloud relay; retries are device-local (WorkManager)
