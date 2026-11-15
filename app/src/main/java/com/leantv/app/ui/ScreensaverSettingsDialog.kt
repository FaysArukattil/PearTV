package com.leantv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Persistent & Asset Thumbnail Store
// ─────────────────────────────────────────────────────────────────────────────
object AerialThumbnailStore {
    fun load(context: Context, url: String): Bitmap? {
        val name = url.substringAfterLast('/').substringBeforeLast('.') + ".jpg"
        // 1. Bundled high-resolution asset first (0 network, instant <1ms)
        try {
            context.assets.open("aerial_thumbs/$name").use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                if (bmp != null) return bmp
            }
        } catch (_: Exception) {}

        // 2. Disk cache
        try {
            val dir = File(context.cacheDir, "aerial_thumbs")
            val f = File(dir, name)
            if (f.exists() && f.length() > 500L) {
                val bmp = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp != null) return bmp
            }
        } catch (_: Exception) {}

        return null
    }

    fun save(context: Context, url: String, bmp: Bitmap) {
        runCatching {
            val dir = File(context.cacheDir, "aerial_thumbs")
            if (!dir.exists()) dir.mkdirs()
            val name = url.substringAfterLast('/').substringBeforeLast('.') + ".jpg"
            val f = File(dir, name)
            f.outputStream().use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main Screensaver Settings Dialog Orchestrator
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScreensaverSettingsDialog(
    currentMode: ScreensaverSource = ScreensaverSource.AERIAL,
    customPath: String? = null,
    idleMinutes: Int,
    autoCache: Boolean,
    selectedUrl: String?,
    onSelectMode: (ScreensaverSource) -> Unit = {},
    onSelectIdleMinutes: (Int) -> Unit,
    onToggleAutoCache: (Boolean) -> Unit,
    onSelectVideoUrl: (String?) -> Unit,
    onPickPath: () -> Unit = {},
    onStartScreensaverNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val autoFocus = rememberAutoFocus()

    // Strict boundary focus requesters to prevent focus escaping outside the dialog
    val streamCardRequester = remember { FocusRequester() }
    val downloadCardRequester = remember { FocusRequester() }
    val exitBtnRequester = remember { FocusRequester() }
    val previewBtnRequester = remember { FocusRequester() }
    val clearAllBtnRequester = remember { FocusRequester() }
    val doneBtnRequester = remember { FocusRequester() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppleMotion.DurStandard, easing = AppleMotion.EaseOut),
        label = "scrim"
    )
    val panelScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.93f,
        animationSpec = AppleMotion.SpringStandard,
        label = "scale"
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppleMotion.DurStandard, easing = AppleMotion.Decelerate),
        label = "alpha"
    )

    val downloadProgressMap = remember { mutableStateMapOf<String, AerialVideoCache.DownloadProgress>() }
    var cacheVersion by remember { mutableIntStateOf(0) }
    fun refreshCache() { cacheVersion++ }

    var cachedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var totalCacheMb by remember { mutableLongStateOf(0L) }

    val thumbnails = remember { mutableStateMapOf<String, Bitmap?>() }

    // Pre-load thumbnails & cache status in background (0 disk I/O on UI thread)
    LaunchedEffect(cacheVersion) {
        withContext(Dispatchers.IO) {
            AerialVideoCache.migrateLegacyCacheIfNeeded(context)
            val cached = APPLE_AERIAL_PRESETS.filter { AerialVideoCache.isCached(context, it.url) }.map { it.url }.toSet()
            val total = AerialVideoCache.getTotalCacheSizeBytes(context) / (1024 * 1024)
            withContext(Dispatchers.Main) {
                cachedUrls = cached
                totalCacheMb = total
            }

            APPLE_AERIAL_PRESETS.forEach { video ->
                if (video.url !in thumbnails) {
                    val diskBmp = AerialThumbnailStore.load(context, video.url)
                    if (diskBmp != null) {
                        withContext(Dispatchers.Main) { thumbnails[video.url] = diskBmp }
                    } else {
                        val file = AerialVideoCache.getCacheFile(context, video.url)
                        if (file.exists() && file.length() > AerialVideoCache.MIN_VALID_SIZE) {
                            runCatching {
                                val r = MediaMetadataRetriever()
                                r.setDataSource(file.absolutePath)
                                val extracted = r.getFrameAtTime(2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                r.release()
                                if (extracted != null) {
                                    AerialThumbnailStore.save(context, video.url, extracted)
                                    withContext(Dispatchers.Main) { thumbnails[video.url] = extracted }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal popup states
    var videoToDelete by remember { mutableStateOf<AppleAerialVideo?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val hasSubDialog = videoToDelete != null || confirmDeleteAll

    // Dedicated BackHandler so Back button ALWAYS works cleanly
    BackHandler(enabled = true) {
        if (videoToDelete != null) {
            videoToDelete = null
        } else if (confirmDeleteAll) {
            confirmDeleteAll = false
        } else {
            onDismiss()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusProperties { exit = { FocusRequester.Cancel } }
            .background(Color(0xCC000000).copy(alpha = 0.95f * scrimAlpha))
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                     ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) {
                    if (videoToDelete != null) {
                        videoToDelete = null
                        true
                    } else if (confirmDeleteAll) {
                        confirmDeleteAll = false
                        true
                    } else {
                        onDismiss()
                        true
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(1060.dp)
                .fillMaxHeight(0.94f)
                .graphicsLayer { scaleX = panelScale; scaleY = panelScale; alpha = panelAlpha }
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF23232A), Color(0xFF15151B))))
                .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(26.dp))
                .padding(horizontal = 30.dp, vertical = 20.dp)
                .focusGroup()
                .focusProperties {
                    canFocus = !hasSubDialog
                    exit = { FocusRequester.Cancel }
                }
                .focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Title + Exit button
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Screensaver Settings",
                        style = Ty.Title.copy(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        "Apple Aerial 4K/1080p Cinematic Drone Footage",
                        style = Ty.Dim.copy(fontSize = 12.sp, color = Color(0x99FFFFFF))
                    )
                }

                Focusable(
                    onClick = onDismiss,
                    modifier = Modifier
                        .focusRequester(exitBtnRequester)
                        .focusProperties {
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = downloadCardRequester
                            left = downloadCardRequester
                        },
                    shape = RoundedCornerShape(10.dp),
                    idle = Color(0x1AFFFFFF),
                    scaleOnFocus = 1.05f
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text("Exit", style = Ty.Small.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp))
                    }
                }
            }

            // Top Mode Selection Cards: Stream All vs Downloaded Only
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Card 1: Stream All (Cannot escape Left or Up)
                val isStreamApplied = !autoCache
                ModeOptionCard(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(streamCardRequester)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            right = downloadCardRequester
                        }
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                            ) {
                                true // Block left navigation from escaping to homescreen
                            } else false
                        },
                    title = "Stream All (27 Scenes)",
                    subtitle = "Streams all scenes online. Saved scenes play locally with 0 internet.",
                    storageTag = "0 MB disk storage used",
                    isApplied = isStreamApplied,
                    icon = Icons.Rounded.WifiTethering,
                    appliedColor = Color(0xFF30D158),
                    onClick = {
                        onToggleAutoCache(false)
                        onSelectVideoUrl(null)
                    }
                )

                // Card 2: Downloaded Only (Cannot escape Right)
                val isDownloadedApplied = autoCache
                ModeOptionCard(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(downloadCardRequester)
                        .focusProperties {
                            right = FocusRequester.Cancel
                            up = exitBtnRequester
                            left = streamCardRequester
                        }
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                            ) {
                                true // Block right navigation from escaping to homescreen
                            } else false
                        },
                    title = "Downloaded (${cachedUrls.size} Scenes)",
                    subtitle = if (cachedUrls.isNotEmpty()) "Loops all ${cachedUrls.size} saved scenes continuously. 100% offline."
                               else "No scenes saved yet. Click any scene below to download it.",
                    storageTag = "$totalCacheMb MB saved in TV storage",
                    isApplied = isDownloadedApplied,
                    icon = Icons.Rounded.CloudDownload,
                    appliedColor = Color(0xFF30D158),
                    onClick = {
                        onToggleAutoCache(true)
                        onSelectVideoUrl(null)
                    }
                )
            }

            // Grid Section Header
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "ALL AERIAL SCENES (Click to download or delete)",
                    style = Ty.Small.copy(
                        color = Color(0x88FFFFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                if (cachedUrls.isNotEmpty()) {
                    Focusable(
                        onClick = { confirmDeleteAll = true },
                        shape = RoundedCornerShape(10.dp),
                        idle = Color(0x28FF3B30),
                        scaleOnFocus = 1.05f
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = Color(0xFFFF453A), modifier = Modifier.size(14.dp))
                            Text("Clear All (${totalCacheMb} MB)", style = Ty.Small.copy(color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 11.sp))
                        }
                    }
                }
            }

            // Scrollable Grid of All 27 Scenes (Left/Right edges locked)
            DownloadedGridPanel(
                modifier = Modifier.fillMaxWidth().weight(1f),
                cachedUrls = cachedUrls,
                thumbnails = thumbnails,
                downloadProgressMap = downloadProgressMap,
                onItemClick = { video, isCached ->
                    if (isCached) {
                        videoToDelete = video
                    } else if (video.url !in downloadProgressMap) {
                        downloadProgressMap[video.url] = AerialVideoCache.DownloadProgress()
                        scope.launch {
                            AerialVideoCache.downloadToCache(context, video.url) { prog ->
                                downloadProgressMap[video.url] = prog
                            }
                            downloadProgressMap.remove(video.url)
                            refreshCache()
                        }
                    }
                }
            )

            // Idle Timer Row (Clearly shows the currently selected timer & switches instantly)
            IdleTimerRow(
                idleMinutes = idleMinutes,
                onSelectIdleMinutes = onSelectIdleMinutes
            )

            // Bottom Actions Bar (Strict focus locking)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Focusable(
                    onClick = onStartScreensaverNow,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(previewBtnRequester)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            right = if (cachedUrls.isNotEmpty()) clearAllBtnRequester else doneBtnRequester
                        },
                    shape = RoundedCornerShape(13.dp),
                    idle = Color(0xFF0A84FF),
                    scaleOnFocus = 1.02f
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Preview Screensaver (${if (autoCache) "Downloaded Only" else "Stream All"})",
                            style = Ty.Button.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                if (cachedUrls.isNotEmpty()) {
                    Focusable(
                        onClick = { confirmDeleteAll = true },
                        modifier = Modifier
                            .focusRequester(clearAllBtnRequester)
                            .focusProperties {
                                down = FocusRequester.Cancel
                                left = previewBtnRequester
                                right = doneBtnRequester
                            },
                        shape = RoundedCornerShape(13.dp),
                        idle = Color(0x28FF3B30),
                        scaleOnFocus = 1.02f
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = Color(0xFFFF453A), modifier = Modifier.size(17.dp))
                            Text("Clear All (${totalCacheMb} MB)", style = Ty.Button.copy(color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }

                Focusable(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(120.dp)
                        .focusRequester(doneBtnRequester)
                        .focusProperties {
                            right = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = if (cachedUrls.isNotEmpty()) clearAllBtnRequester else previewBtnRequester
                        },
                    shape = RoundedCornerShape(13.dp),
                    idle = Color(0x22FFFFFF),
                    scaleOnFocus = 1.02f
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Done", style = Ty.Button.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }

        // Popup: Confirm Delete Single Scene (Delete & Cancel: Identical type & size)
        if (videoToDelete != null) {
            val v = videoToDelete!!
            ConfirmDeleteSingleDialog(
                video = v,
                onConfirm = {
                    AerialVideoCache.deleteFromCache(context, v.url)
                    thumbnails.remove(v.url)
                    refreshCache()
                    videoToDelete = null
                },
                onDismiss = { videoToDelete = null }
            )
        }

        // Popup: Confirm Delete All
        if (confirmDeleteAll) {
            ConfirmDeleteAllDialog(
                count = cachedUrls.size,
                totalMb = totalCacheMb,
                onConfirm = {
                    AerialVideoCache.clearCache(context)
                    thumbnails.clear()
                    onSelectVideoUrl(null)
                    refreshCache()
                    confirmDeleteAll = false
                },
                onDismiss = { confirmDeleteAll = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode Option Card (TV First, Large, Clear State)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ModeOptionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    storageTag: String,
    isApplied: Boolean,
    icon: ImageVector,
    appliedColor: Color,
    onClick: () -> Unit
) {
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        idle = if (isApplied) Color(0x220A84FF) else Color(0x12FFFFFF),
        idleOutline = isApplied,
        scaleOnFocus = 1.02f
    ) { focused ->
        Box(
            Modifier
                .fillMaxWidth()
                .border(
                    width = if (focused) 2.dp else if (isApplied) 1.5.dp else 1.dp,
                    color = if (focused) Color.White else if (isApplied) Color(0xFF0A84FF) else Color(0x18FFFFFF),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isApplied) Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))
                            else Brush.linearGradient(listOf(Color(0x2AFFFFFF), Color(0x1AFFFFFF)))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isApplied || focused) Color.White else Color(0xBBFFFFFF),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            title,
                            style = Ty.Body.copy(
                                color = Color.White,
                                fontWeight = if (isApplied) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        )
                        if (isApplied) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(appliedColor.copy(alpha = 0.2f))
                                    .border(1.dp, appliedColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "APPLIED ✓",
                                    style = Ty.Small.copy(color = appliedColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        } else {
                            Text(
                                "Press to apply",
                                style = Ty.Small.copy(color = Color(0x66FFFFFF), fontSize = 10.sp)
                            )
                        }
                    }

                    Text(
                        subtitle,
                        style = Ty.Small.copy(color = Color(0x99FFFFFF), fontSize = 11.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        storageTag,
                        style = Ty.Small.copy(color = if (isApplied) Color(0xFF64B5F6) else Color(0x66FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Downloaded Grid Panel
// ─────────────────────────────────────────────────────────────────────────────
private sealed class GridEntry {
    data class Header(val location: String) : GridEntry()
    data class VideoItem(val video: AppleAerialVideo, val col: Int) : GridEntry()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DownloadedGridPanel(
    modifier: Modifier,
    cachedUrls: Set<String>,
    thumbnails: SnapshotStateMap<String, Bitmap?>,
    downloadProgressMap: SnapshotStateMap<String, AerialVideoCache.DownloadProgress>,
    onItemClick: (AppleAerialVideo, Boolean) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 4.dp)
    ) {
        val flatItems: List<GridEntry> = buildList {
            APPLE_AERIAL_PRESETS.groupBy { it.location }.forEach { (loc, videos) ->
                add(GridEntry.Header(loc))
                videos.forEachIndexed { idx, v ->
                    add(GridEntry.VideoItem(v, col = idx % 3))
                }
            }
        }

        flatItems.forEach { entry ->
            when (entry) {
                is GridEntry.Header -> {
                    item(key = "hdr_${entry.location}", span = { GridItemSpan(3) }) {
                        Text(
                            entry.location.uppercase(),
                            style = Ty.Small.copy(
                                color = Color(0x77FFFFFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                }
                is GridEntry.VideoItem -> {
                    val video = entry.video
                    val col = entry.col
                    item(key = video.url) {
                        val isCached         = video.url in cachedUrls
                        val downloadProgress = downloadProgressMap[video.url]
                        val thumbnail        = thumbnails[video.url]

                        AerialThumbnailCard(
                            video = video,
                            col = col,
                            thumbnail = thumbnail,
                            isCached = isCached,
                            downloadProgress = downloadProgress,
                            onAction = { onItemClick(video, isCached) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card: High-contrast Dark Pill Badge + Smooth Live Video Crossfade (No grey gap)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AerialThumbnailCard(
    video: AppleAerialVideo,
    col: Int,
    thumbnail: Bitmap?,
    isCached: Boolean,
    downloadProgress: AerialVideoCache.DownloadProgress?,
    onAction: () -> Unit
) {
    val borderColor = when {
        downloadProgress != null -> Color(0xFF0A84FF)
        isCached                 -> Color(0xFF30D158).copy(alpha = 0.6f)
        else                     -> Color(0x18FFFFFF)
    }

    Focusable(
        onClick = onAction,
        modifier = Modifier.focusProperties {
            if (col == 0) left = FocusRequester.Cancel
            if (col == 2) right = FocusRequester.Cancel
        },
        shape = RoundedCornerShape(14.dp),
        idle = Color.Transparent,
        idleOutline = isCached || downloadProgress != null,
        scaleOnFocus = 1.04f
    ) { focused ->
        var isVideoPlaying by remember { mutableStateOf(false) }

        LaunchedEffect(focused) {
            if (!focused) isVideoPlaying = false
        }

        val videoAlpha by animateFloatAsState(
            targetValue = if (focused && isCached && isVideoPlaying) 1f else 0f,
            animationSpec = tween(350, easing = LinearEasing),
            label = "videoFadeIn"
        )

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (focused) 2.dp else if (downloadProgress != null) 2.dp else if (isCached) 1.dp else 0.dp,
                    color = if (focused) Color.White else borderColor,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            // ─── Layer 1: High-res instant static thumbnail ALWAYS stays rendered underneath! ───
            // ZERO grey color, ZERO blank screen while the video prepares!
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF1E2028)))
            }

            // ─── Layer 2: Live Video Player crossfades in smoothly only after prepared ───
            if (focused && isCached && downloadProgress == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = videoAlpha }
                ) {
                    LiveCardVideoPreview(
                        url = video.url,
                        onPrepared = { isVideoPlaying = true }
                    )
                }
            }

            // Gradient scrim for text contrast
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color(0xEE000000)
                        )
                    )
            )

            // Top-Right High-Contrast Badge (Deep Opaque Black Glass: 100% visible on any bright sky/cloud)
            Box(Modifier.align(Alignment.TopEnd).padding(7.dp)) {
                when {
                    downloadProgress != null -> StatusChip("${downloadProgress.percent}%", Color(0xFF64B5F6), Icons.Rounded.Refresh)
                    focused && isCached      -> StatusChip("Live ▶", Color(0xFF30D158))
                    isCached                 -> StatusChip("Saved ✓", Color(0xFF30D158))
                    else                     -> StatusChip("${video.sizeMb} MB", Color(0xFFE8E8E8), Icons.Rounded.Download)
                }
            }

            // In-flight download progress overlay
            if (downloadProgress != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xE6000000))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color(0xFF64B5F6),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "${downloadProgress.percent}%",
                                style = Ty.Body.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            )
                            Text(
                                "· ${String.format(Locale.US, "%.1f", downloadProgress.speedMbPerSec)} MB/s",
                                style = Ty.Small.copy(color = Color(0xFF64B5F6), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            )
                        }

                        // Progress Bar track & fill
                        Box(
                            Modifier
                                .fillMaxWidth(0.9f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0x33FFFFFF))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((downloadProgress.percent / 100f).coerceIn(0.03f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFF0A84FF))
                            )
                        }

                        Text(
                            "${String.format(Locale.US, "%.1f", downloadProgress.downloadedMb)} / ${video.sizeMb} MB",
                            style = Ty.Small.copy(color = Color(0xCCFFFFFF), fontSize = 11.sp)
                        )
                    }
                }
            } else {
                // Bottom overlay: title + size / delete hint
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        video.subtext,
                        style = Ty.Small.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isCached) {
                            Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(11.dp))
                            Text("Click to delete", style = Ty.Small.copy(color = Color(0xFFFF8A80), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                        } else {
                            Icon(Icons.Rounded.Download, null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(11.dp))
                            Text("Click to download", style = Ty.Small.copy(color = Color(0xCCFFFFFF), fontSize = 11.sp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Live Card Video Preview (Only plays local downloaded files, 0 network)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LiveCardVideoPreview(
    url: String,
    onPrepared: () -> Unit = {}
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    var player: MediaPlayer? = null

                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        try {
                            val mp = MediaPlayer()
                            player = mp
                            mp.setSurface(Surface(st))
                            val playable = AerialVideoCache.getPlayableSource(context, url)
                            mp.setDataSource(playable)
                            mp.isLooping = true
                            mp.setVolume(0f, 0f)
                            mp.setOnPreparedListener {
                                mp.start()
                                mp.seekTo(1500)
                                onPrepared()
                            }
                            mp.prepareAsync()
                        } catch (_: Exception) {
                            player?.release()
                            player = null
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        try {
                            player?.stop()
                            player?.release()
                        } catch (_: Exception) {}
                        player = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle Timer Row (Clearly shows the currently selected timer & updates instantly)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun IdleTimerRow(
    idleMinutes: Int,
    onSelectIdleMinutes: (Int) -> Unit
) {
    var activeMins by remember(idleMinutes) { mutableIntStateOf(idleMinutes) }

    val timerOptions = listOf(
        1 to "1 min",
        2 to "2 min",
        3 to "3 min",
        5 to "5 min",
        10 to "10 min",
        15 to "15 min",
        0 to "Never"
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.Timer, null, tint = Color(0x99FFFFFF), modifier = Modifier.size(16.dp))
            Text(
                "IDLE TIMER",
                style = Ty.Small.copy(color = Color(0x77FFFFFF), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
            )
            Text(
                if (activeMins > 0) "($activeMins min)" else "(Disabled)",
                style = Ty.Small.copy(color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold, fontSize = 11.sp)
            )
        }

        Spacer(Modifier.weight(1f))

        timerOptions.forEachIndexed { index, (mins, label) ->
            val sel = activeMins == mins
            val isFirst = index == 0
            val isLast = index == timerOptions.size - 1

            Focusable(
                onClick = {
                    activeMins = mins
                    onSelectIdleMinutes(mins)
                },
                modifier = Modifier.focusProperties {
                    if (isFirst) left = FocusRequester.Cancel
                    if (isLast) right = FocusRequester.Cancel
                },
                shape = RoundedCornerShape(10.dp),
                idle = Color.Transparent,
                scaleOnFocus = 1.05f
            ) { focused ->
                // Ensure the background is ALWAYS visible and distinct:
                // If selected: bright vivid blue (#0A84FF), slightly brighter on focus (#2997FF)
                // If unselected: dark glass (#18FFFFFF), lighter on focus (#38FFFFFF)
                val pillBackground = when {
                    sel && focused -> Color(0xFF2997FF)
                    sel            -> Color(0xFF0A84FF)
                    focused        -> Color(0x38FFFFFF)
                    else           -> Color(0x18FFFFFF)
                }

                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(pillBackground)
                        .border(
                            width = if (focused) 1.5.dp else if (sel) 1.dp else 0.dp,
                            color = if (focused) Color.White else if (sel) Color(0x66FFFFFF) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 11.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (sel) {
                            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                        Text(
                            label,
                            style = Ty.Body.copy(
                                color = Color.White,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirm Delete Single Scene Dialog (Delete & Cancel: Same type & size)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ConfirmDeleteSingleDialog(
    video: AppleAerialVideo,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogFocus = remember { FocusRequester() }
    val deleteBtnFocus = remember { FocusRequester() }
    val cancelBtnFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(60)
        deleteBtnFocus.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xBF000000))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                     ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) { onDismiss(); true } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF282830), Color(0xFF18181E))))
                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(22.dp))
                .padding(24.dp)
                .focusGroup()
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusRestorer(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Rounded.DeleteOutline, null, tint = Color(0xFFFF5252), modifier = Modifier.size(36.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Delete Scene?",
                    style = Ty.Title.copy(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${video.location} · ${video.subtext}",
                    style = Ty.Body.copy(color = Color(0xCCFFFFFF), fontSize = 14.sp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "This will free ${video.sizeMb} MB from your TV storage.",
                    style = Ty.Small.copy(color = Color(0x88FFFFFF), fontSize = 12.sp)
                )
            }

            Spacer(Modifier.height(2.dp))

            // Delete & Cancel buttons: EXACT SAME TYPE & SIZE
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Focusable(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(deleteBtnFocus)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            right = cancelBtnFocus
                        },
                    shape = RoundedCornerShape(12.dp),
                    idle = Color(0xFFFF3B30),
                    scaleOnFocus = 1.03f
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Delete",
                            style = Ty.Button.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Focusable(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(cancelBtnFocus)
                        .focusProperties {
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = deleteBtnFocus
                        },
                    shape = RoundedCornerShape(12.dp),
                    idle = Color(0x22FFFFFF),
                    scaleOnFocus = 1.03f
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Cancel",
                            style = Ty.Button.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirm Delete All Dialog (Delete All & Cancel: Same type & size)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ConfirmDeleteAllDialog(
    count: Int,
    totalMb: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val deleteBtnFocus = remember { FocusRequester() }
    val cancelBtnFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(60)
        deleteBtnFocus.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xBF000000))
            .focusProperties { exit = { FocusRequester.Cancel } }
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                     ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) { onDismiss(); true } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF282830), Color(0xFF18181E))))
                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(24.dp))
                .padding(26.dp)
                .focusGroup()
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusRestorer(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Rounded.DeleteSweep, null, tint = Color(0xFFFF453A), modifier = Modifier.size(38.dp))
            Text(
                "Delete All Downloads?",
                style = Ty.Title.copy(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                "This will remove $count saved screensaver videos ($totalMb MB) from TV storage.",
                style = Ty.Body.copy(color = Color(0xBBFFFFFF), fontSize = 14.sp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))

            // Delete & Cancel buttons: EXACT SAME TYPE & SIZE
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Focusable(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(deleteBtnFocus)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            right = cancelBtnFocus
                        },
                    shape = RoundedCornerShape(12.dp),
                    idle = Color(0xFFFF453A),
                    scaleOnFocus = 1.03f
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Delete All",
                            style = Ty.Button.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Focusable(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(cancelBtnFocus)
                        .focusProperties {
                            right = FocusRequester.Cancel
                            up = FocusRequester.Cancel
                            down = FocusRequester.Cancel
                            left = deleteBtnFocus
                        },
                    shape = RoundedCornerShape(12.dp),
                    idle = Color(0x22FFFFFF),
                    scaleOnFocus = 1.03f
                ) {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Cancel",
                            style = Ty.Button.copy(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// High-Contrast Dark Pill Badge (Solid near-black backdrop: 100% visible on ANY background)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatusChip(
    text: String,
    textColor: Color = Color.White,
    icon: ImageVector? = null
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color(0xF0121216)) // 94% opaque dark glass background
            .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(7.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, tint = textColor, modifier = Modifier.size(11.dp))
            }
            Text(
                text,
                style = Ty.Small.copy(color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}
