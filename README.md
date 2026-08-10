# Open GDrive

An Android 16, tablet-first Markdown editor that uses Google Drive as its storage layer.

## MVP

- Lists existing `.md` files from Google Drive.
- Opens and renders Markdown with tables, task lists, links, and strikethrough.
- Side-by-side editor and live preview on large screens.
- One-second autosave plus an explicit save action.
- Optimistic conflict protection when Drive returns an ETag.
- Signed APK releases produced only by GitHub Actions.

## Google Cloud setup

The source builds without secrets, but Drive authorization requires an OAuth configuration tied to the APK signing certificate.

1. Create or select a project in Google Cloud Console.
2. Enable **Google Drive API**.
3. Configure the OAuth consent screen and add the Drive scope `https://www.googleapis.com/auth/drive`.
4. Create an **Android OAuth client** with package name `dev.opengdrive`.
5. Add the SHA-1 fingerprint of the certificate used to sign the installed APK.

For local debug builds, obtain the fingerprint with:

```sh
./gradlew signingReport
```

For GitHub releases, use the SHA-1 of the private release keystore stored in repository Actions secrets.

## Build

Requirements: JDK 17 and Android SDK 36.

```sh
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Release

The `Release Android APK` workflow accepts a semantic version and Android version code. It requires these repository secrets:

- `SIGNING_KEY_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

It tests, builds a signed minified APK, creates a `vX.Y.Z` tag, and publishes the APK as a GitHub Release asset.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design decisions and next steps.

## License

MIT License.
