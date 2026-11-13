package com.leantv.app.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.R
import com.leantv.app.files.ClipboardItem
import com.leantv.app.files.FileItem
import com.leantv.app.files.FileManagerHelper
import com.leantv.app.files.StorageDrive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun FileManagerScreen(
    initialDir: File? = null,
    highlightFile: String? = null,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var drives by remember { mutableStateOf<List<StorageDrive>>(emptyList()) }
    var currentDir by remember { mutableStateOf<File?>(null) }
    var currentFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var activeCategory by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Clipboard for Copy / Move
    var clipboard by remember { mutableStateOf<ClipboardItem?>(null) }

    // Action dialogs
    var actionFile by remember { mutableStateOf<FileItem?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<FileItem?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderInput by remember { mutableStateOf("") }
    var showDetailsDialog by remember { mutableStateOf<FileItem?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var hasPermission by remember { mutableStateOf(FileManagerHelper.hasStoragePermission(ctx)) }
    var showPermissionPrompt by remember { mutableStateOf(false) }

    fun loadDir(dir: File) {
        scope.launch {
            isLoading = true
            currentDir = dir
            activeCategory = null
            currentFiles = FileManagerHelper.listDirectory(dir)
            isLoading = false
        }
    }

    fun loadCategory(key: String) {
        scope.launch {
            isLoading = true
            activeCategory = key
            currentDir = null
            currentFiles = FileManagerHelper.scanCategoryFiles(key)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        // Auto grant storage permission if Shizuku / ADB is active
        val granted = FileManagerHelper.autoGrantStoragePermission(ctx)
        hasPermission = granted
        if (!granted) {
            showPermissionPrompt = true
        }

        // Initialize drives
        val detected = FileManagerHelper.detectStorageDrives(ctx)
        drives = detected

        if (initialDir != null && (initialDir.exists() || initialDir.mkdirs())) {
            loadDir(initialDir)
        } else if (detected.isNotEmpty()) {
            loadDir(detected.first().path)
        } else {
            loadDir(FileManagerHelper.ensureQuickShareDir())
        }
    }

    // Intercept Back button: navigate up directory, or close file manager
    BackHandler {
        when {
            actionFile != null -> actionFile = null
            showRenameDialog != null -> showRenameDialog = null
            showDeleteDialog != null -> showDeleteDialog = null
            showNewFolderDialog -> showNewFolderDialog = false
            showDetailsDialog != null -> showDetailsDialog = null
            showPermissionPrompt -> showPermissionPrompt = false
            activeCategory != null -> {
                val root = drives.firstOrNull()?.path ?: File("/storage/emulated/0")
                loadDir(root)
            }
            currentDir != null -> {
                val cur = currentDir!!
                val isRoot = drives.any { it.path.absolutePath == cur.absolutePath } || cur.path == "/storage/emulated/0" || cur.path == "/sdcard"
                if (!isRoot && cur.parentFile != null && cur.parentFile!!.canRead()) {
                    loadDir(cur.parentFile!!)
                } else {
                    onClose()
                }
            }
            else -> onClose()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E12))
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Header: Breadcrumb & Main Actions ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF2A85FF), Color(0xFF0051C7)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "LEANTV FILES",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF388AF6),
                            letterSpacing = 2.4.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        // Breadcrumbs path
                        Text(
                            text = if (activeCategory != null) "Category  ›  ${activeCategory?.uppercase()}"
                            else currentDir?.path?.replace("/storage/emulated/0", "Internal Storage") ?: "Storage",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Top Actions (New Folder, Paste, Close)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentDir != null) {
                        TvHeaderButton(
                            label = "New Folder",
                            icon = Icons.Rounded.CreateNewFolder,
                            onClick = {
                                newFolderInput = ""
                                showNewFolderDialog = true
                            }
                        )
                    }

                    clipboard?.let { clip ->
                        TvHeaderButton(
                            label = "Paste (${clip.file.name.take(12)})",
                            icon = Icons.Rounded.ContentPaste,
                            accentColor = Color(0xFF30D158),
                            onClick = {
                                val target = currentDir
                                if (target != null) {
                                    scope.launch {
                                        val ok = if (clip.isCut) {
                                            FileManagerHelper.moveFile(clip.file, target)
                                        } else {
                                            FileManagerHelper.copyFile(clip.file, target)
                                        }
                                        if (ok) {
                                            statusMessage = if (clip.isCut) "Moved successfully" else "Copied successfully"
                                            clipboard = null
                                            loadDir(target)
                                        } else {
                                            statusMessage = "Operation failed"
                                        }
                                    }
                                }
                            }
                        )
                    }

                    if (!hasPermission) {
                        TvHeaderButton(
                            label = "Grant Storage Access",
                            icon = Icons.Rounded.Shield,
                            accentColor = Color(0xFFFF9F0A),
                            onClick = { FileManagerHelper.requestStoragePermission(ctx) }
                        )
                    }

                    TvHeaderButton(
                        label = "Close",
                        icon = Icons.Rounded.Close,
                        onClick = onClose
                    )
                }
            }

            statusMessage?.let { msg ->
                LaunchedEffect(msg) {
                    delay(3500)
                    statusMessage = null
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x33FFA726))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(text = msg, color = Color(0xFFFFD60A), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ─── Quick Access Tabs Row (Amaze style categories + Quick Share) ───
            val categories = listOf(
                Pair("quickshare", "Quick Share"),
                Pair("downloads", "Downloads"),
                Pair("apks", "APKs"),
                Pair("videos", "Videos"),
                Pair("audio", "Music"),
                Pair("images", "Pictures"),
                Pair("documents", "Documents")
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Root Internal Storage tab
                item {
                    val root = drives.firstOrNull()?.path ?: File("/storage/emulated/0")
                    val isSelected = currentDir?.absolutePath == root.absolutePath && activeCategory == null
                    TvCategoryPill(
                        label = "Internal Storage",
                        icon = Icons.Rounded.Storage,
                        isSelected = isSelected,
                        onClick = { loadDir(root) }
                    )
                }

                items(categories) { (key, label) ->
                    val isSelected = activeCategory == key || (key == "quickshare" && currentDir?.name == "Quickshare")
                    val icon = when (key) {
                        "quickshare" -> Icons.Rounded.Bolt
                        "downloads" -> Icons.Rounded.Download
                        "apks" -> Icons.Rounded.Android
                        "videos" -> Icons.Rounded.Movie
                        "audio" -> Icons.Rounded.MusicNote
                        "images" -> Icons.Rounded.Image
                        else -> Icons.Rounded.Description
                    }
                    val isQs = key == "quickshare"
                    TvCategoryPill(
                        label = label,
                        icon = icon,
                        iconRes = if (isQs) R.drawable.ic_quick_share else null,
                        isSelected = isSelected,
                        accent = if (isQs) Color(0xFF0A84FF) else null,
                        onClick = {
                            if (isQs) {
                                loadDir(FileManagerHelper.ensureQuickShareDir())
                            } else {
                                loadCategory(key)
                            }
                        }
                    )
                }

                // Any connected USB / Removable drives
                drives.filter { it.isRemovable }.forEach { usb ->
                    item {
                        TvCategoryPill(
                            label = usb.name,
                            icon = Icons.Rounded.Usb,
                            isSelected = currentDir?.absolutePath == usb.path.absolutePath,
                            onClick = { loadDir(usb.path) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ─── Main Content Grid / Card List ───
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF0A84FF), strokeWidth = 3.dp)
                        Text("Reading storage...", color = Color(0x99FFFFFF), fontSize = 18.sp)
                    }
                }
            } else if (currentFiles.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = Color(0x33FFFFFF),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            text = if (activeCategory != null) "No $activeCategory files found" else "This folder is empty",
                            color = Color(0x66FFFFFF),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // If in subfolder, show ".." Up Folder item
                    if (currentDir != null && drives.none { it.path.absolutePath == currentDir!!.absolutePath } && currentDir!!.parentFile != null) {
                        item {
                            TvParentFolderCard(
                                onClick = { loadDir(currentDir!!.parentFile!!) }
                            )
                        }
                    }

                    items(currentFiles, key = { it.file.absolutePath }) { item ->
                        val isHighlighted = highlightFile != null && item.name.equals(highlightFile, ignoreCase = true)
                        TvFileCard(
                            item = item,
                            isHighlighted = isHighlighted,
                            onClick = {
                                if (item.isDirectory) {
                                    loadDir(item.file)
                                } else {
                                    val opened = FileManagerHelper.openFile(ctx, item.file)
                                    if (!opened) statusMessage = "No compatible app found to open ${item.name}"
                                }
                            },
                            onLongClick = {
                                actionFile = item
                            }
                        )
                    }
                }
            }
        }

        // ─── Modal Context Menu: File Actions ───
        actionFile?.let { item ->
            TvFileActionModal(
                item = item,
                onDismiss = { actionFile = null },
                onOpen = {
                    actionFile = null
                    FileManagerHelper.openFile(ctx, item.file)
                },
                onCopy = {
                    actionFile = null
                    clipboard = ClipboardItem(item.file, isCut = false)
                    statusMessage = "Copied to clipboard. Navigate to target folder and click Paste."
                },
                onMove = {
                    actionFile = null
                    clipboard = ClipboardItem(item.file, isCut = true)
                    statusMessage = "Moved to clipboard. Navigate to target folder and click Paste."
                },
                onRename = {
                    actionFile = null
                    renameInput = item.name
                    showRenameDialog = item
                },
                onDelete = {
                    actionFile = null
                    showDeleteDialog = item
                },
                onDetails = {
                    actionFile = null
                    showDetailsDialog = item
                }
            )
        }

        // ─── Rename Dialog ───
        showRenameDialog?.let { item ->
            TvInputDialog(
                title = "Rename",
                initialValue = renameInput,
                onValueChange = { renameInput = it },
                confirmLabel = "Rename",
                onConfirm = {
                    if (renameInput.isNotBlank() && renameInput != item.name) {
                        scope.launch {
                            val ok = FileManagerHelper.renameFile(item.file, renameInput.trim())
                            if (ok) {
                                statusMessage = "Renamed to ${renameInput.trim()}"
                                currentDir?.let { loadDir(it) } ?: activeCategory?.let { loadCategory(it) }
                            } else {
                                statusMessage = "Rename failed"
                            }
                        }
                    }
                    showRenameDialog = null
                },
                onDismiss = { showRenameDialog = null }
            )
        }

        // ─── New Folder Dialog ───
        if (showNewFolderDialog) {
            TvInputDialog(
                title = "Create New Folder",
                initialValue = newFolderInput,
                onValueChange = { newFolderInput = it },
                confirmLabel = "Create",
                onConfirm = {
                    val name = newFolderInput.trim()
                    if (name.isNotBlank() && currentDir != null) {
                        scope.launch {
                            val ok = FileManagerHelper.createFolder(currentDir!!, name)
                            if (ok) {
                                statusMessage = "Folder '$name' created"
                                loadDir(currentDir!!)
                            } else {
                                statusMessage = "Failed to create folder"
                            }
                        }
                    }
                    showNewFolderDialog = false
                },
                onDismiss = { showNewFolderDialog = false }
            )
        }

        // ─── Delete Confirmation Dialog ───
        showDeleteDialog?.let { item ->
            TvConfirmDialog(
                title = "Delete ${if (item.isDirectory) "Folder" else "File"}?",
                message = "Are you sure you want to permanently delete '${item.name}'? This cannot be undone.",
                confirmLabel = "Delete",
                confirmColor = Color(0xFFFF453A),
                onConfirm = {
                    scope.launch {
                        val ok = FileManagerHelper.deleteFile(item.file)
                        if (ok) {
                            statusMessage = "Deleted '${item.name}'"
                            currentDir?.let { loadDir(it) } ?: activeCategory?.let { loadCategory(it) }
                        } else {
                            statusMessage = "Delete failed"
                        }
                    }
                    showDeleteDialog = null
                },
                onDismiss = { showDeleteDialog = null }
            )
        }

        // ─── File Details Dialog ───
        showDetailsDialog?.let { item ->
            TvFileDetailsDialog(
                item = item,
                onDismiss = { showDetailsDialog = null }
            )
        }

        // ─── Storage Permission Dialog ───
        if (showPermissionPrompt && !hasPermission) {
            TvConfirmDialog(
                title = "Storage Access Required",
                message = "LeanTV needs full storage access to browse your files, install APKs, and save incoming Quick Share files to internal storage.",
                confirmLabel = "Grant Storage Access",
                confirmColor = Color(0xFF0A84FF),
                onConfirm = {
                    showPermissionPrompt = false
                    FileManagerHelper.requestStoragePermission(ctx)
                },
                onDismiss = { showPermissionPrompt = false }
            )
        }
    }
}

