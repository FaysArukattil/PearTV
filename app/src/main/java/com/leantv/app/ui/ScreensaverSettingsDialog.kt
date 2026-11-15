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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Playback Mode Enum
// ─────────────────────────────────────────────────────────────────────────────
enum class AerialPlayMode(val label: String, val subtitle: String) {
    STREAM_ALL("Stream All", "Stream 27 scenes · 0 MB TV storage"),
    DOWNLOADED("Downloaded Scenes", "Save & loop offline · TV storage")
}

private val locationGradients = mapOf(
    "Dubai"         to listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460)),
    "Greenland"     to listOf(Color(0xFF0D2137), Color(0xFF1A3A4A), Color(0xFF0D5C63)),
    "Hong Kong"     to listOf(Color(0xFF1A0533), Color(0xFF2D1B69), Color(0xFF11998E)),
    "Los Angeles"   to listOf(Color(0xFF2D1B1B), Color(0xFF4A1942), Color(0xFF8B2FC9)),
    "Hawaii"        to listOf(Color(0xFF0D3B2E), Color(0xFF1A5276), Color(0xFF0E6655)),
    "London"        to listOf(Color(0xFF1C1C2E), Color(0xFF2C3E50), Color(0xFF4A5568)),
    "New York City" to listOf(Color(0xFF1A1A1A), Color(0xFF2D2D44), Color(0xFF4A4A6A)),
    "San Francisco" to listOf(Color(0xFF1A2744), Color(0xFF1E3A5F), Color(0xFF2874A6)),
    "China"         to listOf(Color(0xFF2D1B00), Color(0xFF5D2E00), Color(0xFF8B4513)),
    "Liwa"          to listOf(Color(0xFF2D1B00), Color(0xFF6B3A1F), Color(0xFFD4A017)),
)

