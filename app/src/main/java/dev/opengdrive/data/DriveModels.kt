package dev.opengdrive.data

data class DriveFile(
    val id: String,
    val name: String,
    val modifiedTime: String? = null,
    val size: String? = null,
    val capabilities: DriveCapabilities? = null,
)

internal fun DriveFile.isMarkdown() = name.endsWith(".md", ignoreCase = true)

data class DriveCapabilities(
    val canEdit: Boolean = false,
    val canDownload: Boolean = true,
)

data class DriveFilePage(
    val files: List<DriveFile> = emptyList(),
    val nextPageToken: String? = null,
)

data class OpenDriveFile(
    val file: DriveFile,
    val content: String,
    val etag: String?,
)

sealed class DriveException(message: String) : Exception(message) {
    class Unauthorized : DriveException("Google Drive authorization expired")
    class Conflict : DriveException("The file changed in Google Drive; reload before saving")
    class Http(val code: Int, detail: String) : DriveException("Drive API error $code: $detail")
}
