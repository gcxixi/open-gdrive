package dev.opengdrive.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.opengdrive.data.DriveApi
import dev.opengdrive.data.DriveCapabilities
import dev.opengdrive.data.DriveException
import dev.opengdrive.data.DriveFile
import dev.opengdrive.data.DriveFolder
import dev.opengdrive.data.LocalMarkdownDocument
import dev.opengdrive.data.LocalMarkdownStore
import dev.opengdrive.data.OpenDriveFile
import dev.opengdrive.data.PreviewCacheStore
import dev.opengdrive.data.PreviewData
import dev.opengdrive.data.isFolder
import dev.opengdrive.data.isMarkdown
import dev.opengdrive.data.matches
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorState(
    val authorized: Boolean = false,
    val files: List<DriveFile> = emptyList(),
    val folderPath: List<DriveFolder> = listOf(DriveFolder("all", "All files")),
    val selected: OpenDriveFile? = null,
    val markdown: String = "",
    val loading: Boolean = false,
    val saveState: SaveState = SaveState.Saved,
    val fileSyncStates: Map<String, SaveState> = emptyMap(),
    val editMode: Boolean = false,
    val filePaneVisible: Boolean = true,
    val previewPaneVisible: Boolean = true,
    val validationState: ValidationState = ValidationState.Ready,
    val message: String? = null,
) {
    val isEditableMarkdown: Boolean
        get() = selected?.file?.isMarkdown() == true && selected.file.capabilities?.canEdit != false

    val canEditMarkdown: Boolean
        get() = isEditableMarkdown && validationState == ValidationState.Ready
}

enum class SaveState { Saved, Pending, Saving, Failed }
enum class ValidationState { Ready, Checking, Failed }

class OpenGDriveViewModel(application: Application) : AndroidViewModel(application) {
    var state = mutableStateOf(EditorState())
        private set

    private val driveApi = DriveApi()
    private val localStore = LocalMarkdownStore(File(application.filesDir, "markdown-documents"))
    private val previewCache = PreviewCacheStore(File(application.cacheDir, "file-previews"))
    private val folderCache = FolderFileCache()
    private val syncJobs = mutableMapOf<String, Job>()
    private val syncingDrafts = mutableSetOf<String>()
    private var localSaveJob: Job? = null
    private var openJob: Job? = null
    private var activeDraft: LocalMarkdownDocument? = null
    private var accessToken: String? = null

    fun onAuthorized(token: String) {
        accessToken = token
        state.value = state.value.copy(authorized = true, message = null)
        refresh()
        localStore.list().filter(LocalMarkdownDocument::dirty).forEach { scheduleSync(it.localId, 0) }
    }

    fun onAuthorizationError(error: Throwable) {
        state.value = state.value.copy(message = error.message ?: "Google authorization failed")
    }

    fun refresh() {
        loadFolder(state.value.folderPath.last(), forceRefresh = true)
    }

    fun createMarkdown() {
        openJob?.cancel()
        viewModelScope.launch {
            flushLocalSave()
            val current = state.value
            val name = nextUntitledName(current.files.map(DriveFile::name))
            val parentId = current.folderPath.last().id
            val document = withContext(Dispatchers.IO) { localStore.create(name, parentId) }
            activeDraft = document
            val file = document.asDriveFile()
            state.value = current.copy(
                files = sortFiles(current.files + file),
                selected = OpenDriveFile(file, PreviewData.Markdown(""), null),
                markdown = "",
                editMode = true,
                previewPaneVisible = true,
                validationState = ValidationState.Ready,
                saveState = SaveState.Pending,
                fileSyncStates = current.fileSyncStates + (file.id to SaveState.Pending),
                message = null,
            )
            scheduleSync(document.localId, 0)
        }
    }

