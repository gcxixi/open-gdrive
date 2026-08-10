package dev.opengdrive.ui

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.opengdrive.data.DriveFile
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

@Composable
fun OpenGDriveApp(viewModel: OpenGDriveViewModel, onAuthorize: () -> Unit) {
    val state by viewModel.state
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.selected?.file?.name ?: "Open GDrive") },
                actions = {
                    if (state.authorized) {
                        SaveIndicator(state.saveState)
                        IconButton(onClick = viewModel::save) {
                            Icon(Icons.Default.Save, contentDescription = "Save now")
                        }
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh files")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.authorized -> AuthorizationWelcome(onAuthorize)
                else -> Workspace(
                    state = state,
                    onOpen = viewModel::open,
                    onEdit = viewModel::edit,
                )
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun AuthorizationWelcome(onAuthorize: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Your Markdown, backed by Google Drive", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.size(16.dp))
        Text(
            "Open, edit, preview, and save .md files without moving them out of Drive.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.size(28.dp))
        Button(onClick = onAuthorize) { Text("Connect Google Drive") }
    }
}

@Composable
private fun Workspace(
    state: EditorState,
    onOpen: (DriveFile) -> Unit,
    onEdit: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                FileList(state.files, state.selected?.file?.id, onOpen, Modifier.width(280.dp))
                Divider(Modifier.fillMaxHeight().width(1.dp))
                Editor(state, onEdit, Modifier.weight(1f))
                Divider(Modifier.fillMaxHeight().width(1.dp))
                MarkdownPreview(state.markdown, Modifier.weight(1f))
            }
        } else {
            CompactWorkspace(state, onOpen, onEdit)
        }
    }
}

@Composable
private fun FileList(
    files: List<DriveFile>,
    selectedId: String?,
    onOpen: (DriveFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLow)) {
        Text(
            "MARKDOWN FILES",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(20.dp, 16.dp, 16.dp, 8.dp),
        )
        if (files.isEmpty()) {
            Text("No .md files found", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.outline)
        }
        LazyColumn {
            items(files, key = { it.id }) { file ->
                ListItem(
                    headlineContent = { Text(file.name, maxLines = 2) },
                    supportingContent = {
                        Text(if (file.capabilities?.canEdit == false) "Read only" else "Drive")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (file.id == selectedId) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        )
                        .clickable { onOpen(file) },
                )
            }
        }
    }
}

@Composable
private fun Editor(state: EditorState, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    val editable = state.selected?.file?.capabilities?.canEdit != false
    Column(modifier.fillMaxHeight().padding(16.dp)) {
        Text("EDITOR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = state.markdown,
            onValueChange = onEdit,
            enabled = state.selected != null && editable,
            placeholder = { Text("Choose a Markdown file from Drive") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .build()
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val background = MaterialTheme.colorScheme.surface.toArgb()
    AndroidView(
        factory = {
            TextView(it).apply {
                setPadding(28, 24, 28, 80)
                textSize = 17f
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setBackgroundColor(background)
            markwon.setMarkdown(view, markdown.ifBlank { "*Preview appears here*" })
        },
        modifier = modifier.fillMaxHeight().verticalScroll(rememberScrollState()),
    )
}

@Composable
private fun CompactWorkspace(state: EditorState, onOpen: (DriveFile) -> Unit, onEdit: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Files") })
            Tab(tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.Edit, null) })
            Tab(tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Preview, null) })
        }
        when (tab) {
            0 -> FileList(state.files, state.selected?.file?.id, { onOpen(it); tab = 1 })
            1 -> Editor(state, onEdit)
            else -> MarkdownPreview(state.markdown)
        }
    }
}

@Composable
private fun SaveIndicator(saveState: SaveState) {
    val (icon, text) = when (saveState) {
        SaveState.Saved -> Icons.Default.CloudDone to "Saved"
        SaveState.Pending -> Icons.Default.Edit to "Waiting to save"
        SaveState.Saving -> Icons.Default.CloudDone to "Saving"
        SaveState.Failed -> Icons.Default.CloudOff to "Save failed"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
