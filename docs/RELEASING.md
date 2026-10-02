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

F-Droid is unaffected by any of the automation below — it builds from source from the git tag, independently of the signed APK/AAB fastlane/CI produce.

## Signing & secrets

The release APK/AAB are signed with a single **upload keystore**, provided by you — Notificapp's automation never generates or commits one. F-Droid's own signing (source build) and Google Play's app-signing key (Play App Signing re-signs whatever we upload) are separate identities and are not affected by this keystore.

### 1. Generate an upload keystore (once)

Documentation only — run this yourself, do not commit the output:

```bash
keytool -genkey -v -keystore upload-keystore.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias notificapp-upload
```

Keep `upload-keystore.jks` and its passwords somewhere safe (password manager). It must never be committed — `.gitignore` already excludes `*.jks`, `*.keystore`, and `keystore.properties`.

### 2. Local builds

Create a gitignored `keystore.properties` at the repo root:

```properties
storeFile=/absolute/path/to/upload-keystore.jks
storePassword=...
keyAlias=notificapp-upload
keyPassword=...
```

If this file is absent and the `UPLOAD_*` env vars below aren't set either, `assembleRelease` still configures and builds — just unsigned.

### 3. CI (GitHub Actions) secrets

Base64-encode the keystore and set these **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|--------|-------|
| `UPLOAD_KEYSTORE_BASE64` | `base64 -i upload-keystore.jks \| pbcopy` (or equivalent) |
| `UPLOAD_STORE_PASSWORD` | keystore password |
| `UPLOAD_KEY_ALIAS` | `notificapp-upload` (or whatever alias you chose) |
| `UPLOAD_KEY_PASSWORD` | key password |

### 4. Play service account (optional, for Play internal track)

Play Console → Setup → API access → create a service account with "Release to production, exclude devices, and use Play App Signing" permission (or the internal-testing equivalent) → download its JSON key.

Set it as the `PLAY_SERVICE_ACCOUNT_JSON` repository secret (the raw JSON content). Also set the `ENABLE_PLAY_DEPLOY` repository **variable** to `true` once you want `release.yml` to actually push to Play — until then the Play deploy job is skipped, not failed.

The app must already exist in the Play Console under `dev.gaferneira.notificapp` before the first `supply`/`upload_to_play_store` call — that first creation is always manual.

## Automated release

Once the secrets above are set, cutting a release is:

1. Follow the manual checklist below (bump version, write changelog, verify migrations, run the local gate).
2. Commit, then `git tag v<versionName>` and `git push --tags`.
3. `.github/workflows/release.yml` picks up the tag and:
   - Validates the tag matches `v<versionName>` and that a changelog exists for `versionCode` — fails fast with a clear error otherwise.
   - Runs `spotlessCheck`, `detekt`, `test` as a gate.
   - Builds a signed APK and AAB using the `UPLOAD_*` secrets.
   - Creates a GitHub Release for the tag, attaching the signed APK, with the changelog as the release body.
   - If `ENABLE_PLAY_DEPLOY == 'true'`, builds again and pushes the AAB to the Play **internal** track via fastlane `supply`. Otherwise this step is skipped (no-op), not a failure.

What's still manual: the version/changelog authorship itself (by design — versionCode is never auto-generated), the Room migration check (`connectedDebugAndroidTest`, needs an emulator), and the one-time Play Console app creation.
