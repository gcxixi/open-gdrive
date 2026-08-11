package dev.opengdrive.data

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String = "application/octet-stream",
    val modifiedTime: String? = null,
    val size: String? = null,
    val webViewLink: String? = null,
    val capabilities: DriveCapabilities? = null,
)

data class DriveCapabilities(
    val canEdit: Boolean = false,
    val canDownload: Boolean = true,
)

data class DriveFilePage(
    val files: List<DriveFile> = emptyList(),
    val nextPageToken: String? = null,
)

data class DriveFolder(val id: String, val name: String)

data class OpenDriveFile(
    val file: DriveFile,
    val preview: PreviewData,
    val etag: String?,
)

data class DriveMetadata(
    val file: DriveFile,
    val etag: String?,
)

internal fun DriveMetadata.matches(cached: OpenDriveFile): Boolean {
    if (etag != null && cached.etag != null) return etag == cached.etag
    return file.modifiedTime == cached.file.modifiedTime && file.size == cached.file.size
}

sealed interface PreviewData {
    data class Markdown(val text: String) : PreviewData
    data class Text(val text: String, val formatLabel: String) : PreviewData
    data class Image(val bytes: ByteArray, val mimeType: String) : PreviewData
    data class Pdf(val bytes: ByteArray) : PreviewData
    data class Unsupported(val reason: String) : PreviewData
}

internal fun DriveFile.isFolder() = mimeType == GOOGLE_FOLDER

internal fun DriveFile.isMarkdown() =
    name.endsWith(".md", ignoreCase = true) ||
        mimeType.equals("text/markdown", ignoreCase = true) ||
        mimeType.equals("text/x-markdown", ignoreCase = true)

internal fun DriveFile.previewSpec(): PreviewSpec = when {
    isFolder() -> PreviewSpec.Unsupported("Folder")
    capabilities?.canDownload == false -> PreviewSpec.Unsupported("The file owner disabled downloading")
    isMarkdown() -> PreviewSpec.Download(PreviewKind.Markdown)
    mimeType == GOOGLE_DOCUMENT -> PreviewSpec.Export("text/plain", PreviewKind.Text("Google Docs"))
    mimeType == GOOGLE_SPREADSHEET -> PreviewSpec.Export("text/csv", PreviewKind.Text("Google Sheets · CSV"))
    mimeType == GOOGLE_PRESENTATION -> PreviewSpec.Export("application/pdf", PreviewKind.Pdf)
    mimeType == GOOGLE_DRAWING -> PreviewSpec.Export("image/png", PreviewKind.Image)
    mimeType == "application/pdf" -> PreviewSpec.Download(PreviewKind.Pdf)
    mimeType.startsWith("image/") -> PreviewSpec.Download(PreviewKind.Image)
    isTextLike() -> PreviewSpec.Download(PreviewKind.Text(textFormatLabel()))
    mimeType.startsWith("application/vnd.google-apps.") ->
        PreviewSpec.Unsupported("This Google Workspace file type cannot be exported for an in-app preview yet")
    else -> PreviewSpec.Unsupported("No safe preview is available for this binary file")
}

private fun DriveFile.isTextLike(): Boolean {
    if (mimeType.startsWith("text/")) return true
    if (mimeType in TEXT_APPLICATION_MIME_TYPES) return true
    return name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
}

private fun DriveFile.textFormatLabel(): String {
    val extension = name.substringAfterLast('.', "").uppercase()
    return extension.ifBlank { "Text" }
}

internal sealed interface PreviewSpec {
    data class Download(val kind: PreviewKind) : PreviewSpec
    data class Export(val mimeType: String, val kind: PreviewKind) : PreviewSpec
    data class Unsupported(val reason: String) : PreviewSpec
}

internal sealed interface PreviewKind {
    data object Markdown : PreviewKind
    data class Text(val label: String) : PreviewKind
    data object Image : PreviewKind
    data object Pdf : PreviewKind
}

internal const val GOOGLE_FOLDER = "application/vnd.google-apps.folder"
internal const val GOOGLE_DOCUMENT = "application/vnd.google-apps.document"
internal const val GOOGLE_SPREADSHEET = "application/vnd.google-apps.spreadsheet"
internal const val GOOGLE_PRESENTATION = "application/vnd.google-apps.presentation"
internal const val GOOGLE_DRAWING = "application/vnd.google-apps.drawing"

private val TEXT_APPLICATION_MIME_TYPES = setOf(
    "application/json",
    "application/ld+json",
    "application/xml",
    "application/x-httpd-php",
    "application/x-sh",
    "application/x-yaml",
    "application/yaml",
    "application/toml",
    "application/javascript",
    "application/sql",
)

private val TEXT_EXTENSIONS = setOf(
    "txt", "text", "log", "csv", "tsv", "json", "jsonl", "xml", "yaml", "yml", "toml",
    "ini", "conf", "cfg", "properties", "gradle", "kts", "kt", "java", "c", "h", "cpp", "hpp",
    "cs", "go", "rs", "py", "rb", "php", "js", "jsx", "ts", "tsx", "css", "scss", "html",
    "htm", "sh", "zsh", "bash", "fish", "sql", "graphql", "proto", "tex", "rst", "adoc",
)

sealed class DriveException(message: String) : Exception(message) {
    class Unauthorized : DriveException("Google Drive authorization expired")
    class Conflict : DriveException("The file changed in Google Drive; reload before saving")
    class TooLarge(val limitMb: Int) : DriveException("Preview is limited to $limitMb MB")
    class Http(val code: Int, detail: String) : DriveException("Drive API error $code: $detail")
}
