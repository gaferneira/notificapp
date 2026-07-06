# Contributing to Notificapp

Thanks for your interest! There are two very different ways to contribute, and **you don't need to know Kotlin for the first one**.

## 1. Contribute extraction rules (no coding required)

Notificapp's value grows with every shared rule — a tested rule for a popular app ("Revolut transaction", "DHL tracking") helps every user of that app.

**Status:** the shareable rule format and community gallery are under development (see [roadmap Phase 2](docs/roadmap.md)). Until they ship you can still help:

- Open a **Rule request** issue describing an app and the notification data worth extracting (use the issue template, and **redact anything personal** from sample notification text)
- Once rule import/export ships, submit rules as JSON files via PR to the `rules/` directory (a format spec in `docs/rule-format.md` and a dedicated PR template will exist by then)

## 2. Contribute code

### Setup

1. Install [Android Studio](https://developer.android.com/studio) (latest stable) with JDK 17+
2. Clone and build:

```bash
git clone https://github.com/gaferneira/Notificapp.git
cd Notificapp
./gradlew assembleDebug
```

3. Run the app on a device/emulator and grant Notification Access when prompted

### Before you start

- **Small fixes** (bugs, typos, docs): just open a PR
- **Features or behavior changes**: open an issue first so we can agree on direction before you invest time — the [roadmap](docs/roadmap.md) defines current priorities, and features on the [out-of-scope list](docs/roadmap.md#out-of-scope-mvp) won't be merged
- Read the architecture ground rules: [`CLAUDE.md`](CLAUDE.md) (quick reference), [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (full guide), and [`docs/adr/`](docs/adr/) (decision records). Key expectations:
  - MVI pattern with per-feature Contract objects (ADR 001/002)
  - Feature-first packaging: new screens live in `features/[name]/` with `contract/`, `ui/`, `viewmodel/`
  - No DAO or entity access outside `core/data` — go through repositories (ADR 005)
  - No hardcoded dispatchers — inject them (ADR 008)
  - **No network access and no new permissions** without prior discussion (ADR 012 — this is the product's core promise)
  - Features need agreed-upon behavior before code: open an issue describing what the feature should do. Behavior specs live in `openspec/specs/` as plain markdown (Gherkin scenarios) — maintainers use the [OpenSpec workflow](docs/SDD-METHODOLOGY.md) to produce them, and you're welcome to try it, but it is **not required**; a clear issue is enough, and a maintainer will formalize the spec

### Before you push

```bash
./gradlew spotlessApply   # required — CI/pre-commit checks formatting
./gradlew detekt          # required — CI gates complexity/size checks
./gradlew test            # all tests must pass
```

Add unit tests for new logic (JUnit 5 + Kotest + MockK; see the Testing sections in `CLAUDE.md`). Extraction-engine changes without tests won't be merged — that code is pure Kotlin and cheap to test.

If your PR meaningfully touches a file with pre-existing Detekt baseline entries (`config/detekt/baseline.xml`), fix them and regenerate the baseline (`./gradlew detektBaseline`) as part of the same PR — see the boy-scout policy in `CLAUDE.md`.

### Commits and PRs

- Branch from `main` (`feature/...` or `fix/...`)
- If you use the OpenSpec workflow: active `openspec/changes/` stay on your feature branch and are archived before merge — `main` only carries `openspec/changes/archive/`
- Conventional-style commit messages, as in the existing history: `feat: ...`, `fix: ...`, `refactor: ...`
- Fill in the PR template; keep PRs focused — one concern per PR
- If your change alters architecture, update the relevant doc/ADR in the same PR

## Code of conduct

Be kind. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

Notificapp is licensed under [GPL-3.0](LICENSE). By contributing, you agree that your contributions are licensed under the same terms.
