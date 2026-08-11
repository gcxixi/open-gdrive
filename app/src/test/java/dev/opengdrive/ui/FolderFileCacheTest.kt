package dev.opengdrive.ui

import dev.opengdrive.data.DriveFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderFileCacheTest {
    @Test fun `returns cached folder until ttl expires`() {
        var time = 1_000L
        val cache = FolderFileCache(ttlMillis = 5_000L, now = { time })
        val files = listOf(DriveFile("1", "notes.md", "text/markdown"))

        cache.put("root", files)
        time += 4_999L
        assertEquals(files, cache.get("root"))

        time += 1L
        assertNull(cache.get("root"))
    }

    @Test fun `folder entries are isolated`() {
        val cache = FolderFileCache(now = { 0L })
        cache.put("a", listOf(DriveFile("1", "a.txt", "text/plain")))
        cache.put("b", listOf(DriveFile("2", "b.txt", "text/plain")))

        assertEquals("a.txt", cache.get("a")?.single()?.name)
        assertEquals("b.txt", cache.get("b")?.single()?.name)
    }
}
