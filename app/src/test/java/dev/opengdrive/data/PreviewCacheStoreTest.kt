package dev.opengdrive.data

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PreviewCacheStoreTest {
    private lateinit var directory: java.io.File
    private lateinit var store: PreviewCacheStore

    @Before fun setUp() {
        directory = Files.createTempDirectory("open-gdrive-previews").toFile()
        store = PreviewCacheStore(directory)
    }

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    @Test fun `markdown preview and revision metadata survive a new store instance`() {
        val opened = OpenDriveFile(
            file = DriveFile(
                id = "drive/file:1",
                name = "notes.md",
                mimeType = "text/markdown",
                modifiedTime = "2026-08-11T03:15:13.000Z",
                size = "12",
                capabilities = DriveCapabilities(canEdit = true),
            ),
            preview = PreviewData.Markdown("# Cached"),
            etag = "etag-1",
        )

        store.put(opened)
        val restored = PreviewCacheStore(directory).find(opened.file.id)!!.opened

        assertEquals(opened.file, restored.file)
        assertEquals(opened.preview, restored.preview)
        assertEquals("etag-1", restored.etag)
    }

    @Test fun `binary previews retain their original bytes`() {
        val bytes = byteArrayOf(0, 1, 2, 127, -1)
        val opened = OpenDriveFile(
            DriveFile("image-id", "photo.png", "image/png"),
            PreviewData.Image(bytes, "image/png"),
            "etag-image",
        )

        store.put(opened)
        val restored = store.find("image-id")!!.opened.preview as PreviewData.Image

        assertArrayEquals(bytes, restored.bytes)
        assertEquals("image/png", restored.mimeType)
        assertNull(store.find("missing"))
    }

    @Test fun `removing a preview clears its cache entry`() {
        val opened = OpenDriveFile(
            DriveFile("delete-me", "notes.md", "text/markdown"),
            PreviewData.Markdown("temporary"),
            "etag",
        )
        store.put(opened)

        store.remove(opened.file.id)

        assertNull(store.find(opened.file.id))
    }
}
