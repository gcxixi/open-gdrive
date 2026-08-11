package dev.opengdrive.data

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalMarkdownStoreTest {
    private lateinit var directory: java.io.File
    private lateinit var store: LocalMarkdownStore

    @Before fun setUp() {
        directory = Files.createTempDirectory("open-gdrive-drafts").toFile()
        store = LocalMarkdownStore(directory)
    }

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    @Test fun `new markdown survives a new store instance`() {
        val created = store.create("untitled-1.md", "root-folder")
        store.save(created.copy(content = "# Local first", revision = 2, dirty = true))

        val restored = LocalMarkdownStore(directory).find(created.localId)!!

        assertEquals("untitled-1.md", restored.name)
        assertEquals("# Local first", restored.content)
        assertTrue(restored.dirty)
    }

    @Test fun `older asynchronous write cannot replace a newer revision`() {
        val created = store.create("notes.md", "all")
        val newer = store.save(created.copy(content = "new", revision = 3, dirty = true))
        val result = store.save(created.copy(content = "old", revision = 2, dirty = true))

        assertEquals(newer, result)
        assertEquals("new", store.find(created.localId)!!.content)
    }

    @Test fun `sync only clears dirty when the synced revision is current`() {
        val created = store.create("notes.md", "all")
        val edited = store.save(created.copy(content = "new", revision = 2, dirty = true))

        val stale = store.markSynced(created.localId, 1, "drive-id", "etag-1")!!
        assertTrue(stale.dirty)

        val current = store.markSynced(created.localId, edited.revision, "drive-id", "etag-2")!!
        assertFalse(current.dirty)
        assertEquals("etag-2", current.etag)
    }

    @Test fun `deleting a local draft removes content and metadata`() {
        val created = store.create("discard.md", "all")

        assertTrue(store.delete(created.localId))
        assertEquals(null, store.find(created.localId))
        assertFalse(store.delete(created.localId))
    }
}
