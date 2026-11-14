package com.leantv.app.ui

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
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
import com.leantv.app.files.MultiClipboard
import com.leantv.app.files.StorageDrive
import com.leantv.app.share.QrGenerator
import com.leantv.app.share.QuickShareHost
import android.net.wifi.WifiManager
import android.util.Log
import dev.bluehouse.bada.discovery.NearbyPeer
import dev.bluehouse.bada.discovery.NearbyPeerDiscovery
import dev.bluehouse.bada.discovery.NearbyPeerEvent
import dev.bluehouse.bada.discovery.NearbyPeerRoute
import dev.bluehouse.bada.discovery.bootstrap.BleGattInitialControlClient
import dev.bluehouse.bada.discovery.bootstrap.BleL2capInitialControlClient
import dev.bluehouse.bada.discovery.bootstrap.BluetoothClassicBootstrapClient
import dev.bluehouse.bada.discovery.medium.MediumRegistries
import dev.bluehouse.bada.discovery.wifi.AndroidWifiCapabilities
import dev.bluehouse.bada.protocol.connection.FileSource
import dev.bluehouse.bada.protocol.connection.OutboundConnection
import dev.bluehouse.bada.protocol.connection.OutboundConnectionState
import dev.bluehouse.bada.protocol.endpoint.DeviceType
import dev.bluehouse.bada.protocol.endpoint.EndpointInfo
import dev.bluehouse.bada.protocol.medium.MediumRegistry
import java.io.IOException
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    // Multi-Select Mode
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf<Set<File>>(emptySet()) }
    var multiClipboard by remember { mutableStateOf<MultiClipboard?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // Media Viewer States
    var viewerImageFiles by remember { mutableStateOf<List<File>?>(null) }
    var viewerImageIndex by remember { mutableStateOf(0) }
    var viewerPdfFile by remember { mutableStateOf<File?>(null) }
    var viewerTextFile by remember { mutableStateOf<File?>(null) }
    var playingAudioFile by remember { mutableStateOf<File?>(null) }

    // Action dialogs
    var actionFile by remember { mutableStateOf<FileItem?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<FileItem?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderInput by remember { mutableStateOf("") }
    var showDetailsDialog by remember { mutableStateOf<FileItem?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var quickShareSendFiles by remember { mutableStateOf<List<File>?>(null) }

    var hasPermission by remember { mutableStateOf(FileManagerHelper.hasStoragePermission(ctx)) }
    var showPermissionPrompt by remember { mutableStateOf(false) }

    // Focus requesters for bulletproof TV remote navigation
    val internalStorageFocusRequester = rememberAutoFocus()
    val firstGridItemFocusRequester = remember { FocusRequester() }
    val headerActionFocusRequester = remember { FocusRequester() }
    val highlightedFileFocusRequester = remember { FocusRequester() }
    val multiSelectActionBarFocusRequester = remember { FocusRequester() }

    fun toggleSelect(file: File) {
        selectedFiles = if (selectedFiles.contains(file)) {
            selectedFiles - file
        } else {
            selectedFiles + file
        }
    }

    fun loadDir(dir: File, focusFirstItem: Boolean = false) {
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                FileManagerHelper.listDirectory(dir)
            }
            currentDir = dir
            activeCategory = null
            selectedFiles = emptySet()
            currentFiles = files
            isLoading = false
            if (focusFirstItem) {
                delay(100)
                val ok = runCatching {
                    if (files.isNotEmpty()) {
                        firstGridItemFocusRequester.requestFocus()
                        true
                    } else false
                }.getOrDefault(false)
                if (!ok) {
                    delay(100)
                    val retry = runCatching {
                        if (files.isNotEmpty()) {
                            firstGridItemFocusRequester.requestFocus()
                            true
                        } else false
                    }.getOrDefault(false)
                    if (!retry) {
                        runCatching { internalStorageFocusRequester.requestFocus() }
                    }
                }
            }
        }
    }

    fun loadCategory(key: String, focusFirstItem: Boolean = false) {
        scope.launch {
            val files = withContext(Dispatchers.IO) {
                FileManagerHelper.scanCategoryFiles(ctx, key)
            }
            activeCategory = key
            currentDir = null
            selectedFiles = emptySet()
            currentFiles = files
            isLoading = false
            if (focusFirstItem) {
                delay(100)
                val ok = runCatching {
                    if (files.isNotEmpty()) {
                        firstGridItemFocusRequester.requestFocus()
                        true
                    } else false
                }.getOrDefault(false)
                if (!ok) {
                    delay(100)
                    val retry = runCatching {
                        if (files.isNotEmpty()) {
                            firstGridItemFocusRequester.requestFocus()
                            true
                        } else false
                    }.getOrDefault(false)
                    if (!retry) {
                        runCatching { internalStorageFocusRequester.requestFocus() }
                    }
                }
            }
        }
    }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        val granted = FileManagerHelper.hasStoragePermission(ctx)
        hasPermission = granted
        if (granted) {
            currentDir?.let { loadDir(it) } ?: activeCategory?.let { loadCategory(it) }
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        val granted = FileManagerHelper.autoGrantStoragePermission(ctx)
        hasPermission = granted
        if (!granted) {
            showPermissionPrompt = true
        }

        val detected = withContext(Dispatchers.IO) {
            FileManagerHelper.detectStorageDrives(ctx)
        }
        drives = detected

        val targetDir = when {
            initialDir != null && (initialDir.exists() || initialDir.mkdirs()) -> initialDir
            detected.isNotEmpty() -> detected.first().path
            else -> FileManagerHelper.ensureQuickShareDir()
        }
        loadDir(targetDir)

        if (highlightFile != null) {
            delay(180)
            runCatching { highlightedFileFocusRequester.requestFocus() }
        }
    }

    // Restore focus when any modal dialog or viewer closes
    val isAnyModalOpen = actionFile != null || viewerImageFiles != null || viewerPdfFile != null ||
            viewerTextFile != null || playingAudioFile != null || showBatchDeleteDialog ||
            showRenameDialog != null || showDeleteDialog != null || showNewFolderDialog ||
            showDetailsDialog != null || showPermissionPrompt || quickShareSendFiles != null

    var wasAnyModalOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isAnyModalOpen) {
        if (wasAnyModalOpen && !isAnyModalOpen) {
            delay(100)
            if (currentFiles.isNotEmpty()) {
                runCatching { firstGridItemFocusRequester.requestFocus() }
            } else {
                runCatching { internalStorageFocusRequester.requestFocus() }
            }
        }
        wasAnyModalOpen = isAnyModalOpen
    }

    // Safety focus guard when clipboard is cleared (e.g. after pasting)
    var hadClipboard by remember { mutableStateOf(false) }
    LaunchedEffect(multiClipboard) {
        if (hadClipboard && multiClipboard == null) {
            delay(120)
            val ok = runCatching {
                if (currentFiles.isNotEmpty()) {
                    firstGridItemFocusRequester.requestFocus()
                    true
                } else false
            }.getOrDefault(false)
            if (!ok) {
                runCatching { internalStorageFocusRequester.requestFocus() }
            }
        }
        hadClipboard = (multiClipboard != null)
    }

    // Intercept Back button: navigate up directory, exit multi-select, or close file manager
    BackHandler {
        when {
            quickShareSendFiles != null -> {
                quickShareSendFiles = null
                QuickShareHost.clear()
            }
            viewerImageFiles != null -> viewerImageFiles = null
            viewerTextFile != null -> viewerTextFile = null
            viewerPdfFile != null -> viewerPdfFile = null
            playingAudioFile != null -> playingAudioFile = null
            showBatchDeleteDialog -> showBatchDeleteDialog = false
            actionFile != null -> actionFile = null
            showRenameDialog != null -> showRenameDialog = null
            showDeleteDialog != null -> showDeleteDialog = null
            showNewFolderDialog -> showNewFolderDialog = false
            showDetailsDialog != null -> showDetailsDialog = null
            showPermissionPrompt -> showPermissionPrompt = false
            isMultiSelectMode -> {
                isMultiSelectMode = false
                selectedFiles = emptySet()
                scope.launch {
                    delay(60)
                    runCatching { headerActionFocusRequester.requestFocus() }
                }
            }
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
            // ─── Header: Multi-Select Bar or Standard Path Bar ───
            if (isMultiSelectMode) {
                TvMultiSelectActionBar(
                    selectedCount = selectedFiles.size,
                    totalCount = currentFiles.size,
                    focusRequester = multiSelectActionBarFocusRequester,
                    onMoveDown = {
                        if (currentFiles.isNotEmpty()) {
                            runCatching { firstGridItemFocusRequester.requestFocus() }
                        } else {
                            runCatching { internalStorageFocusRequester.requestFocus() }
                        }
                    },
                    onSelectAll = {
                        selectedFiles = if (selectedFiles.size == currentFiles.size) emptySet()
                        else currentFiles.map { it.file }.toSet()
                    },
                    onCopy = {
                        if (selectedFiles.isNotEmpty()) {
                            multiClipboard = MultiClipboard(selectedFiles.toList(), isCut = false)
                            statusMessage = "Copied ${selectedFiles.size} items. Go to target folder and click Paste."
                            isMultiSelectMode = false
                            selectedFiles = emptySet()
                            scope.launch {
                                delay(60)
                                runCatching { firstGridItemFocusRequester.requestFocus() }
                            }
                        }
                    },
                    onMove = {
                        if (selectedFiles.isNotEmpty()) {
                            multiClipboard = MultiClipboard(selectedFiles.toList(), isCut = true)
                            statusMessage = "Cut ${selectedFiles.size} items. Go to target folder and click Paste."
                            isMultiSelectMode = false
                            selectedFiles = emptySet()
                            scope.launch {
                                delay(60)
                                runCatching { firstGridItemFocusRequester.requestFocus() }
                            }
                        }
                    },
                    onQuickShare = {
                        if (selectedFiles.isNotEmpty()) {
                            val files = selectedFiles.toList()
                            quickShareSendFiles = files
                        }
                    },
                    onDelete = {
                        if (selectedFiles.isNotEmpty()) {
                            showBatchDeleteDialog = true
                        }
                    },
                    onDone = {
                        isMultiSelectMode = false
                        selectedFiles = emptySet()
                        scope.launch {
                            delay(60)
                            runCatching { headerActionFocusRequester.requestFocus() }
                        }
                    }
                )
            } else {
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

                    // Top Action Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Multi-select toggle button
                        TvHeaderButton(
                            label = "Select",
                            icon = Icons.Rounded.Checklist,
                            modifier = Modifier.focusRequester(headerActionFocusRequester),
                            onMoveDown = { runCatching { internalStorageFocusRequester.requestFocus() } },
                            onClick = {
                                isMultiSelectMode = true
                                selectedFiles = emptySet()
                                scope.launch {
                                    delay(60)
                                    if (currentFiles.isNotEmpty()) {
                                        runCatching { firstGridItemFocusRequester.requestFocus() }
                                    } else {
                                        runCatching { multiSelectActionBarFocusRequester.requestFocus() }
                                    }
                                }
                            }
                        )

                        if (currentDir != null) {
                            TvHeaderButton(
                                label = "New Folder",
                                icon = Icons.Rounded.CreateNewFolder,
                                onMoveDown = { runCatching { internalStorageFocusRequester.requestFocus() } },
                                onClick = {
                                    newFolderInput = ""
                                    showNewFolderDialog = true
                                }
                            )
                        }

                        multiClipboard?.let { clip ->
                            val count = clip.files.size
                            val pasteLabel = if (clip.isCut) "Paste ($count items) [Move]" else "Paste ($count items) [Copy]"
                            val pasteFirstFileName = clip.files.firstOrNull()?.name
                            TvHeaderButton(
                                label = pasteLabel,
                                icon = Icons.Rounded.ContentPaste,
                                accentColor = Color(0xFF30D158),
                                onMoveDown = { runCatching { internalStorageFocusRequester.requestFocus() } },
                                onClick = {
                                    val target = currentDir
                                    if (target != null) {
                                        // Immediately rescue focus before Paste button disappears
                                        runCatching { headerActionFocusRequester.requestFocus() }
                                        scope.launch {
                                            val done = if (clip.isCut) {
                                                FileManagerHelper.batchMove(ctx, clip.files, target)
                                            } else {
                                                FileManagerHelper.batchCopy(ctx, clip.files, target)
                                            }
                                            statusMessage = if (clip.isCut) "Moved $done items" else "Copied $done items"
                                            multiClipboard = null
                                            loadDir(target, focusFirstItem = true)
                                            if (pasteFirstFileName != null) {
                                                delay(150)
                                                runCatching { highlightedFileFocusRequester.requestFocus() }
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
                                onMoveDown = { runCatching { internalStorageFocusRequester.requestFocus() } },
                                onClick = { FileManagerHelper.requestStoragePermission(ctx) }
                            )
                        }

                        TvHeaderButton(
                            label = "Close",
                            icon = Icons.Rounded.Close,
                            onMoveDown = { runCatching { internalStorageFocusRequester.requestFocus() } },
                            onClick = onClose
                        )
                    }
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

            Spacer(Modifier.height(16.dp))

            // ─── Quick Access Tabs Row (Categories & Drives) ───
            val categories = listOf(
                Pair("quickshare", "Quick Share"),
                Pair("downloads", "Downloads"),
                Pair("apks", "APKs"),
                Pair("videos", "Videos"),
                Pair("audio", "Music"),
                Pair("images", "Pictures"),
                Pair("documents", "Documents")
            )

            val categoryScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(categoryScrollState),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Root Internal Storage tab
                val root = drives.firstOrNull()?.path ?: File("/storage/emulated/0")
                val isRootSelected = currentDir?.absolutePath == root.absolutePath && activeCategory == null
                TvCategoryPill(
                    label = "Internal Storage",
                    icon = Icons.Rounded.Storage,
                    isSelected = isRootSelected,
                    modifier = Modifier.focusRequester(internalStorageFocusRequester),
                    onMoveDown = {
                        if (currentFiles.isNotEmpty()) {
                            runCatching { firstGridItemFocusRequester.requestFocus() }
                        }
                    },
                    onMoveUp = {
                        runCatching { headerActionFocusRequester.requestFocus() }
                    },
                    onClick = { loadDir(root) }
                )

                categories.forEach { (key, label) ->
                    val isCatSelected = activeCategory == key || (key == "quickshare" && currentDir?.name == "LeanTvQuickshare")
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
                        isSelected = isCatSelected,
                        accent = if (isQs) Color(0xFF0A84FF) else null,
                        onMoveDown = {
                            if (currentFiles.isNotEmpty()) {
                                runCatching { firstGridItemFocusRequester.requestFocus() }
                            }
                        },
                        onMoveUp = {
                            runCatching { headerActionFocusRequester.requestFocus() }
                        },
                        onClick = {
                            if (isQs) {
                                loadDir(FileManagerHelper.ensureQuickShareDir())
                            } else {
                                loadCategory(key)
                            }
                        }
                    )
                }

                // Connected USB / Removable drives
                drives.filter { it.isRemovable }.forEach { usb ->
                    val isUsbSelected = currentDir?.absolutePath == usb.path.absolutePath
                    TvCategoryPill(
                        label = usb.name,
                        icon = Icons.Rounded.Usb,
                        isSelected = isUsbSelected,
                        onMoveDown = {
                            if (currentFiles.isNotEmpty()) {
                                runCatching { firstGridItemFocusRequester.requestFocus() }
                            }
                        },
                        onMoveUp = {
                            runCatching { headerActionFocusRequester.requestFocus() }
                        },
                        onClick = { loadDir(usb.path) }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ─── Main Files Grid ───
            if (isLoading) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF0A84FF), strokeWidth = 3.dp)
                }
            } else if (currentFiles.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
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
                val hasParentCard = currentDir != null && drives.none { it.path.absolutePath == currentDir!!.absolutePath } && currentDir!!.parentFile != null

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Parent folder ".." card
                    if (hasParentCard) {
                        item {
                            TvParentFolderCard(
                                modifier = Modifier.focusRequester(firstGridItemFocusRequester),
                                onMoveUp = {
                                    if (isMultiSelectMode) {
                                        runCatching { multiSelectActionBarFocusRequester.requestFocus() }
                                    } else {
                                        runCatching { internalStorageFocusRequester.requestFocus() }
                                    }
                                },
                                onClick = { loadDir(currentDir!!.parentFile!!, focusFirstItem = true) }
                            )
                        }
                    }

                    itemsIndexed(currentFiles, key = { _, it -> it.file.absolutePath }) { index, item ->
                        val isHighlighted = highlightFile != null && item.name.equals(highlightFile, ignoreCase = true)
                        val isSelected = selectedFiles.contains(item.file)
                        val isFirstItem = (!hasParentCard && index == 0)
                        val isInTopRow = if (hasParentCard) index < 3 else index < 4

                        val cardModifier = when {
                            isHighlighted -> Modifier.focusRequester(highlightedFileFocusRequester)
                            isFirstItem -> Modifier.focusRequester(firstGridItemFocusRequester)
                            else -> Modifier
                        }

                        TvFileCard(
                            item = item,
                            isSelected = isSelected,
                            isMultiSelectMode = isMultiSelectMode,
                            isHighlighted = isHighlighted,
                            modifier = cardModifier,
                            onMoveUp = if (isInTopRow) {
                                {
                                    if (isMultiSelectMode) {
                                        runCatching { multiSelectActionBarFocusRequester.requestFocus() }
                                    } else {
                                        runCatching { internalStorageFocusRequester.requestFocus() }
                                    }
                                }
                            } else null,
                            onClick = {
                                if (isMultiSelectMode) {
                                    toggleSelect(item.file)
                                } else {
                                    val ext = item.extension.lowercase(Locale.ROOT)
                                    when {
                                        item.isDirectory -> loadDir(item.file, focusFirstItem = true)
                                        FileManagerHelper.isImage(ext) -> {
                                            val allImages = currentFiles
                                                .filter { !it.isDirectory && FileManagerHelper.isImage(it.extension) }
                                                .map { it.file }
                                            val idx = allImages.indexOfFirst { it.absolutePath == item.file.absolutePath }.coerceAtLeast(0)
                                            viewerImageFiles = allImages
                                            viewerImageIndex = idx
                                        }
                                        FileManagerHelper.isPdf(ext) -> {
                                            viewerPdfFile = item.file
                                        }
                                        FileManagerHelper.isAudio(ext) -> {
                                            playingAudioFile = item.file
                                        }
                                        FileManagerHelper.isText(ext) -> {
                                            viewerTextFile = item.file
                                        }
                                        else -> {
                                            val opened = FileManagerHelper.openFile(ctx, item.file)
                                            if (!opened) statusMessage = "No compatible app found to open ${item.name}"
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                if (isMultiSelectMode) {
                                    toggleSelect(item.file)
                                } else {
                                    actionFile = item
                                }
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
                    val ext = item.extension.lowercase(Locale.ROOT)
                    when {
                        item.isDirectory -> loadDir(item.file)
                        FileManagerHelper.isImage(ext) -> {
                            val allImages = currentFiles.filter { !it.isDirectory && FileManagerHelper.isImage(it.extension) }.map { it.file }
                            viewerImageFiles = allImages
                            viewerImageIndex = allImages.indexOfFirst { it.absolutePath == item.file.absolutePath }.coerceAtLeast(0)
                        }
                        FileManagerHelper.isPdf(ext) -> viewerPdfFile = item.file
                        FileManagerHelper.isAudio(ext) -> playingAudioFile = item.file
                        FileManagerHelper.isText(ext) -> viewerTextFile = item.file
                        else -> FileManagerHelper.openFile(ctx, item.file)
                    }
                },
                onQuickShare = {
                    actionFile = null
                    quickShareSendFiles = listOf(item.file)
                },
                onSelectMultiple = {
                    actionFile = null
                    isMultiSelectMode = true
                    selectedFiles = setOf(item.file)
                },
                onCopy = {
                    actionFile = null
                    multiClipboard = MultiClipboard(listOf(item.file), isCut = false)
                    statusMessage = "Copied '${item.name}' to clipboard. Navigate to target folder and click Paste."
                },
                onMove = {
                    actionFile = null
                    multiClipboard = MultiClipboard(listOf(item.file), isCut = true)
                    statusMessage = "Moved '${item.name}' to clipboard. Navigate to target folder and click Paste."
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
                                currentDir?.let { loadDir(it, focusFirstItem = true) } ?: activeCategory?.let { loadCategory(it, focusFirstItem = true) }
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
                                loadDir(currentDir!!, focusFirstItem = true)
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

        // ─── Single Delete Confirmation Dialog ───
        showDeleteDialog?.let { item ->
            TvConfirmDialog(
                title = "Delete ${if (item.isDirectory) "Folder" else "File"}?",
                message = "Are you sure you want to permanently delete '${item.name}'? This cannot be undone.",
                confirmLabel = "Delete",
                confirmColor = Color(0xFFFF453A),
                onConfirm = {
                    scope.launch {
                        val ok = FileManagerHelper.deleteFile(ctx, item.file)
                        if (ok) {
                            statusMessage = "'${item.name}' deleted"
                            currentDir?.let { loadDir(it, focusFirstItem = true) } ?: activeCategory?.let { loadCategory(it, focusFirstItem = true) }
                        } else {
                            statusMessage = "Delete failed"
                        }
                    }
                    showDeleteDialog = null
                },
                onDismiss = { showDeleteDialog = null }
            )
        }

        // ─── Batch Delete Confirmation Dialog ───
        if (showBatchDeleteDialog) {
            val count = selectedFiles.size
            val totalBytes = selectedFiles.sumOf { if (it.isDirectory) 0L else it.length() }
            val formattedSize = FileManagerHelper.formatFileSize(totalBytes)

            TvConfirmDialog(
                title = "Delete $count items?",
                message = "Are you sure you want to permanently delete $count selected items ($formattedSize)? This cannot be undone.",
                confirmLabel = "Delete All",
                confirmColor = Color(0xFFFF453A),
                onConfirm = {
                    showBatchDeleteDialog = false
                    val toDelete = selectedFiles.toList()
                    scope.launch {
                        val deleted = FileManagerHelper.batchDelete(ctx, toDelete)
                        statusMessage = "Deleted $deleted items"
                        selectedFiles = emptySet()
                        isMultiSelectMode = false
                        currentDir?.let { loadDir(it, focusFirstItem = true) } ?: activeCategory?.let { loadCategory(it, focusFirstItem = true) }
                    }
                },
                onDismiss = { showBatchDeleteDialog = false }
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

        // ─── Built-in Photo / Image Viewer ───
        viewerImageFiles?.let { files ->
            TvImageViewerModal(
                files = files,
                initialIndex = viewerImageIndex,
                onClose = { viewerImageFiles = null }
            )
        }

        // ─── Built-in PDF Viewer ───
        viewerPdfFile?.let { file ->
            TvPdfViewerModal(
                file = file,
                onClose = { viewerPdfFile = null }
            )
        }

        // ─── Built-in Audio Player ───
        playingAudioFile?.let { file ->
            TvAudioPlayerModal(
                file = file,
                onClose = { playingAudioFile = null }
            )
        }

        // ─── Built-in Text / Code Viewer ───
        viewerTextFile?.let { file ->
            TvTextViewerModal(
                file = file,
                onClose = { viewerTextFile = null }
            )
        }

        // ─── Quick Share Send to Phone Dialog ───
        quickShareSendFiles?.let { files ->
            TvQuickShareSendDialog(
                files = files,
                onDismiss = {
                    quickShareSendFiles = null
                }
            )
        }
    }
}

// ─── Sub-Composables: Multi-Select Action Bar ───

@Composable
private fun TvMultiSelectActionBar(
    selectedCount: Int,
    totalCount: Int,
    focusRequester: FocusRequester? = null,
    onSelectAll: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onQuickShare: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    onMoveDown: (() -> Unit)? = null
) {
    val autoFocus = rememberAutoFocus()
    val allSelected = totalCount > 0 && selectedCount == totalCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF1E2128))
            .border(1.dp, Color(0x330A84FF), RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0A84FF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$selectedCount",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Text(
                text = if (selectedCount == 0) "Select items" else "$selectedCount of $totalCount selected",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TvHeaderButton(
                label = if (allSelected) "Deselect All" else "Select All",
                icon = if (allSelected) Icons.Rounded.ClearAll else Icons.Rounded.SelectAll,
                modifier = Modifier.focusRequester(focusRequester ?: autoFocus),
                onMoveDown = onMoveDown,
                onClick = onSelectAll
            )

            if (selectedCount > 0) {
                TvHeaderButton(
                    label = "Copy",
                    icon = Icons.Rounded.ContentCopy,
                    onMoveDown = onMoveDown,
                    onClick = onCopy
                )
                TvHeaderButton(
                    label = "Move",
                    icon = Icons.Rounded.DriveFileMove,
                    onMoveDown = onMoveDown,
                    onClick = onMove
                )
                TvHeaderButton(
                    label = "Quick Share",
                    icon = Icons.Rounded.Bolt,
                    accentColor = Color(0xFF0A84FF),
                    onMoveDown = onMoveDown,
                    onClick = onQuickShare
                )
                TvHeaderButton(
                    label = "Delete",
                    icon = Icons.Rounded.Delete,
                    accentColor = Color(0xFFFF453A),
                    onMoveDown = onMoveDown,
                    onClick = onDelete
                )
            }

            TvHeaderButton(
                label = "Done",
                icon = Icons.Rounded.Check,
                onMoveDown = onMoveDown,
                onClick = onDone
            )
        }
    }
}

// ─── Sub-Composables: File Card ───

@Composable
private fun TvFileCard(
    item: FileItem,
    isSelected: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
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
        FileManagerHelper.isPdf(item.extension) -> Icons.Rounded.PictureAsPdf
        FileManagerHelper.isText(item.extension) -> Icons.Rounded.Subject
        else -> Icons.Rounded.Description
    }

    val iconTint = when {
        focused -> Color.Black
        item.isDirectory -> Color(0xFF2A85FF)
        item.extension == "apk" -> Color(0xFF30D158)
        FileManagerHelper.isVideo(item.extension) -> Color(0xFFFF9F0A)
        FileManagerHelper.isAudio(item.extension) -> Color(0xFFFF375F)
        FileManagerHelper.isImage(item.extension) -> Color(0xFFBF5AF2)
        FileManagerHelper.isPdf(item.extension) -> Color(0xFFFF453A)
        FileManagerHelper.isText(item.extension) -> Color(0xFF64D2FF)
        else -> Color(0xFF64D2FF)
    }

    val borderColor = when {
        focused -> Color.White
        isSelected -> Color(0xFF0A84FF)
        isHighlighted -> Color(0xFF0A84FF)
        else -> Color(0x1AFFFFFF)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 16.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(
                if (focused) Color.White
                else if (isSelected) Color(0x330A84FF)
                else if (isHighlighted) Color(0x260A84FF)
                else Color(0x12FFFFFF)
            )
            .border(if (focused) 2.dp else if (isSelected || isHighlighted) 1.5.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .focusable()
            .onPreviewKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                val center = k.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || k.keyCode == KeyEvent.KEYCODE_ENTER || k.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
                val menu = k.keyCode == KeyEvent.KEYCODE_MENU || k.keyCode == KeyEvent.KEYCODE_BUTTON_Y
                when {
                    k.action == KeyEvent.ACTION_DOWN && k.keyCode == KeyEvent.KEYCODE_DPAD_UP && onMoveUp != null -> {
                        onMoveUp()
                        true
                    }
                    menu && k.action == KeyEvent.ACTION_DOWN -> {
                        TvSound.playClick(view)
                        onLongClick()
                        true
                    }
                    center && k.action == KeyEvent.ACTION_DOWN -> {
                        if (isMultiSelectMode) {
                            TvSound.playClick(view)
                            onClick()
                            true
                        } else {
                            if (k.repeatCount == 0) {
                                isLongPressHandled = false
                                longPressJob?.cancel()
                                longPressJob = scope.launch {
                                    delay(500)
                                    isLongPressHandled = true
                                    TvSound.playClick(view)
                                    onLongClick()
                                }
                            }
                            true
                        }
                    }
                    center && k.action == KeyEvent.ACTION_UP -> {
                        if (!isMultiSelectMode) {
                            val wasLongPress = isLongPressHandled
                            longPressJob?.cancel()
                            longPressJob = null
                            isLongPressHandled = false
                            if (!wasLongPress) {
                                TvSound.playClick(view)
                                onClick()
                            }
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

        // Selection checkbox indicator in multi-select mode
        if (isMultiSelectMode || isSelected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFF0A84FF) else Color(0x22FFFFFF))
                    .border(1.5.dp, if (isSelected) Color.White else Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ─── Modal Context Menu: Single File Actions ───

@Composable
private fun TvFileActionModal(
    item: FileItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onQuickShare: () -> Unit,
    onSelectMultiple: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                label = if (item.isDirectory) "Open Folder" else if (item.extension == "apk") "Install APK" else "Open / View",
                icon = if (item.extension == "apk") Icons.Rounded.Download else Icons.Rounded.Launch,
                modifier = Modifier.focusRequester(autoFocus),
                onClick = onOpen
            )
            TvModalActionButton(
                label = "Quick Share to Phone",
                icon = Icons.Rounded.Bolt,
                accentColor = Color(0xFF0A84FF),
                onClick = onQuickShare
            )
            TvModalActionButton(
                label = "Select Multiple",
                icon = Icons.Rounded.Checklist,
                onClick = onSelectMultiple
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

// ─── Built-in Photo / Image Viewer ───

@Composable
private fun TvImageViewerModal(
    files: List<File>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))) }
    val currentFile = files.getOrNull(currentIndex)
    val bitmap = remember(currentFile) {
        currentFile?.let { FileManagerHelper.decodeBitmap(it) }
    }
    val autoFocus = rememberAutoFocus()

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .modalTrap()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (ev.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (currentIndex > 0) {
                                currentIndex--
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (currentIndex < files.size - 1) {
                                currentIndex++
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        // Image centered
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = currentFile?.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 56.dp)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.BrokenImage, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                Text("Unable to load image", color = Color.White, fontSize = 18.sp)
            }
        }

        // Top Overlay: file name & counter
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = currentFile?.name ?: "",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${currentIndex + 1} of ${files.size} • ${FileManagerHelper.formatFileSize(currentFile?.length() ?: 0L)}",
                    color = Color(0xCCFFFFFF),
                    fontSize = 14.sp
                )
            }

            TvHeaderButton(
                label = "Close",
                icon = Icons.Rounded.Close,
                modifier = Modifier.focusRequester(autoFocus),
                onClick = onClose
            )
        }

        // Bottom navigation buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentIndex > 0) {
                TvHeaderButton(
                    label = "Previous",
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = { currentIndex-- }
                )
            }
            if (currentIndex < files.size - 1) {
                TvHeaderButton(
                    label = "Next",
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = { currentIndex++ }
                )
            }
        }
    }
}

// ─── Built-in Native PDF Viewer ───

@Composable
private fun TvPdfViewerModal(
    file: File,
    onClose: () -> Unit
) {
    var pageIndex by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val autoFocus = rememberAutoFocus()

    LaunchedEffect(file, pageIndex) {
        val (bmp, count) = FileManagerHelper.renderPdfPage(file, pageIndex, 1080)
        pageBitmap = bmp
        if (count > 0) totalPages = count
    }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1014))
            .modalTrap()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (ev.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (pageIndex > 0) {
                                pageIndex--
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (pageIndex < totalPages - 1) {
                                pageIndex++
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        // PDF page centered
        if (pageBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.85f)
                    .aspectRatio(pageBitmap!!.width.toFloat() / pageBitmap!!.height.toFloat())
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .shadow(16.dp)
            ) {
                Image(
                    bitmap = pageBitmap!!.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            CircularProgressIndicator(color = Color(0xFF0A84FF))
        }

        // Top Header
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(file.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Page ${pageIndex + 1} of $totalPages", color = Color(0xFF0A84FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            TvHeaderButton(label = "Close", icon = Icons.Rounded.Close, modifier = Modifier.focusRequester(autoFocus), onClick = onClose)
        }

        // Bottom Page Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pageIndex > 0) {
                TvHeaderButton(
                    label = "Previous Page",
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = { pageIndex-- }
                )
            }
            if (pageIndex < totalPages - 1) {
                TvHeaderButton(
                    label = "Next Page",
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    onClick = { pageIndex++ }
                )
            }
        }
    }
}

// ─── Built-in Audio Player ───

@Composable
private fun TvAudioPlayerModal(
    file: File,
    onClose: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(true) }
    var currentPos by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    val autoFocus = rememberAutoFocus()

    val mp = remember {
        MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(mp) {
        duration = try { mp.duration } catch (_: Exception) { 0 }
        while (true) {
            try {
                if (mp.isPlaying) {
                    currentPos = mp.currentPosition
                    isPlaying = true
                } else {
                    isPlaying = false
                }
            } catch (_: Exception) {}
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mp.isPlaying) mp.stop()
                mp.release()
            } catch (_: Exception) {}
        }
    }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onClose)
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1E2128))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFFF375F).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFFF375F),
                    modifier = Modifier.size(54.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = file.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = FileManagerHelper.formatFileSize(file.length()),
                    color = Color(0x80FFFFFF),
                    fontSize = 14.sp
                )
            }

            // Progress bar
            val progress = if (duration > 0) currentPos.toFloat() / duration else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFFF375F),
                trackColor = Color(0x33FFFFFF)
            )

            fun formatTime(ms: Int): String {
                val sec = (ms / 1000) % 60
                val min = (ms / (1000 * 60)) % 60
                return "%d:%02d".format(min, sec)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(currentPos), color = Color(0x99FFFFFF), fontSize = 13.sp)
                Text(formatTime(duration), color = Color(0x99FFFFFF), fontSize = 13.sp)
            }

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvHeaderButton(
                    label = "-10s",
                    icon = Icons.Rounded.Replay10,
                    onClick = {
                        try {
                            val newPos = (mp.currentPosition - 10000).coerceAtLeast(0)
                            mp.seekTo(newPos)
                        } catch (_: Exception) {}
                    }
                )

                Focusable(
                    onClick = {
                        try {
                            if (mp.isPlaying) {
                                mp.pause()
                                isPlaying = false
                            } else {
                                mp.start()
                                isPlaying = true
                            }
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.focusRequester(autoFocus),
                    shape = CircleShape,
                    idle = Color(0xFFFF375F)
                ) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                TvHeaderButton(
                    label = "+10s",
                    icon = Icons.Rounded.Forward10,
                    onClick = {
                        try {
                            val newPos = (mp.currentPosition + 10000).coerceAtMost(duration)
                            mp.seekTo(newPos)
                        } catch (_: Exception) {}
                    }
                )
            }

            TvHeaderButton(
                label = "Close Player",
                icon = Icons.Rounded.Close,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClose
            )
        }
    }
}

