package dev.opengdrive.data

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

data class CachedPreview(
    val opened: OpenDriveFile,
)

class PreviewCacheStore(private val directory: File) {
    private val lock = Any()

    init {
        directory.mkdirs()
    }

    fun find(fileId: String): CachedPreview? = synchronized(lock) {
        val key = cacheKey(fileId)
        val metadata = File(directory, "$key.properties")
        val content = File(directory, "$key.preview")
        if (!metadata.isFile || !content.isFile) return@synchronized null
        runCatching {
            val properties = Properties().apply { metadata.inputStream().use(::load) }
            if (properties.getProperty("fileId") != fileId) return@runCatching null
            val bytes = content.readBytes()
            val preview = when (properties.getProperty("kind")) {
                "markdown" -> PreviewData.Markdown(bytes.toString(Charsets.UTF_8))
                "text" -> PreviewData.Text(
                    bytes.toString(Charsets.UTF_8),
                    properties.getProperty("formatLabel").orEmpty(),
                )
                "image" -> PreviewData.Image(bytes, properties.getProperty("previewMimeType").orEmpty())
                "pdf" -> PreviewData.Pdf(bytes)
                else -> return@runCatching null
            }
            val file = DriveFile(
                id = fileId,
                name = properties.getProperty("name").orEmpty(),
                mimeType = properties.getProperty("mimeType") ?: "application/octet-stream",
                modifiedTime = properties.getProperty("modifiedTime")?.takeIf(String::isNotBlank),
                size = properties.getProperty("size")?.takeIf(String::isNotBlank),
                webViewLink = properties.getProperty("webViewLink")?.takeIf(String::isNotBlank),
                capabilities = DriveCapabilities(
                    canEdit = properties.getProperty("canEdit").toBoolean(),
                    canDownload = properties.getProperty("canDownload", "true").toBoolean(),
                ),
            )
            metadata.setLastModified(System.currentTimeMillis())
            CachedPreview(
                OpenDriveFile(
                    file = file,
                    preview = preview,
                    etag = properties.getProperty("etag")?.takeIf(String::isNotBlank),
                ),
            )
        }.getOrNull()
    }

    fun put(opened: OpenDriveFile) = synchronized(lock) {
        val preview = opened.preview
        if (preview is PreviewData.Unsupported) return@synchronized
        val (kind, bytes, formatLabel, previewMimeType) = when (preview) {
            is PreviewData.Markdown -> CachePayload("markdown", preview.text.toByteArray(), null, null)
            is PreviewData.Text -> CachePayload("text", preview.text.toByteArray(), preview.formatLabel, null)
            is PreviewData.Image -> CachePayload("image", preview.bytes, null, preview.mimeType)
            is PreviewData.Pdf -> CachePayload("pdf", preview.bytes, null, null)
            is PreviewData.Unsupported -> error("Unsupported previews are not cached")
        }
        val key = cacheKey(opened.file.id)
        writeAtomic(File(directory, "$key.preview"), bytes)
        val properties = Properties().apply {
            setProperty("fileId", opened.file.id)
            setProperty("name", opened.file.name)
            setProperty("mimeType", opened.file.mimeType)
            setProperty("modifiedTime", opened.file.modifiedTime.orEmpty())
            setProperty("size", opened.file.size.orEmpty())
            setProperty("webViewLink", opened.file.webViewLink.orEmpty())
            setProperty("canEdit", (opened.file.capabilities?.canEdit == true).toString())
            setProperty("canDownload", (opened.file.capabilities?.canDownload != false).toString())
            setProperty("etag", opened.etag.orEmpty())
            setProperty("kind", kind)
            setProperty("formatLabel", formatLabel.orEmpty())
            setProperty("previewMimeType", previewMimeType.orEmpty())
        }
        val temporary = File(directory, "$key.properties.tmp")
        FileOutputStream(temporary).use { output ->
            properties.store(output, null)
            output.fd.sync()
        }
        replace(temporary, File(directory, "$key.properties"))
        prune()
    }

    fun remove(fileId: String) = synchronized(lock) {
        val key = cacheKey(fileId)
        File(directory, "$key.properties").delete()
        File(directory, "$key.preview").delete()
        File(directory, "$key.properties.tmp").delete()
        File(directory, "$key.preview.tmp").delete()
    }

    private fun prune() {
        directory.listFiles { file -> file.extension == "properties" }
            .orEmpty()
            .sortedByDescending(File::lastModified)
            .drop(MAX_ENTRIES)
            .forEach { metadata ->
                File(directory, "${metadata.nameWithoutExtension}.preview").delete()
                metadata.delete()
            }
    }

    private fun writeAtomic(target: File, bytes: ByteArray) {
        val temporary = File(directory, "${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        replace(temporary, target)
    }

    private fun replace(temporary: File, target: File) {
        if (target.exists() && !target.delete()) error("Cannot replace ${target.name}")
        if (!temporary.renameTo(target)) error("Cannot save ${target.name}")
    }

    private fun cacheKey(fileId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(fileId.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private data class CachePayload(
        val kind: String,
        val bytes: ByteArray,
        val formatLabel: String?,
        val previewMimeType: String?,
    )

    companion object {
        private const val MAX_ENTRIES = 128
    }
}
