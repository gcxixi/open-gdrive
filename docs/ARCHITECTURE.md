# Architecture

Open GDrive is a tablet-first Android 16 app. Google Drive is the source of truth; the app keeps no second document database.

## Data flow

1. `DriveAuthorization` asks Google Identity Services for the full Drive OAuth scope.
2. `DriveApi` calls Drive REST v3 directly with the short-lived access token.
3. `OpenGDriveViewModel` owns the current file, editor buffer, and save state.
4. Compose updates the editor immediately; Markwon renders the same buffer in the adjacent preview.
5. Edits are uploaded after a one-second debounce. `If-Match` prevents silently replacing a remotely changed file when Drive supplies an ETag.

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
