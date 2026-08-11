package dev.opengdrive.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import compose.icons.SimpleIcons
import compose.icons.simpleicons.Googledocs
import compose.icons.simpleicons.Googlesheets
import compose.icons.simpleicons.Googleslides
import compose.icons.simpleicons.Markdown
import dev.opengdrive.data.DriveFile
import dev.opengdrive.data.DriveFolder
import dev.opengdrive.data.GOOGLE_DOCUMENT
import dev.opengdrive.data.GOOGLE_PRESENTATION
import dev.opengdrive.data.GOOGLE_SPREADSHEET
import dev.opengdrive.data.PreviewData
import dev.opengdrive.data.isFolder
import dev.opengdrive.data.isMarkdown
import io.noties.markwon.Markwon
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.syntax.Prism4jThemeDarkula
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!state.authorized) {
                AuthorizationWelcome(onAuthorize)
            } else {
                Workspace(
                    state = state,
                    onSelect = viewModel::select,
                    onPathClick = viewModel::navigateToPath,
                    onEdit = viewModel::edit,
                    onToggleEdit = viewModel::toggleEditMode,
                    onSave = viewModel::save,
                    onCreate = viewModel::createMarkdown,
                    onRename = viewModel::renameMarkdown,
                    onDelete = viewModel::deleteFiles,
                    onToggleFilePane = viewModel::toggleFilePane,
                    onTogglePreviewPane = viewModel::togglePreviewPane,
                    onRefresh = viewModel::refresh,
                )
            }
            if (state.loading) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 6.dp,
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    CircularProgressIndicator(Modifier.padding(20.dp).size(30.dp), strokeWidth = 3.dp)
                }
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
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(18.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text("Markdown for Google Drive", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "Browse every file. Preview Markdown properly. Edit only when you need to.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onAuthorize) { Text("Connect Google Drive") }
    }
}

@Composable
private fun Workspace(
    state: EditorState,
    onSelect: (DriveFile) -> Unit,
    onPathClick: (Int) -> Unit,
    onEdit: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onSave: () -> Unit,
    onCreate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (List<DriveFile>) -> Unit,
    onToggleFilePane: () -> Unit,
    onTogglePreviewPane: () -> Unit,
    onRefresh: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val toolbarReservedWidth = 0.dp
        if (maxWidth >= 840.dp) {
            Row(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.filePaneVisible || state.selected == null) {
                    FilePane(
                        files = state.files,
                        path = state.folderPath,
                        selectedId = state.selected?.file?.id,
                        syncStates = state.fileSyncStates,
                        onSelect = onSelect,
                        onPathClick = onPathClick,
                        onCreate = onCreate,
                        onDelete = onDelete,
                        toolbarReservedWidth = 0.dp,
                        modifier = Modifier.width(if (state.editMode) 300.dp else 340.dp),
                    )
                }
                if (state.editMode) {
                    EditorPane(
                        state,
                        onEdit,
                        onToggleEdit,
                        onSave,
                        onRename,
                        toolbarReservedWidth = if (state.previewPaneVisible) 0.dp else toolbarReservedWidth,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (!state.editMode || state.previewPaneVisible) {
                    PreviewPane(
                        state,
                        onToggleEdit,
                        toolbarReservedWidth = toolbarReservedWidth,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            CompactWorkspace(
                state,
                onSelect,
                onPathClick,
                onEdit,
                onToggleEdit,
                onSave,
                onCreate,
                onRename,
                onDelete,
                toolbarReservedWidth,
            )
        }
        FloatingWorkspaceToolbar(
            state = state,
            onToggleFilePane = onToggleFilePane,
            onTogglePreviewPane = onTogglePreviewPane,
            onRefresh = onRefresh,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 18.dp),
        )
    }
}

@Composable
private fun FloatingWorkspaceToolbar(
    state: EditorState,
    onToggleFilePane: () -> Unit,
    onTogglePreviewPane: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
            exit = fadeOut() + scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (state.editMode || state.saveState != SaveState.Saved) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CircleShape,
                        tonalElevation = 3.dp,
                        shadowElevation = 8.dp,
                    ) {
                        SaveIndicator(state.saveState)
                    }
                }
                if (state.selected != null) {
                    FloatingToolbarButton(
                        icon = if (state.filePaneVisible) Icons.Default.MenuOpen else Icons.Default.FolderOpen,
                        description = if (state.filePaneVisible) "Hide file list" else "Show file list",
                        onClick = {
                            expanded = false
                            onToggleFilePane()
                        },
                    )
                }
                if (state.editMode) {
                    FloatingToolbarButton(
                        icon = if (state.previewPaneVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        description = if (state.previewPaneVisible) "Hide preview" else "Show preview",
                        onClick = {
                            expanded = false
                            onTogglePreviewPane()
                        },
                    )
                }
                FloatingToolbarButton(
                    icon = Icons.Default.Refresh,
                    description = "Refresh files",
                    onClick = {
                        expanded = false
                        onRefresh()
                    },
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = CircleShape,
            tonalElevation = 6.dp,
            shadowElevation = 14.dp,
            modifier = Modifier.size(44.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = if (expanded) "Close workspace controls" else "Open workspace controls",
                        modifier = Modifier.size(21.dp),
                    )
                }
                SaveStateBadge(state.saveState, Modifier.align(Alignment.TopEnd).padding(3.dp))
            }
        }
    }
}

