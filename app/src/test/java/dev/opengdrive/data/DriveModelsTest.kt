package dev.opengdrive.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModelsTest {
    @Test fun `markdown extension is case insensitive`() {
        assertTrue(DriveFile("1", "notes.md").isMarkdown())
        assertTrue(DriveFile("2", "README.MD").isMarkdown())
    }

    @Test fun `markdown-looking backup is excluded`() {
        assertFalse(DriveFile("1", "notes.md.bak").isMarkdown())
        assertFalse(DriveFile("2", "notes.txt").isMarkdown())
    }

    @Test fun `Drive query excludes folders and trash`() {
        assertTrue(DriveApi.MARKDOWN_QUERY.contains("trashed = false"))
        assertTrue(DriveApi.MARKDOWN_QUERY.contains("name contains '.md'"))
        assertTrue(DriveApi.MARKDOWN_QUERY.contains("folder"))
    }
}