    fun renameMarkdown(requestedName: String) {
        val current = state.value
        val document = activeDraft ?: return
        if (!current.canEditMarkdown) return
        val normalized = normalizeMarkdownName(requestedName) ?: run {
            state.value = current.copy(message = "File name cannot be empty or contain / or \\")
            return
        }
        if (normalized == document.name) return
        val updated = document.copy(
            name = normalized,
            revision = document.revision + 1,
            dirty = true,
            syncError = null,
        )
        activeDraft = updated
        val displayId = current.selected?.file?.id ?: updated.displayId()
        state.value = current.copy(
            files = current.files.map { if (it.id == displayId) it.copy(name = normalized) else it },
            selected = current.selected?.let { it.copy(file = it.file.copy(name = normalized)) },
            saveState = SaveState.Pending,
            fileSyncStates = current.fileSyncStates + (displayId to SaveState.Pending),
            message = null,
        )
        persistAndSchedule(updated, 0)
    }

    fun select(item: DriveFile) {
        if (item.isFolder()) navigateInto(item) else open(item)
    }

    fun navigateToPath(index: Int) {
        val current = state.value
        if (index !in current.folderPath.indices || index == current.folderPath.lastIndex) return
        openJob?.cancel()
        viewModelScope.launch {
            flushLocalSave()
            val newPath = current.folderPath.take(index + 1)
            activeDraft = null
            state.value = state.value.copy(
                folderPath = newPath,
                selected = null,
                markdown = "",
                editMode = false,
                saveState = SaveState.Saved,
                validationState = ValidationState.Ready,
            )
            loadFolder(newPath.last())
        }
    }

    fun open(file: DriveFile) {
        val token = accessToken ?: return
        openJob?.cancel()
        state.value = state.value.copy(
            editMode = false,
            validationState = ValidationState.Checking,
            message = null,
        )
        openJob = viewModelScope.launch {
            flushLocalSave()
            val local = withContext(Dispatchers.IO) {
                if (file.id.startsWith(LOCAL_FILE_PREFIX)) {
                    localStore.find(file.id.removePrefix(LOCAL_FILE_PREFIX))
                } else {
                    localStore.findByDriveId(file.id)
                }
            }
            if (file.id.startsWith(LOCAL_FILE_PREFIX)) {
                activeDraft = local
                val localFile = local!!.asDriveFile()
                state.value = state.value.copy(
                    selected = OpenDriveFile(localFile, PreviewData.Markdown(local.content), local.etag),
                    markdown = local.content,
                    loading = false,
                    editMode = false,
                    saveState = local.saveState(),
                    validationState = ValidationState.Ready,
                    message = null,
                )
                return@launch
            }

            if (local?.dirty == true) {
                activeDraft = local
                val localOpened = OpenDriveFile(
                    local.asDriveFile().copy(
                        modifiedTime = file.modifiedTime,
                        webViewLink = file.webViewLink,
                    ),
                    PreviewData.Markdown(local.content),
                    local.etag,
                )
                state.value = state.value.copy(
                    selected = localOpened,
                    markdown = local.content,
                    loading = false,
                    editMode = false,
                    saveState = local.saveState(),
                    validationState = ValidationState.Checking,
                    message = null,
                )
                try {
                    val metadata = driveApi.getMetadata(file.id, token)
                    if (metadata.matches(localOpened)) {
                        state.value = state.value.copy(
                            selected = localOpened.copy(
                                file = metadata.file,
                                etag = metadata.etag ?: localOpened.etag,
                            ),
                            validationState = ValidationState.Ready,
                        )
                    } else {
                        state.value = state.value.copy(
                            validationState = ValidationState.Failed,
                            message = "Drive has a newer revision; the local draft was preserved and editing is locked",
                        )
                    }
                } catch (error: Throwable) {
                    handleCachedValidationError(error)
                }
                return@launch
            }

            activeDraft = null
            val cached = withContext(Dispatchers.IO) { previewCache.find(file.id)?.opened }
                ?: local?.let {
                    OpenDriveFile(it.asDriveFile(), PreviewData.Markdown(it.content), it.etag)
                }
            if (cached != null) {
                activeDraft = local
                state.value = state.value.copy(
                    selected = cached,
                    markdown = (cached.preview as? PreviewData.Markdown)?.text.orEmpty(),
                    loading = false,
                    editMode = false,
                    saveState = local?.saveState() ?: SaveState.Saved,
                    validationState = ValidationState.Checking,
                    message = null,
                )
                try {
                    val metadata = driveApi.getMetadata(file.id, token)
                    val opened = if (metadata.matches(cached)) {
                        cached.copy(file = metadata.file, etag = metadata.etag ?: cached.etag)
                    } else {
                        driveApi.open(metadata.file, token)
                    }
                    applyOpenedFile(opened)
                } catch (error: Throwable) {
                    handleCachedValidationError(error)
                }
                return@launch
            }

            state.value = state.value.copy(
                selected = null,
                markdown = "",
                loading = true,
                editMode = false,
                validationState = ValidationState.Checking,
                message = null,
            )
            try {
                val opened = driveApi.open(file, token)
                applyOpenedFile(opened)
            } catch (error: Throwable) {
                if (local != null) {
                    activeDraft = local
                    val localFile = local.asDriveFile()
                    state.value = state.value.copy(
                        selected = OpenDriveFile(localFile, PreviewData.Markdown(local.content), local.etag),
                        markdown = local.content,
                        loading = false,
                        saveState = local.saveState(),
                        validationState = ValidationState.Failed,
                        message = "Showing the local copy; Drive is unavailable",
                    )
                } else {
                    handleError(error)
                }
            }
        }
    }

