# Privacy Policy

Notificapp turns notification text into structured, reusable data — **locally on your device**. This document explains exactly what the app can see, what it stores, and what leaves your phone (spoiler: nothing).

Because Notificapp is open source, you don't have to take any of this on faith — every claim below can be verified in the code and in the app manifest.

## The permission we ask for, and why

Notificapp requests **Notification Access** (`BIND_NOTIFICATION_LISTENER_SERVICE`). This is a powerful, sensitive permission: it lets an app read the content of every notification on your device, which can include private messages, one-time passcodes (OTPs), and financial information.

Notificapp needs it for one purpose: reading the text of notifications **from the apps you explicitly select** so it can extract structured data using your rules.

That is the app's entire permission footprint. Notificapp does **not** request the `INTERNET` permission — you can verify this in [`app/src/main/AndroidManifest.xml`](app/src/main/AndroidManifest.xml). An app without that permission is technically incapable of sending your data anywhere.

## What we collect and where it lives

- **Notifications from monitored apps only.** Notifications from apps you haven't selected are ignored and never stored. Notifications without meaningful text content are skipped.
- **Everything stays on your device**, in a local database in the app's private storage. There is no cloud, no sync, no server — we don't operate any backend at all.
- **Extracted data** (the fields your rules pull out of notifications) is stored in the same local database.

## What we never do

- No data transmission off your device (no `INTERNET` permission)
- No analytics, telemetry, crash reporting, or tracking of any kind
- No advertising, no third-party SDKs that phone home
- No accounts, no login, no cloud AI

## Your controls

- **Choose monitored apps**: only apps you enable are captured; change the list anytime in Settings
- **Pause entirely**: revoke Notification Access in Android's system settings at any time
- **Delete your data**: remove captured notifications and extracted data from within the app, or clear all app data via Android settings — it's gone, because there was never a copy anywhere else
- **Export your data**: your extracted data is yours; export features keep it portable

## A note on sensitive notifications

Be deliberate about which apps you monitor. If you enable an app that delivers OTPs or private messages, that text will be stored in Notificapp's local database like any other notification. If your threat model includes someone with physical access to your unlocked device, prefer monitoring only the apps you need, and delete captured data you no longer use.

## Future changes

The [roadmap](docs/roadmap.md) includes an optional **webhooks** feature, which will require adding the `INTERNET` permission. When that ships:

- Network access will be used **exclusively** to deliver data to webhook URLs that *you* configure — there is still no server of ours involved
- The permission change will be disclosed prominently in the release notes and this policy will be updated
- Everything else above remains true

This policy changes only via a public commit to this repository, so its history is fully auditable.

## Contact

Questions or concerns: open a [GitHub issue](../../issues), or for security matters see [SECURITY.md](SECURITY.md).
