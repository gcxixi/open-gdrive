package dev.opengdrive.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownFrontMatterTest {
    @Test fun `splits YAML front matter from markdown body`() {
        val parts = splitMarkdownFrontMatter(
            """
            ---
            title: Open GDrive
            tags:
              - android
              - markdown
            ---
            # Hello
            Body
            """.trimIndent(),
        )

        assertEquals("title: Open GDrive\ntags:\n  - android\n  - markdown", parts.frontMatter)
        assertEquals("# Hello\nBody", parts.body)
    }

    @Test fun `supports YAML document end marker and Windows line endings`() {
        val parts = splitMarkdownFrontMatter("---\r\ntitle: Notes\r\n...\r\nText")

        assertEquals("title: Notes", parts.frontMatter)
        assertEquals("Text", parts.body)
    }

    @Test fun `accepts harmless whitespace around delimiters`() {
        val parts = splitMarkdownFrontMatter("---  \nkey: value\n  ---\nBody")

        assertEquals("key: value", parts.frontMatter)
        assertEquals("Body", parts.body)
    }

    @Test fun `does not consume horizontal rules outside a valid first block`() {
        val ordinary = splitMarkdownFrontMatter("Intro\n---\nText")
        val unterminated = splitMarkdownFrontMatter("---\ntitle: Draft\nText")

        assertNull(ordinary.frontMatter)
        assertEquals("Intro\n---\nText", ordinary.body)
        assertNull(unterminated.frontMatter)
        assertEquals("---\ntitle: Draft\nText", unterminated.body)
    }
}
