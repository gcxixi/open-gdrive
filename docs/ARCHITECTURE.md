# Architecture

Open GDrive is a tablet-first Android 16 app. Google Drive is the source of truth; the app keeps no second document database.

## Data flow

1. `DriveAuthorization` asks Google Identity Services for the full Drive OAuth scope.
2. `DriveApi` calls Drive REST v3 directly with the short-lived access token.
3. `OpenGDriveViewModel` owns folder navigation, durable Markdown snapshots, and independent local-save and Drive-sync state. Sora Editor owns the incremental live editing buffer while the user is typing.
4. The default list-detail layout shows a folder/file pane and one preview pane.
5. Markdown editing is opt-in. Edit mode adds the editor between files and preview; the file and preview panes can be hidden independently, including a full-width distraction-free editor.
6. Markdown editing is local-first. Content and filename changes are atomically persisted in app-private storage before a Drive request is scheduled.
7. A dirty local revision is uploaded after 30 seconds of editing inactivity or immediately on explicit Save/Done. `If-Match` prevents silently replacing a remotely changed file when Drive supplies an ETag.
8. New documents begin as `untitled-N.md` local drafts. A multipart Drive upload later creates metadata and content together; a successful response binds the stable local ID to the new Drive file ID.
9. Reopening a file is cache-first. The last preview is rendered immediately while a lightweight Drive metadata request validates its ETag, or its modified time and size when no ETag is available. A changed revision is downloaded and rendered only after that comparison.
10. Deletion is explicit and recoverable for remote files. A bounded bidirectional row drag reveals exactly one delete action and can be dragged closed; long-press starts checkbox multi-selection. Both paths always open a confirmation dialog, then remote files and folders are patched with `trashed=true`. Unsynced local-only drafts are permanently removed after the same confirmation.
11. Multi-selected items can be moved through a My Drive folder picker. Remote items use Drive's atomic parent update; local-only Markdown drafts update their durable parent first and are later created directly in the selected destination.

## Preview routing

- Markdown uses Markwon with tables, tasks, links, strikethrough, and Prism4j syntax highlighting for fenced code blocks. A leading YAML Front Matter block is split before CommonMark parsing and rendered as a compact selectable metadata card; editing and Drive storage retain the exact source syntax.
- Text, source code, JSON, XML, YAML, and CSV use a selectable native text preview.
- Images are decoded locally; PDFs are rendered page-by-page with Android `PdfRenderer`.
- Google Docs export to plain text, Sheets to CSV, Slides to PDF, and Drawings to PNG through Drive REST v3.
- Unsupported binary formats remain visible in the file browser and can be opened in Google Drive through `webViewLink`.

Preview downloads are capped at 25 MB to avoid loading an unexpectedly large Drive file into the app process. While editing, preview updates are adaptively debounced by document size. Markwon parsing and Prism4j highlighting run on a background dispatcher, and the last complete render stays visible until its replacement is ready.

Downloaded Markdown, text, image, and PDF previews are stored in the app-private cache directory. Entries are keyed by a SHA-256 digest of the Drive file ID and capped at 128 files using least-recently-used metadata timestamps. Android may reclaim this cache at any time. Cache loss only causes the next open to download the preview again.

When a cached Markdown preview is selected, the preview becomes visible immediately but editing stays locked during remote revision validation. If the ETag matches, the existing bytes remain on screen and editing unlocks. If it differs, Drive content replaces the cached preview before editing unlocks. If validation fails, the cached preview remains readable and editing stays locked to avoid overwriting an unseen remote revision.

Drive list responses use an explicit streaming JSON parser. They do not rely on reflected Kotlin model names, so R8 minification cannot silently turn a successful Release response into an empty file list.

Folder listings use a five-minute process-memory cache keyed by Drive folder ID. Revisiting a folder avoids a network request while the entry is fresh. Toolbar refresh and native list pull-to-refresh always bypass the cache while retaining the visible rows until the new response arrives. The cache is intentionally not written to disk because file names are private Drive metadata and the current authorization flow does not expose a stable account identifier for safely separating multiple accounts.

## Local-first Markdown state

Each opened or newly created Markdown file gets an app-private record under `filesDir/markdown-documents`. The content is stored as UTF-8 Markdown and metadata is stored separately: local ID, optional Drive ID, parent folder, filename, ETag, monotonically increasing local revision, dirty flag, and the latest synchronization error. Writes use a temporary file followed by replacement and flush the file descriptor before replacement.

