package com.leantv.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.leantv.app.adb.AdbClient
import com.leantv.app.data.AppGroup
import com.leantv.app.data.Journal
import com.leantv.app.data.Scanner
import com.leantv.app.data.DebloatResult
import com.leantv.app.data.SystemDebloater
import com.leantv.app.storage.AppStorageDetail
import com.leantv.app.storage.StorageFileItem
import com.leantv.app.storage.StorageHelper
import com.leantv.app.storage.StorageOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StorageCategory(val title: String) {
    APPS("Installed Apps"),
    VIDEOS("Videos & Movies"),
    IMAGES("Images & Photos"),
    AUDIO("Audio & Music"),
    APKS("APK Installation Files")
}

/**
 * TV Remote-optimized Storage Manager.
 *
 * Requirements:
 * - Shows high-level storage overview at a glance: "Used X GB of Y GB (Z GB Free)".
 * - Multi-color proportional gauge bar and breakdown by category (Apps, Videos, Images, Audio, APKs).
 * - Clicking any category allows viewing and deleting files/apps to reclaim storage.
 * - 100% remote-control optimized: natural D-Pad focus navigation, Back key handling,
 *   and safe delete confirmation dialogs.
 */
@Composable
fun StorageManagerScreen(
    adbClient: AdbClient?,
    journal: Journal,
    scanner: Scanner,
    initialCategory: StorageCategory? = null,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (adbClient?.isConnected == false) {
            adbClient.connect()
        }
    }

    var overview by remember { mutableStateOf<StorageOverview?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Navigation state
    var selectedCategory by remember { mutableStateOf(initialCategory) }

    // Apps selection state (inside APPS category)
    var selectedApp by remember { mutableStateOf<AppStorageDetail?>(null) }
    var appIcons by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // File deletion confirmation dialog state
    var fileToDelete by remember { mutableStateOf<StorageFileItem?>(null) }

    val overviewFocusRequester = remember { FocusRequester() }
    val categoryFocusRequester = remember { FocusRequester() }
    val dialogFocusRequester = remember { FocusRequester() }

    fun refresh() {
        scope.launch {
            isLoading = true
            val o = StorageHelper.getStorageOverview(ctx, journal)
            overview = o
            if (selectedApp == null || o.appDetails.none { it.packageName == selectedApp?.packageName }) {
                selectedApp = o.appDetails.firstOrNull()
            }
            isLoading = false

            // Preload app icons asynchronously
            withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                val icons = mutableMapOf<String, Bitmap>()
                for (app in o.appDetails.take(40)) {
                    try {
                        val d = pm.getApplicationIcon(app.packageName)
                        icons[app.packageName] = d.toBitmap(96, 96)
                    } catch (_: Exception) {}
                }
                appIcons = icons
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    // Auto-focus management
    LaunchedEffect(selectedCategory, isLoading) {
        if (!isLoading && overview != null) {
            delay(120)
            if (selectedCategory != null) {
                runCatching { categoryFocusRequester.requestFocus() }
            } else {
                runCatching { overviewFocusRequester.requestFocus() }
            }
        }
    }

    LaunchedEffect(fileToDelete) {
        if (fileToDelete != null) {
            delay(120)
            runCatching { dialogFocusRequester.requestFocus() }
        }
    }

    // HomeActivity swallows Back and calls the ViewModel, so we register here (see StorageCommon.kt).
    StorageBackHandler {
        when {
            fileToDelete != null -> {
                fileToDelete = null
                true
            }
            selectedCategory != null -> {
                selectedCategory = null
                refresh()
                true
            }
            else -> false
        }
    }

    BackHandler(enabled = true) {
        when {
            fileToDelete != null -> {
                fileToDelete = null
            }
            selectedCategory != null -> {
                selectedCategory = null
            }
            else -> {
                onClose()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 48.dp, vertical = 28.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK
                ) {
                    when {
                        // Apps + file views handle Back themselves (viewer -> side panel -> list -> storage)
                        selectedCategory != null -> false
                        fileToDelete != null -> {
                            fileToDelete = null
                            true
                        }
                        selectedCategory != null -> {
                            selectedCategory = null
                            true
                        }
                        else -> {
                            onClose()
                            true
                        }
                    }
                } else false
            }
    ) {
        val currentOverview = overview

        if (currentOverview == null || isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PearTvLoading("Analyzing TV storage...")
            }
        } else {
            when (selectedCategory) {
                null -> {
                    // ══════════════════════════════════════════════════════════════
                    // 1. OVERVIEW SCREEN: High-level Total Storage & Categories
                    // ══════════════════════════════════════════════════════════════
                    StorageOverviewView(
                        overview = currentOverview,
                        statusMessage = statusMessage,
                        autoFocus = overviewFocusRequester,
                        onSelectCategory = { cat -> selectedCategory = cat },
                        onCleanCache = {
                            scope.launch {
                                val reclaimed = StorageHelper.clearAllAppCaches(ctx, adbClient)
                                statusMessage = "Cleaned ${StorageHelper.formatBytes(reclaimed)} of cache!"
                                refresh()
                            }
                        },
                        onClose = onClose
                    )
                }

                StorageCategory.APPS -> {
                    // ══════════════════════════════════════════════════════════════
                    // 2. APPS MANAGEMENT VIEW (Clear Cache / Data / Uninstall)
                    // ══════════════════════════════════════════════════════════════
                    StorageAppsView(
                        overview = currentOverview,
                        adbClient = adbClient,
                        journal = journal,
                        scanner = scanner,
                        initialGroup = if (initialCategory == StorageCategory.APPS) AppGroup.BLOAT else AppGroup.INSTALLED,
                        onBack = {
                            selectedCategory = null
                            refresh()
                        }
                    )
                }

                StorageCategory.VIDEOS,
                StorageCategory.IMAGES,
                StorageCategory.AUDIO,
                StorageCategory.APKS -> {
                    // ══════════════════════════════════════════════════════════════
                    // 3. FILE CATEGORY VIEW (Inspect & Delete Files)
                    // ══════════════════════════════════════════════════════════════
                    val files = when (selectedCategory) {
                        StorageCategory.VIDEOS -> currentOverview.videoFiles
                        StorageCategory.IMAGES -> currentOverview.imageFiles
                        StorageCategory.AUDIO -> currentOverview.audioFiles
                        StorageCategory.APKS -> currentOverview.apkFiles
                        else -> emptyList()
                    }
                    val catTitle = when (selectedCategory) {
                        StorageCategory.VIDEOS -> "Videos & Movies"
                        StorageCategory.IMAGES -> "Images & Photos"
                        StorageCategory.AUDIO -> "Audio & Music"
                        StorageCategory.APKS -> "APK Installers"
                        else -> ""
                    }
                    val catTotal = when (selectedCategory) {
                        StorageCategory.VIDEOS -> currentOverview.videosBytes
                        StorageCategory.IMAGES -> currentOverview.imagesBytes
                        StorageCategory.AUDIO -> currentOverview.audioBytes
                        StorageCategory.APKS -> currentOverview.apksBytes
                        else -> 0L
                    }

                    StorageFilesView(
                        category = selectedCategory!!,
                        title = catTitle,
                        files = files,
                        onBack = {
                            selectedCategory = null
                            refresh()
                        }
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // DELETE FILE CONFIRMATION MODAL
        // ══════════════════════════════════════════════════════════════════════
        fileToDelete?.let { item ->
            DeleteFileModal(
                fileItem = item,
                autoFocus = dialogFocusRequester,
                onConfirm = {
                    scope.launch {
                        val ok = StorageHelper.deleteFile(ctx, item.file)
                        if (ok) {
                            statusMessage = "Deleted ${item.name} (${StorageHelper.formatBytes(item.size)})"
                            fileToDelete = null
                            refresh()
                        } else {
                            statusMessage = "Unable to delete file: ${item.name}"
                            fileToDelete = null
                        }
                    }
                },
                onDismiss = { fileToDelete = null }
            )
        }
    }
}

/**
 * Main Overview View: Total Storage Gauge, Progress Bar, and Category Cards.
 */
@Composable
private fun StorageOverviewView(
    overview: StorageOverview,
    statusMessage: String?,
    autoFocus: FocusRequester,
    onSelectCategory: (StorageCategory) -> Unit,
    onCleanCache: () -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val cleanCacheFocus = remember { FocusRequester() }
    val backHomeFocus = remember { FocusRequester() }
    val appsCardFocus = remember { FocusRequester() }
    val videosCardFocus = remember { FocusRequester() }
    val photosCardFocus = remember { FocusRequester() }
    val audioCardFocus = remember { FocusRequester() }
    val apksCardFocus = remember { FocusRequester() }
    val systemCardFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching { appsCardFocus.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF64D2FF))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "DEVICE CARE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64D2FF),
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Storage Management",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = "Clean All Cache",
                    onClick = onCleanCache,
                    primary = true,
                    modifier = Modifier
                        .focusRequester(cleanCacheFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (ev.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        runCatching { appsCardFocus.requestFocus() }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        runCatching { backHomeFocus.requestFocus() }
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )

                TvButton(
                    text = "Back to Home",
                    onClick = onClose,
                    primary = false,
                    modifier = Modifier
                        .focusRequester(backHomeFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (ev.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        runCatching { photosCardFocus.requestFocus() }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        runCatching { cleanCacheFocus.requestFocus() }
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
        }

        statusMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x334CAF50))
                    .border(1.dp, Color(0x664CAF50), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(text = msg, color = Color(0xFF30D158), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(18.dp))

        // ─── Hero Storage Progress Gauge Card ───
        val usedGbStr = StorageHelper.formatBytes(overview.usedBytes)
        val totalGbStr = StorageHelper.formatBytes(overview.totalBytes)
        val freeGbStr = StorageHelper.formatBytes(overview.freeBytes)
        val usedPct = if (overview.totalBytes > 0) ((overview.usedBytes.toDouble() / overview.totalBytes) * 100).toInt() else 0

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1E212A), Color(0xFF141720))))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(24.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Used $usedGbStr of $totalGbStr",
                            style = Ty.Title.copy(
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "$freeGbStr free • $usedPct% storage capacity utilized",
                            style = Ty.Dim.copy(fontSize = 16.sp, color = Color(0xBBFFFFFF))
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0A84FF).copy(alpha = 0.25f))
                            .border(1.dp, Color(0xFF0A84FF).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "$usedPct% USED",
                            style = Ty.Small.copy(color = Color(0xFF64D2FF), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        )
                    }
                }

                // Proportional Multi-Segment Storage Bar
                StorageSegmentedBar(overview = overview)
            }
        }

        Spacer(Modifier.height(22.dp))

        // ─── Storage Categories Header ───
        Text(
            text = "STORAGE CATEGORIES (Click to inspect & delete)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0x99FFFFFF),
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        // ─── Category Cards Grid ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StorageCategoryCard(
                title = "Apps",
                size = StorageHelper.formatBytes(overview.appsBytes),
                count = "${overview.appDetails.size} Installed",
                icon = Icons.Rounded.Apps,
                accentColor = Color(0xFF3B82F6),
                onClick = { onSelectCategory(StorageCategory.APPS) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(appsCardFocus)
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (ev.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    runCatching { cleanCacheFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    runCatching { videosCardFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    runCatching { audioCardFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )

            StorageCategoryCard(
                title = "Videos",
                size = StorageHelper.formatBytes(overview.videosBytes),
                count = "${overview.videoFiles.size} Video Files",
                icon = Icons.Rounded.Movie,
                accentColor = Color(0xFF8B5CF6),
                onClick = { onSelectCategory(StorageCategory.VIDEOS) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(videosCardFocus)
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (ev.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    runCatching { cleanCacheFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    runCatching { appsCardFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    runCatching { photosCardFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    runCatching { apksCardFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )

            StorageCategoryCard(
                title = "Photos",
                size = StorageHelper.formatBytes(overview.imagesBytes),
                count = "${overview.imageFiles.size} Image Files",
                icon = Icons.Rounded.Image,
                accentColor = Color(0xFFEC4899),
                onClick = { onSelectCategory(StorageCategory.IMAGES) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(photosCardFocus)
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (ev.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    runCatching { backHomeFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    runCatching { videosCardFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    runCatching { systemCardFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StorageCategoryCard(
                title = "Audio",
                size = StorageHelper.formatBytes(overview.audioBytes),
                count = "${overview.audioFiles.size} Audio Tracks",
                icon = Icons.Rounded.MusicNote,
                accentColor = Color(0xFFF59E0B),
                onClick = { onSelectCategory(StorageCategory.AUDIO) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(audioCardFocus)
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (ev.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    runCatching { appsCardFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    runCatching { apksCardFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )

            StorageCategoryCard(
                title = "APK Files",
                size = StorageHelper.formatBytes(overview.apksBytes),
                count = "${overview.apkFiles.size} Installers",
                icon = Icons.Rounded.Download,
                accentColor = Color(0xFF06B6D4),
                onClick = { onSelectCategory(StorageCategory.APKS) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(apksCardFocus)
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (ev.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    runCatching { videosCardFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    runCatching { audioCardFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    runCatching { systemCardFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )

            StorageCategoryCard(
                title = "System OS",
                size = StorageHelper.formatBytes(overview.systemBytes),
                count = "Android System & ROM",
                icon = Icons.Rounded.Settings,
                accentColor = Color(0xFF64748B),
                onClick = {
                    try {
                        ctx.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(systemCardFocus)
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (ev.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    runCatching { photosCardFocus.requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    runCatching { apksCardFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
            )
        }
    }
}

/**
 * Multi-color segmented storage gauge bar.
 */
@Composable
private fun StorageSegmentedBar(
    overview: StorageOverview,
    modifier: Modifier = Modifier
) {
    val total = overview.totalBytes.toFloat().coerceAtLeast(1f)
    val appsPct = (overview.appsBytes / total).coerceIn(0f, 1f)
    val videosPct = (overview.videosBytes / total).coerceIn(0f, 1f)
    val imagesPct = (overview.imagesBytes / total).coerceIn(0f, 1f)
    val audioPct = (overview.audioBytes / total).coerceIn(0f, 1f)
    val apksPct = (overview.apksBytes / total).coerceIn(0f, 1f)
    val systemPct = (overview.systemBytes / total).coerceIn(0f, 1f)
    val usedPct = (overview.usedBytes / total).coerceIn(0f, 1f)
    val freePct = (1f - usedPct).coerceIn(0f, 1f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Multi-color horizontal progress bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF1E293B))
        ) {
            if (appsPct > 0.005f) Box(Modifier.weight(appsPct).fillMaxHeight().background(Color(0xFF3B82F6)))
            if (videosPct > 0.005f) Box(Modifier.weight(videosPct).fillMaxHeight().background(Color(0xFF8B5CF6)))
            if (imagesPct > 0.005f) Box(Modifier.weight(imagesPct).fillMaxHeight().background(Color(0xFFEC4899)))
            if (audioPct > 0.005f) Box(Modifier.weight(audioPct).fillMaxHeight().background(Color(0xFFF59E0B)))
            if (apksPct > 0.005f) Box(Modifier.weight(apksPct).fillMaxHeight().background(Color(0xFF06B6D4)))
            if (systemPct > 0.005f) Box(Modifier.weight(systemPct).fillMaxHeight().background(Color(0xFF64748B)))
            if (freePct > 0.005f) Box(Modifier.weight(freePct).fillMaxHeight().background(Color(0xFF1E293B)))
        }

        // Legend row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(label = "Apps", color = Color(0xFF3B82F6), size = StorageHelper.formatBytes(overview.appsBytes))
            LegendItem(label = "Videos", color = Color(0xFF8B5CF6), size = StorageHelper.formatBytes(overview.videosBytes))
            LegendItem(label = "Photos", color = Color(0xFFEC4899), size = StorageHelper.formatBytes(overview.imagesBytes))
            LegendItem(label = "Audio", color = Color(0xFFF59E0B), size = StorageHelper.formatBytes(overview.audioBytes))
            LegendItem(label = "APKs", color = Color(0xFF06B6D4), size = StorageHelper.formatBytes(overview.apksBytes))
            LegendItem(label = "System", color = Color(0xFF64748B), size = StorageHelper.formatBytes(overview.systemBytes))
            LegendItem(label = "Free Space", color = Color(0xFF94A3B8), size = StorageHelper.formatBytes(overview.freeBytes))
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, size: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = Ty.Small.copy(color = Color(0xBBFFFFFF), fontSize = 13.sp))
        Spacer(Modifier.width(4.dp))
        Text(text = size, style = Ty.Small.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp))
    }
}

/**
 * Category Card with large colorful icon, title, item count, and bold size badge.
 */
@Composable
private fun StorageCategoryCard(
    title: String,
    size: String,
    count: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.height(118.dp),
        shape = RoundedCornerShape(20.dp),
        idle = Color(0x14FFFFFF),
        scaleOnFocus = 1.04f,
        idleOutline = false
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accentColor.copy(alpha = if (focused) 0.35f else 0.20f))
                    .border(1.dp, accentColor.copy(alpha = if (focused) 0.8f else 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Ty.Heading.copy(
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = count,
                    style = Ty.Small.copy(color = Color(0x99FFFFFF), fontSize = 13.sp)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = size,
                    style = Ty.Title.copy(
                        color = accentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Manage",
                        style = Ty.Small.copy(color = Color(0x77FFFFFF), fontSize = 12.sp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0x77FFFFFF), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/**
 * File Category Screen: Displays video, photo, audio, or APK files with delete action.
 */
@Composable
private fun FileCategoryView(
    title: String,
    category: StorageCategory,
    totalBytes: Long,
    files: List<StorageFileItem>,
    backFocus: FocusRequester,
    onBack: () -> Unit,
    onRequestDelete: (StorageFileItem) -> Unit
) {
    val firstFileFocus = remember { FocusRequester() }
    val backBtnFocus = remember { FocusRequester() }

    LaunchedEffect(files.size) {
        delay(100)
        if (files.isNotEmpty()) {
            runCatching { firstFileFocus.requestFocus() }
        } else {
            runCatching { backBtnFocus.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Focusable(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backBtnFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                                files.isNotEmpty()
                            ) {
                                runCatching { firstFileFocus.requestFocus() }
                                true
                            } else false
                        },
                    shape = RoundedCornerShape(14.dp),
                    idle = Color(0x22FFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back to Storage", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.width(20.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${files.size} files • Total ${StorageHelper.formatBytes(totalBytes)}",
                        fontSize = 15.sp,
                        color = Color(0xAAFFFFFF)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1AFFFFFF))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Press OK on any file to delete",
                    color = Color(0x88FFFFFF),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x0EFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(54.dp))
                    Text(
                        text = "No $title Found",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Your storage is completely clean of files in this category.",
                        fontSize = 15.sp,
                        color = Color(0x88FFFFFF)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x0EFFFFFF))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(files, key = { _, it -> it.path }) { index, item ->
                    FileRowItem(
                        item = item,
                        category = category,
                        onClick = { onRequestDelete(item) },
                        modifier = (if (index == 0) Modifier.focusRequester(firstFileFocus) else Modifier)
                            .onPreviewKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                    ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                                    index == 0
                                ) {
                                    runCatching { backBtnFocus.requestFocus() }
                                    true
                                } else false
                            }
                    )
                }
            }
        }
    }
}

/**
 * Single file row with remote focus, metadata, and delete icon.
 */
@Composable
private fun FileRowItem(
    item: StorageFileItem,
    category: StorageCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        idle = Color(0x12FFFFFF),
        scaleOnFocus = 1.02f
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val fileIcon = when (category) {
                StorageCategory.VIDEOS -> Icons.Rounded.Movie
                StorageCategory.IMAGES -> Icons.Rounded.Image
                StorageCategory.AUDIO -> Icons.Rounded.MusicNote
                StorageCategory.APKS -> Icons.Rounded.Download
                else -> Icons.Rounded.Description
            }
            val accentColor = when (category) {
                StorageCategory.VIDEOS -> Color(0xFF8B5CF6)
                StorageCategory.IMAGES -> Color(0xFFEC4899)
                StorageCategory.AUDIO -> Color(0xFFF59E0B)
                StorageCategory.APKS -> Color(0xFF06B6D4)
                else -> Color.White
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(fileIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.path,
                    color = Color(0x77FFFFFF),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(16.dp))

            // File Size Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x22FFFFFF))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = StorageHelper.formatBytes(item.size),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            // Trash Action Button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (focused) Color(0xFFFF453A).copy(alpha = 0.35f) else Color(0x18FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = if (focused) Color(0xFFFF453A) else Color(0x99FFFFFF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Delete File Confirmation Modal Dialog.
 */
@Composable
private fun DeleteFileModal(
    fileItem: StorageFileItem,
    autoFocus: FocusRequester,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF26262B), Color(0xFF17171B))))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0x22FF453A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFFF453A), modifier = Modifier.size(32.dp))
            }

            Text(
                text = "Delete File?",
                style = Ty.Title.copy(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = fileItem.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Size: ${StorageHelper.formatBytes(fileItem.size)}",
                    color = Color(0xFF64D2FF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "This file will be permanently deleted from TV internal storage to free up space.",
                color = Color(0x99FFFFFF),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val deleteBtnFocus = remember { FocusRequester() }

                TvButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(autoFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            ) {
                                runCatching { deleteBtnFocus.requestFocus() }
                                true
                            } else false
                        }
                )

                TvButton(
                    text = "Delete Permanently",
                    onClick = onConfirm,
                    primary = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(deleteBtnFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            ) {
                                runCatching { autoFocus.requestFocus() }
                                true
                            } else false
                        }
                )
            }
        }
    }
}

/**
 * Apps Management View: Two-column layout for clearing cache, app data, and uninstalling apps.
 */
@Composable
private fun AppsManagementView(
    overview: StorageOverview,
    selectedApp: AppStorageDetail?,
    appIcons: Map<String, Bitmap>,
    adbClient: AdbClient?,
    journal: Journal,
    backFocus: FocusRequester,
    onBack: () -> Unit,
    onSelectApp: (AppStorageDetail) -> Unit,
    onAppModified: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstAppFocus = remember { FocusRequester() }
    val backBtnFocus = remember { FocusRequester() }
    val clearCacheFocus = remember { FocusRequester() }
    val clearDataFocus = remember { FocusRequester() }
    val uninstallFocus = remember { FocusRequester() }
    val appInfoFocus = remember { FocusRequester() }

    LaunchedEffect(overview.appDetails.size) {
        delay(100)
        if (overview.appDetails.isNotEmpty()) {
            runCatching { firstAppFocus.requestFocus() }
        } else {
            runCatching { backBtnFocus.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Apps Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Focusable(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backBtnFocus)
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
                                overview.appDetails.isNotEmpty()
                            ) {
                                runCatching { firstAppFocus.requestFocus() }
                                true
                            } else false
                        },
                    shape = RoundedCornerShape(14.dp),
                    idle = Color(0x22FFFFFF)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back to Storage", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.width(20.dp))

                Column {
                    Text(
                        text = "Installed Applications",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${overview.appDetails.size} applications • Total ${StorageHelper.formatBytes(overview.appsBytes)}",
                        fontSize = 15.sp,
                        color = Color(0xAAFFFFFF)
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Two-Column TV Layout (55% Apps List, 45% Inspector & Actions)
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Column: Apps List (55%)
            LazyColumn(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x0EFFFFFF))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                itemsIndexed(overview.appDetails, key = { _, it -> it.packageName }) { index, app ->
                    val isSelected = selectedApp?.packageName == app.packageName
                    val icon = appIcons[app.packageName]

                    Focusable(
                        onClick = {
                            onSelectApp(app)
                            runCatching { clearCacheFocus.requestFocus() }
                        },
                        shape = RoundedCornerShape(14.dp),
                        idle = if (isSelected) Color(0x2EFFFFFF) else Color(0x0EFFFFFF),
                        scaleOnFocus = 1.02f,
                        modifier = (if (index == 0) Modifier.focusRequester(firstAppFocus) else Modifier)
                            .onPreviewKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (ev.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                            onSelectApp(app)
                                            runCatching { clearCacheFocus.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (index == 0) {
                                                runCatching { backBtnFocus.requestFocus() }
                                                true
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) { isFocused ->
                        if (isFocused && selectedApp?.packageName != app.packageName) {
                            onSelectApp(app)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (icon != null) {
                                Image(
                                    bitmap = icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x28FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Android, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
                                }
                            }

                            Spacer(Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appLabel,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (app.isSystemApp) "System App • ${app.packageName}" else app.packageName,
                                    color = Color(0x77FFFFFF),
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.width(10.dp))

                            Text(
                                text = StorageHelper.formatBytes(app.totalBytes),
                                color = Color(0xFF64D2FF),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Right Column: Storage Inspector & Actions (45%)
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
            ) {
                if (selectedApp == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x0EFFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Select an app to inspect", color = Color(0x66FFFFFF), fontSize = 18.sp)
                    }
                } else {
                    val activeIcon = appIcons[selectedApp.packageName]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x16FFFFFF))
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // App Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeIcon != null) {
                                Image(
                                    bitmap = activeIcon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0x28FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Android, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                                }
                            }

                            Spacer(Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedApp.appLabel,
                                    style = Ty.Title.copy(fontSize = 24.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = selectedApp.packageName,
                                    style = Ty.Small.copy(color = Color(0x88FFFFFF), fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Storage Metrics
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StorageMetricRow(title = "App Size (APK)", size = StorageHelper.formatBytes(selectedApp.appBytes), color = Color(0xFF64D2FF))
                            StorageMetricRow(title = "User Data", size = StorageHelper.formatBytes(selectedApp.dataBytes), color = Color(0xFFFFA726))
                            StorageMetricRow(title = "Cache", size = StorageHelper.formatBytes(selectedApp.cacheBytes), color = Color(0xFF30D158))
                        }

                        Spacer(Modifier.weight(1f))

                        // Actions
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TvButton(
                                text = "Clear Cache (${StorageHelper.formatBytes(selectedApp.cacheBytes)})",
                                onClick = {
                                    scope.launch {
                                        if (adbClient != null && adbClient.isConnected) {
                                            adbClient.shell("pm trim-caches 9999999999")
                                        }
                                        onAppModified("✓ Cache cleared for ${selectedApp.appLabel}")
                                    }
                                },
                                primary = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(clearCacheFocus)
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (ev.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> {
                                                    runCatching { firstAppFocus.requestFocus() }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    runCatching { clearDataFocus.requestFocus() }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    runCatching { backBtnFocus.requestFocus() }
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            )

                            TvButton(
                                text = "Clear All App Data",
                                onClick = {
                                    scope.launch {
                                        val ok = StorageHelper.clearAppData(ctx, adbClient, selectedApp.packageName)
                                        onAppModified(if (ok) "✓ Cleared data for ${selectedApp.appLabel}" else "Opened App Settings")
                                    }
                                },
                                primary = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(clearDataFocus)
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (ev.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> {
                                                    runCatching { firstAppFocus.requestFocus() }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    runCatching { clearCacheFocus.requestFocus() }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    runCatching { uninstallFocus.requestFocus() }
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            )

                            TvButton(
                                text = if (selectedApp.isSystemApp) "Uninstall System App" else "Uninstall Application",
                                onClick = {
                                    scope.launch {
                                        val res = SystemDebloater.uninstallOrDisable(
                                             ctx = ctx,
                                             pkg = selectedApp.packageName,
                                             label = selectedApp.appLabel,
                                             isSystemApp = selectedApp.isSystemApp,
                                             adbClient = adbClient,
                                             journal = journal
                                        )
                                        when (res) {
                                            is DebloatResult.Success -> onAppModified(res.message)
                                            is DebloatResult.NeedsPrivilege -> onAppModified(res.message)
                                            is DebloatResult.Failure -> onAppModified(res.reason)
                                        }
                                    }
                                },
                                primary = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(uninstallFocus)
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (ev.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> {
                                                    runCatching { firstAppFocus.requestFocus() }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    runCatching { clearDataFocus.requestFocus() }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    runCatching { appInfoFocus.requestFocus() }
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            )

                            TvButton(
                                text = "App Info in TV Settings",
                                onClick = { StorageHelper.openAppDetails(ctx, selectedApp.packageName) },
                                primary = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(appInfoFocus)
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (ev.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> {
                                                    runCatching { firstAppFocus.requestFocus() }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    runCatching { uninstallFocus.requestFocus() }
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageMetricRow(title: String, size: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = title, color = Color(0xDDFFFFFF), fontSize = 15.sp)
        }
        Text(text = size, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}