// ─── Built-in Text / Code Viewer ───

@Composable
private fun TvTextViewerModal(
    file: File,
    onClose: () -> Unit
) {
    val autoFocus = rememberAutoFocus()
    val textContent = remember(file) { FileManagerHelper.readText(file) }
    val scrollState = rememberScrollState()

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onClose)
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(820.dp)
                .height(600.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF1E2128))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                .padding(26.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(file.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(FileManagerHelper.formatFileSize(file.length()), color = Color(0x80FFFFFF), fontSize = 13.sp)
                }
                TvHeaderButton(label = "Close", icon = Icons.Rounded.Close, modifier = Modifier.focusRequester(autoFocus), onClick = onClose)
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF121418))
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = textContent,
                    color = Color(0xFFE2E8F0),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

// ─── Sub-Composables: Buttons, Category Pills & Up-Level Card ───

@Composable
private fun TvCategoryPill(
    label: String,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    isSelected: Boolean = false,
    accent: Color? = null,
    modifier: Modifier = Modifier,
    onMoveDown: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, spring(dampingRatio = 0.8f, stiffness = 400f), label = "catScale")
    val shape = RoundedCornerShape(50)

    val bg = when {
        focused -> Color.White
        isSelected -> accent?.copy(alpha = 0.35f) ?: Color(0x330A84FF)
        else -> Color(0x1AFFFFFF)
    }
    val fg = when {
        focused -> Color.Black
        isSelected -> accent ?: Color(0xFF64D2FF)
        else -> Color(0xCCFFFFFF)
    }
    val borderCol = when {
        focused -> Color.White
        isSelected -> accent ?: Color(0xFF0A84FF)
        else -> Color(0x24FFFFFF)
    }

    Row(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 16.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(bg)
            .border(
                if (focused) 2.dp else if (isSelected) 1.5.dp else 0.5.dp,
                borderCol,
                shape
            )
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .onPreviewKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                when {
                    k.action == KeyEvent.ACTION_DOWN && k.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && onMoveDown != null -> {
                        onMoveDown()
                        true
                    }
                    k.action == KeyEvent.ACTION_DOWN && k.keyCode == KeyEvent.KEYCODE_DPAD_UP && onMoveUp != null -> {
                        onMoveUp()
                        true
                    }
                    else -> false
                }
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
    modifier: Modifier = Modifier,
    onMoveDown: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = modifier
            .height(42.dp)
            .clip(shape)
            .background(if (focused) Color.White else Color(0x1FFFFFFF))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0x2EFFFFFF), shape)
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .onPreviewKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                if (k.action == KeyEvent.ACTION_DOWN && k.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && onMoveDown != null) {
                    onMoveDown()
                    true
                } else false
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
private fun TvParentFolderCard(
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(shape)
            .background(if (focused) Color.White else Color(0x14FFFFFF))
            .border(if (focused) 2.dp else 1.5.dp, if (focused) Color.White else Color(0x24FFFFFF), shape)
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .onPreviewKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                if (k.action == KeyEvent.ACTION_DOWN && k.keyCode == KeyEvent.KEYCODE_DPAD_UP && onMoveUp != null) {
                    onMoveUp()
                    true
                } else false
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
            .clickable(onClick = onDismiss)
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

            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onValueChange(it)
                },
                modifier = Modifier.fillMaxWidth().focusRequester(autoFocus),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF0A84FF),
                    unfocusedBorderColor = Color(0x33FFFFFF)
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
                    idle = Color(0x260A84FF)
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