    fun toggleEditMode() {
        val current = state.value
        if (!current.editMode && !current.canEditMarkdown) return
        if (current.editMode) save()
        state.value = current.copy(
            editMode = !current.editMode,
            previewPaneVisible = if (current.editMode) current.previewPaneVisible else true,
        )
    }

    fun toggleFilePane() {
        state.value = state.value.copy(filePaneVisible = !state.value.filePaneVisible)
    }

    fun togglePreviewPane() {
        val current = state.value
        if (!current.editMode) return
        state.value = current.copy(previewPaneVisible = !current.previewPaneVisible)
    }

    fun edit(markdown: String) {
        val current = state.value
        val document = activeDraft ?: return
        if (!current.editMode || !current.canEditMarkdown || markdown == current.markdown) return
        val updated = document.copy(
            content = markdown,
            revision = document.revision + 1,
            dirty = true,
            syncError = null,
        )
        activeDraft = updated
        val displayId = current.selected?.file?.id ?: updated.displayId()
        state.value = current.copy(
            markdown = markdown,
            saveState = SaveState.Pending,
            fileSyncStates = current.fileSyncStates + (displayId to SaveState.Pending),
        )
        persistAndSchedule(updated, AUTOSAVE_INTERVAL_MS)
    }

    fun save() {
        viewModelScope.launch {
            flushLocalSave()
            activeDraft?.let { scheduleSync(it.localId, 0) }
        }
    }

    fun clearMessage() {
        state.value = state.value.copy(message = null)
    }

    private fun navigateInto(folder: DriveFile) {
        openJob?.cancel()
        viewModelScope.launch {
            flushLocalSave()
            val destination = DriveFolder(folder.id, folder.name)
            activeDraft = null
            state.value = state.value.copy(
                folderPath = state.value.folderPath + destination,
                selected = null,
                markdown = "",
                editMode = false,
                saveState = SaveState.Saved,
                validationState = ValidationState.Ready,
            )
            loadFolder(destination)
        }
    }

    private fun loadFolder(folder: DriveFolder, forceRefresh: Boolean = false) {
        val token = accessToken ?: return
        if (!forceRefresh) {
            folderCache.get(folder.id)?.let { cachedFiles ->
                applyMergedFiles(folder, cachedFiles)
                return
            }
        }
        viewModelScope.launch {
            state.value = state.value.copy(files = emptyList(), loading = true, message = null)
            runCatching { driveApi.listFiles(token, folder.id) }
                .onSuccess { files ->
                    folderCache.put(folder.id, files)
                    applyMergedFiles(folder, files)
                }
                .onFailure(::handleError)
        }
    }

