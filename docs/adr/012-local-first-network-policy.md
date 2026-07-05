# ADR 012 – Local-First Network Policy and Distribution

## Status
Accepted

## Context
Notificapp's notification-listener permission grants access to extremely sensitive data (OTPs, private messages, financial notifications). The product's differentiator and trust story is **structured extraction + local-first + open source** (see `docs/roadmap.md` Positioning), and its target audience — self-hosters, Home Assistant users, privacy-conscious OSS users — evaluates apps by their permission and network footprint. Architectural choices that quietly add network access or proprietary dependencies would undermine the product's core promise.

## Decision
1. **No `INTERNET` permission until the Webhooks phase** (Roadmap Phase 4). Until then the app can honestly claim zero network access. When webhooks land, user-configured webhook delivery is the *only* network egress; the permission addition is disclosed prominently (privacy statement, changelog).

2. **No telemetry, analytics, or crash reporting** without explicit user opt-in. Nothing in the architecture may assume a backend exists.

3. **Proprietary dependencies stay out of the main build flavor.** On-device AI (Gemini Nano via AICore, Roadmap Phase 5) ships in a separate flavor behind an `AiExtractor` abstraction, keeping the main flavor F-Droid-compliant and the engine open to alternative local models.

4. **F-Droid is the primary distribution channel**, which requires reproducible builds and a tracker-free main flavor. Play Store distribution is deferred.

5. **Community features avoid in-app network access**: the rules gallery (Roadmap Phase 2) lives in the GitHub repository; import is manual (file/clipboard) rather than fetched, preserving the zero-network claim pre-webhooks.

## Consequences

**Positive:**
- The permission/network footprint *is* the marketing claim, and it is verifiable from the manifest — strongest possible trust signal for the target audience
- F-Droid compliance is designed-in rather than retrofitted
- The `AiExtractor` abstraction prevents vendor lock-in on the extraction core

**Negative:**
- No crash reporting makes field debugging harder (mitigation: users can share logs manually; Timber logging retained)
- Manual rule import has more friction than an in-app gallery fetch
- Maintaining a build flavor split (main vs AI) adds CI and release complexity from Phase 5 onward
- Webhook delivery cannot fall back to any cloud relay; retries are device-local (WorkManager)