// ─────────────────────────────────────────────────────────────────────────────
// Persistent & Asset Thumbnail Store
// ─────────────────────────────────────────────────────────────────────────────
object AerialThumbnailStore {
    fun load(context: Context, url: String): Bitmap? {
        val name = url.substringAfterLast('/').substringBeforeLast('.') + ".jpg"
        // 1. Try bundled high-resolution asset first (0 network, instant <1ms)
        try {
            context.assets.open("aerial_thumbs/$name").use { input ->
                val bmp = BitmapFactory.decodeStream(input)
                if (bmp != null) return bmp
            }
        } catch (_: Exception) {}

        // 2. Try disk cache
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

    // Focus bridges for TV remote
    val firstGridItemFocusRequester = remember { FocusRequester() }
    val modePillFocusRequester = remember { FocusRequester() }
    val previewNowFocusRequester = remember { FocusRequester() }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppleMotion.DurStandard, easing = AppleMotion.EaseOut), label = "scrim"
    )
    val panelScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.93f,
        animationSpec = AppleMotion.SpringStandard, label = "scale"
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppleMotion.DurStandard, easing = AppleMotion.Decelerate), label = "alpha"
    )

    // Set of URLs selected by user (can be 0, 1, or multiple)
    val selectedUrlSet = remember(selectedUrl) {
        selectedUrl?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }

    var playMode by remember {
        mutableStateOf(
            if (!autoCache && selectedUrl == null) AerialPlayMode.STREAM_ALL else AerialPlayMode.DOWNLOADED
        )
    }

    val downloadProgressMap = remember { mutableStateMapOf<String, AerialVideoCache.DownloadProgress>() }
    var cacheVersion by remember { mutableIntStateOf(0) }
    fun refreshCache() { cacheVersion++ }

    val cachedUrls by remember(cacheVersion) {
        derivedStateOf {
            APPLE_AERIAL_PRESETS.filter { AerialVideoCache.isCached(context, it.url) }.map { it.url }.toSet()
        }
    }

    val totalCacheMb by remember(cacheVersion) {
        derivedStateOf {
            AerialVideoCache.getTotalCacheSizeBytes(context) / (1024 * 1024)
        }
    }

    val thumbnails = remember { mutableStateMapOf<String, Bitmap?>() }

    // Pre-load all thumbnails completely offline: bundled assets first, then disk cache, then local video file
    LaunchedEffect(cacheVersion) {
        withContext(Dispatchers.IO) {
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
    var videoForAction by remember { mutableStateOf<AppleAerialVideo?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000).copy(alpha = 0.95f * scrimAlpha))
            .modalTrap()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                     ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) {
                    if (videoForAction != null) {
                        videoForAction = null
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
                .width(1020.dp)
                .fillMaxHeight(0.92f)
                .graphicsLayer { scaleX = panelScale; scaleY = panelScale; alpha = panelAlpha }
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF24242A), Color(0xFF16161C))))
                .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(28.dp))
                .padding(horizontal = 32.dp, vertical = 20.dp)
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            true // Never leak upwards
                        } else false
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Apple Aerial Screensaver",
                        style = Ty.Title.copy(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        if (playMode == AerialPlayMode.STREAM_ALL) {
                            "Streaming live from Apple CDN · 0 MB TV storage used"
                        } else {
                            "${cachedUrls.size} of ${APPLE_AERIAL_PRESETS.size} downloaded (${totalCacheMb} MB on TV)"
                        },
                        style = Ty.Dim.copy(fontSize = 13.sp)
                    )
                }

                if (cachedUrls.isNotEmpty() && playMode == AerialPlayMode.DOWNLOADED) {
                    Focusable(
                        onClick = { confirmDeleteAll = true },
                        shape = RoundedCornerShape(10.dp),
                        idle = Color(0x1FFF453A),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = Color(0xFFFF453A), modifier = Modifier.size(16.dp))
                            Text(
                                "Delete All (${totalCacheMb} MB)",
                                style = Ty.Small.copy(color = Color(0xFFFF453A), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }

            // Mode Selector Pills (Stream All vs Downloaded Scenes)
            AerialModePills(
                currentPlayMode = playMode,
                autoFocus = autoFocus,
                modeFocusRequester = modePillFocusRequester,
                onMoveDownToGrid = { runCatching { firstGridItemFocusRequester.requestFocus() } },
                onMoveDownToBottom = { runCatching { previewNowFocusRequester.requestFocus() } },
                onSelectMode = { mode ->
                    playMode = mode
                    when (mode) {
                        AerialPlayMode.STREAM_ALL -> {
                            onToggleAutoCache(false)
                            onSelectVideoUrl(null)
                        }
                        AerialPlayMode.DOWNLOADED -> {
                            onToggleAutoCache(true)
                        }
                    }
                }
            )

            // Section Hint
            val gridHint = when (playMode) {
                AerialPlayMode.STREAM_ALL -> "ONLINE STREAMING · DIRECT FROM APPLE CDN · 0 MB STORAGE USED"
                AerialPlayMode.DOWNLOADED -> when {
                    selectedUrlSet.size == 1 -> "1 SCENE SET AS SOLE SCREENSAVER · CLICK TO CHANGE"
                    selectedUrlSet.size > 1  -> "${selectedUrlSet.size} SCENES SELECTED IN PLAYLIST · CLICK TO CHANGE"
                    cachedUrls.isNotEmpty()   -> "ALL ${cachedUrls.size} SAVED SCENES LOOPING · CLICK FOR OPTIONS"
                    else                     -> "NO SCENES SAVED YET · CLICK ANY SCENE TO DOWNLOAD TO TV"
                }
            }
            Text(
                gridHint,
                style = Ty.Small.copy(color = Color(0x44FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            )

            // Center Content: Stream All info banner OR Downloaded grid
            if (playMode == AerialPlayMode.STREAM_ALL) {
                StreamAllInfoCard(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            } else {
                AerialGrid(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    cachedUrls = cachedUrls,
                    selectedUrlSet = selectedUrlSet,
                    thumbnails = thumbnails,
                    downloadProgressMap = downloadProgressMap,
                    firstGridItemFocusRequester = firstGridItemFocusRequester,
                    onMoveUpFromGrid = { runCatching { modePillFocusRequester.requestFocus() } },
                    onItemClick = { video, isCached ->
                        if (isCached) {
                            videoForAction = video
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
                    },
                    onFirstFrame = { url, bmp ->
                        thumbnails[url] = bmp
                        AerialThumbnailStore.save(context, url, bmp)
                    }
                )
            }

            // Idle Timer
            IdleTimerRow(
                idleMinutes = idleMinutes,
                onMoveUp = {
                    if (playMode == AerialPlayMode.DOWNLOADED) {
                        runCatching { firstGridItemFocusRequester.requestFocus() }
                    } else {
                        runCatching { modePillFocusRequester.requestFocus() }
                    }
                },
                onSelectIdleMinutes = onSelectIdleMinutes
            )

            // Bottom Actions
            Row(
                Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            true // Never leak downwards out of the screensaver dialog!
                        } else false
                    },
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Focusable(
                    onClick = onStartScreensaverNow,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(previewNowFocusRequester)
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                                playMode == AerialPlayMode.STREAM_ALL) {
                                runCatching { modePillFocusRequester.requestFocus() }
                                true
                            } else false
                        },
                    shape = RoundedCornerShape(14.dp),
                    idle = Color(0xFF0A84FF).copy(alpha = 0.25f)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Preview Now", style = Ty.Button.copy(color = Color.White, fontSize = 14.sp))
                    }
                }
                TvButton(text = "Done", onClick = onDismiss, primary = false, modifier = Modifier.width(130.dp))
            }
        }

        // Popup: Cached Video Options (Single / Multi / Loop All / Delete)
        if (videoForAction != null) {
            val v = videoForAction!!
            val isSole = selectedUrlSet.size == 1 && v.url in selectedUrlSet
            val isSelected = v.url in selectedUrlSet

            VideoActionDialog(
                video = v,
                isSelected = isSelected,
                isSole = isSole,
                hasSelection = selectedUrlSet.isNotEmpty(),
                onToggleSole = {
                    if (isSole) {
                        onSelectVideoUrl(null)
                    } else {
                        onSelectVideoUrl(v.url)
                    }
                    videoForAction = null
                },
                onToggleMultiSelect = {
                    val newSet = if (isSelected) selectedUrlSet - v.url else selectedUrlSet + v.url
                    onSelectVideoUrl(if (newSet.isEmpty()) null else newSet.joinToString(","))
                    videoForAction = null
                },
                onLoopAll = {
                    onSelectVideoUrl(null)
                    videoForAction = null
                },
                onDelete = {
                    AerialVideoCache.deleteFromCache(context, v.url)
                    thumbnails.remove(v.url)
                    if (v.url in selectedUrlSet) {
                        val newSet = selectedUrlSet - v.url
                        onSelectVideoUrl(if (newSet.isEmpty()) null else newSet.joinToString(","))
                    }
                    refreshCache()
                    videoForAction = null
                },
                onDismiss = { videoForAction = null }
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
// Stream All Info Banner (When in pure streaming mode)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StreamAllInfoCard(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .width(620.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x14FFFFFF))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                .padding(horizontal = 36.dp, vertical = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.WifiTethering, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Streaming All 27 Scenes",
                    style = Ty.Title.copy(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    "Screensaver streams Apple 1080p aerial footage live over internet with smooth 1.5s crossfading.",
                    style = Ty.Body.copy(color = Color(0xBBFFFFFF), fontSize = 14.sp),
                    textAlign = TextAlign.Center
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x1FFFFFFF))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Storage, null, tint = Color(0xFF30D158), modifier = Modifier.size(18.dp))
                Text(
                    "0 MB disk storage used · Background caching disabled",
                    style = Ty.Small.copy(color = Color(0xFF30D158), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }

            Text(
                "To save specific scenes on TV for offline playback, switch to \"Downloaded Scenes\" above.",
                style = Ty.Small.copy(color = Color(0x66FFFFFF), fontSize = 12.sp),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode Pills Component
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AerialModePills(
    currentPlayMode: AerialPlayMode,
    autoFocus: FocusRequester,
    modeFocusRequester: FocusRequester,
    onMoveDownToGrid: () -> Unit,
    onMoveDownToBottom: () -> Unit,
    onSelectMode: (AerialPlayMode) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AerialPlayMode.entries.forEachIndexed { idx, mode ->
            val isActive = currentPlayMode == mode
            Focusable(
                onClick = { onSelectMode(mode) },
                modifier = Modifier
                    .weight(1f)
                    .then(if (idx == 0) Modifier.focusRequester(autoFocus).focusRequester(modeFocusRequester) else Modifier)
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            if (currentPlayMode == AerialPlayMode.STREAM_ALL) {
                                onMoveDownToBottom()
                            } else {
                                onMoveDownToGrid()
                            }
                            true
                        } else false
                    },
                shape = RoundedCornerShape(14.dp),
                idle = if (isActive) Color(0x280A84FF) else Color(0x10FFFFFF),
                idleOutline = isActive
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                AerialPlayMode.STREAM_ALL -> Icons.Rounded.WifiTethering
                                AerialPlayMode.DOWNLOADED -> Icons.Rounded.CloudDownload
                            },
                            contentDescription = null,
                            tint = if (isActive) Color(0xFF64B5F6) else Color(0x55FFFFFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            mode.label,
                            style = Ty.Body.copy(
                                color = if (isActive) Color.White else Color(0xBBFFFFFF),
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        )
                        if (isActive) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF30D158), modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        mode.subtitle,
                        style = Ty.Small.copy(color = Color(0x55FFFFFF), fontSize = 11.sp),
                        maxLines = 2
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Grid Section
// ─────────────────────────────────────────────────────────────────────────────
private sealed class GridEntry {
    data class Header(val location: String) : GridEntry()
    data class VideoItem(val video: AppleAerialVideo) : GridEntry()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AerialGrid(
    modifier: Modifier,
    cachedUrls: Set<String>,
    selectedUrlSet: Set<String>,
    thumbnails: SnapshotStateMap<String, Bitmap?>,
    downloadProgressMap: SnapshotStateMap<String, AerialVideoCache.DownloadProgress>,
    firstGridItemFocusRequester: FocusRequester,
    onMoveUpFromGrid: () -> Unit,
    onItemClick: (AppleAerialVideo, Boolean) -> Unit,
    onFirstFrame: (String, Bitmap) -> Unit
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
                videos.forEach { add(GridEntry.VideoItem(it)) }
            }
        }

        var videoCardIndex = 0
        flatItems.forEach { entry ->
            when (entry) {
                is GridEntry.Header -> {
                    item(key = "hdr_${entry.location}", span = { GridItemSpan(3) }) {
                        Text(
                            entry.location.uppercase(),
                            style = Ty.Small.copy(
                                color = Color(0x44FFFFFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            ),
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }
                }
                is GridEntry.VideoItem -> {
                    val video = entry.video
                    val currentCardIdx = videoCardIndex++
                    item(key = video.url) {
                        val isCached         = video.url in cachedUrls
                        val isSelected       = video.url in selectedUrlSet
                        val downloadProgress = downloadProgressMap[video.url]
                        val thumbnail        = thumbnails[video.url]

                        val cardModifier = when {
                            currentCardIdx == 0 -> Modifier.focusRequester(firstGridItemFocusRequester)
                            else -> Modifier
                        }.onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP &&
                                currentCardIdx < 3) {
                                onMoveUpFromGrid()
                                true
                            } else false
                        }

                        AerialThumbnailCard(
                            modifier = cardModifier,
                            video = video,
                            thumbnail = thumbnail,
                            isCached = isCached,
                            isSelected = isSelected,
                            downloadProgress = downloadProgress,
                            onAction = { onItemClick(video, isCached) },
                            onCapturedFrame = { bmp -> onFirstFrame(video.url, bmp) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Card: Live preview video on focus ONLY for downloaded videos; static image otherwise
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AerialThumbnailCard(
    modifier: Modifier = Modifier,
    video: AppleAerialVideo,
    thumbnail: Bitmap?,
    isCached: Boolean,
    isSelected: Boolean,
    downloadProgress: AerialVideoCache.DownloadProgress?,
    onAction: () -> Unit,
    onCapturedFrame: (Bitmap) -> Unit
) {
    val gradColors = locationGradients[video.location]
        ?: listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))

    val borderColor = when {
        downloadProgress != null -> Color(0xFF0A84FF)
        isSelected               -> Color(0xFF0A84FF)
        isCached                 -> Color(0xFF30D158).copy(alpha = 0.6f)
        else                     -> Color(0x18FFFFFF)
    }

    Focusable(
        onClick = onAction,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        idle = Color.Transparent,
        idleOutline = isSelected || isCached || downloadProgress != null,
    ) { focused ->
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (focused) 2.dp else if (isSelected || downloadProgress != null) 2.dp else if (isCached) 1.dp else 0.dp,
                    color = if (focused) Color.White else borderColor,
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            // Live Video stream preview ONLY when focused AND video is already downloaded locally
            if (focused && isCached && downloadProgress == null) {
                LiveCardVideoPreview(
                    url = video.url,
                    onFirstFrameCaptured = onCapturedFrame
                )
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
            } else if (thumbnail != null) {
                // High-resolution instant image from bundled asset / disk cache
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.45f to Color.Transparent,
                                1f to Color(0xDD000000)
                            )
                        )
                )
            } else {
                // First-frame scenic gradient backdrop fallback
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(gradColors)))
                Box(Modifier.fillMaxSize().background(Color(0x0AFFFFFF)))
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        video.location.take(2).uppercase(),
                        style = Ty.Display.copy(
                            color = Color.White.copy(alpha = 0.10f),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black
                        )
                    )
                }
            }

            // Top-right badge
            Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                when {
                    downloadProgress != null -> StatusChip("${downloadProgress.percent}%", Color(0xFF0A84FF))
                    focused && isCached      -> StatusChip("Live ▶", Color(0xFF30D158))
                    isSelected               -> StatusChip("Active ✓", Color(0xFF0A84FF))
                    isCached                 -> StatusChip("✓ Saved", Color(0xFF30D158))
                    else                     -> StatusChip("${video.sizeMb} MB", Color(0x33FFFFFF))
                }
            }

            // In-flight download progress overlay
            if (downloadProgress != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xD9000000))
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
                            style = Ty.Small.copy(color = Color(0x99FFFFFF), fontSize = 11.sp)
                        )
                    }
                }
            } else {
                // Bottom overlay: title + size + options
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
                        when {
                            isCached -> {
                                Icon(Icons.Rounded.MoreVert, null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(12.dp))
                                Text("${video.sizeMb} MB · Options", style = Ty.Small.copy(color = Color(0xCCFFFFFF), fontSize = 11.sp))
                            }
                            else -> {
                                Icon(Icons.Rounded.Download, null, tint = Color(0x99FFFFFF), modifier = Modifier.size(11.dp))
                                Text("${video.sizeMb} MB · Download", style = Ty.Small.copy(color = Color(0x99FFFFFF), fontSize = 11.sp))
                            }
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
    onFirstFrameCaptured: (Bitmap) -> Unit
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
                            val playSource = AerialVideoCache.getPlayableSource(context, url)
                            mp.setDataSource(playSource)
                            mp.isLooping = true
                            mp.setVolume(0f, 0f) // Silent preview
                            mp.setOnPreparedListener {
                                mp.start()
                            }
                            mp.setOnInfoListener { _, what, _ ->
                                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                    postDelayed({
                                        bitmap?.let { bmp -> onFirstFrameCaptured(bmp) }
                                    }, 250)
                                }
                                true
                            }
                            mp.setOnErrorListener { _, _, _ -> true }
                            mp.prepareAsync()
                        } catch (_: Exception) {}
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        player?.stop()
                        player?.release()
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
// Idle Timer Row Component
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun IdleTimerRow(
    idleMinutes: Int,
    onMoveUp: () -> Unit,
    onSelectIdleMinutes: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "IDLE TIMER",
            style = Ty.Small.copy(color = Color(0x44FFFFFF), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
        )
        Spacer(Modifier.width(4.dp))
        listOf(2 to "2 min", 5 to "5 min", 10 to "10 min", 15 to "15 min", 0 to "Off").forEach { (mins, label) ->
            val sel = idleMinutes == mins
            Focusable(
                onClick = { onSelectIdleMinutes(mins) },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                            onMoveUp()
                            true
                        } else false
                    },
                shape = RoundedCornerShape(10.dp),
                idle = if (sel) Color(0x330A84FF) else Color(0x10FFFFFF),
                idleOutline = sel
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = Ty.Body.copy(
                            color = if (sel) Color(0xFF64B5F6) else Color.White,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Video Action Popup Dialog (Set as Solo / Toggle Multi-Select / Loop All / Delete)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VideoActionDialog(
    video: AppleAerialVideo,
    isSelected: Boolean,
    isSole: Boolean,
    hasSelection: Boolean,
    onToggleSole: () -> Unit,
    onToggleMultiSelect: () -> Unit,
    onLoopAll: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val dialogFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xBF000000))
            .modalTrap()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                     ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) {
                    if (videoForAction != null) {
                        videoForAction = null
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
                .width(460.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF282830), Color(0xFF18181E))))
                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
                .focusGroup()
                .focusRestorer(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Tv, null, tint = Color(0xFF64B5F6), modifier = Modifier.size(36.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    video.location,
                    style = Ty.Title.copy(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    video.subtext,
                    style = Ty.Body.copy(color = Color(0xCCFFFFFF), fontSize = 14.sp),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${video.sizeMb} MB · Saved on TV",
                    style = Ty.Small.copy(color = Color(0xFF81C784), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                )
            }

            Spacer(Modifier.height(2.dp))

            // Action 1: Set as Sole Screensaver / Switch to Loop All
            Focusable(
                onClick = onToggleSole,
                modifier = Modifier.fillMaxWidth().focusRequester(dialogFocus),
                shape = RoundedCornerShape(14.dp),
                idle = if (isSole) Color(0x280A84FF) else Color(0x18FFFFFF),
                idleOutline = isSole
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isSole) Icons.Rounded.Loop else Icons.Rounded.PlayCircle,
                        null,
                        tint = if (isSole) Color(0xFF64B5F6) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isSole) "Switch to Loop All Saved" else "Set as Sole Screensaver",
                        style = Ty.Body.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    )
                }
            }

            // Action 2: Add to / Remove from Multi-Select Playlist
            Focusable(
                onClick = onToggleMultiSelect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                idle = if (isSelected && !isSole) Color(0x280A84FF) else Color(0x18FFFFFF),
                idleOutline = isSelected && !isSole
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline,
                        null,
                        tint = if (isSelected) Color(0xFF30D158) else Color(0xCCFFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isSelected) "Remove from Playlist" else "Add to Screensaver Playlist",
                        style = Ty.Body.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    )
                }
            }

            // Action 3: Loop All Saved Scenes (if currently filtered)
            if (hasSelection) {
                Focusable(
                    onClick = onLoopAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    idle = Color(0x12FFFFFF)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.PlaylistPlay, null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Loop All Saved Scenes",
                            style = Ty.Body.copy(color = Color(0xEEFFFFFF), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        )
                    }
                }
            }

            // Action 4: Delete from TV Storage
            Focusable(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                idle = Color(0x28FF453A)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF453A), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Delete from TV Storage",
                        style = Ty.Body.copy(color = Color(0xFFFF453A), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                }
            }

            TvButton(
                text = "Cancel",
                onClick = onDismiss,
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirm Delete All Dialog
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ConfirmDeleteAllDialog(
    count: Int,
    totalMb: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val deleteDialogFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xBF000000))
            .modalTrap()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    (ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK ||
                     ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ESCAPE)
                ) {
                    if (videoForAction != null) {
                        videoForAction = null
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
                .width(440.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF282830), Color(0xFF18181E))))
                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(24.dp))
                .padding(26.dp)
                .focusGroup()
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
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Focusable(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f).focusRequester(deleteDialogFocus),
                    shape = RoundedCornerShape(12.dp),
                    idle = Color(0xFFFF453A)
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Delete All", style = Ty.Button.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }
                }
                TvButton(text = "Cancel", onClick = onDismiss, primary = false, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pill Chip Badge
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun StatusChip(text: String, bg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg.copy(alpha = 0.88f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = Ty.Small.copy(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        )
    }
}