// ─── Sub-Composables for Amaze TV UI ───

@Composable
private fun TvCategoryPill(
    label: String,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    isSelected: Boolean = false,
    accent: Color? = null,
    onClick: () -> Unit
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, spring(dampingRatio = 0.8f, stiffness = 400f), label = "catScale")
    val shape = RoundedCornerShape(50)

    val bg = when {
        focused -> Color.White
        isSelected -> accent?.copy(alpha = 0.35f) ?: Color(0x3DFFFFFF)
        else -> Color(0x1AFFFFFF)
    }
    val fg = when {
        focused -> Color.Black
        isSelected -> accent ?: Color.White
        else -> Color(0xCCFFFFFF)
    }

    Row(
        modifier = Modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .border(
                if (focused) 1.5.dp else if (isSelected) 1.dp else 0.5.dp,
                if (focused) Color.White else if (isSelected) (accent ?: Color.White) else Color(0x24FFFFFF),
                shape
            )
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .clickable {
                TvSound.playClick(view)
                onClick()
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = label, color = fg, fontSize = 16.sp, fontWeight = if (isSelected || focused) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
private fun TvHeaderButton(
    label: String,
    icon: ImageVector,
    accentColor: Color = Color.White,
    onClick: () -> Unit
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .height(42.dp)
            .clip(shape)
            .background(if (focused) Color.White else Color(0x1FFFFFFF))
            .border(1.dp, if (focused) Color.White else Color(0x2EFFFFFF), shape)
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .clickable {
                TvSound.playClick(view)
                onClick()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Color.Black else accentColor, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (focused) Color.Black else accentColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TvParentFolderCard(onClick: () -> Unit) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(if (focused) Color.White else Color(0x14FFFFFF))
            .border(1.5.dp, if (focused) Color.White else Color(0x24FFFFFF), shape)
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .clickable {
                TvSound.playClick(view)
                onClick()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = null,
                tint = if (focused) Color.Black else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(".. (Up Level)", color = if (focused) Color.Black else Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Return to parent folder", color = if (focused) Color(0x99000000) else Color(0x80FFFFFF), fontSize = 13.sp)
        }
    }
}

@Composable
private fun TvFileCard(
    item: FileItem,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var isLongPressHandled by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        if (focused) 1.05f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "fileScale"
    )
    val shape = RoundedCornerShape(20.dp)

    val iconVector = when {
        item.isDirectory -> Icons.Rounded.Folder
        item.extension == "apk" -> Icons.Rounded.Android
        FileManagerHelper.isVideo(item.extension) -> Icons.Rounded.Movie
        FileManagerHelper.isAudio(item.extension) -> Icons.Rounded.MusicNote
        FileManagerHelper.isImage(item.extension) -> Icons.Rounded.Image
        item.extension in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Rounded.Archive
        else -> Icons.Rounded.Description
    }

    val iconTint = when {
        focused -> Color.Black
        item.isDirectory -> Color(0xFF2A85FF)
        item.extension == "apk" -> Color(0xFF30D158)
        FileManagerHelper.isVideo(item.extension) -> Color(0xFFFF9F0A)
        FileManagerHelper.isAudio(item.extension) -> Color(0xFFFF375F)
        FileManagerHelper.isImage(item.extension) -> Color(0xFFBF5AF2)
        else -> Color(0xFF64D2FF)
    }

    val borderColor = when {
        focused -> Color.White
        isHighlighted -> Color(0xFF0A84FF)
        else -> Color(0x1AFFFFFF)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 16.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(if (focused) Color.White else if (isHighlighted) Color(0x260A84FF) else Color(0x12FFFFFF))
            .border(if (focused) 2.dp else if (isHighlighted) 1.5.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .focusable()
            .onPreviewKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                val center = k.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || k.keyCode == KeyEvent.KEYCODE_ENTER
                val menu = k.keyCode == KeyEvent.KEYCODE_MENU
                when {
                    menu && k.action == KeyEvent.ACTION_DOWN -> {
                        TvSound.playClick(view)
                        onLongClick()
                        true
                    }
                    center && k.action == KeyEvent.ACTION_DOWN -> {
                        if (k.repeatCount == 0) {
                            isLongPressHandled = false
                            longPressJob?.cancel()
                            longPressJob = scope.launch {
                                delay(450)
                                isLongPressHandled = true
                                TvSound.playClick(view)
                                onLongClick()
                            }
                        }
                        true
                    }
                    center && k.action == KeyEvent.ACTION_UP -> {
                        val wasLongPress = isLongPressHandled
                        longPressJob?.cancel()
                        longPressJob = null
                        isLongPressHandled = false
                        if (!wasLongPress) {
                            TvSound.playClick(view)
                            onClick()
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (focused) Color(0x1F000000) else iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                iconVector,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = if (focused) Color.Black else Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            val detail = if (item.isDirectory) {
                "${item.childCount} items • ${FileManagerHelper.formatDate(item.lastModified)}"
            } else {
                "${FileManagerHelper.formatFileSize(item.sizeBytes)} • ${FileManagerHelper.formatDate(item.lastModified)}"
            }
            Text(
                text = detail,
                color = if (focused) Color(0x99000000) else Color(0x80FFFFFF),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─── Modal Context Menu: File Actions ───

@Composable
private fun TvFileActionModal(
    item: FileItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDetails: () -> Unit
) {
    val autoFocus = rememberAutoFocus()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss)
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1E2128))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            TvModalActionButton(
                label = if (item.isDirectory) "Open Folder" else if (item.extension == "apk") "Install APK" else "Open File",
                icon = if (item.extension == "apk") Icons.Rounded.Download else Icons.Rounded.Launch,
                modifier = Modifier.focusRequester(autoFocus),
                onClick = onOpen
            )
            TvModalActionButton(label = "Copy", icon = Icons.Rounded.ContentCopy, onClick = onCopy)
            TvModalActionButton(label = "Move / Cut", icon = Icons.Rounded.DriveFileMove, onClick = onMove)
            TvModalActionButton(label = "Rename", icon = Icons.Rounded.Edit, onClick = onRename)
            TvModalActionButton(label = "File Details", icon = Icons.Rounded.Info, onClick = onDetails)
            TvModalActionButton(label = "Delete", icon = Icons.Rounded.Delete, accentColor = Color(0xFFFF453A), onClick = onDelete)
            TvModalActionButton(label = "Cancel", icon = Icons.Rounded.Close, onClick = onDismiss)
        }
    }
}

@Composable
private fun TvModalActionButton(
    label: String,
    icon: ImageVector,
    accentColor: Color = Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(if (focused) Color.White else Color(0x14FFFFFF))
            .border(1.dp, if (focused) Color.White else Color(0x1FFFFFFF), shape)
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .clickable {
                TvSound.playClick(view)
                onClick()
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (focused) Color.Black else accentColor, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (focused) Color.Black else accentColor, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Input Dialog for Rename & New Folder ───

@Composable
private fun TvInputDialog(
    title: String,
    initialValue: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val autoFocus = rememberAutoFocus()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1E2128))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

            TextField(
                value = text,
                onValueChange = {
                    text = it
                    onValueChange(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(autoFocus),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0x26FFFFFF),
                    unfocusedContainerColor = Color(0x14FFFFFF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF0A84FF),
                    focusedIndicatorColor = Color(0xFF0A84FF),
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Focusable(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    idle = Color(0xFF0A84FF).copy(alpha = 0.25f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(confirmLabel, style = Ty.Button.copy(color = Color(0xFF0A84FF)))
                    }
                }

                Focusable(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    idle = Color(0x1AFFFFFF)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Cancel", style = Ty.Button.copy(color = Color.White))
                    }
                }
            }
        }
    }
}

// ─── Confirmation Dialog ───

@Composable
private fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    confirmColor: Color = Color.White,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val autoFocus = rememberAutoFocus()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1E2128))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(message, fontSize = 16.sp, color = Color(0xCCFFFFFF), lineHeight = 24.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Focusable(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).focusRequester(autoFocus),
                    shape = RoundedCornerShape(50),
                    idle = confirmColor.copy(alpha = 0.25f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(confirmLabel, style = Ty.Button.copy(color = confirmColor))
                    }
                }

                Focusable(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    idle = Color(0x1AFFFFFF)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Cancel", style = Ty.Button.copy(color = Color.White))
                    }
                }
            }
        }
    }
}

// ─── File Details Dialog ───

@Composable
private fun TvFileDetailsDialog(
    item: FileItem,
    onDismiss: () -> Unit
) {
    val autoFocus = rememberAutoFocus()
    val details = remember(item) { FileManagerHelper.getFileDetails(item.file) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(onClick = onDismiss)
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1E2128))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("File Details", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(4.dp))

            details.forEach { (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(key, color = Color(0x80FFFFFF), fontSize = 15.sp)
                    Text(
                        value,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.widthIn(max = 340.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Focusable(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().focusRequester(autoFocus),
                shape = RoundedCornerShape(50),
                idle = Color(0x24FFFFFF)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("OK", style = Ty.Button.copy(color = Color.White))
                }
            }
        }
    }
}
