package dev.opengdrive.data

import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

data class LocalMarkdownDocument(
    val localId: String,
    val driveFileId: String? = null,
    val parentId: String,
    val name: String,
    val content: String,
    val etag: String? = null,
    val revision: Long = 0,
    val dirty: Boolean = false,
    val syncError: String? = null,
)

class LocalMarkdownStore(private val directory: File) {
    private val lock = Any()

    init {
        directory.mkdirs()
    }

    fun create(name: String, parentId: String): LocalMarkdownDocument = synchronized(lock) {
        val document = LocalMarkdownDocument(
            localId = UUID.randomUUID().toString(),
            parentId = parentId,
            name = name,
            content = "",
            revision = 1,
            dirty = true,
        )
        write(document)
        document
    }

    fun cacheRemote(
        file: DriveFile,
        parentId: String,
        content: String,
        etag: String?,
    ): LocalMarkdownDocument = synchronized(lock) {
        findByDriveIdLocked(file.id)?.takeIf { it.dirty } ?: LocalMarkdownDocument(
            localId = "remote-${file.id}",
            driveFileId = file.id,
            parentId = parentId,
            name = file.name,
            content = content,
            etag = etag,
        ).also(::write)
    }

    fun save(document: LocalMarkdownDocument): LocalMarkdownDocument = synchronized(lock) {
        val currentRevision = readMetadataLocked(document.localId)?.getProperty("revision")?.toLongOrNull()
        if (currentRevision != null && currentRevision > document.revision) {
            return@synchronized loadLocked(document.localId) ?: document
        }
        write(document)
        document
    }

    fun find(localId: String): LocalMarkdownDocument? = synchronized(lock) { loadLocked(localId) }

    fun findByDriveId(driveFileId: String): LocalMarkdownDocument? =
        synchronized(lock) { findByDriveIdLocked(driveFileId) }

    fun list(): List<LocalMarkdownDocument> = synchronized(lock) {
        directory.listFiles { file -> file.extension == METADATA_EXTENSION }
            .orEmpty()
            .mapNotNull { loadLocked(it.nameWithoutExtension) }
    }

    fun recordRemoteProgress(localId: String, driveFileId: String, etag: String?): LocalMarkdownDocument? =
        synchronized(lock) {
            val current = loadLocked(localId) ?: return@synchronized null
            current.copy(driveFileId = driveFileId, etag = etag ?: current.etag).also(::writeMetadata)
        }

    fun markSynced(
        localId: String,
        syncedRevision: Long,
        driveFileId: String,
        etag: String?,
    ): LocalMarkdownDocument? = synchronized(lock) {
        val current = loadLocked(localId) ?: return@synchronized null
        current.copy(
            driveFileId = driveFileId,
            etag = etag ?: current.etag,
            dirty = current.revision != syncedRevision,
            syncError = null,
        ).also(::writeMetadata)
    }

    fun markFailed(localId: String, message: String): LocalMarkdownDocument? = synchronized(lock) {
        val current = loadLocked(localId) ?: return@synchronized null
        current.copy(dirty = true, syncError = message).also(::writeMetadata)
    }

    fun updateParent(localId: String, parentId: String): LocalMarkdownDocument? = synchronized(lock) {
        val current = loadLocked(localId) ?: return@synchronized null
        current.copy(parentId = parentId).also(::writeMetadata)
    }

    fun delete(localId: String): Boolean = synchronized(lock) {
        val metadata = metadataFile(localId)
        val content = contentFile(localId)
        val existed = metadata.exists() || content.exists()
        val metadataDeleted = !metadata.exists() || metadata.delete()
        val contentDeleted = !content.exists() || content.delete()
        File(directory, "$localId.$METADATA_EXTENSION.tmp").delete()
        File(directory, "$localId.md.tmp").delete()
        existed && metadataDeleted && contentDeleted
    }

    private fun findByDriveIdLocked(driveFileId: String): LocalMarkdownDocument? =
        directory.listFiles { file -> file.extension == METADATA_EXTENSION }
            .orEmpty()
            .asSequence()
            .mapNotNull { loadLocked(it.nameWithoutExtension) }
            .firstOrNull { it.driveFileId == driveFileId }

    private fun loadLocked(localId: String): LocalMarkdownDocument? {
        val properties = readMetadataLocked(localId) ?: return null
        return runCatching {
            LocalMarkdownDocument(
                localId = localId,
                driveFileId = properties.getProperty("driveFileId")?.takeIf(String::isNotBlank),
                parentId = properties.getProperty("parentId") ?: "all",
                name = properties.getProperty("name") ?: "untitled.md",
                content = contentFile(localId).takeIf(File::isFile)?.readText().orEmpty(),
                etag = properties.getProperty("etag")?.takeIf(String::isNotBlank),
                revision = properties.getProperty("revision")?.toLongOrNull() ?: 0,
                dirty = properties.getProperty("dirty").toBoolean(),
                syncError = properties.getProperty("syncError")?.takeIf(String::isNotBlank),
            )
        }.getOrNull()
    }

    private fun readMetadataLocked(localId: String): Properties? {
        val metadataFile = metadataFile(localId)
        if (!metadataFile.isFile) return null
        return runCatching { Properties().apply { metadataFile.inputStream().use(::load) } }.getOrNull()
    }

    private fun write(document: LocalMarkdownDocument) {
        val wasDirty = readMetadataLocked(document.localId)?.getProperty("dirty")?.toBoolean() == true
        if (!wasDirty) writeMetadata(document.copy(dirty = true))
        writeAtomic(contentFile(document.localId), document.content.toByteArray(Charsets.UTF_8))
        writeMetadata(document)
    }

    private fun writeMetadata(document: LocalMarkdownDocument) {
        val properties = Properties().apply {
            setProperty("driveFileId", document.driveFileId.orEmpty())
            setProperty("parentId", document.parentId)
            setProperty("name", document.name)
            setProperty("etag", document.etag.orEmpty())
            setProperty("revision", document.revision.toString())
            setProperty("dirty", document.dirty.toString())
            setProperty("syncError", document.syncError.orEmpty())
        }
        val temporary = File(directory, "${document.localId}.$METADATA_EXTENSION.tmp")
        FileOutputStream(temporary).use { output ->
            properties.store(output, null)
            output.fd.sync()
        }
        replace(temporary, metadataFile(document.localId))
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

    private fun metadataFile(localId: String) = File(directory, "$localId.$METADATA_EXTENSION")
    private fun contentFile(localId: String) = File(directory, "$localId.md")

    companion object {
        private const val METADATA_EXTENSION = "properties"
    }
}
