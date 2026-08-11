package dev.opengdrive.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModelsTest {
    @Test fun `markdown extension is case insensitive`() {
        assertTrue(DriveFile("1", "notes.md").isMarkdown())
        assertTrue(DriveFile("2", "README.MD").isMarkdown())
    }

    @Test fun `markdown-looking backup remains a regular file`() {
        assertFalse(DriveFile("1", "notes.md.bak").isMarkdown())
        assertFalse(DriveFile("2", "notes.txt").isMarkdown())
    }

    @Test fun `folders and common files receive the right preview strategy`() {
        assertTrue(DriveFile("1", "Notes", GOOGLE_FOLDER).isFolder())
        assertTrue(DriveFile("2", "notes.md", "text/plain").previewSpec() is PreviewSpec.Download)
        assertTrue(DriveFile("3", "photo.jpg", "image/jpeg").previewSpec() is PreviewSpec.Download)
        assertTrue(DriveFile("4", "Sheet", GOOGLE_SPREADSHEET).previewSpec() is PreviewSpec.Export)
        assertTrue(DriveFile("5", "archive.zip", "application/zip").previewSpec() is PreviewSpec.Unsupported)
    }

    @Test fun `child query escapes Drive folder id`() {
        assertEquals("trashed = false", DriveApi.childQuery("all"))
        assertEquals(
            "trashed = false and 'folder\\'id' in parents",
            DriveApi.childQuery("folder'id"),
        )
    }
}
