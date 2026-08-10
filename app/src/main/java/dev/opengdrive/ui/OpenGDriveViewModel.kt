package dev.opengdrive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opengdrive.data.DriveApi
import dev.opengdrive.data.DriveException
import dev.opengdrive.data.DriveFile
import dev.opengdrive.data.OpenDriveFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class EditorState(
    val authorized: Boolean = false,
    val files: List<DriveFile> = emptyList(),
    val selected: OpenDriveFile? = null,
    val markdown: String = "",
    val loading: Boolean = false,
    val saveState: SaveState = SaveState.Saved,
    val message: String? = null,
)

enum class SaveState { Saved, Pending, Saving, Failed }

class OpenGDriveViewModel(private val driveApi: DriveApi = DriveApi()) : ViewModel() {
    var state = androidx.compose.runtime.mutableStateOf(EditorState())
        private set

    private var accessToken: String? = null
    private var saveJob: Job? = null

    fun onAuthorized(token: String) {
        accessToken = token
        state.value = state.value.copy(authorized = true, message = null)
        refresh()
    }

    fun onAuthorizationError(error: Throwable) {
        state.value = state.value.copy(message = error.message ?: "Google authorization failed")
    }

    fun refresh() {
        val token = accessToken ?: return
        viewModelScope.launch {
            state.value = state.value.copy(loading = true, message = null)
            runCatching { driveApi.listMarkdownFiles(token) }
                .onSuccess { files -> state.value = state.value.copy(files = files, loading = false) }
                .onFailure(::handleError)
        }
    }

    fun open(file: DriveFile) {
        val token = accessToken ?: return
        viewModelScope.launch {
            saveImmediately()
            state.value = state.value.copy(loading = true, message = null)
            runCatching { driveApi.download(file, token) }
                .onSuccess { opened ->
                    state.value = state.value.copy(
                        selected = opened,
                        markdown = opened.content,
                        loading = false,
                        saveState = SaveState.Saved,
                    )
                }
                .onFailure(::handleError)
        }
    }

    fun edit(markdown: String) {
        if (state.value.selected?.file?.capabilities?.canEdit == false) return
        state.value = state.value.copy(markdown = markdown, saveState = SaveState.Pending)
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            saveImmediately()
        }
    }

    fun save() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { saveImmediately() }
    }

    fun clearMessage() {
        state.value = state.value.copy(message = null)
    }

    private suspend fun saveImmediately() {
        val current = state.value
        val opened = current.selected ?: return
        val token = accessToken ?: return
        if (current.saveState != SaveState.Pending && current.saveState != SaveState.Failed) return
        state.value = current.copy(saveState = SaveState.Saving, message = null)
        runCatching {
            driveApi.update(opened.file.id, current.markdown, token, opened.etag)
        }.onSuccess { newEtag ->
            val latest = state.value
            if (latest.markdown == current.markdown) {
                state.value = latest.copy(
                    selected = opened.copy(content = current.markdown, etag = newEtag ?: opened.etag),
                    saveState = SaveState.Saved,
                )
            } else {
                state.value = latest.copy(saveState = SaveState.Pending)
                saveJob = viewModelScope.launch {
                    delay(AUTOSAVE_DELAY_MS)
                    saveImmediately()
                }
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
        const val AUTOSAVE_DELAY_MS = 1_000L
    }
}
