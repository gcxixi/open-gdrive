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

Drive authorization requires a Google Cloud project whose Android OAuth client matches both the package name and the certificate that signed the installed APK.

Quick reference:

- Package name: `dev.opengdrive`
- OAuth scope: `https://www.googleapis.com/auth/drive`
- Release certificate SHA-1: `C9:9F:66:48:3A:5F:F3:CE:5E:F6:89:A8:FF:71:E0:57:16:F5:CA:32`
- No `google-services.json`, embedded client ID, or Android client secret is required.

Follow the complete Chinese guide in [docs/GOOGLE_DRIVE_SETUP.md](docs/GOOGLE_DRIVE_SETUP.md). It covers Google Cloud project creation, Drive API enablement, consent-screen configuration, test users, debug and release certificates, production verification, GitHub signing secrets, and troubleshooting.

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

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for design decisions and [docs/GOOGLE_DRIVE_SETUP.md](docs/GOOGLE_DRIVE_SETUP.md) for the complete configuration guide.

## License

MIT License.
