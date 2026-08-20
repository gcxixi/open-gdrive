package dev.opengdrive.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveModelsTest {
    @Test fun `metadata comparison prefers etag and falls back to modification data`() {
        val cachedFile = DriveFile("1", "notes.md", modifiedTime = "time-1", size = "10")
        val cached = OpenDriveFile(cachedFile, PreviewData.Markdown("cached"), "etag-1")

        assertTrue(DriveMetadata(cachedFile.copy(name = "renamed.md"), "etag-1").matches(cached))
        assertFalse(DriveMetadata(cachedFile, "etag-2").matches(cached))
        assertTrue(DriveMetadata(cachedFile, null).matches(cached.copy(etag = null)))
        assertFalse(
            DriveMetadata(cachedFile.copy(modifiedTime = "time-2"), null).matches(cached.copy(etag = null)),
        )
    }

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
        assertEquals(
            "trashed = false and 'root' in parents and mimeType = '$GOOGLE_FOLDER'",
            DriveApi.childQuery("root", foldersOnly = true),
        )
    }

    @Test fun `Drive response parser is independent of reflected model names`() {
        val page = DriveApi.parseFilePage(
            """
            {
              "nextPageToken": "next",
              "files": [
                {
                  "id": "folder-1",
                  "name": "Notes",
                  "mimeType": "$GOOGLE_FOLDER",
                  "capabilities": {"canEdit": true, "canDownload": true},
                  "ignoredField": "safe to ignore"
                },
                {
                  "id": "markdown-1",
                  "name": "hello.md",
                  "mimeType": "text/markdown",
                  "size": "42",
                  "webViewLink": "https://drive.google.com/file/d/markdown-1/view"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals("next", page.nextPageToken)
        assertEquals(2, page.files.size)
        assertTrue(page.files.first().isFolder())
        assertTrue(page.files.last().isMarkdown())
        assertEquals("42", page.files.last().size)
    }

    @Test fun `Drive response parser keeps parent ids for move operations`() {
        val file = DriveApi.parseDriveFileJson(
            """{"id":"note-1","name":"note.md","parents":["old-folder"]}""",
        )!!

        assertEquals(listOf("old-folder"), file.parents)
    }

    @Test fun `new markdown metadata escapes names and omits parent for all files view`() {
        assertEquals(
            "{\"name\":\"a\\\"b.md\",\"mimeType\":\"text/markdown\"," +
                "\"appProperties\":{\"openGDriveLocalId\":\"local-1\"}}",
            DriveApi.createMetadataJson("a\"b.md", "all", "local-1"),
        )
        assertTrue(DriveApi.createMetadataJson("notes.md", "folder-1", "local-2").contains(
            "\"parents\":[\"folder-1\"]",
        ))
    }
}
