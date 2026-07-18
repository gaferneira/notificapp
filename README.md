# Notificapp

**Take control of your Android notifications.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com)
[![Status](https://img.shields.io/badge/Status-pre--release-orange.svg)](docs/roadmap.md)

Create rules that act on your notifications automatically: dismiss the noise, snooze things until later, get alerted for what matters — or extract the data inside them (amounts, tracking numbers, dates) into a structured dataset you own. **If this notification, then that.** All on your device.

<!-- TODO: 30-second demo GIF here — a rule matching a notification → action firing → extracted data appearing -->

## What can you do with it?

- **Build a spending tracker out of your bank's notifications** — every *"You paid €12.50 at…"* push becomes a structured row (amount, merchant, date) in a dataset on your phone. No bank API needed, no third-party finance app reading your account.
- **Never lose a tracking number again** — extract the carrier, tracking code, and status from every delivery notification; all your packages in one searchable place.
- **Silence the noise** — auto-dismiss the *"20% off this weekend!"* spam, snooze the group chat until after work, keep the notifications you actually read.
- **Bridge notifications into your smart home** — "package delivered" makes Home Assistant announce it on your speakers; a bank payment updates your budget dashboard the second it happens.
- **Make critical alerts impossible to miss** — a server-down or security notification can flash your camera light or fire an alarm, even on silent.
- **Own everything** — browse, filter, and export your extracted data anytime; it never leaves your device unless *you* send it somewhere.

## How it works

1. **Select apps to monitor** — only notifications from apps you enable are ever captured
2. **Create rules from real notifications** — pick a notification as a sample and define match conditions (6 operators)
3. **Choose what happens** — dismiss, snooze, extract data fields, and more actions per rule
4. **Rules run automatically** — actions fire as notifications arrive; extracted data becomes structured, typed records (text, number, date, currency)

## The data superpower

Most notification tools stop at managing alerts. Notificapp can also **read** them. Notifications are often the only data interface an app exposes — your bank doesn't give you an API, but it notifies you about every transaction. Extraction rules (10 methods: regex, text anchors, keywords, smart amount/date detection, ...) turn that text into a queryable personal dataset that never leaves your phone. No other notification automation app does this.

## Privacy

Notification access is one of Android's most sensitive permissions — it can see private messages and OTPs. Notificapp's answer:

- **All your data stays on your device** — captured notifications and extracted data live in a local database you can wipe anytime; there is no cloud, no account, no sync
- Only apps you explicitly select are captured
- No analytics, no telemetry, no third-party SDKs
- Fully open source under GPL-3.0, so all of the above is auditable

Details in [PRIVACY.md](PRIVACY.md). Webhook delivery (to *your* servers) is opt-in and per-rule — nothing is sent anywhere unless you configure it — see [ADR 012](docs/adr/012-local-first-network-policy.md).

## Status

Pre-release and under active development. Working today: notification capture, inbox with search/filters, the rule engine (conditions + extraction), rule editor, dismiss/snooze/alarm/flash actions, rule sharing (import/export) and backtesting, a data browser with CSV/JSON export, and webhooks. On the way: starter-template-first rule creation, a read-aloud action, full internationalization, an F-Droid release, and optional on-device AI extraction. See the full [roadmap](docs/roadmap.md).

**Installation:** build from source for now (below). F-Droid is the planned primary distribution channel.

## Building from source

Requirements: Android Studio (latest stable), JDK 17+.

```bash
git clone https://github.com/gaferneira/Notificapp.git
cd Notificapp
./gradlew assembleDebug
```

Install the APK from `app/build/outputs/apk/debug/`, launch, and grant Notification Access when prompted.

## Contributing

Contributions are welcome — **including from non-programmers**:

- 📖 Read [CONTRIBUTING.md](CONTRIBUTING.md) to get started
- 🧩 **Know an app worth automating or extracting data from?** Open a [rule request](../../issues/new?template=rule_request.yml) — community-contributed rules are the heart of this project
- 🐛 [Report bugs](../../issues/new?template=bug_report.yml) · 💡 [Suggest features](../../issues/new?template=feature_request.yml)
- 🔒 Security issues: see [SECURITY.md](SECURITY.md) (never public issues)

## Documentation

| Doc | Contents |
|-----|----------|
| [Roadmap](docs/roadmap.md) | Product positioning, phases, what's in/out of scope |
| [Architecture](docs/ARCHITECTURE.md) | MVI + feature-first structure, rule pipeline, coding standards |
| [ADRs](docs/adr/) | 13 architecture decision records |
| [Capabilities](docs/capabilities.md) | Present-tense map of every shipped feature |
| [Privacy](PRIVACY.md) | What the app can see, stores, and (never) sends |

## Tech stack

Kotlin · Jetpack Compose + Material 3 · MVI · Hilt · Room · Navigation3 · Paging3 · Coroutines/Flow · JUnit 5 + Kotest + MockK

## License

[GPL-3.0](LICENSE) — free to use, study, modify, and share; forks stay open source.
