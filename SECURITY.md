# Security Policy

Notificapp handles some of the most sensitive data on a device — notification content can include private messages, one-time passcodes, and financial information. We take reports about anything that could compromise that data seriously.

## Supported versions

The project is pre-release; security fixes land on the `main` branch. Once tagged releases exist, the latest release will be the supported version.

## Reporting a vulnerability

**Please do not open a public issue for security vulnerabilities.**

Instead, use one of these private channels:

1. **GitHub private vulnerability reporting** (preferred): use the *"Report a vulnerability"* button under the repository's **Security** tab
2. **Email**: gabo.neira@gmail.com with a subject starting with `[SECURITY]`

Include what you can: affected component, steps to reproduce or proof of concept, and your assessment of impact.

## What to expect

- **Acknowledgement** within 7 days
- A fix or mitigation plan communicated once the report is validated
- Credit in the release notes when the fix ships (unless you prefer to stay anonymous)
- Public disclosure coordinated with you after a fix is available

## In scope

- Any way notification data or extracted data could leave the device without explicit user action
- Data exposure to other apps (exported components, world-readable storage, intent leaks)
- Bypasses of the app-selection boundary (capturing notifications from non-monitored apps)
- Rule import parsing issues (malicious rule JSON), once import ships

## Out of scope

- Attacks requiring a rooted device or physical access to an unlocked phone
- The inherent capabilities of the Notification Access permission itself (documented in [PRIVACY.md](PRIVACY.md))