@Composable
private fun FloatingToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        tonalElevation = 4.dp,
        shadowElevation = 12.dp,
        modifier = Modifier.size(38.dp),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(19.dp))
        }
    }
}

@Composable
private fun SaveStateBadge(saveState: SaveState, modifier: Modifier = Modifier) {
    if (saveState == SaveState.Saved) return
    val color = when (saveState) {
        SaveState.Failed -> MaterialTheme.colorScheme.error
        SaveState.Pending -> MaterialTheme.colorScheme.tertiary
        SaveState.Saving -> MaterialTheme.colorScheme.primary
        SaveState.Saved -> Color.Transparent
    }
    Box(modifier.size(9.dp).clip(CircleShape).background(color))
}

@Composable
private fun FilePane(
    files: List<DriveFile>,
    path: List<DriveFolder>,
    selectedId: String?,
    syncStates: Map<String, SaveState>,
    onSelect: (DriveFile) -> Unit,
    onPathClick: (Int) -> Unit,
    onCreate: () -> Unit,
    onDelete: (List<DriveFile>) -> Unit,
    toolbarReservedWidth: Dp,
    modifier: Modifier = Modifier,
) {
    var selectedIds by remember(path) { mutableStateOf(emptySet<String>()) }
    var deleteRequest by remember { mutableStateOf<List<DriveFile>?>(null) }
    val selectionMode = selectedIds.isNotEmpty()
    LaunchedEffect(files.map(DriveFile::id)) {
        selectedIds = selectedIds.intersect(files.map(DriveFile::id).toSet())
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxHeight(),
    ) {
        Column {
            if (selectionMode) {
                SelectionToolbar(
                    count = selectedIds.size,
                    onClose = { selectedIds = emptySet() },
                    onDelete = { deleteRequest = files.filter { it.id in selectedIds } },
                    toolbarReservedWidth = toolbarReservedWidth,
                )
            } else {
                Breadcrumbs(path, onPathClick, onCreate, toolbarReservedWidth)
            }
            if (files.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(42.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("This folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(files, key = { it.id }) { file ->
                        SwipeFileRow(
                            file = file,
                            selected = file.id == selectedId,
                            checked = file.id in selectedIds,
                            selectionMode = selectionMode,
                            syncState = syncStates[file.id],
                            onClick = {
                                if (selectionMode) {
                                    selectedIds = if (file.id in selectedIds) {
                                        selectedIds - file.id
                                    } else {
                                        selectedIds + file.id
                                    }
                                } else {
                                    onSelect(file)
                                }
                            },
                            onLongClick = { selectedIds = selectedIds + file.id },
                            onRequestDelete = { deleteRequest = listOf(file) },
                        )
                    }
                }
            }
        }
    }
    deleteRequest?.let { targets ->
        DeleteConfirmationDialog(
            files = targets,
            onDismiss = { deleteRequest = null },
            onConfirm = {
                onDelete(targets)
                selectedIds = emptySet()
                deleteRequest = null
            },
        )
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    toolbarReservedWidth: Dp,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(start = 8.dp, end = 8.dp + toolbarReservedWidth),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Exit selection") }
        Text("$count selected", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SwipeFileRow(
    file: DriveFile,
    selected: Boolean,
    checked: Boolean,
    selectionMode: Boolean,
    syncState: SaveState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val actionWidth = 92.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    var dragOffset by remember(file.id) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val draggableState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(-actionWidthPx, 0f)
    }
    LaunchedEffect(selectionMode) {
        if (selectionMode) dragOffset = 0f
    }
    Box(
        Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                .width(actionWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.errorContainer),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onRequestDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${file.name}",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = !selectionMode,
                    onDragStopped = {
                        val target = if (dragOffset <= -actionWidthPx * 0.45f) -actionWidthPx else 0f
                        animate(dragOffset, target) { value, _ -> dragOffset = value }
                    },
                ),
        ) {
            FileRow(
                file = file,
                selected = selected,
                checked = checked,
                selectionMode = selectionMode,
                syncState = syncState,
                onClick = {
                    if (dragOffset < 0f) {
                        scope.launch { animate(dragOffset, 0f) { value, _ -> dragOffset = value } }
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongClick,
            )
        }
    }
}

@Composable
private fun Breadcrumbs(
    path: List<DriveFolder>,
    onPathClick: (Int) -> Unit,
    onCreate: () -> Unit,
    toolbarReservedWidth: Dp,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp + toolbarReservedWidth, top = 14.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "FILES",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCreate, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Add, contentDescription = "New Markdown", modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (path.size > 1) {
                IconButton(onClick = { onPathClick(path.lastIndex - 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Parent folder", Modifier.size(18.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            path.takeLast(3).forEachIndexed { visibleIndex, folder ->
                val actualIndex = path.size - minOf(3, path.size) + visibleIndex
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (actualIndex == path.lastIndex) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(enabled = actualIndex != path.lastIndex) { onPathClick(actualIndex) },
                )
                if (actualIndex != path.lastIndex) {
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: DriveFile,
    selected: Boolean,
    checked: Boolean,
    selectionMode: Boolean,
    syncState: SaveState?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = checked, onCheckedChange = { onClick() }, modifier = Modifier.padding(start = 4.dp))
        }
        FileTypeIcon(file, Modifier.padding(start = 12.dp))
        Text(
            file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 9.dp),
        )
        if (file.isFolder()) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp).size(17.dp),
            )
        } else if (syncState == SaveState.Failed) {
            Row(
                modifier = Modifier.padding(end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = "Sync issue",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Sync issue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    files: List<DriveFile>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val localCount = files.count { it.id.startsWith("local:") }
    val remoteCount = files.size - localCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${files.size} item${if (files.size == 1) "" else "s"}?") },
        text = {
            Text(
                buildString {
                    if (remoteCount > 0) append("$remoteCount Google Drive item${if (remoteCount == 1) "" else "s"} will be moved to Trash.")
                    if (remoteCount > 0 && localCount > 0) append("\n\n")
                    if (localCount > 0) append("$localCount unsynced local draft${if (localCount == 1) "" else "s"} will be permanently deleted.")
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PreviewPane(
    state: EditorState,
    onToggleEdit: () -> Unit,
    toolbarReservedWidth: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(Modifier.fillMaxSize()) {
            val selected = state.selected
            if (selected == null) {
                EmptyPreview()
                return@Column
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(start = 22.dp, end = 22.dp + toolbarReservedWidth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(selected.file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        lastChangedLabel(selected.file),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.isEditableMarkdown && !state.editMode) {
                    FilledTonalButton(onClick = onToggleEdit, enabled = state.canEditMarkdown) {
                        if (state.validationState == ValidationState.Checking) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (state.validationState) {
                                ValidationState.Ready -> "Edit"
                                ValidationState.Checking -> "Checking"
                                ValidationState.Failed -> "Edit locked"
                            },
                        )
                    }
                }
            }
            val livePreview = if (state.editMode && selected.file.isMarkdown()) {
                PreviewData.Markdown(state.markdown)
            } else {
                selected.preview
            }
            PreviewContent(livePreview, selected.file.webViewLink, Modifier.fillMaxSize())
        }
    }
}

private fun lastChangedLabel(file: DriveFile): String {
    if (file.modifiedTime == null) {
        return if (file.id.startsWith("local:")) "Not synced yet" else "Last changed time unavailable"
    }
    val formatted = runCatching {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault())
            .format(Instant.parse(file.modifiedTime).atZone(ZoneId.systemDefault()))
    }.getOrNull() ?: file.modifiedTime
    return "Last changed $formatted"
}

@Composable
private fun EditorPane(
    state: EditorState,
    onEdit: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onSave: () -> Unit,
    onRename: (String) -> Unit,
    toolbarReservedWidth: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    var showRename by remember(state.selected?.file?.id) { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxHeight(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .padding(start = 16.dp, end = 16.dp + toolbarReservedWidth),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.selected?.file?.name ?: "Edit Markdown",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showRename = true }) {
                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Rename Markdown")
                }
                IconButton(onClick = onSave) { Icon(Icons.Default.Save, contentDescription = "Save now") }
                TextButton(onClick = onToggleEdit) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Done")
                }
            }
            BasicTextField(
                value = state.markdown,
                onValueChange = onEdit,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 25.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (state.markdown.isEmpty()) {
                            Text("Start writing…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        inner()
                    }
                },
            )
        }
    }
    if (showRename) {
        RenameDialog(
            currentName = state.selected?.file?.name.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = {
                onRename(it)
                showRename = false
            },
        )
    }
}

@Composable
private fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(currentName) { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Markdown") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("File name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PreviewContent(preview: PreviewData, webViewLink: String?, modifier: Modifier = Modifier) {
    when (preview) {
        is PreviewData.Markdown -> MarkdownPreview(preview.text, modifier)
        is PreviewData.Text -> TextPreview(preview, modifier)
        is PreviewData.Image -> ImagePreview(preview.bytes, modifier)
        is PreviewData.Pdf -> PdfPreview(preview.bytes, modifier)
        is PreviewData.Unsupported -> UnsupportedPreview(preview.reason, webViewLink, modifier)
    }
}

@Composable
private fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb()
    val codeTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val markwon = remember(context, darkTheme, codeBackground, codeTextColor) {
        val prism4j = Prism4j(OpenGDriveGrammarLocator())
        val syntaxTheme = if (darkTheme) {
            Prism4jThemeDarkula.create(codeBackground)
        } else {
            Prism4jThemeDefault.create(codeBackground)
        }
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, syntaxTheme, "clike"))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeBackgroundColor(codeBackground)
                        .codeBlockBackgroundColor(codeBackground)
                        .codeTextColor(codeTextColor)
                        .codeBlockTextColor(codeTextColor)
                        .codeBlockMargin(18)
                }
            })
            .build()
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 22.dp)) {
        AndroidView(
            factory = {
                TextView(it).apply {
                    textSize = 17f
                    typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
                    includeFontPadding = false
                    setTextIsSelectable(true)
                    movementMethod = LinkMovementMethod.getInstance()
                }
            },
            update = { view ->
                view.setTextColor(textColor)
                markwon.setMarkdown(view, markdown.ifBlank { "*Empty document*" })
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TextPreview(preview: PreviewData.Text, modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 22.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(8.dp),
        ) {
            SelectionContainer {
                Text(
                    preview.text.ifEmpty { "Empty file" },
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ImagePreview(bytes: ByteArray, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.Default) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Image preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(24.dp),
            )
        } ?: CircularProgressIndicator()
    }
}

private data class PdfInfo(val file: File, val pageCount: Int)

@Composable
private fun PdfPreview(bytes: ByteArray, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info by produceState<PdfInfo?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.IO) {
            val file = File.createTempFile("open-gdrive-preview-", ".pdf", context.cacheDir)
            file.writeBytes(bytes)
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val count = PdfRenderer(descriptor).use { it.pageCount }
            PdfInfo(file, count)
        }
    }
    DisposableEffect(info) {
        val file = info?.file
        onDispose { file?.delete() }
    }
    if (info == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        LazyColumn(
            modifier.background(MaterialTheme.colorScheme.surfaceContainer),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(List(info!!.pageCount) { it }, key = { _, page -> page }) { _, page ->
                PdfPage(info!!, page)
            }
        }
    }
}