    private fun applyMergedFiles(folder: DriveFolder, remoteFiles: List<DriveFile>) {
        val localDocuments = localStore.list().filter { folder.id == "all" || it.parentId == folder.id }
        val localByDriveId = localDocuments.mapNotNull { document ->
            document.driveFileId?.let { it to document }
        }.toMap()
        val merged = remoteFiles.map { remote ->
            localByDriveId[remote.id]?.asDriveFile() ?: remote
        }.toMutableList()
        val represented = merged.map(DriveFile::id).toSet()
        localDocuments
            .filter { it.displayId() !in represented && (it.dirty || it.driveFileId == null) }
            .forEach { merged += it.asDriveFile() }
        val syncStates = localDocuments.associate { it.displayId() to it.saveState() }
        state.value = state.value.copy(
            files = sortFiles(merged.distinctBy(DriveFile::id)),
            fileSyncStates = syncStates,
            loading = false,
            message = null,
        )
    }

    private suspend fun applyOpenedFile(opened: OpenDriveFile) {
        withContext(Dispatchers.IO) { previewCache.put(opened) }
        val markdown = opened.preview as? PreviewData.Markdown
        if (markdown != null) {
            activeDraft = withContext(Dispatchers.IO) {
                localStore.cacheRemote(
                    opened.file,
                    state.value.folderPath.last().id,
                    markdown.text,
                    opened.etag,
                )
            }
        } else {
            activeDraft = null
        }
        state.value = state.value.copy(
            selected = opened,
            markdown = markdown?.text.orEmpty(),
            loading = false,
            saveState = SaveState.Saved,
            validationState = ValidationState.Ready,
            message = null,
        )
    }

    private fun handleCachedValidationError(error: Throwable) {
        if (error is DriveException.Unauthorized) accessToken = null
        state.value = state.value.copy(
            authorized = accessToken != null,
            loading = false,
            validationState = ValidationState.Failed,
            message = "Showing cached content; Drive verification failed, so editing is disabled",
        )
    }

    private fun persistAndSchedule(document: LocalMarkdownDocument, syncDelay: Long) {
        localSaveJob?.cancel()
        localSaveJob = viewModelScope.launch {
            delay(LOCAL_SAVE_DEBOUNCE_MS)
            withContext(Dispatchers.IO) { localStore.save(document) }
            localSaveJob = null
            scheduleSync(document.localId, syncDelay)
        }
    }

    private suspend fun flushLocalSave() {
        localSaveJob?.cancel()
        localSaveJob = null
        val document = activeDraft ?: return
        withContext(Dispatchers.IO) { localStore.save(document) }
        if (document.dirty) scheduleSync(document.localId, 0)
    }

    private fun scheduleSync(localId: String, delayMillis: Long) {
        if (localId in syncingDrafts) return
        syncJobs.remove(localId)?.cancel()
        syncJobs[localId] = viewModelScope.launch {
            delay(delayMillis)
            syncJobs.remove(localId)
            syncDocument(localId)
        }
    }

    private suspend fun syncDocument(localId: String) {
        val token = accessToken ?: return
        val snapshot = withContext(Dispatchers.IO) { localStore.find(localId) } ?: return
        if (!snapshot.dirty) return
        syncingDrafts += localId
        updateDocumentStatus(snapshot, SaveState.Saving)
        try {
            val opened = if (snapshot.driveFileId == null) {
                driveApi.createMarkdown(
                    snapshot.name,
                    snapshot.parentId,
                    snapshot.content,
                    snapshot.localId,
                    token,
                )
            } else {
                val contentEtag = driveApi.update(
                    snapshot.driveFileId,
                    snapshot.content,
                    token,
                    snapshot.etag,
                )
                withContext(Dispatchers.IO) {
                    localStore.recordRemoteProgress(snapshot.localId, snapshot.driveFileId, contentEtag)
                }
                val renameEtag = driveApi.rename(snapshot.driveFileId, snapshot.name, token)
                OpenDriveFile(snapshot.asDriveFile(), PreviewData.Markdown(snapshot.content), renameEtag ?: contentEtag)
            }
            val saved = withContext(Dispatchers.IO) {
                localStore.markSynced(
                    snapshot.localId,
                    snapshot.revision,
                    opened.file.id,
                    opened.etag,
                )
            } ?: return
            syncingDrafts -= localId
            folderCache.clear()
            onDocumentSynced(snapshot, saved, opened.file)
            if (saved.dirty) scheduleSync(saved.localId, AUTOSAVE_INTERVAL_MS)
        } catch (error: Throwable) {
            syncingDrafts -= localId
            val message = error.message ?: "Google Drive sync failed"
            val failed = withContext(Dispatchers.IO) { localStore.markFailed(localId, message) } ?: snapshot
            if (error is DriveException.Unauthorized) accessToken = null
            updateDocumentStatus(failed, SaveState.Failed, "Sync issue: $message")
        }
    }

