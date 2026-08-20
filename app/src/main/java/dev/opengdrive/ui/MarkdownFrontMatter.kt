package dev.opengdrive.ui

internal data class MarkdownParts(
    val frontMatter: String? = null,
    val body: String,
)

/** Splits YAML Front Matter only when it is the first block in the document. */
internal fun splitMarkdownFrontMatter(markdown: String): MarkdownParts {
    val contentStart = if (markdown.startsWith('\uFEFF')) 1 else 0
    val firstLineEnd = markdown.indexOf('\n', contentStart).takeIf { it >= 0 } ?: markdown.length
    if (markdown.substring(contentStart, firstLineEnd).trim() != "---") {
        return MarkdownParts(body = markdown)
    }

    var lineStart = if (firstLineEnd < markdown.length) firstLineEnd + 1 else markdown.length
    val searchEnd = minOf(markdown.length, lineStart + MAX_FRONT_MATTER_CHARS)
    while (lineStart < searchEnd) {
        val lineEnd = markdown.indexOf('\n', lineStart).takeIf { it >= 0 } ?: markdown.length
        val line = markdown.substring(lineStart, lineEnd).trim()
        if (line == "---" || line == "...") {
            val bodyStart = if (lineEnd < markdown.length) lineEnd + 1 else markdown.length
            return MarkdownParts(
                frontMatter = markdown.substring(firstLineEnd + 1, lineStart).trimEnd('\r', '\n'),
                body = markdown.substring(bodyStart),
            )
        }
        if (lineEnd >= markdown.length) break
        lineStart = lineEnd + 1
    }
    return MarkdownParts(body = markdown)
}

private const val MAX_FRONT_MATTER_CHARS = 64 * 1024
