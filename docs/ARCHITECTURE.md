# Architecture

Open GDrive is a tablet-first Android 16 app. Google Drive is the source of truth; the app keeps no second document database.

## Data flow

1. `DriveAuthorization` asks Google Identity Services for the full Drive OAuth scope.
2. `DriveApi` calls Drive REST v3 directly with the short-lived access token.
3. `OpenGDriveViewModel` owns folder navigation, the current file, editor buffer, and save state.
4. The default list-detail layout shows a folder/file pane and one preview pane.
5. Markdown editing is opt-in. Edit mode adds the editor between files and preview; the file and preview panes can be hidden independently, including a full-width distraction-free editor.
6. Markdown editing is local-first. Content and filename changes are atomically persisted in app-private storage before a Drive request is scheduled.
7. A dirty local revision is uploaded after five seconds or immediately on explicit save. `If-Match` prevents silently replacing a remotely changed file when Drive supplies an ETag.
8. New documents begin as `untitled-N.md` local drafts. A multipart Drive upload later creates metadata and content together; a successful response binds the stable local ID to the new Drive file ID.
9. Reopening a file is cache-first. The last preview is rendered immediately while a lightweight Drive metadata request validates its ETag, or its modified time and size when no ETag is available. A changed revision is downloaded and rendered only after that comparison.
10. Deletion is explicit and recoverable for remote files. A bounded bidirectional row drag reveals exactly one delete action and can be dragged closed; long-press starts checkbox multi-selection. Both paths always open a confirmation dialog, then remote files and folders are patched with `trashed=true`. Unsynced local-only drafts are permanently removed after the same confirmation.

## Preview routing

- Markdown uses Markwon with tables, tasks, links, strikethrough, and Prism4j syntax highlighting for fenced code blocks.
- Text, source code, JSON, XML, YAML, and CSV use a selectable native text preview.
- Images are decoded locally; PDFs are rendered page-by-page with Android `PdfRenderer`.
- Google Docs export to plain text, Sheets to CSV, Slides to PDF, and Drawings to PNG through Drive REST v3.
- Unsupported binary formats remain visible in the file browser and can be opened in Google Drive through `webViewLink`.

Preview downloads are capped at 25 MB to avoid loading an unexpectedly large Drive file into the app process.

Downloaded Markdown, text, image, and PDF previews are stored in the app-private cache directory. Entries are keyed by a SHA-256 digest of the Drive file ID and capped at 128 files using least-recently-used metadata timestamps. Android may reclaim this cache at any time. Cache loss only causes the next open to download the preview again.

When a cached Markdown preview is selected, the preview becomes visible immediately but editing stays locked during remote revision validation. If the ETag matches, the existing bytes remain on screen and editing unlocks. If it differs, Drive content replaces the cached preview before editing unlocks. If validation fails, the cached preview remains readable and editing stays locked to avoid overwriting an unseen remote revision.

Drive list responses use an explicit streaming JSON parser. They do not rely on reflected Kotlin model names, so R8 minification cannot silently turn a successful Release response into an empty file list.

Folder listings use a five-minute process-memory cache keyed by Drive folder ID. Revisiting a folder avoids a network request while the entry is fresh, and the refresh action always bypasses the cache. The cache is intentionally not written to disk because file names are private Drive metadata and the current authorization flow does not expose a stable account identifier for safely separating multiple accounts.

## Local-first Markdown state

Each opened or newly created Markdown file gets an app-private record under `filesDir/markdown-documents`. The content is stored as UTF-8 Markdown and metadata is stored separately: local ID, optional Drive ID, parent folder, filename, ETag, monotonically increasing local revision, dirty flag, and the latest synchronization error. Writes use a temporary file followed by replacement and flush the file descriptor before replacement.

The UI treats this local record as the editable source of truth. Every text or filename change increments its revision and is persisted locally after a short 150 ms disk-write debounce. Drive synchronization remains at five seconds, so rapid input does not create one network request per keystroke. If edits arrive while an older revision is syncing, the completion only clears `dirty` when its revision is still current; otherwise another sync is scheduled.

New files use a multipart Drive upload, which creates the filename, `text/markdown` MIME type, parent, application-local identifier, and Markdown content in one request. Existing files upload content with ETag conflict protection and then synchronize the filename. A failed create, upload, or rename leaves the local record dirty, displays `Sync issue`, and can retry on explicit save or after the next authorization. Because OAuth access tokens are deliberately memory-only, synchronization cannot continue after process death; the persisted dirty records resume when the app obtains authorization again.

Deleting a draft coordinates with its synchronization job. A scheduled upload is cancelled; an upload already in flight is allowed to settle so a newly assigned Drive ID can also be moved to Trash. Successful deletion clears the local Markdown record, cached preview, list row, sync indicator, and active editor selection together. Batch deletion is best-effort: successful items disappear immediately while failed items remain listed with an error summary.

Google Workspace and Markdown rows use local Simple Icons brand vectors, while the remaining file types use Material icons. Every glyph is rendered at 18dp inside the same 28dp pale circular container, so the list stays aligned and works fully offline.

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
