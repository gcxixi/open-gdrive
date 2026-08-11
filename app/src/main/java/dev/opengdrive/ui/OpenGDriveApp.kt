package dev.opengdrive.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.opengdrive.data.DriveFile
import dev.opengdrive.data.DriveFolder
import dev.opengdrive.data.GOOGLE_DOCUMENT
import dev.opengdrive.data.GOOGLE_PRESENTATION
import dev.opengdrive.data.GOOGLE_SPREADSHEET
import dev.opengdrive.data.PreviewData
import dev.opengdrive.data.isFolder
import dev.opengdrive.data.isMarkdown
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
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
                title = {
                    Column {
                        Text("Open GDrive", style = MaterialTheme.typography.titleLarge)
                        if (state.authorized) {
                            Text(
                                state.selected?.file?.name ?: state.folderPath.last().name,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (state.authorized) {
                        if (state.editMode) SaveIndicator(state.saveState)
                        if (state.selected != null) {
                            IconButton(onClick = viewModel::toggleFilePane) {
                                Icon(
                                    if (state.filePaneVisible) Icons.Default.MenuOpen else Icons.Default.FolderOpen,
                                    contentDescription = if (state.filePaneVisible) "Hide file list" else "Show file list",
                                )
                            }
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
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                if (state.filePaneVisible || state.selected == null) {
                    FilePane(
                        files = state.files,
                        path = state.folderPath,
                        selectedId = state.selected?.file?.id,
                        onSelect = onSelect,
                        onPathClick = onPathClick,
                        modifier = Modifier.width(if (state.editMode) 300.dp else 340.dp),
                    )
                    VerticalDivider()
                }
                if (state.editMode) {
                    EditorPane(state, onEdit, onToggleEdit, onSave, Modifier.weight(1f))
                    VerticalDivider()
                }
                PreviewPane(state, onToggleEdit, Modifier.weight(1f))
            }
        } else {
            CompactWorkspace(state, onSelect, onPathClick, onEdit, onToggleEdit, onSave)
        }
    }
}

@Composable
private fun FilePane(
    files: List<DriveFile>,
    path: List<DriveFolder>,
    selectedId: String?,
    onSelect: (DriveFile) -> Unit,
    onPathClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxHeight()) {
        Column {
            Breadcrumbs(path, onPathClick)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
                        FileRow(file, file.id == selectedId, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(path: List<DriveFolder>, onPathClick: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("FILES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
private fun FileRow(file: DriveFile, selected: Boolean, onSelect: (DriveFile) -> Unit) {
    val icon = fileIcon(file)
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow
    ListItem(
        leadingContent = {
            Surface(
                color = if (file.isFolder()) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(38.dp),
            ) {
                Icon(icon, null, Modifier.padding(9.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        headlineContent = {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(fileTypeLabel(file), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            if (file.isFolder()) Icon(Icons.Default.ChevronRight, null, Modifier.size(18.dp))
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = background),
        modifier = Modifier.fillMaxWidth().clickable { onSelect(file) },
    )
}

@Composable
private fun PreviewPane(state: EditorState, onToggleEdit: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
        val selected = state.selected
        if (selected == null) {
            EmptyPreview()
            return@Column
        }
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(selected.file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    fileTypeLabel(selected.file),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.canEditMarkdown && !state.editMode) {
                FilledTonalButton(onClick = onToggleEdit) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Edit")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        val livePreview = if (state.editMode && selected.file.isMarkdown()) {
            PreviewData.Markdown(state.markdown)
        } else {
            selected.preview
        }
        PreviewContent(livePreview, selected.file.webViewLink, Modifier.fillMaxSize())
    }
}

@Composable
private fun EditorPane(
    state: EditorState,
    onEdit: (String) -> Unit,
    onToggleEdit: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Edit Markdown", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSave) { Icon(Icons.Default.Save, contentDescription = "Save now") }
            TextButton(onClick = onToggleEdit) {
                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Done")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
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
) {
    var showFiles by remember(state.folderPath) { mutableStateOf(state.selected == null) }
    if (showFiles || state.selected == null) {
        FilePane(
            files = state.files,
            path = state.folderPath,
            selectedId = state.selected?.file?.id,
            onSelect = {
                onSelect(it)
                if (!it.isFolder()) showFiles = false
            },
            onPathClick = onPathClick,
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
                EditorPane(state, onEdit, onToggleEdit, onSave, Modifier.weight(1f))
            } else {
                PreviewPane(state, onToggleEdit, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SaveIndicator(saveState: SaveState) {
    val (icon, text) = when (saveState) {
        SaveState.Saved -> Icons.Default.CloudDone to "Saved"
        SaveState.Pending -> Icons.Default.Edit to "Autosave in 5s"
        SaveState.Saving -> Icons.Default.CloudDone to "Saving"
        SaveState.Failed -> Icons.Default.CloudOff to "Save failed"
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

private fun fileIcon(file: DriveFile) = when {
    file.isFolder() -> Icons.Default.Folder
    file.isMarkdown() -> Icons.Default.Description
    file.mimeType.startsWith("image/") -> Icons.Default.Image
    file.mimeType == "application/pdf" -> Icons.Default.PictureAsPdf
    file.mimeType == GOOGLE_DOCUMENT -> Icons.Default.Description
    file.mimeType == GOOGLE_SPREADSHEET -> Icons.Default.TableChart
    file.mimeType == GOOGLE_PRESENTATION -> Icons.Default.Slideshow
    file.mimeType.startsWith("text/") -> Icons.Default.Code
    else -> Icons.Default.InsertDriveFile
}

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
