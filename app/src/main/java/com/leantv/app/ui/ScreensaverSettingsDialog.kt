package com.leantv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.focus.onFocusChanged
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Persistent & Asset Thumbnail Store
// ─────────────────────────────────────────────────────────────────────────────
object AerialThumbnailStore {
    // Process-wide memory cache: the 2nd open of the screensaver popup shows every thumbnail instantly.
    private val mem = object : LruCache<String, ImageBitmap>(14 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 2
    }

    /** Cards are ~360px wide on a 1080p TV: decoding at ~480px keeps 27 thumbnails tiny and fast. */
    private fun sample(w: Int): Int {
        var n = 1
        while (w / (n * 2) >= 400) n *= 2
        return n
    }

    private fun fileName(url: String) = url.substringAfterLast('/').substringBeforeLast('.') + ".jpg"

    fun peek(url: String): ImageBitmap? = mem.get(url)

    private fun decode(open: () -> java.io.InputStream?): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample(bounds.outWidth)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        open()?.use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (_: Throwable) { null }

    /** Memory -> bundled asset -> disk -> (optional) frame pulled from a downloaded video. Call off the main thread. */
    suspend fun get(context: Context, url: String, allowExtract: Boolean): ImageBitmap? {
        mem.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            val name = fileName(url)
            var bmp = decode { context.assets.open("aerial_thumbs/$name") }
            if (bmp == null) {
                val f = File(File(context.cacheDir, "aerial_thumbs"), name)
                if (f.exists() && f.length() > 500L) bmp = decode { f.inputStream() }
            }
            if (bmp == null && allowExtract) bmp = extractFromVideo(context, url)
            bmp?.asImageBitmap()?.also { mem.put(url, it) }
        }
    }

    private fun extractFromVideo(context: Context, url: String): Bitmap? = try {
        val file = AerialVideoCache.getCacheFile(context, url)
        if (!file.exists() || file.length() <= AerialVideoCache.MIN_VALID_SIZE) null else {
            val r = MediaMetadataRetriever()
            r.setDataSource(file.absolutePath)
            val frame = r.getFrameAtTime(2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            r.release()
            frame?.let {
                val small = if (it.width > 480) Bitmap.createScaledBitmap(it, 480, 480 * it.height / it.width, true) else it
                save(context, url, small)
                small
            }
        }
    } catch (_: Throwable) { null }

    /** Fills the memory cache in the background (called shortly after the launcher starts). */
    suspend fun prewarm(context: Context) {
        for (v in APPLE_AERIAL_PRESETS) {
            if (peek(v.url) == null) get(context, v.url, allowExtract = false)
            kotlinx.coroutines.yield()
        }
    }

    fun save(context: Context, url: String, bmp: Bitmap) {
        runCatching {
            val dir = File(context.cacheDir, "aerial_thumbs")
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName(url)).outputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Light focus surface for this popup. Everything that changes on focus (scale, fill, ring, glow) is
// animated INSIDE draw/graphicsLayer lambdas, so moving the remote never recomposes or re-measures.
// No elevation shadow (the old one was the main cost per move). Optional [glow] draws a cheap soft halo.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SsFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    idle: Color = Color.Transparent,
    focusedBg: Color = Color(0x33FFFFFF),
    ring: Color = Color.White,
    idleRing: Color = Color(0x22FFFFFF),
    glow: Color? = null,
    glowRadius: Dp = 14.dp,
    idleOutline: Boolean = false,
    scaleOnFocus: Float = 1.03f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val p = animateFloatAsState(if (focused) 1f else 0f, tween(110, easing = LinearOutSlowInEasing), label = "ssf")
    Box(
        modifier
            .graphicsLayer {
                val sc = 1f + (scaleOnFocus - 1f) * p.value
                scaleX = sc
                scaleY = sc
            }
            .drawBehind {
                val g = glow
                if (g != null && p.value > 0.01f) {
                    val r = glowRadius.toPx()
                    for (i in 1..3) {
                        val grow = i * 5.dp.toPx()
                        drawRoundRect(
                            color = g.copy(alpha = 0.20f * p.value / i),
                            topLeft = Offset(-grow, -grow),
                            size = Size(size.width + grow * 2, size.height + grow * 2),
                            cornerRadius = CornerRadius(r + grow)
                        )
                    }
                }
            }
            .clip(shape)
            .drawBehind { drawRect(lerp(idle, focusedBg, p.value)) }
            .drawWithContent {
                drawContent()
                val outline = shape.createOutline(size, layoutDirection, this)
                if (p.value > 0.01f) {
                    drawOutline(outline, ring.copy(alpha = 0.9f * p.value), style = Stroke(1.5.dp.toPx() + 0.5.dp.toPx() * p.value))
                } else if (idleOutline) {
                    drawOutline(outline, idleRing, style = Stroke(1.dp.toPx()))
                }
            }
            .onFocusChanged {
                if (it.isFocused && !focused) TvSound.playMove(view)
                focused = it.isFocused
            }
            .focusable()
            .clickable(onClick = {
                TvSound.playClick(view)
                onClick()
            })
    ) {
        content(focused)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Process-wide download manager: downloads keep running if the popup is closed,
// show their progress again when it is re-opened, and can be cancelled with OK.
// ─────────────────────────────────────────────────────────────────────────────
private object AerialDownloads {
    val progress = mutableStateMapOf<String, AerialVideoCache.DownloadProgress>()
    val finishTick = mutableIntStateOf(0)
    private val jobs = HashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(context: Context, url: String) {
        if (jobs[url]?.isActive == true) return
        val appCtx = context.applicationContext
        progress[url] = AerialVideoCache.DownloadProgress()
        jobs[url] = scope.launch {
            try {
                AerialVideoCache.downloadToCache(appCtx, url) { prog -> progress[url] = prog }
            } catch (e: CancellationException) {
                // Cancelled by the user: throw away the partial file so no storage is wasted.
                runCatching {
                    val target = AerialVideoCache.getCacheFile(appCtx, url)
                    File(target.parentFile, "${target.name}.download").delete()
                }
                throw e
            } finally {
                progress.remove(url)
                jobs.remove(url)
                finishTick.intValue++
            }
        }
    }

    fun cancel(url: String) {
        jobs[url]?.cancel()
    }
}

/** Focus target + remembers "the last left-list item you were on" so Left from the grid returns there. */
private fun Modifier.leftItem(req: FocusRequester, onFocused: (FocusRequester) -> Unit): Modifier =
    this.focusRequester(req).onFocusChanged { if (it.isFocused) onFocused(req) }


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

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppleMotion.DurStandard, easing = AppleMotion.EaseOut),
        label = "scrim"
    )
    val panelScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.97f,
        animationSpec = tween(170, easing = LinearOutSlowInEasing),
        label = "scale"
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(150, easing = LinearOutSlowInEasing),
        label = "alpha"
    )

    val downloadProgressMap = AerialDownloads.progress
    var cacheVersion by remember { mutableIntStateOf(0) }
    fun refreshCache() { cacheVersion++ }

    var cachedUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var totalCacheMb by remember { mutableLongStateOf(0L) }

    // Pre-load thumbnails & cache status in background (0 disk I/O on UI thread)
    LaunchedEffect(cacheVersion, AerialDownloads.finishTick.intValue) {
        withContext(Dispatchers.IO) {
            AerialVideoCache.migrateLegacyCacheIfNeeded(context)
            val cached = APPLE_AERIAL_PRESETS.filter { AerialVideoCache.isCached(context, it.url) }.map { it.url }.toSet()
            val total = AerialVideoCache.getTotalCacheSizeBytes(context) / (1024 * 1024)
            withContext(Dispatchers.Main) {
                cachedUrls = cached
                totalCacheMb = total
            }

        }
    }

    // Modal popup states
    var videoToDelete by remember { mutableStateOf<AppleAerialVideo?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val hasSubDialog = videoToDelete != null || confirmDeleteAll

    // ── Remote-control focus map ───────────────────────────────────────────────────────────
    //   LEFT column (settings, one vertical list)   ⇄   RIGHT (scene grid)
    //   • Up / Down  : move through the left list, or through the grid rows
    //   • Right      : from any left item, jump into the grid (back to the last scene you were on)
    //   • Left       : from the grid's first column, jump back to the left list (last item you were on)
    //   • OK         : select / download / delete        • Back : close
    val streamReq = remember { FocusRequester() }
    val downloadedReq = remember { FocusRequester() }
    val chipReqs = remember { List(TIMER_OPTIONS.size) { FocusRequester() } }
    val previewReq = remember { FocusRequester() }
    val clearAllReq = remember { FocusRequester() }
    val doneReq = remember { FocusRequester() }
    val gridZoneReq = remember { FocusRequester() }
    var lastLeftReq by remember { mutableStateOf<FocusRequester?>(null) }
    var returnToGrid by remember { mutableStateOf(false) }
    var openedOnce by remember { mutableStateOf(false) }

    val selTimerIdx = TIMER_OPTIONS.indexOfFirst { it.first == idleMinutes }.coerceAtLeast(0)
    val hasDownloads = cachedUrls.isNotEmpty()

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

    // Focus is pulled into this popup when it opens, and handed back to where the user was
    // whenever a delete-confirmation sub-popup closes.
    LaunchedEffect(hasSubDialog) {
        if (hasSubDialog) return@LaunchedEffect
        delay(50)
        val target = when {
            !openedOnce  -> if (autoCache) downloadedReq else streamReq   // the currently applied source
            returnToGrid -> gridZoneReq
            else         -> lastLeftReq ?: streamReq
        }
        // The remembered item may have disappeared (e.g. "Clear All" after deleting everything).
        runCatching { target.requestFocus() }.onFailure { runCatching { previewReq.requestFocus() } }
        openedOnce = true
    }

    Box(
        Modifier
            .fillMaxSize()
            .modalTrap()
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
        Row(
            Modifier
                .width(1160.dp)
                .fillMaxHeight(0.94f)
                .graphicsLayer { scaleX = panelScale; scaleY = panelScale; alpha = panelAlpha }
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF23232A), Color(0xFF15151B))))
                .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(26.dp))
                .padding(26.dp),
            horizontalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            // ───────────────────────── LEFT: settings list ─────────────────────────
            // Every item has an explicit focus target and explicit Up/Down/Left/Right neighbours,
            // so the remote never depends on guesswork.
            Column(
                Modifier
                    .width(340.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Screensaver",
                    style = Ty.Title.copy(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    "Apple Aerial cinematic footage",
                    style = Ty.Dim.copy(fontSize = 12.sp, color = Color(0x99FFFFFF))
                )
                Spacer(Modifier.height(2.dp))

                SettingsLabel("SOURCE")
                SettingChoiceRow(
                    title = "Stream All",
                    subtitle = "Plays online. Saved scenes play offline.",
                    selected = !autoCache,
                    icon = Icons.Rounded.WifiTethering,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = downloadedReq
                            left = FocusRequester.Cancel
                            right = gridZoneReq
                        }
                        .leftItem(streamReq) { lastLeftReq = it },
                    onClick = {
                        onToggleAutoCache(false)
                        onSelectVideoUrl(null)
                    }
                )
                SettingChoiceRow(
                    title = "Downloaded Only",
                    subtitle = if (hasDownloads) "${cachedUrls.size} saved scenes, $totalCacheMb MB. 100% offline."
                               else "Nothing saved yet. Pick scenes on the right.",
                    selected = autoCache,
                    icon = Icons.Rounded.CloudDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            up = streamReq
                            down = chipReqs[selTimerIdx % TIMER_COLS]
                            left = FocusRequester.Cancel
                            right = gridZoneReq
                        }
                        .leftItem(downloadedReq) { lastLeftReq = it },
                    onClick = {
                        onToggleAutoCache(true)
                        onSelectVideoUrl(null)
                    }
                )

                Spacer(Modifier.height(2.dp))
                SettingsLabel(
                    "IDLE TIMER",
                    if (idleMinutes > 0) "Starts after ${formatIdle(idleMinutes)}" else "Disabled"
                )
                IdleTimerChips(
                    idleMinutes = idleMinutes,
                    onSelect = onSelectIdleMinutes,
                    requesters = chipReqs,
                    upTarget = downloadedReq,
                    downTarget = previewReq,
                    rightTarget = gridZoneReq,
                    onFocused = { lastLeftReq = it }
                )

                Spacer(Modifier.weight(1f))

                SettingsButton(
                    label = "Preview Screensaver",
                    icon = Icons.Rounded.PlayArrow,
                    idle = Color(0xFF0A84FF),
                    focusedBg = Color(0xFF3AA0FF),
                    ring = Color(0xFFBFE0FF),
                    glow = Color(0xFF0A84FF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            up = chipReqs[TIMER_COLS * (TIMER_OPTIONS.size / TIMER_COLS - 1) + selTimerIdx % TIMER_COLS]
                            down = if (hasDownloads) clearAllReq else doneReq
                            left = FocusRequester.Cancel
                            right = gridZoneReq
                        }
                        .leftItem(previewReq) { lastLeftReq = it },
                    onClick = onStartScreensaverNow
                )
                if (hasDownloads) {
                    SettingsButton(
                        label = "Clear All Downloads ($totalCacheMb MB)",
                        icon = Icons.Rounded.DeleteSweep,
                        idle = Color(0x28FF3B30),
                        contentColor = Color(0xFFFF6B60),
                        focusedBg = Color(0xFFFF3B30),
                        ring = Color(0xFFFFB4AB),
                        glow = Color(0xFFFF3B30),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusProperties {
                                up = previewReq
                                down = doneReq
                                left = FocusRequester.Cancel
                                right = gridZoneReq
                            }
                            .leftItem(clearAllReq) { lastLeftReq = it },
                        onClick = {
                            returnToGrid = false
                            confirmDeleteAll = true
                        }
                    )
                }
                SettingsButton(
                    label = "Done",
                    icon = Icons.Rounded.Check,
                    idle = Color(0x22FFFFFF),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            up = if (hasDownloads) clearAllReq else previewReq
                            down = FocusRequester.Cancel
                            left = FocusRequester.Cancel
                            right = gridZoneReq
                        }
                        .leftItem(doneReq) { lastLeftReq = it },
                    onClick = onDismiss
                )
            }

            // ───────────────────────── RIGHT: scene grid ─────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "AERIAL SCENES",
                        style = Ty.Small.copy(
                            color = Color(0x99FFFFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        "OK: download scene  •  OK on a saved scene: remove  •  Left: settings",
                        style = Ty.Small.copy(color = Color(0x66FFFFFF), fontSize = 11.sp)
                    )
                }

                DownloadedGridPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(gridZoneReq),
                    leftExit = { lastLeftReq ?: streamReq },
                    cachedUrls = cachedUrls,
                    downloadProgressMap = downloadProgressMap,
                    onItemClick = { video, isCached ->
                        returnToGrid = true
                        when {
                            // Downloading right now → OK cancels it
                            video.url in downloadProgressMap -> AerialDownloads.cancel(video.url)
                            // Saved → confirm removal
                            isCached -> videoToDelete = video
                            // Not saved → start downloading
                            else -> AerialDownloads.start(context, video.url)
                        }
                    }
                )
            }
        }

        // Popup: Confirm Delete Single Scene (Delete & Cancel: Identical type & size)
        if (videoToDelete != null) {
            val v = videoToDelete!!
            ConfirmDeleteSingleDialog(
                video = v,
                onConfirm = {
                    AerialVideoCache.deleteFromCache(context, v.url)
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
// Remote-friendly building blocks for the left settings list
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsLabel(text: String, value: String? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = Ty.Small.copy(color = Color(0x88FFFFFF), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        )
        if (value != null) {
            Text(
                value,
                style = Ty.Small.copy(color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun SettingChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    SsFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        idle = if (selected) Color(0x330A84FF) else Color(0x12FFFFFF),
        idleOutline = selected,
        scaleOnFocus = 1.02f
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFF5E5CE6)))
                        else Brush.linearGradient(listOf(Color(0x2AFFFFFF), Color(0x1AFFFFFF)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, null,
                    tint = if (selected || focused) Color.White else Color(0xBBFFFFFF),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    style = Ty.Body.copy(color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
                )
                Text(
                    subtitle,
                    style = Ty.Small.copy(color = Color(0x99FFFFFF), fontSize = 11.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF30D158), modifier = Modifier.size(20.dp))
            }
        }
    }
}

private const val TIMER_COLS = 4
private val TIMER_OPTIONS = listOf(
    1 to "1 min", 2 to "2 min", 3 to "3 min", 5 to "5 min",
    10 to "10 min", 15 to "15 min", 20 to "20 min", 30 to "30 min",
    45 to "45 min", 60 to "1 hr", 120 to "2 hr", 0 to "Never"
)

private fun formatIdle(mins: Int): String = when {
    mins < 60 -> "$mins min"
    mins % 60 == 0 -> if (mins == 60) "1 hour" else "${mins / 60} hours"
    else -> "$mins min"
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun IdleTimerChips(
    idleMinutes: Int,
    onSelect: (Int) -> Unit,
    requesters: List<FocusRequester>,
    upTarget: FocusRequester,
    downTarget: FocusRequester,
    rightTarget: FocusRequester,
    onFocused: (FocusRequester) -> Unit
) {
    val options = TIMER_OPTIONS
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(TIMER_COLS).forEachIndexed { rowIdx, rowOptions ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEachIndexed { i, (mins, label) ->
                    val idx = rowIdx * TIMER_COLS + i
                    val sel = idleMinutes == mins
                    SsFocusable(
                        onClick = { onSelect(mins) },
                        modifier = Modifier
                            .weight(1f)
                            .focusProperties {
                                up = if (rowIdx == 0) upTarget else requesters[idx - TIMER_COLS]
                                down = if (idx + TIMER_COLS < options.size) requesters[idx + TIMER_COLS] else downTarget
                                left = if (i == 0) FocusRequester.Cancel else requesters[idx - 1]
                                right = if (i == rowOptions.size - 1) rightTarget else requesters[idx + 1]
                            }
                            .leftItem(requesters[idx], onFocused),
                        shape = RoundedCornerShape(10.dp),
                        idle = if (sel) Color(0xFF0A84FF) else Color(0x18FFFFFF),
                        focusedBg = if (sel) Color(0xFF2997FF) else Color(0x40FFFFFF),
                        scaleOnFocus = 1.05f
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = Ty.Body.copy(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsButton(
    label: String,
    icon: ImageVector,
    idle: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    focusedContentColor: Color = Color.White,
    focusedBg: Color = Color(0x44FFFFFF),
    ring: Color = Color.White,
    glow: Color? = null,
    onClick: () -> Unit
) {
    SsFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        idle = idle,
        focusedBg = focusedBg,
        ring = ring,
        glow = glow,
        glowRadius = 13.dp,
        scaleOnFocus = 1.02f
    ) { focused ->
        val c = if (focused) focusedContentColor else contentColor
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = c, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = Ty.Button.copy(color = c, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
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
    SsFocusable(
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
private val GRID_ENTRIES: List<GridEntry> by lazy {
    buildList {
        APPLE_AERIAL_PRESETS.groupBy { it.location }.forEach { (loc, videos) ->
            add(GridEntry.Header(loc))
            videos.forEachIndexed { idx, v -> add(GridEntry.VideoItem(v, col = idx % 3)) }
        }
    }
}

private sealed class GridEntry {
    data class Header(val location: String) : GridEntry()
    data class VideoItem(val video: AppleAerialVideo, val col: Int) : GridEntry()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DownloadedGridPanel(
    modifier: Modifier,
    leftExit: () -> FocusRequester,
    cachedUrls: Set<String>,
    downloadProgressMap: SnapshotStateMap<String, AerialVideoCache.DownloadProgress>,
    onItemClick: (AppleAerialVideo, Boolean) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
    ) {
        GRID_ENTRIES.forEach { entry ->
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

                        AerialThumbnailCard(
                            video = video,
                            col = col,
                            leftExit = leftExit,
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
// Card: static thumbnail + status badge (no live video, keeps the grid snappy)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AerialThumbnailCard(
    video: AppleAerialVideo,
    col: Int,
    leftExit: () -> FocusRequester,
    isCached: Boolean,
    downloadProgress: AerialVideoCache.DownloadProgress?,
    onAction: () -> Unit
) {
    val context = LocalContext.current
    // Loaded per card, only when the card is composed (visible rows) and served from the shared memory cache.
    val thumb by produceState<ImageBitmap?>(AerialThumbnailStore.peek(video.url), video.url, isCached) {
        if (value == null) value = AerialThumbnailStore.get(context, video.url, allowExtract = isCached)
    }
    val borderColor = when {
        downloadProgress != null -> Color(0xFF0A84FF)
        isCached                 -> Color(0x9930D158)
        else                     -> Color(0x18FFFFFF)
    }

    SsFocusable(
        onClick = onAction,
        modifier = Modifier.focusProperties {
            if (col == 0) left = leftExit()
            if (col == 2) right = FocusRequester.Cancel
        },
        shape = RoundedCornerShape(14.dp),
        idle = Color.Transparent,
        focusedBg = Color.Transparent,
        idleRing = borderColor,
        idleOutline = isCached || downloadProgress != null,
        scaleOnFocus = 1.04f
    ) { focused ->
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            val t = thumb
            if (t != null) {
                Image(
                    bitmap = t,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF1E2028)))
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
                        Text(
                            if (focused) "Press OK to cancel" else "Downloading…",
                            style = Ty.Small.copy(
                                color = if (focused) Color(0xFFFF8A80) else Color(0x99FFFFFF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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

            SsFocusable(
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
            .modalTrap(autoFocus = false)
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
                SsFocusable(
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
                    idle = Color(0x33FF3B30),
                    focusedBg = Color(0xFFFF3B30),
                    ring = Color(0xFFFFB4AB),
                    glow = Color(0xFFFF3B30),
                    glowRadius = 12.dp,
                    scaleOnFocus = 1.04f
                ) { f ->
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Delete",
                            style = Ty.Button.copy(color = if (f) Color.White else Color(0xFFFF6B60), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                SsFocusable(
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
                    focusedBg = Color(0x55FFFFFF),
                    scaleOnFocus = 1.04f
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
            .modalTrap(autoFocus = false)
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
                SsFocusable(
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
                    idle = Color(0x33FF3B30),
                    focusedBg = Color(0xFFFF453A),
                    ring = Color(0xFFFFB4AB),
                    glow = Color(0xFFFF453A),
                    glowRadius = 12.dp,
                    scaleOnFocus = 1.04f
                ) { f ->
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Delete All",
                            style = Ty.Button.copy(color = if (f) Color.White else Color(0xFFFF6B60), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }

                SsFocusable(
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
                    focusedBg = Color(0x55FFFFFF),
                    scaleOnFocus = 1.04f
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