package dev.opengdrive.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opengdrive.data.DriveApi
import dev.opengdrive.data.DriveException
import dev.opengdrive.data.DriveFile
import dev.opengdrive.data.DriveFolder
import dev.opengdrive.data.OpenDriveFile
import dev.opengdrive.data.PreviewData
import dev.opengdrive.data.isFolder
import dev.opengdrive.data.isMarkdown
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class EditorState(
    val authorized: Boolean = false,
    val files: List<DriveFile> = emptyList(),
    val folderPath: List<DriveFolder> = listOf(DriveFolder("all", "All files")),
    val selected: OpenDriveFile? = null,
    val markdown: String = "",
    val loading: Boolean = false,
    val saveState: SaveState = SaveState.Saved,
    val editMode: Boolean = false,
    val filePaneVisible: Boolean = true,
    val message: String? = null,
) {
    val canEditMarkdown: Boolean
        get() = selected?.file?.isMarkdown() == true && selected.file.capabilities?.canEdit != false
}

enum class SaveState { Saved, Pending, Saving, Failed }

class OpenGDriveViewModel(private val driveApi: DriveApi = DriveApi()) : ViewModel() {
    var state = mutableStateOf(EditorState())
        private set

    private var accessToken: String? = null
    private var saveJob: Job? = null
    private val folderCache = FolderFileCache()

    fun onAuthorized(token: String) {
        accessToken = token
        state.value = state.value.copy(authorized = true, message = null)
        refresh()
    }

    fun onAuthorizationError(error: Throwable) {
        state.value = state.value.copy(message = error.message ?: "Google authorization failed")
    }

    fun refresh() {
        loadFolder(state.value.folderPath.last(), forceRefresh = true)
    }

    fun select(item: DriveFile) {
        if (item.isFolder()) navigateInto(item) else open(item)
    }

    fun navigateToPath(index: Int) {
        val current = state.value
        if (index !in current.folderPath.indices || index == current.folderPath.lastIndex) return
        viewModelScope.launch {
            flushPendingSave()
            val newPath = current.folderPath.take(index + 1)
            state.value = state.value.copy(
                folderPath = newPath,
                selected = null,
                markdown = "",
                editMode = false,
                saveState = SaveState.Saved,
            )
            loadFolder(newPath.last())
        }
    }

    fun open(file: DriveFile) {
        val token = accessToken ?: return
        viewModelScope.launch {
            flushPendingSave()
            state.value = state.value.copy(loading = true, editMode = false, message = null)
            runCatching { driveApi.open(file, token) }
                .onSuccess { opened ->
                    state.value = state.value.copy(
                        selected = opened,
                        markdown = (opened.preview as? PreviewData.Markdown)?.text.orEmpty(),
                        loading = false,
                        saveState = SaveState.Saved,
                    )
                }
                .onFailure(::handleError)
        }
    }

    fun toggleEditMode() {
        val current = state.value
        if (!current.editMode && !current.canEditMarkdown) return
        if (current.editMode) save()
        state.value = current.copy(editMode = !current.editMode)
    }

    fun toggleFilePane() {
        state.value = state.value.copy(filePaneVisible = !state.value.filePaneVisible)
    }

    fun edit(markdown: String) {
        if (!state.value.editMode || !state.value.canEditMarkdown || markdown == state.value.markdown) return
        state.value = state.value.copy(markdown = markdown, saveState = SaveState.Pending)
        scheduleAutosaveIfNeeded()
    }

    fun save() {
        saveJob?.cancel()
        saveJob = null
        viewModelScope.launch { saveImmediately() }
    }

    fun clearMessage() {
        state.value = state.value.copy(message = null)
    }

    private fun navigateInto(folder: DriveFile) {
        viewModelScope.launch {
            flushPendingSave()
            val destination = DriveFolder(folder.id, folder.name)
            state.value = state.value.copy(
                folderPath = state.value.folderPath + destination,
                selected = null,
                markdown = "",
                editMode = false,
                saveState = SaveState.Saved,
            )
            loadFolder(destination)
        }
    }

    private fun loadFolder(folder: DriveFolder, forceRefresh: Boolean = false) {
        val token = accessToken ?: return
        if (!forceRefresh) {
            folderCache.get(folder.id)?.let { cachedFiles ->
                state.value = state.value.copy(files = cachedFiles, loading = false, message = null)
                return
            }
        }
        viewModelScope.launch {
            state.value = state.value.copy(files = emptyList(), loading = true, message = null)
            runCatching { driveApi.listFiles(token, folder.id) }
                .onSuccess { files ->
                    folderCache.put(folder.id, files)
                    state.value = state.value.copy(files = files, loading = false)
                }
                .onFailure(::handleError)
        }
    }

    private fun scheduleAutosaveIfNeeded() {
        if (saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_INTERVAL_MS)
            saveJob = null
            saveImmediately()
        }
    }

    private suspend fun flushPendingSave() {
        saveJob?.cancel()
        saveJob = null
        saveImmediately()
    }

    private suspend fun saveImmediately() {
        val current = state.value
        val opened = current.selected ?: return
        val token = accessToken ?: return
        if (!current.canEditMarkdown) return
        if (current.saveState != SaveState.Pending && current.saveState != SaveState.Failed) return
        state.value = current.copy(saveState = SaveState.Saving, message = null)
        runCatching {
            driveApi.update(opened.file.id, current.markdown, token, opened.etag)
        }.onSuccess { newEtag ->
            val latest = state.value
            val savedFile = opened.copy(
                preview = PreviewData.Markdown(current.markdown),
                etag = newEtag ?: opened.etag,
            )
            if (latest.markdown == current.markdown) {
                state.value = latest.copy(selected = savedFile, saveState = SaveState.Saved)
            } else {
                state.value = latest.copy(selected = savedFile, saveState = SaveState.Pending)
                scheduleAutosaveIfNeeded()
            }
        }.onFailure(::handleSaveError)
    }

    private fun handleError(error: Throwable) {
        if (error is DriveException.Unauthorized) accessToken = null
        state.value = state.value.copy(
            authorized = accessToken != null,
            loading = false,
            message = error.message ?: "Google Drive request failed",
        )
    }

    private fun handleSaveError(error: Throwable) {
        if (error is DriveException.Unauthorized) accessToken = null
        state.value = state.value.copy(
            authorized = accessToken != null,
            saveState = SaveState.Failed,
            message = error.message ?: "Save failed",
        )
    }

    companion object {
        const val AUTOSAVE_INTERVAL_MS = 5_000L
    }
}
