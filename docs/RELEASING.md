# Releasing

Notificapp targets F-Droid as its primary distribution channel (see `docs/roadmap.md` - Distribution). F-Droid builds are keyed to tagged versions, and its changelog format is keyed to `versionCode` — both need to exist from the first tagged release onward, since they can't be retrofitted onto history.

## Versioning scheme

- `versionCode` (in `app/build.gradle.kts`) is a monotonically increasing integer. Bump it by exactly 1 on every tagged release, forever — never reuse or skip a value.
- `versionName` follows `0.x.y` semver-ish numbering until the first stable release, then standard semver.
- Tag format: `v<versionName>` (e.g. `v0.3.0`), created from the commit that bumps the version.

## Release checklist

1. Bump `versionCode` (+1) and `versionName` in `app/build.gradle.kts`.
2. Write `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` — plain text, user-facing, one file per `versionCode`.
3. Verify the Room migration chain covers every previously-shipped schema version (`APP_DATABASE_MIGRATIONS` in `core/data/local/migration/`). `AppDatabaseMigrationTest` is the test that catches a gap here, but it's an `androidTest` and needs an emulator — CI (`.github/workflows/ci.yml`) does not run it. Run it manually before every release: `./gradlew connectedDebugAndroidTest`.
4. Run the full local gate: `./gradlew spotlessApply detekt test assembleDebug`.
5. Commit the version bump and changelog together, then tag: `git tag v<versionName>`.
6. Push the tag.

## Fastlane metadata

F-Droid reads listing metadata from `fastlane/metadata/android/en-US/`:

```
fastlane/metadata/android/en-US/
├── short_description.txt
├── full_description.txt
├── changelogs/
│   └── <versionCode>.txt
└── images/
```

`short_description.txt` and `full_description.txt` are populated once, ahead of the first submission to F-Droid; screenshots (`images/`) are a roadmap item tracked separately.
