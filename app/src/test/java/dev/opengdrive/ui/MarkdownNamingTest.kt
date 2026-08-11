package dev.opengdrive.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownNamingTest {
    @Test fun `new markdown uses first available untitled number`() {
        assertEquals(
            "untitled-3.md",
            OpenGDriveViewModel.nextUntitledName(listOf("Untitled-1.md", "untitled-2.MD")),
        )
    }

    @Test fun `rename normalizes markdown extension and rejects paths`() {
        assertEquals("notes.md", OpenGDriveViewModel.normalizeMarkdownName(" notes "))
        assertEquals("README.MD", OpenGDriveViewModel.normalizeMarkdownName("README.MD"))
        assertNull(OpenGDriveViewModel.normalizeMarkdownName("folder/notes"))
        assertNull(OpenGDriveViewModel.normalizeMarkdownName("  "))
    }
}
