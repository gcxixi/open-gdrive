# Open GDrive

An Android 16, tablet-first Markdown editor that uses Google Drive as its storage layer.

## MVP

- Browses all files and folders in Google Drive.
- Creates new Markdown files immediately as local drafts, then creates the matching Drive file asynchronously.
- Reuses folder listings from a five-minute in-memory cache; toolbar refresh and list pull-to-refresh always bypass it without clearing the visible cache first.
- Opens previously viewed files from an app-private preview cache immediately, then validates the cached revision against Drive asynchronously. ETag is preferred, with modified time and size as fallback.
- Previews Markdown with YAML Front Matter metadata, fenced-code syntax highlighting, plus text/code, JSON, CSV, images, PDFs, Google Docs, Sheets, Slides, and Drawings.
- Uses a clean file-list + preview layout by default.
- Uses compact single-line file rows with consistently sized Simple Icons brand vectors for Google Workspace and Markdown.
- Enters a three-pane file/editor/preview workspace only while editing Markdown; the file pane can be collapsed.
- Lets tablet editors hide the file list and preview independently for a distraction-free full-width editor.
- Uses the Sora editing engine so large Markdown files do not re-layout or copy the entire document on every keystroke.
- Persists edits and renames to app-private local storage first, then syncs changed Markdown to Drive after 30 seconds of editing inactivity or on explicit save.
- Debounces real-time preview by document size and parses Markdown plus fenced-code highlighting off the UI thread.
- Keeps local-save and Drive-sync states separate; transient network failures never block editing and retry with capped exponential backoff.
- Keeps unsynced drafts across process restarts and surfaces a compact red sync warning when Drive synchronization fails.
- Optimistic conflict protection when Drive returns an ETag.
- Keeps cached Markdown read-only until Drive revision validation finishes; failed validation never silently unlocks editing.
- Reveals one fixed-width delete action by swiping a row from right to left; the row can be swiped right to close it. Long-press enters checkbox-based multi-select. Every single or batch deletion requires confirmation, and Drive items are moved to Trash rather than permanently erased.
- Moves checkbox-selected files, folders, and unsynced local Markdown drafts through a My Drive folder picker. An open document remains editable after it is moved.
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

Release changes are recorded in [CHANGELOG.md](CHANGELOG.md).

## License

MIT License.

The APK includes [Sora Editor](https://github.com/Rosemoe/sora-editor), licensed under LGPL-2.1-or-later. Open GDrive's complete corresponding source and reproducible Gradle configuration are provided in this repository.