// ─── Native Quick Share Send to Phone Dialog ───

@Composable
private fun TvQuickShareSendDialog(
    files: List<File>,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoFocus = rememberAutoFocus()
    val firstPeerFocus = remember { FocusRequester() }

    val wifiName = remember { QuickShareHost.getNetworkLabel(ctx) }
    val totalBytes = remember(files) { files.sumOf { if (it.isDirectory) 0L else it.length() } }
    val formattedTotalSize = remember(totalBytes) { FileManagerHelper.formatFileSize(totalBytes) }

    val peers = remember { mutableStateMapOf<String, NearbyPeer>() }
    var isBrowsing by remember { mutableStateOf(true) }
    var targetPeer by remember { mutableStateOf<NearbyPeer?>(null) }
    var connectionState by remember { mutableStateOf<OutboundConnectionState>(OutboundConnectionState.Idle) }
    var activeConnection by remember { mutableStateOf<OutboundConnection?>(null) }
    var scanIteration by remember { mutableIntStateOf(0) }

    // Intercept Back: cancel active transfer or dismiss dialog
    BackHandler(enabled = true) {
        if (targetPeer != null) {
            activeConnection?.cancel()
            targetPeer = null
            activeConnection = null
            connectionState = OutboundConnectionState.Idle
        } else {
            onDismiss()
        }
    }

    // Auto-focus first discovered peer when available
    LaunchedEffect(peers.size, targetPeer) {
        if (targetPeer == null && peers.isNotEmpty()) {
            delay(120)
            runCatching { firstPeerFocus.requestFocus() }
        }
    }

    // Continuous peer discovery (LAN mDNS + BLE fast advertisements)
    LaunchedEffect(scanIteration) {
        peers.clear()
        isBrowsing = true
        val wifi = ctx.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager
        val mLock = wifi?.createMulticastLock("LeanTvQuickShareBrowse")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            val discovery = NearbyPeerDiscovery(ctx)
            discovery.browse().collect { event ->
                when (event) {
                    is NearbyPeerEvent.Resolved -> {
                        peers[event.peer.stableId] = event.peer
                    }
                    is NearbyPeerEvent.Lost -> {
                        peers.remove(event.stableId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("QuickShareSend", "Peer discovery error: ${e.message}")
        } finally {
            isBrowsing = false
            try { mLock?.release() } catch (_: Exception) {}
        }
    }

    // Outbound Transfer Lifecycle
    LaunchedEffect(targetPeer) {
        val peer = targetPeer ?: return@LaunchedEffect
        connectionState = OutboundConnectionState.Connecting

        val wifi = ctx.getSystemService(android.content.Context.WIFI_SERVICE) as? WifiManager
        val mLock = wifi?.createMulticastLock("LeanTvQuickShareSend")?.apply {
            setReferenceCounted(false)
            acquire()
        }

        withContext(Dispatchers.IO) {
            try {
                val route = peer.preferredRoute()
                if (route == null) {
                    withContext(Dispatchers.Main) {
                        connectionState = OutboundConnectionState.Failed(
                            "No connectable route found for ${peer.endpointInfo?.deviceName ?: "device"}"
                        )
                    }
                    return@withContext
                }

                // Exclude BLE_L2CAP on TV to avoid hardware stalls; include Wi-Fi Direct and Hotspot for superfast speeds
                val providers = MediumRegistries.defaultProviders(ctx).filterNot {
                    it.medium == dev.bluehouse.bada.protocol.medium.Medium.BLE_L2CAP
                }
                val mediumRegistry = MediumRegistry(providers = providers)
                val wifiCaps = AndroidWifiCapabilities.read(ctx)

                val friendlyName = try {
                    android.provider.Settings.Global.getString(ctx.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
                        ?: android.provider.Settings.Secure.getString(ctx.contentResolver, "bluetooth_name")
                        ?: android.os.Build.MODEL
                } catch (_: Exception) {
                    android.os.Build.MODEL
                }

                val myEndpointInfo = EndpointInfo(
                    version = 1,
                    hidden = false,
                    deviceType = DeviceType.LAPTOP,
                    reserved = false,
                    metadata = ByteArray(EndpointInfo.METADATA_LEN).also { SecureRandom().nextBytes(it) },
                    deviceName = friendlyName.take(EndpointInfo.MAX_NAME_LEN),
                ).serialize()

                val epId = peer.endpointId ?: "TV01"

                val conn = when (route) {
                    is NearbyPeerRoute.Lan -> {
                        OutboundConnection(
                            targetAddress = route.address,
                            port = route.port,
                            endpointId = epId,
                            endpointInfo = myEndpointInfo,
                            mediumRegistry = mediumRegistry,
                            logger = { Log.d("QuickShareSend", "BadaOutbound: $it") },
                            wifiCapabilities = wifiCaps
                        )
                    }
                    is NearbyPeerRoute.BluetoothClassic -> {
                        val client = BluetoothClassicBootstrapClient(ctx)
                        val transport = client.connect(route.macAddress)
                            ?: throw IOException("Could not connect via Bluetooth Classic")
                        OutboundConnection(
                            transport = transport,
                            endpointId = epId,
                            endpointInfo = myEndpointInfo,
                            mediumRegistry = mediumRegistry,
                            logger = { Log.d("QuickShareSend", "BadaOutbound: $it") },
                            wifiCapabilities = wifiCaps
                        )
                    }
                    is NearbyPeerRoute.BleL2cap -> {
                        val client = BleL2capInitialControlClient(ctx)
                        val transport = client.connect(route.macAddress, route.psm)
                            ?: throw IOException("Could not connect via BLE L2CAP")
                        OutboundConnection(
                            transport = transport,
                            endpointId = epId,
                            endpointInfo = myEndpointInfo,
                            mediumRegistry = mediumRegistry,
                            logger = { Log.d("QuickShareSend", "BadaOutbound: $it") },
                            wifiCapabilities = wifiCaps
                        )
                    }
                    is NearbyPeerRoute.BleGatt -> {
                        val client = BleGattInitialControlClient(ctx)
                        val transport = client.connect(route.macAddress)
                            ?: throw IOException("Could not connect via BLE GATT")
                        OutboundConnection(
                            transport = transport,
                            endpointId = epId,
                            endpointInfo = myEndpointInfo,
                            mediumRegistry = mediumRegistry,
                            logger = { Log.d("QuickShareSend", "BadaOutbound: $it") },
                            wifiCapabilities = wifiCaps
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    activeConnection = conn
                }

                val stateJob = launch {
                    conn.state.collect { s ->
                        withContext(Dispatchers.Main) {
                            connectionState = s
                        }
                    }
                }

                // Flatten files in case directories were passed
                val flattened = mutableListOf<Pair<File, String>>()
                fun collectFiles(f: File, relDir: String) {
                    if (f.isDirectory) {
                        val ch = f.listFiles() ?: return
                        val p = if (relDir.isEmpty()) f.name else "$relDir/${f.name}"
                        for (c in ch) collectFiles(c, p)
                    } else if (f.isFile && f.length() > 0) {
                        flattened.add(f to relDir)
                    }
                }
                for (f in files) collectFiles(f, "")

                val sources = flattened.mapIndexed { idx, (f, rel) ->
                    FileSource(
                        name = f.name,
                        size = f.length(),
                        mimeType = FileManagerHelper.getMimeType(f.extension.lowercase(Locale.ROOT)),
                        lastModifiedTimestampMillis = f.lastModified(),
                        payloadId = kotlin.math.abs(SecureRandom().nextLong()).coerceAtLeast(1L) + idx,
                        parentFolder = rel,
                        open = { java.io.FileInputStream(f).channel }
                    )
                }

                val res = conn.run(sources)
                stateJob.cancel()
                Log.i("QuickShareSend", "Transfer finished with: $res")
            } catch (e: Exception) {
                Log.e("QuickShareSend", "Transfer error", e)
                withContext(Dispatchers.Main) {
                    connectionState = OutboundConnectionState.Failed(e.message ?: e::class.java.simpleName)
                }
            } finally {
                try { mLock?.release() } catch (_: Exception) {}
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF16181E))
                .border(1.5.dp, Color(0x330A84FF), RoundedCornerShape(32.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF0051C7)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "QUICK SHARE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF388AF6),
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = if (targetPeer != null) {
                                targetPeer?.endpointInfo?.deviceName ?: "Sending..."
                            } else {
                                "Send to Nearby Devices"
                            },
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // File count & size badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x240A84FF))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${files.size} ${if (files.size == 1) "file" else "files"} • $formattedTotalSize",
                        color = Color(0xFF64D2FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (targetPeer == null) {
                // ─── Device Selection View ───

                // Connectivity info banner (explains Wi-Fi / Hotspot & Direct support)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x1F0A84FF))
                        .border(1.dp, Color(0x4D0A84FF), RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0x330A84FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF64D2FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Wi-Fi & Direct High-Speed Transfer",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Keep phone unlocked with Quick Share set to 'Everyone'. Works on same Wi-Fi or directly via Wi-Fi Direct.",
                                color = Color(0xCC64D2FF),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                if (peers.isEmpty()) {
                    // Scanning state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x0FFFFFFF))
                            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0x240A84FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = Color(0xFF64D2FF),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Looking for nearby devices...",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Swipe down your phone's Quick Settings and tap 'Quick Share' to make it visible.",
                            fontSize = 13.sp,
                            color = Color(0xFF8E8E93),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Discovered devices list
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "AVAILABLE RECIPIENTS (${peers.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64D2FF),
                            letterSpacing = 1.sp
                        )

                        val peerList = peers.values.toList()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            peerList.forEachIndexed { index, peer ->
                                val deviceName = peer.endpointInfo?.deviceName ?: "Nearby Phone"
                                val deviceType = peer.endpointInfo?.deviceType
                                val deviceIcon = when (deviceType) {
                                    DeviceType.PHONE, DeviceType.FOLDABLE -> Icons.Rounded.Smartphone
                                    DeviceType.TABLET -> Icons.Rounded.Tablet
                                    DeviceType.LAPTOP -> Icons.Rounded.Laptop
                                    else -> Icons.Rounded.Devices
                                }
                                val isFirst = (index == 0)

                                Focusable(
                                    onClick = {
                                        targetPeer = peer
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (isFirst) Modifier.focusRequester(firstPeerFocus) else Modifier),
                                    shape = RoundedCornerShape(18.dp),
                                    idle = Color(0x14FFFFFF),
                                    scaleOnFocus = 1.03f
                                ) { isFocused ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 18.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isFocused) Color(0xFF0A84FF) else Color(0x260A84FF)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    deviceIcon,
                                                    contentDescription = null,
                                                    tint = if (isFocused) Color.White else Color(0xFF64D2FF),
                                                    modifier = Modifier.size(26.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(16.dp))
                                            Column {
                                                Text(
                                                    text = deviceName,
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = "Quick Share • Tap to send",
                                                    fontSize = 13.sp,
                                                    color = if (isFocused) Color(0xCCFFFFFF) else Color(0xFF8E8E93)
                                                )
                                            }
                                        }

                                        Icon(
                                            Icons.AutoMirrored.Rounded.ArrowForward,
                                            contentDescription = null,
                                            tint = if (isFocused) Color.White else Color(0x66FFFFFF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Action buttons (Rescan & Close)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Focusable(
                        onClick = { scanIteration++ },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        idle = Color(0x1AFFFFFF)
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Rescan", style = Ty.Button.copy(color = Color.White))
                        }
                    }

                    Focusable(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .then(if (peers.isEmpty()) Modifier.focusRequester(autoFocus) else Modifier),
                        shape = RoundedCornerShape(50),
                        idle = Color(0x1AFFFFFF)
                    ) { isFocused ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text("Close", style = Ty.Button.copy(color = Color.White))
                        }
                    }
                }
            } else {
                // ─── Active In-Flight Transfer View ───
                val peerName = targetPeer?.endpointInfo?.deviceName ?: "Recipient"

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(22.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    when (val s = connectionState) {
                        is OutboundConnectionState.Idle,
                        is OutboundConnectionState.Connecting,
                        is OutboundConnectionState.Handshaking -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(52.dp),
                                color = Color(0xFF0A84FF),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "Connecting to $peerName...",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Establishing secure Quick Share connection",
                                fontSize = 14.sp,
                                color = Color(0xFF8E8E93)
                            )
                        }

                        is OutboundConnectionState.AwaitingRemoteAcceptance -> {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x260A84FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.TouchApp,
                                    contentDescription = null,
                                    tint = Color(0xFF64D2FF),
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "Check your phone",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "A Quick Share request has popped up on $peerName.\nTap Accept on your phone to start the transfer.",
                                fontSize = 15.sp,
                                color = Color(0xFFE5E5EA),
                                textAlign = TextAlign.Center,
                                lineHeight = 22.sp
                            )

                            if (s.pin.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0x330A84FF))
                                        .border(1.dp, Color(0x660A84FF), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "Verification PIN: ${s.pin}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF64D2FF)
                                    )
                                }
                            }
                        }

                        is OutboundConnectionState.Sending -> {
                            val fraction = if (s.progress.totalSize > 0L) {
                                (s.progress.bytesTransferred.toFloat() / s.progress.totalSize.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val percent = (fraction * 100).toInt()

                            Text(
                                text = "Sending to $peerName...",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = Color(0xFF0A84FF),
                                trackColor = Color(0x33FFFFFF)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${FileManagerHelper.formatFileSize(s.progress.bytesTransferred)} / $formattedTotalSize ($percent%)",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${FileManagerHelper.formatFileSize(s.progress.bytesPerSecond)}/s",
                                    fontSize = 14.sp,
                                    color = Color(0xFF64D2FF),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        is OutboundConnectionState.Completed -> {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF30D158),
                                modifier = Modifier.size(60.dp)
                            )

                            Text(
                                text = "Sent successfully!",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "${files.size} ${if (files.size == 1) "file" else "files"} ($formattedTotalSize) delivered to $peerName.",
                                fontSize = 14.sp,
                                color = Color(0xFF8E8E93),
                                textAlign = TextAlign.Center
                            )
                        }

                        is OutboundConnectionState.Rejected -> {
                            Icon(
                                Icons.Rounded.Cancel,
                                contentDescription = null,
                                tint = Color(0xFFFF453A),
                                modifier = Modifier.size(60.dp)
                            )

                            Text(
                                text = "Transfer Declined",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "The transfer request was declined on $peerName.",
                                fontSize = 14.sp,
                                color = Color(0xFF8E8E93)
                            )
                        }

                        is OutboundConnectionState.Cancelled -> {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(60.dp)
                            )

                            Text(
                                text = "Transfer Cancelled",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        is OutboundConnectionState.Failed -> {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9F0A),
                                modifier = Modifier.size(60.dp)
                            )

                            Text(
                                text = "Transfer Failed",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = s.reason,
                                fontSize = 14.sp,
                                color = Color(0xFF8E8E93),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Bottom control button
                val isFinished = connectionState is OutboundConnectionState.Completed ||
                        connectionState is OutboundConnectionState.Rejected ||
                        connectionState is OutboundConnectionState.Cancelled ||
                        connectionState is OutboundConnectionState.Failed

                Focusable(
                    onClick = {
                        if (isFinished) {
                            if (connectionState is OutboundConnectionState.Completed) {
                                onDismiss()
                            } else {
                                // Reset to device picker
                                targetPeer = null
                                connectionState = OutboundConnectionState.Idle
                            }
                        } else {
                            // Cancel in-flight transfer
                            activeConnection?.cancel()
                            targetPeer = null
                            connectionState = OutboundConnectionState.Idle
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(autoFocus),
                    shape = RoundedCornerShape(50),
                    idle = if (connectionState is OutboundConnectionState.Completed) Color(0xFF0A84FF) else Color(0x26FFFFFF)
                ) { isFocused ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (connectionState is OutboundConnectionState.Completed) "Done"
                            else if (isFinished) "Back to Devices"
                            else "Cancel",
                            style = Ty.Button.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
