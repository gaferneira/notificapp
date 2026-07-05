# Notificapp

**Turn notification text into structured, reusable data — locally on your device.**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com)
[![Status](https://img.shields.io/badge/Status-pre--release-orange.svg)](docs/roadmap.md)

Notifications are often the only data interface an app exposes. Your bank doesn't give you an API — but it sends you a notification for every transaction. Notificapp captures notifications from apps *you* choose, extracts the fields *you* define, and builds a structured dataset that never leaves your phone.

<!-- TODO: 30-second demo GIF here — bank notification arriving → rule matching → extracted amount/merchant appearing as structured data -->

## What can you do with it?

- **Track spending** from bank and payment app notifications — every transaction becomes a structured row (amount, merchant, account) without giving anyone access to your bank account
- **Follow deliveries** — extract tracking numbers and delivery status from carrier notifications
- **Tame notification noise** — auto-dismiss or snooze notifications that match your rules
- **Own your data** — everything is stored locally, browsable, and exportable; *(planned)* deliver extracted data to your own services (Home Assistant, webhooks)

## How it works

1. **Select apps to monitor** — only notifications from apps you enable are ever captured
2. **Create rules** — pick a real notification as a sample, define match conditions (6 operators) and extraction fields (10 methods: regex, text anchors, keywords, smart amount/date detection, ...)
3. **Data extracts automatically** — matching notifications become structured, typed records (text, number, date, currency)
4. **Actions fire** — dismiss, snooze, and more per rule

No accounts. No cloud. No AI you didn't ask for.

## Privacy: verifiable, not promised

Notification access is one of Android's most sensitive permissions — it can see private messages and OTPs. Notificapp's answer is architectural, not a pinky-promise:

- **The app does not request the `INTERNET` permission.** It is technically incapable of sending your data anywhere — check [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) yourself
- Only apps you explicitly select are captured; everything is stored in a local database you can wipe anytime
- No analytics, no telemetry, no third-party SDKs
- Fully open source under GPL-3.0, so all of the above is auditable

Details in [PRIVACY.md](PRIVACY.md). Planned webhook delivery (to *your* servers) will be opt-in and prominently disclosed — see [ADR 012](docs/adr/012-local-first-network-policy.md).

## Status

Pre-release and under active development. Working today: notification capture, inbox with search/filters, the rule engine (conditions + extraction), rule editor, and dismiss/snooze actions. On the way: rule sharing and backtesting, a data browser with export, webhooks, and optional on-device AI extraction. See the full [roadmap](docs/roadmap.md).

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
- 🧩 **Know an app worth extracting data from?** Open a [rule request](../../issues/new?template=rule_request.yml) — community-contributed extraction rules are the heart of this project
- 🐛 [Report bugs](../../issues/new?template=bug_report.yml) · 💡 [Suggest features](../../issues/new?template=feature_request.yml)
- 🔒 Security issues: see [SECURITY.md](SECURITY.md) (never public issues)

## Documentation

| Doc | Contents |
|-----|----------|
| [Roadmap](docs/roadmap.md) | Product positioning, phases, what's in/out of scope |
| [Architecture](docs/ARCHITECTURE.md) | MVI + feature-first structure, extraction pipeline, coding standards |
| [ADRs](docs/adr/) | 12 architecture decision records |
| [Tech debt](docs/roadmap_tech_debt.md) | Known debt with detailed solutions |
| [Privacy](PRIVACY.md) | What the app can see, stores, and (never) sends |

## Tech stack

Kotlin · Jetpack Compose + Material 3 · MVI · Hilt · Room · Navigation3 · Paging3 · Coroutines/Flow · JUnit 5 + Kotest + MockK

## License

[GPL-3.0](LICENSE) — free to use, study, modify, and share; forks stay open source.
