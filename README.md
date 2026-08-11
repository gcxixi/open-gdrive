# Open GDrive

An Android 16, tablet-first Markdown editor that uses Google Drive as its storage layer.

## MVP

- Browses all files and folders in Google Drive.
- Creates new Markdown files immediately as local drafts, then creates the matching Drive file asynchronously.
- Reuses folder listings from a five-minute in-memory cache; manual refresh always bypasses it.
- Opens previously viewed files from an app-private preview cache immediately, then validates the cached revision against Drive asynchronously. ETag is preferred, with modified time and size as fallback.
- Previews Markdown with fenced-code syntax highlighting, plus text/code, JSON, CSV, images, PDFs, Google Docs, Sheets, Slides, and Drawings.
- Uses a clean file-list + preview layout by default.
- Uses compact single-line file rows with consistently sized Simple Icons brand vectors for Google Workspace and Markdown.
- Enters a three-pane file/editor/preview workspace only while editing Markdown; the file pane can be collapsed.
- Lets tablet editors hide the file list and preview independently for a distraction-free full-width editor.
- Persists edits and renames to app-private local storage first, then syncs changed Markdown to Drive after five seconds or on explicit save.
- Keeps unsynced drafts across process restarts and surfaces a compact red sync warning when Drive synchronization fails.
- Optimistic conflict protection when Drive returns an ETag.
- Keeps cached Markdown read-only until Drive revision validation finishes; failed validation never silently unlocks editing.
- Reveals a delete action by swiping a row from right to left; long-press enters checkbox-based multi-select. Every single or batch deletion requires confirmation, and Drive items are moved to Trash rather than permanently erased.
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
