# Architecture

Open GDrive is a tablet-first Android 16 app. Google Drive is the source of truth; the app keeps no second document database.

## Data flow

1. `DriveAuthorization` asks Google Identity Services for the full Drive OAuth scope.
2. `DriveApi` calls Drive REST v3 directly with the short-lived access token.
3. `OpenGDriveViewModel` owns folder navigation, the current file, editor buffer, and save state.
4. The default list-detail layout shows a folder/file pane and one preview pane.
5. Markdown editing is opt-in. Edit mode adds the editor between files and preview; hiding files produces a focused editor/preview layout.
6. A dirty Markdown buffer is uploaded at most once every five seconds. Unchanged content never produces an upload. `If-Match` prevents silently replacing a remotely changed file when Drive supplies an ETag.

## Preview routing

- Markdown uses Markwon with tables, tasks, links, and strikethrough.
- Text, source code, JSON, XML, YAML, and CSV use a selectable native text preview.
- Images are decoded locally; PDFs are rendered page-by-page with Android `PdfRenderer`.
- Google Docs export to plain text, Sheets to CSV, Slides to PDF, and Drawings to PNG through Drive REST v3.
- Unsupported binary formats remain visible in the file browser and can be opened in Google Drive through `webViewLink`.

Preview downloads are capped at 25 MB to avoid loading an unexpectedly large Drive file into the app process.

Drive list responses use an explicit streaming JSON parser. They do not rely on reflected Kotlin model names, so R8 minification cannot silently turn a successful Release response into an empty file list.

Folder listings use a five-minute process-memory cache keyed by Drive folder ID. Revisiting a folder avoids a network request while the entry is fresh, and the refresh action always bypasses the cache. The cache is intentionally not written to disk because file names are private Drive metadata and the current authorization flow does not expose a stable account identifier for safely separating multiple accounts.

The token is deliberately held in memory only. After process death or a 401 response, the app requests authorization again; Google can satisfy an existing grant without showing consent every time.

## Why Markwon

Markwon is a mature native Android CommonMark renderer. The app enables tables, task lists, and strikethrough while keeping editing as plain Markdown. Rendering runs in a native `TextView`, embedded in Compose, which preserves selection and link handling.

## Scope decision

`drive.file` can only access files the user opened with or created through this app. The core requirement is to find existing Markdown anywhere in Drive, so the MVP requests `https://www.googleapis.com/auth/drive`. This is a restricted scope and requires Google OAuth verification before broad public distribution.

## Next milestones

- Create, rename, move, search, and delete Markdown files.
- Local encrypted cache and offline conflict resolution.
- Shared Drive support and folder navigation.
- Images referenced by relative Drive paths.
- Editor optimizations for very large documents and optional syntax highlighting.
