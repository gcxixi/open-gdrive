package dev.opengdrive.ui

import android.graphics.Typeface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class MarkdownEditorController {
    private var editor: CodeEditor? = null
    private var snapshotJob: Job? = null

    internal fun attach(editor: CodeEditor) {
        this.editor = editor
    }

    internal fun scheduleSnapshot(scope: CoroutineScope, onSnapshot: (String) -> Unit) {
        snapshotJob?.cancel()
        val activeEditor = editor ?: return
        snapshotJob = scope.launch {
            delay(snapshotDelayMillis(activeEditor.text.length))
            onSnapshot(activeEditor.text.toString())
        }
    }

    internal fun flush(onSnapshot: (String) -> Unit) {
        snapshotJob?.cancel()
        snapshotJob = null
        editor?.text?.toString()?.let(onSnapshot)
    }

    internal fun release(editor: CodeEditor) {
        snapshotJob?.cancel()
        snapshotJob = null
        if (this.editor === editor) this.editor = null
        editor.release()
    }

    private fun snapshotDelayMillis(length: Int): Long = when {
        length >= LARGE_DOCUMENT_CHARS -> 1_200L
        length >= MEDIUM_DOCUMENT_CHARS -> 700L
        else -> 350L
    }

    private companion object {
        const val MEDIUM_DOCUMENT_CHARS = 250_000
        const val LARGE_DOCUMENT_CHARS = 1_000_000
    }
}

@Composable
internal fun MarkdownCodeEditor(
    documentId: String,
    initialText: String,
    controller: MarkdownEditorController,
    onSnapshot: (String) -> Unit,
    backgroundColor: Color,
    textColor: Color,
    secondaryTextColor: Color,
    cursorColor: Color,
    selectionColor: Color,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentOnSnapshot by rememberUpdatedState(onSnapshot)
    val horizontalPadding = with(LocalDensity.current) { 18.dp.roundToPx() }
    val verticalPadding = with(LocalDensity.current) { 14.dp.roundToPx() }

    key(documentId) {
        AndroidView(
            factory = { context ->
                CodeEditor(context).apply {
                    setText(initialText)
                    setTextSize(16f)
                    setTypefaceText(Typeface.create("sans", Typeface.NORMAL))
                    setLineNumberEnabled(false)
                    setWordwrap(true, true)
                    setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
                    colorScheme = EditorColorScheme(this).apply {
                        setColor(EditorColorScheme.WHOLE_BACKGROUND, backgroundColor.toArgb())
                        setColor(EditorColorScheme.TEXT_NORMAL, textColor.toArgb())
                        setColor(EditorColorScheme.LINE_NUMBER, secondaryTextColor.toArgb())
                        setColor(EditorColorScheme.LINE_NUMBER_CURRENT, textColor.toArgb())
                        setColor(EditorColorScheme.SELECTION_INSERT, cursorColor.toArgb())
                        setColor(EditorColorScheme.SELECTION_HANDLE, cursorColor.toArgb())
                        setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selectionColor.toArgb())
                        setColor(EditorColorScheme.CURRENT_LINE, Color.Transparent.toArgb())
                    }
                    controller.attach(this)
                    subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                        if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                            controller.scheduleSnapshot(scope, currentOnSnapshot)
                        }
                    }
                }
            },
            modifier = modifier.fillMaxSize(),
            onRelease = {
                controller.flush(currentOnSnapshot)
                controller.release(it)
            },
        )
        DisposableEffect(Unit) {
            onDispose { controller.flush(currentOnSnapshot) }
        }
    }
}
