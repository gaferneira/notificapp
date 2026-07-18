# Privacy Policy

Notificapp acts on your notifications — automating them and extracting data from them — **entirely on your device**. This document explains exactly what the app can see, what it stores, and what leaves your phone (spoiler: nothing).

Because Notificapp is open source, you don't have to take any of this on faith — every claim below can be verified in the code.

## The permission we ask for, and why

Notificapp requests **Notification Access** (`BIND_NOTIFICATION_LISTENER_SERVICE`). This is a powerful, sensitive permission: it lets an app read the content of every notification on your device, which can include private messages, one-time passcodes (OTPs), and financial information.

Notificapp needs it for one purpose: reading the text of notifications **from the apps you explicitly select** so it can extract structured data using your rules.

## What we collect and where it lives

- **Notifications from monitored apps only.** Notifications from apps you haven't selected are ignored and never stored. Notifications without meaningful text content are skipped.
- **Everything stays on your device**, in a local database in the app's private storage. There is no cloud, no sync, no server — we don't operate any backend at all.
- **Extracted data** (the fields your rules pull out of notifications) is stored in the same local database.

## What we never do

- No data transmission off your device, except to webhook URLs you explicitly configure and trigger
- No analytics, telemetry, crash reporting, or tracking of any kind
- No advertising, no third-party SDKs that phone home
- No accounts, no login, no cloud AI

## Network access

Starting with this version, Notificapp requests the **INTERNET** permission. This is used for exactly one thing: sending a webhook POST request to a URL *you* configure, and only when you explicitly trigger it — by tapping "Send test payload" in the webhook editor, or, once rule-action wiring ships, when a rule with a webhook action fires. No other network traffic happens: no analytics, no update checks, no background calls.

The destination URL, any custom headers, and the authentication method/value are entirely user-configured — Notificapp never sends data to a URL you didn't enter yourself, and there is still no server of ours involved.

## Retry queue for webhook deliveries

When a rule with a "Send webhook" action fires, the delivery isn't sent inline — it's handed to a small on-device retry queue (`webhook_deliveries`) so a temporary network hiccup or a slow endpoint doesn't drop it silently. That queue is treated exactly like the webhook's own authentication value:

- **Encrypted at rest.** The queued payload (which can carry extracted notification data) lives in the same SQLCipher-encrypted database as everything else in the app — there is no separate, unencrypted staging file or cache.
- **Never logged.** The payload body, the destination URL, request headers, and the authentication value are never written to logs, exceptions, or crash output, in the queue or anywhere else in the delivery path. Log lines only ever reference the webhook's internal id, never its contents.
- **Local only, drained automatically.** A queued row is deleted as soon as delivery succeeds. If it fails, it's retried on-device (WorkManager) up to a fixed schedule; anything still unresolved is retried again the next time you open the app. Nothing about the queue introduces a new network destination — deliveries only ever go to the URL you configured for that webhook.

## Your controls

- **Choose monitored apps**: only apps you enable are captured; change the list anytime in Settings
- **Pause entirely**: revoke Notification Access in Android's system settings at any time
- **Delete your data**: remove captured notifications and extracted data from within the app, or clear all app data via Android settings — it's gone, because there was never a copy anywhere else
- **Export your data**: your extracted data is yours; export features keep it portable

## A note on sensitive notifications

Be deliberate about which apps you monitor. If you enable an app that delivers OTPs or private messages, that text will be stored in Notificapp's local database like any other notification. If your threat model includes someone with physical access to your unlocked device, prefer monitoring only the apps you need, and delete captured data you no longer use.

## Contact

Questions or concerns: open a [GitHub issue](../../issues), or for security matters see [SECURITY.md](SECURITY.md).