    private fun onDocumentSynced(
        before: LocalMarkdownDocument,
        saved: LocalMarkdownDocument,
        remoteFile: DriveFile,
    ) {
        val oldId = before.displayId()
        val newFile = saved.asDriveFile().copy(
            modifiedTime = remoteFile.modifiedTime ?: Instant.now().toString(),
            webViewLink = remoteFile.webViewLink,
        )
        val status = saved.saveState()
        val current = state.value
        val selected = current.selected?.let { opened ->
            if (opened.file.id == oldId || activeDraft?.localId == saved.localId) {
                opened.copy(file = newFile, preview = PreviewData.Markdown(saved.content), etag = saved.etag)
            } else {
                opened
            }
        }
        if (activeDraft?.localId == saved.localId) activeDraft = saved
        val cachedOpened = OpenDriveFile(newFile, PreviewData.Markdown(saved.content), saved.etag)
        viewModelScope.launch(Dispatchers.IO) { previewCache.put(cachedOpened) }
        state.value = current.copy(
            files = sortFiles(current.files.map { if (it.id == oldId) newFile else it }),
            selected = selected,
            saveState = if (activeDraft?.localId == saved.localId) status else current.saveState,
            fileSyncStates = current.fileSyncStates.toMutableMap().apply {
                remove(oldId)
                put(newFile.id, status)
            },
        )
    }

    private fun updateDocumentStatus(
        document: LocalMarkdownDocument,
        status: SaveState,
        message: String? = null,
    ) {
        val displayId = document.displayId()
        val current = state.value
        state.value = current.copy(
            saveState = if (activeDraft?.localId == document.localId) status else current.saveState,
            fileSyncStates = current.fileSyncStates + (displayId to status),
            message = message ?: current.message,
        )
    }

    private fun handleError(error: Throwable) {
        if (error is DriveException.Unauthorized) accessToken = null
        state.value = state.value.copy(
            authorized = accessToken != null,
            loading = false,
            validationState = if (state.value.validationState == ValidationState.Checking) {
                ValidationState.Failed
            } else {
                state.value.validationState
            },
            message = error.message ?: "Google Drive request failed",
        )
    }

    private fun LocalMarkdownDocument.saveState(): SaveState = when {
        localId in syncingDrafts -> SaveState.Saving
        syncError != null -> SaveState.Failed
        dirty -> SaveState.Pending
        else -> SaveState.Saved
    }

    private fun LocalMarkdownDocument.displayId(): String = driveFileId ?: "$LOCAL_FILE_PREFIX$localId"

    private fun LocalMarkdownDocument.asDriveFile() = DriveFile(
        id = displayId(),
        name = name,
        mimeType = "text/markdown",
        size = content.toByteArray().size.toString(),
        capabilities = DriveCapabilities(canEdit = true, canDownload = true),
    )

    private fun sortFiles(files: List<DriveFile>) = files.sortedWith(
        compareByDescending<DriveFile> { it.isFolder() }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
    )

    companion object {
        const val AUTOSAVE_INTERVAL_MS = 5_000L
        private const val LOCAL_SAVE_DEBOUNCE_MS = 150L
        private const val LOCAL_FILE_PREFIX = "local:"

        internal fun nextUntitledName(existingNames: List<String>): String {
            val used = existingNames.map { it.lowercase() }.toSet()
            var index = 1
            while ("untitled-$index.md" in used) index++
            return "untitled-$index.md"
        }

        internal fun normalizeMarkdownName(requestedName: String): String? {
            val trimmed = requestedName.trim()
            if (trimmed.isEmpty() || '/' in trimmed || '\\' in trimmed) return null
            return if (trimmed.endsWith(".md", ignoreCase = true)) trimmed else "$trimmed.md"
        }
    }

}
