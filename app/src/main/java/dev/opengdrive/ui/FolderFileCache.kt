package dev.opengdrive.ui

import dev.opengdrive.data.DriveFile

internal class FolderFileCache(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val files: List<DriveFile>, val storedAt: Long)

    private val entries = mutableMapOf<String, Entry>()

    fun get(folderId: String): List<DriveFile>? {
        val entry = entries[folderId] ?: return null
        if (now() - entry.storedAt >= ttlMillis) {
            entries.remove(folderId)
            return null
        }
        return entry.files
    }

    fun put(folderId: String, files: List<DriveFile>) {
        entries[folderId] = Entry(files.toList(), now())
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 5 * 60 * 1_000L
    }
}
