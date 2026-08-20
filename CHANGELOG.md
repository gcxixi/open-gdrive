# Changelog

All notable changes to Open GDrive are documented here.

## [0.4.9] - 2026-08-20

### Added

- Added YAML Front Matter support for Markdown documents. A leading `---` metadata block is rendered separately from the CommonMark body while the editor and Drive file preserve the original source.
- Added native pull-to-refresh to the file list without clearing cached rows while Drive responds.
- Added batch Move for checkbox-selected files, folders, and local-only Markdown drafts, with a navigable My Drive destination picker and best-effort failure reporting.

### Changed

- Drive list metadata now retains parent IDs so remote moves can add the destination and remove the old parent in one request.
- An open Markdown document remains in the editor after being moved, preventing pending editor input from being discarded.

## [0.4.8] - 2026-08-11

### Fixed

- Restored Android 16 touchpad two-finger scrolling in the Markdown editor by bridging pixel-based trackpad pan gestures to Sora's native scroller.
- Kept trackpad scrolling entirely inside the editor viewport so it does not create document snapshots, trigger preview rendering, or enter the local-save and Drive-sync pipeline.
- Preserved touchscreen dragging, mouse-wheel scrolling, selection, and hardware-keyboard behavior.

## [0.4.7] - 2026-08-11

### Added

- Replaced the Compose whole-string text field with the Sora Editor 0.24.4 editing engine for responsive large-document editing, efficient line layout, native undo/redo, selection, word wrapping, and hardware-keyboard support.
- Added explicit local-save state so the UI distinguishes `Saving locally`, `Saved locally`, `Syncing`, `Synced`, and `Sync issue`.
- Added capped exponential retry for transient Drive failures without blocking or interrupting editing.

### Changed

- Editor changes stay inside Sora's incremental content model while typing. Open GDrive creates a full UTF-8 snapshot only after input becomes idle, with a longer adaptive delay for very large documents.
- Markdown preview rendering is now debounced by document size. CommonMark parsing, tables, task lists, and fenced-code highlighting run on a background dispatcher; the previous rendered preview remains visible until the new render is ready.
- Local drafts are persisted after a 750 ms idle debounce, while Drive synchronization is independently delayed until 30 seconds of editing inactivity. Explicit Save and Done still persist locally and request synchronization immediately.
- Transient Drive errors now remain a compact sync-status warning instead of appearing as editing errors or Snackbar interruptions.
- Local draft persistence no longer rereads the complete Markdown body just to compare revisions, and avoids an unnecessary first metadata flush when the document is already marked dirty.

### Fixed

- Fixed severe input lag caused by parsing, syntax-highlighting, and laying out the entire Markdown preview on every keystroke.
- Fixed large-document network failures appearing to be editor failures even though the local draft was safe.
- Reduced repeated whole-document string copies, disk reads, writes, and full-file Drive uploads during active typing.

## [0.4.6] - 2026-08-11

- Replaced the top workspace control group with a compact bottom-right settings speed dial.
- Kept file-list, preview, refresh, and sync-state controls reachable independently of pane visibility.