@Composable
private fun PdfPage(info: PdfInfo, pageNumber: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val targetWidth = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val bitmap by produceState<Bitmap?>(initialValue = null, info.file, pageNumber, targetWidth) {
            value = withContext(Dispatchers.IO) { renderPdfPage(info.file, pageNumber, targetWidth) }
        }
        Surface(shape = RoundedCornerShape(6.dp), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "PDF page ${pageNumber + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                )
            } ?: Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            }
        }
    }
}

private fun renderPdfPage(file: File, pageNumber: Int, targetWidth: Int): Bitmap {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    PdfRenderer(descriptor).use { renderer ->
        renderer.openPage(pageNumber).use { page ->
            val targetHeight = (targetWidth * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
            return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }
}

@Composable
private fun UnsupportedPreview(reason: String, webViewLink: String?, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier.padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.InsertDriveFile,
            null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(54.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Preview unavailable", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (webViewLink != null) {
            Spacer(Modifier.height(22.dp))
            FilledTonalButton(onClick = { uriHandler.openUri(webViewLink) }) {
                Icon(Icons.Default.OpenInNew, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Open in Google Drive")
            }
        }
    }
}

@Composable
private fun EmptyPreview() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Description,
            null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Select a file to preview", style = MaterialTheme.typography.titleMedium)
        Text(
            "Markdown, text, images, PDFs, and Google Workspace files are supported.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CompactWorkspace(
    state: EditorState,
    onSelect: (DriveFile) -> Unit,
    onPathClick: (Int) -> Unit,
    onEdit: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onSave: () -> Unit,
    onCreate: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (List<DriveFile>) -> Unit,
    toolbarReservedWidth: Dp,
) {
    var showFiles by remember(state.folderPath) { mutableStateOf(state.selected == null) }
    if (showFiles || state.selected == null) {
        FilePane(
            files = state.files,
            path = state.folderPath,
            selectedId = state.selected?.file?.id,
            syncStates = state.fileSyncStates,
            onSelect = {
                onSelect(it)
                if (!it.isFolder()) showFiles = false
            },
            onPathClick = onPathClick,
            onCreate = {
                onCreate()
                showFiles = false
            },
            onDelete = onDelete,
            toolbarReservedWidth = toolbarReservedWidth,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { showFiles = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Spacer(Modifier.width(6.dp))
                Text("Files")
            }
            if (state.editMode) {
                EditorPane(
                    state,
                    onEdit,
                    onToggleEdit,
                    onSave,
                    onRename,
                    toolbarReservedWidth = toolbarReservedWidth,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PreviewPane(
                    state,
                    onToggleEdit,
                    toolbarReservedWidth = toolbarReservedWidth,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SaveIndicator(saveState: SaveState) {
    val (icon, text) = when (saveState) {
        SaveState.Saved -> Icons.Default.CloudDone to "Synced"
        SaveState.Pending -> Icons.Default.CloudUpload to "Saved locally"
        SaveState.Saving -> Icons.Default.Sync to "Syncing"
        SaveState.Failed -> Icons.Default.ErrorOutline to "Sync issue"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        val color = if (saveState == SaveState.Failed) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = text, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun FileTypeIcon(file: DriveFile, modifier: Modifier = Modifier) {
    val localIcon = fileLocalIcon(file)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = CircleShape,
        modifier = modifier.size(28.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                localIcon.icon,
                contentDescription = fileTypeLabel(file),
                tint = localIcon.tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private data class LocalFileIcon(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
)

@Composable
private fun fileLocalIcon(file: DriveFile): LocalFileIcon = when {
    file.isFolder() -> LocalFileIcon(Icons.Default.Folder, MaterialTheme.colorScheme.primary)
    file.isMarkdown() -> LocalFileIcon(SimpleIcons.Markdown, MaterialTheme.colorScheme.onSurface)
    file.mimeType == "application/pdf" -> LocalFileIcon(Icons.Default.PictureAsPdf, Color(0xFFE5252A))
    file.mimeType == GOOGLE_DOCUMENT -> LocalFileIcon(SimpleIcons.Googledocs, Color(0xFF4285F4))
    file.mimeType == GOOGLE_SPREADSHEET -> LocalFileIcon(SimpleIcons.Googlesheets, Color(0xFF34A853))
    file.mimeType == GOOGLE_PRESENTATION -> LocalFileIcon(SimpleIcons.Googleslides, Color(0xFFFBBC04))
    file.mimeType.startsWith("image/") -> LocalFileIcon(Icons.Default.Image, Color(0xFF8E5BB7))
    file.mimeType.startsWith("audio/") -> LocalFileIcon(Icons.Default.Audiotrack, Color(0xFFDB6D28))
    file.mimeType.startsWith("video/") -> LocalFileIcon(Icons.Default.Movie, Color(0xFF7E57C2))
    file.mimeType.contains("zip") || file.mimeType.contains("archive") ->
        LocalFileIcon(Icons.Default.Archive, Color(0xFF8D6E63))
    file.mimeType.contains("spreadsheet") || file.mimeType.contains("excel") ->
        LocalFileIcon(Icons.Default.TableChart, Color(0xFF217346))
    file.mimeType.contains("presentation") || file.mimeType.contains("powerpoint") ->
        LocalFileIcon(Icons.Default.Slideshow, Color(0xFFD24726))
    file.mimeType.contains("wordprocessing") || file.mimeType.contains("msword") ->
        LocalFileIcon(Icons.Default.Description, Color(0xFF2B579A))
    file.mimeType.startsWith("text/") || file.name.substringAfterLast('.', "").lowercase() in CODE_EXTENSIONS ->
        LocalFileIcon(Icons.Default.Code, Color(0xFF397D78))
    else -> LocalFileIcon(Icons.Default.InsertDriveFile, MaterialTheme.colorScheme.onSurfaceVariant)
}

private val CODE_EXTENSIONS = setOf(
    "json", "xml", "yaml", "yml", "toml", "kt", "kts", "java", "c", "cpp", "h", "hpp",
    "cs", "go", "rs", "py", "rb", "php", "js", "jsx", "ts", "tsx", "css", "scss", "html",
    "sh", "zsh", "bash", "sql", "graphql", "proto",
)

private fun fileTypeLabel(file: DriveFile): String = when {
    file.isFolder() -> "Folder"
    file.isMarkdown() -> "Markdown"
    file.mimeType == GOOGLE_DOCUMENT -> "Google Docs"
    file.mimeType == GOOGLE_SPREADSHEET -> "Google Sheets"
    file.mimeType == GOOGLE_PRESENTATION -> "Google Slides"
    file.mimeType == "application/pdf" -> "PDF"
    file.mimeType.startsWith("image/") -> "Image"
    file.mimeType.startsWith("text/") -> "Text"
    else -> file.name.substringAfterLast('.', "File").uppercase()
}