Sora Editor is the live source of truth during an editing session. Its incremental content model handles keystrokes, selection, undo/redo, and visible-line layout without recreating a whole-document Compose `String` on every input event. After input becomes idle, the editor emits one full snapshot: 350 ms for ordinary files, 700 ms above 250,000 characters, and 1,200 ms above 1,000,000 characters.

The snapshot increments the local revision and is atomically persisted after a 750 ms disk-write debounce. Drive synchronization is a separate state machine and begins only after 30 seconds of editing inactivity. Save and Done flush the current Sora buffer to local storage before requesting immediate Drive synchronization. If edits arrive while an older revision is syncing, completion clears `dirty` only when its revision is still current; otherwise another idle sync is scheduled.

Local persistence compares metadata revisions without reading the complete previous Markdown body. It rewrites the UTF-8 content atomically, but avoids a redundant crash-safety metadata write when the record is already dirty. The UI separately reports local persistence (`Saving locally`) and remote state (`Saved locally`, `Syncing`, `Synced`, or `Sync issue`).

New files use a multipart Drive upload, which creates the filename, `text/markdown` MIME type, parent, application-local identifier, and Markdown content in one request. Existing files upload content with ETag conflict protection and then synchronize the filename. A failed create, upload, or rename leaves the local record dirty and displays only the compact `Sync issue` state; editing continues entirely from the local buffer. Transient failures retry with capped exponential delays from 10 seconds to 5 minutes, while authorization and conflict failures wait for user resolution. Because OAuth access tokens are deliberately memory-only, synchronization cannot continue after process death; persisted dirty records resume when the app obtains authorization again.

Deleting a draft coordinates with its synchronization job. A scheduled upload is cancelled; an upload already in flight is allowed to settle so a newly assigned Drive ID can also be moved to Trash. Successful deletion clears the local Markdown record, cached preview, list row, sync indicator, and active editor selection together. Batch deletion is best-effort: successful items disappear immediately while failed items remain listed with an error summary.

Moving uses the same synchronization coordination. Drive list metadata includes parent IDs; `files.update` adds the destination parent and removes the previous parent in one request. A local-only draft changes only its durable parent metadata before its normal asynchronous create. Batch moves are best-effort, and an already open editor remains active even if its row moves out of the currently viewed folder so pending Sora input can still be snapshotted safely.

Google Workspace and Markdown rows use local Simple Icons brand vectors, while the remaining file types use Material icons. Every glyph is rendered at 18dp inside the same 28dp pale circular container, so the list stays aligned and works fully offline.

The token is deliberately held in memory only. After process death or a 401 response, the app requests authorization again; Google can satisfy an existing grant without showing consent every time.

## Why Markwon

Markwon is a mature native Android CommonMark renderer. The app enables tables, task lists, and strikethrough while keeping editing as plain Markdown. Rendering runs in a native `TextView`, embedded in Compose, which preserves selection and link handling.

## Why Sora Editor

Compose's value-based `BasicTextField` required Open GDrive to recreate and publish the complete Markdown string for every edit, while Compose and the live preview repeatedly laid out the full document. Sora Editor keeps an incremental line-based content model inside a purpose-built Android editor view and exposes change events without requiring a whole-document callback. Open GDrive deliberately converts the buffer to a `String` only after idle or an explicit flush, preserving the existing local-draft and Drive APIs without putting their cost on the keystroke path.

Android 16 exposes touchpad two-finger scrolling as pixel-based gesture axes rather than only the traditional mouse-wheel axes that Sora handles. `TrackpadCodeEditor` bridges those pan distances directly to Sora's native scroller. The gesture remains a viewport-only operation and does not cross the document snapshot, preview, persistence, or Drive synchronization boundaries.

## Scope decision

`drive.file` can only access files the user opened with or created through this app. The core requirement is to find existing Markdown anywhere in Drive, so the MVP requests `https://www.googleapis.com/auth/drive`. This is a restricted scope and requires Google OAuth verification before broad public distribution.

## Next milestones

- Search Markdown files.
- Local encrypted cache and offline conflict resolution.
- Shared Drive support and folder navigation.
- Images referenced by relative Drive paths.
- Optional incremental Markdown syntax highlighting in the editor without affecting the preview renderer.
