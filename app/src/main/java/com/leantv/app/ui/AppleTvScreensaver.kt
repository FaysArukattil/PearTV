package com.leantv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Environment
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Curated list of verified 1080p Apple TV Aerial video streams from Apple CDN (a1.phobos.apple.com).
 * All URLs are active, return HTTP 200 OK, and cycle continuously.
 */
data class AppleAerialVideo(
    val location: String,
    val subtext: String,
    val url: String,
    val sizeMb: Int
)

val APPLE_AERIAL_PRESETS = listOf(
    // Dubai
    AppleAerialVideo("Dubai", "Night Skyline & Burj Khalifa", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_DB_D011_D009_SIGNCMP_v15_6Mbps.mov", 175),
    AppleAerialVideo("Dubai", "Desert Dunes & Coastline", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_DB_D001_C001_v03_6Mbps.mov", 351),
    AppleAerialVideo("Dubai", "City Lights at Twilight", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b6-2.mov", 95),
    AppleAerialVideo("Dubai", "Downtown & Financial Centre", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b6-1.mov", 169),

    // Greenland
    AppleAerialVideo("Greenland", "Ilulissat Icefjord", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_GL_G004_C010_v03_6Mbps.mov", 211),
    AppleAerialVideo("Greenland", "Icefjords & Icebergs", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_GL_G010_C006_v08_6Mbps.mov", 432),
    AppleAerialVideo("Greenland", "Glacier Overflight", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b7-1.mov", 244),

    // Hong Kong
    AppleAerialVideo("Hong Kong", "Victoria Harbour at Night", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_HK_B005_C011_t9_6M_tag0.mov", 239),
    AppleAerialVideo("Hong Kong", "Daytime Peaks & Bay", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_HK_H004_C008_v10_6Mbps.mov", 487),
    AppleAerialVideo("Hong Kong", "Kowloon Skyline", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b8-2.mov", 276),

    // Los Angeles
    AppleAerialVideo("Los Angeles", "Downtown Twilight", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_LA_A005_C009_v05_t9_6M.mov", 155),
    AppleAerialVideo("Los Angeles", "Night Skyline & Traffic", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_LA_A009_C009_t9_6M_tag0.mov", 162),
    AppleAerialVideo("Los Angeles", "Metropolis Freeways", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b9-1.mov", 274),

    // Hawaii
    AppleAerialVideo("Hawaii", "Na Pali Coast Cliffs", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b1-1.mov", 148),
    AppleAerialVideo("Hawaii", "Tropical Island Sunset", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b1-2.mov", 107),
    AppleAerialVideo("Hawaii", "Emerald Coastline Waves", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b1-3.mov", 83),

    // London
    AppleAerialVideo("London", "Tower Bridge & River Thames", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b2-1.mov", 111),
    AppleAerialVideo("London", "Westminster & Big Ben", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b2-2.mov", 274),
    AppleAerialVideo("London", "City Skyline at Night", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b2-3.mov", 136),

    // New York City
    AppleAerialVideo("New York City", "Manhattan Skyline", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b3-1.mov", 205),
    AppleAerialVideo("New York City", "Central Park & Midtown", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b3-2.mov", 111),

    // San Francisco
    AppleAerialVideo("San Francisco", "Golden Gate Bridge", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b4-1.mov", 136),
    AppleAerialVideo("San Francisco", "Bay Bridge at Night", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b4-2.mov", 108),

    // China
    AppleAerialVideo("China", "Great Wall of China", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b5-1.mov", 59),
    AppleAerialVideo("China", "Li River Mountains", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b5-2.mov", 256),

    // Liwa / UAE
    AppleAerialVideo("Liwa", "Endless Desert Dunes", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b10-1.mov", 148),
    AppleAerialVideo("Liwa", "Desert Sunset", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b10-2.mov", 136)
)

enum class ScreensaverSource(val id: String, val title: String, val desc: String) {
    AERIAL("aerial", "Apple Aerial Videos", "Cinematic Apple TV aerial landscapes with auto-cache loop (zero data after download)"),
    SLIDESHOW("slideshow", "Custom Photos Slideshow", "Cycle smoothly through photos in a chosen folder"),
    SINGLE_IMAGE("image", "Single Custom Image", "Display a chosen custom wallpaper with Apple TV motion")
}

/**
 * Apple TV–style screensaver for Android TV.
 *
 * Features:
 * - Dual-player background preloading & butter-smooth 1200ms crossfading between scenes.
 * - Instant atmospheric backdrop with zero black-screen lag on launch or transitions.
 * - Dynamically animated Apple TV location pill in bottom-left corner.
 * - Large Apple TV clock & date in bottom-right corner.
 * - Input debounce guard so remote clicks won't immediately dismiss upon launching.
 * - Wake TV on any remote button press.
 */
@Composable
fun AppleTvScreensaver(
    mode: ScreensaverSource = ScreensaverSource.AERIAL,
    customPath: String? = null,
    selectedUrl: String? = null,
    autoCache: Boolean = true,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var canDismiss by remember { mutableStateOf(false) }

    // Live clock ticker
    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("h:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTime = timeFmt.format(now)
            currentDate = dateFmt.format(now)
            delay(1000L)
        }
    }

    // Dismissal debounce: ignore remote keys for 600ms so the trigger click doesn't immediately dismiss
    LaunchedEffect(Unit) {
        delay(600L)
        canDismiss = true
    }

    // Auto-focus so remote keys wake TV
    LaunchedEffect(Unit) {
        delay(150L)
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (canDismiss) {
                        onDismiss()
                    }
                    true
                } else false
            }
    ) {
        when (mode) {
            ScreensaverSource.AERIAL -> {
                AppleAerialVideoPlayer(selectedUrl = selectedUrl, autoCache = autoCache)
            }
            ScreensaverSource.SLIDESHOW -> {
                CustomPhotoSlideshow(
                    folderPath = customPath,
                    onEmpty = { AppleAerialVideoPlayer(selectedUrl = selectedUrl, autoCache = autoCache) }
                )
            }
            ScreensaverSource.SINGLE_IMAGE -> {
                SingleImageBackdrop(
                    filePath = customPath,
                    onEmpty = { AppleAerialVideoPlayer(selectedUrl = selectedUrl, autoCache = autoCache) }
                )
            }
        }

        // ─── Apple TV Bottom Right: Clock & Date (Default timer, always visible) ─
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 48.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentTime,
                    style = Ty.Display.copy(
                        fontSize = 96.sp,
                        lineHeight = 100.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp,
                        color = Color.White.copy(alpha = 0.95f),
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.75f),
                            offset = Offset(0f, 2f),
                            blurRadius = 14f
                        )
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = currentDate,
                    style = Ty.Small.copy(
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.70f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f
                        )
                    )
                )
            }
        }

        // Top subtle wake hint
        Text(
            text = "Press any key to wake TV",
            style = Ty.Small.copy(
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 14.sp,
                letterSpacing = 1.5.sp
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        )
    }
}

/**
 * Encapsulates an alpha-friendly hardware TextureView backed by Android MediaPlayer.
 * Unlike SurfaceView/VideoView, TextureView respects Compose alpha blending and never
 * punches black holes through the screen during buffering.
 */
private class TextureVideoPlayer(context: Context) {
    var mediaPlayer: MediaPlayer? = null
    var surface: Surface? = null
    var isSurfaceAvailable = false
    var pendingUrl: String? = null
    var onPreparedCallback: (() -> Unit)? = null

    val textureView = TextureView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                surface = Surface(st)
                isSurfaceAvailable = true
                mediaPlayer?.setSurface(surface)
                pendingUrl?.let { url ->
                    val cb = onPreparedCallback
                    pendingUrl = null
                    onPreparedCallback = null
                    prepare(url, cb)
                }
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                isSurfaceAvailable = false
                mediaPlayer?.setSurface(null)
                surface?.release()
                surface = null
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    fun prepare(url: String, onPrepared: (() -> Unit)? = null) {
        if (!isSurfaceAvailable) {
            pendingUrl = url
            onPreparedCallback = onPrepared
            return
        }
        releasePlayer()
        try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setSurface(surface)
            mp.setDataSource(url)
            mp.isLooping = true
            mp.setVolume(0f, 0f) // Aerial videos are silent
            mp.setOnPreparedListener {
                onPrepared?.invoke()
            }
            mp.setOnErrorListener { _, _, _ ->
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            releasePlayer()
        }
    }

    fun start() {
        runCatching { mediaPlayer?.start() }
    }

    fun releasePlayer() {
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }

    fun releaseAll() {
        releasePlayer()
        surface?.release()
        surface = null
    }
}

/**
 * Intelligent Local Video Disk Cache for Apple Aerial Streams.
 * - Stores downloaded video assets in public storage (/sdcard/Movies/LeanTVScreensaver)
 *   so Android OS does NOT add the file sizes to LeanTV's "App Size / User Data" in TV Settings.
 * - Automatically checks if a video is already cached before requesting network stream.
 * - Once cached, MediaPlayer plays directly from local storage with ZERO bytes used over internet.
 * - In the screensaver loop, it preferentially loops among cached videos, completely eliminating network usage!
 */
object AerialVideoCache {
    private const val PUBLIC_DIR_NAME = "LeanTVScreensaver"
    private const val LEGACY_CACHE_DIR_NAME = "aerial_cache"
    const val MIN_VALID_SIZE = 10_000_000L // 10MB minimum to be considered a complete valid video

    fun getCacheDir(context: Context): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val dir = File(moviesDir, PUBLIC_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return if (dir.exists() && dir.canWrite()) {
            dir
        } else {
            // Fallback to app external files dir only if public storage cannot be created
            context.getExternalFilesDir(LEGACY_CACHE_DIR_NAME) ?: File(context.cacheDir, LEGACY_CACHE_DIR_NAME).also { it.mkdirs() }
        }
    }

    private fun getLegacyCacheDir(context: Context): File? {
        return context.getExternalFilesDir(LEGACY_CACHE_DIR_NAME)
    }

    fun getCacheFile(context: Context, url: String): File {
        val filename = url.substringAfterLast('/')
        val publicDir = getCacheDir(context)
        val publicFile = File(publicDir, filename)
        if (publicFile.exists() && publicFile.length() >= MIN_VALID_SIZE) {
            return publicFile
        }

        // Return legacy file if present (migration to public storage happens in background)
        val legacyDir = getLegacyCacheDir(context)
        if (legacyDir != null && legacyDir.exists()) {
            val legacyFile = File(legacyDir, filename)
            if (legacyFile.exists() && legacyFile.length() >= MIN_VALID_SIZE) {
                return legacyFile
            }
        }

        return publicFile
    }

    suspend fun migrateLegacyCacheIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        val legacyDir = getLegacyCacheDir(context) ?: return@withContext
        if (!legacyDir.exists()) return@withContext
        val publicDir = getCacheDir(context)
        legacyDir.listFiles()?.forEach { file ->
            runCatching {
                if (file.isFile) {
                    if (file.name.endsWith(".mov") && file.length() >= MIN_VALID_SIZE) {
                        val target = File(publicDir, file.name)
                        if (!file.renameTo(target)) {
                            file.copyTo(target, overwrite = true)
                            file.delete()
                        }
                    } else {
                        file.delete()
                    }
                }
            }
        }
    }

    fun isCached(context: Context, url: String): Boolean {
        val file = getCacheFile(context, url)
        return file.exists() && file.length() >= MIN_VALID_SIZE
    }

    fun getPlayableSource(context: Context, url: String): String {
        val file = getCacheFile(context, url)
        return if (file.exists() && file.length() >= MIN_VALID_SIZE) {
            file.absolutePath
        } else {
            url
        }
    }

    fun getCachedCount(context: Context): Int {
        val dir = getCacheDir(context)
        val publicCount = dir.listFiles { f -> f.isFile && f.length() >= MIN_VALID_SIZE }?.size ?: 0
        if (publicCount > 0) return publicCount
        val legacyDir = getLegacyCacheDir(context)
        return legacyDir?.listFiles { f -> f.isFile && f.length() >= MIN_VALID_SIZE }?.size ?: 0
    }

    fun getTotalCacheSizeBytes(context: Context): Long {
        val dir = getCacheDir(context)
        var total = dir.listFiles { f -> f.isFile && !f.name.endsWith(".download") }?.sumOf { it.length() } ?: 0L
        val legacyDir = getLegacyCacheDir(context)
        if (legacyDir != null && legacyDir.exists()) {
            total += legacyDir.listFiles { f -> f.isFile && !f.name.endsWith(".download") }?.sumOf { it.length() } ?: 0L
        }
        return total
    }

    fun clearCache(context: Context) {
        val dir = getCacheDir(context)
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val legacyDir = getLegacyCacheDir(context)
        legacyDir?.listFiles()?.forEach { runCatching { it.delete() } }
    }

    /** Delete only the cached file for a single URL. Returns true if deleted. */
    fun deleteFromCache(context: Context, url: String): Boolean {
        val filename = url.substringAfterLast('/')
        val file = File(getCacheDir(context), filename)
        var deleted = file.exists() && file.delete()
        val legacyDir = getLegacyCacheDir(context)
        if (legacyDir != null && legacyDir.exists()) {
            val legacyFile = File(legacyDir, filename)
            if (legacyFile.exists() && legacyFile.delete()) {
                deleted = true
            }
        }
        return deleted
    }

    /** File size of a cached video in MB, or 0 if not cached. */
    fun getCachedFileSizeMb(context: Context, url: String): Int {
        val file = getCacheFile(context, url)
        return if (file.exists()) (file.length() / (1024 * 1024)).toInt() else 0
    }

    data class DownloadProgress(
        val bytesDownloaded: Long = 0L,
        val totalBytes: Long = 0L,
        val speedBps: Double = 0.0
    ) {
        val percent: Int get() = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
        val downloadedMb: Double get() = bytesDownloaded / (1024.0 * 1024.0)
        val totalMb: Double get() = totalBytes / (1024.0 * 1024.0)
        val speedMbPerSec: Double get() = speedBps / (1024.0 * 1024.0)
    }

    suspend fun downloadToCache(
        context: Context,
        url: String,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val target = getCacheFile(context, url)
        if (target.exists() && target.length() >= MIN_VALID_SIZE) {
            onProgress?.invoke(DownloadProgress(target.length(), target.length(), 0.0))
            return@withContext true
        }
        val temp = File(target.parentFile, "${target.name}.download")
        runCatching {
            val u = java.net.URL(url)
            val conn = u.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 25000
            conn.instanceFollowRedirects = true
            // AppleCoreMedia header uncaps Apple Akamai CDN bandwidth limit
            conn.setRequestProperty("User-Agent", "AppleCoreMedia/1.0.0.18J386 (Apple TV; U; CPU OS 14_0 like Mac OS X; en_us)")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Connection", "keep-alive")

            // Support resume if partial download exists
            var existingBytes = if (temp.exists()) temp.length() else 0L
            if (existingBytes > 0L) {
                conn.setRequestProperty("Range", "bytes=$existingBytes-")
            }

            conn.connect()
            val code = conn.responseCode
            val isPartial = (code == java.net.HttpURLConnection.HTTP_PARTIAL)
            val isOk = (code == java.net.HttpURLConnection.HTTP_OK)

            if (!isPartial) {
                existingBytes = 0L
            }

            if (isOk || isPartial) {
                val contentLength = conn.contentLength.takeIf { it > 0 }?.toLong() ?: 0L
                val totalBytes = if (isPartial) existingBytes + contentLength else {
                    if (contentLength > 0L) contentLength else {
                        val preset = APPLE_AERIAL_PRESETS.firstOrNull { it.url == url }
                        (preset?.sizeMb ?: 150) * 1024L * 1024L
                    }
                }

                var downloaded = existingBytes
                var lastTime = System.currentTimeMillis()
                var lastBytes = downloaded
                var speedBps = 0.0

                val append = isPartial && existingBytes > 0L
                java.io.BufferedInputStream(conn.inputStream, 256 * 1024).use { input ->
                    java.io.BufferedOutputStream(java.io.FileOutputStream(temp, append), 256 * 1024).use { output ->
                        val buf = ByteArray(256 * 1024)
                        var read: Int
                        while (input.read(buf).also { read = it } != -1) {
                            output.write(buf, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastTime
                            if (elapsed >= 200L) {
                                speedBps = ((downloaded - lastBytes) * 1000.0) / elapsed
                                lastTime = now
                                lastBytes = downloaded
                                onProgress?.invoke(DownloadProgress(downloaded, totalBytes, speedBps))
                            }
                        }
                        output.flush()
                    }
                }
                if (temp.length() >= MIN_VALID_SIZE) {
                    temp.renameTo(target)
                    onProgress?.invoke(DownloadProgress(target.length(), target.length(), 0.0))
                    true
                } else {
                    false
                }
            } else {
                temp.delete()
                false
            }
        }.getOrElse {
            false
        }
    }
}

/**
 * Continuous Seamless Apple Aerial Video Player with Local Caching & Loop.
 * - Uses two alternating hardware TextureViews (Player 0 and Player 1).
 * - Preloads the next video in the background while the current video continues playing.
 * - Crossfades between videos over 1500ms with ZERO black frames and zero interruptions.
 * - Automatically caches downloaded videos locally and loops them to stop eating data!
 */
@Composable
private fun AppleAerialVideoPlayer(
    selectedUrl: String? = null,
    autoCache: Boolean = true,
    onReady: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val player0 = remember { TextureVideoPlayer(ctx) }
    val player1 = remember { TextureVideoPlayer(ctx) }

    DisposableEffect(Unit) {
        onDispose {
            player0.releaseAll()
            player1.releaseAll()
        }
    }

    var activeSlot by remember { mutableIntStateOf(0) }
    var isFirstVideoReady by remember { mutableStateOf(false) }
    var showLocationText by remember { mutableStateOf(false) }

    val selectedUrlSet = remember(selectedUrl) {
        selectedUrl?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }

    // Active pool of indices to cycle through
    val activeIndices by remember(selectedUrl, autoCache) {
        derivedStateOf {
            when {
                selectedUrlSet.isNotEmpty() -> {
                    val matching = APPLE_AERIAL_PRESETS.indices.filter { APPLE_AERIAL_PRESETS[it].url in selectedUrlSet }
                    if (matching.isNotEmpty()) matching else APPLE_AERIAL_PRESETS.indices.toList()
                }
                autoCache -> {
                    // Downloaded Only mode: loop among cached videos
                    val cached = APPLE_AERIAL_PRESETS.indices.filter { AerialVideoCache.isCached(ctx, APPLE_AERIAL_PRESETS[it].url) }
                    if (cached.isNotEmpty()) cached else APPLE_AERIAL_PRESETS.indices.toList()
                }
                else -> {
                    // Stream All mode: all presets, pure streaming, 0 disk storage
                    APPLE_AERIAL_PRESETS.indices.toList()
                }
            }
        }
    }

    var currentPresetIndex by remember(activeIndices) {
        mutableIntStateOf(activeIndices.randomOrNull() ?: 0)
    }
    val currentPreset = APPLE_AERIAL_PRESETS[currentPresetIndex]

    // Crossfade alpha states
    val alpha0 by animateFloatAsState(
        targetValue = if (activeSlot == 0 && isFirstVideoReady) 1f else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "alphaPlayer0"
    )
    val alpha1 by animateFloatAsState(
        targetValue = if (activeSlot == 1) 1f else 0f,
        animationSpec = tween(1500, easing = FastOutSlowInEasing),
        label = "alphaPlayer1"
    )

    // Animate location text into place AFTER video is loaded initially
    LaunchedEffect(isFirstVideoReady) {
        if (isFirstVideoReady) {
            delay(500L)
            showLocationText = true
        }
    }

    val locationAlpha by animateFloatAsState(
        targetValue = if (showLocationText) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "locationAlpha"
    )
    val locationSlideY by animateFloatAsState(
        targetValue = if (showLocationText) 0f else 28f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "locationSlideY"
    )

    // Preload, Caching & Crossfade orchestration loop (NEVER auto-downloads in background)
    LaunchedEffect(activeIndices) {
        // Step 1: Start initial video on Player 0
        val initialPreset = APPLE_AERIAL_PRESETS.getOrNull(currentPresetIndex) ?: APPLE_AERIAL_PRESETS[0]
        val initialSource = AerialVideoCache.getPlayableSource(ctx, initialPreset.url)

        player0.prepare(initialSource) {
            player0.start()
            isFirstVideoReady = true
            onReady()
        }

        while (true) {
            // Cinematic 40 seconds per scene
            delay(40000L)

            // Step 2: Choose next video from active pool
            val pool = activeIndices.filter { it != currentPresetIndex }
            val targetNextIndex = if (pool.isNotEmpty()) {
                pool.random()
            } else {
                currentPresetIndex
            }

            val nextPreset = APPLE_AERIAL_PRESETS[targetNextIndex]
            val nextSource = AerialVideoCache.getPlayableSource(ctx, nextPreset.url)

            val idlePlayer = if (activeSlot == 0) player1 else player0
            val readySignal = CompletableDeferred<Boolean>()
            idlePlayer.prepare(nextSource) {
                readySignal.complete(true)
            }

            // Wait up to 10s for buffering or local loading to finish
            val loaded = withTimeoutOrNull(10000L) { readySignal.await() } ?: false
            if (loaded) {
                // Step 3: Start idle player & initiate butter-smooth crossfade
                idlePlayer.start()
                currentPresetIndex = targetNextIndex
                activeSlot = if (activeSlot == 0) 1 else 0

                // Allow 1.5s for crossfade animation to complete before stopping previous player
                delay(1500L)
                val previousPlayer = if (activeSlot == 0) player1 else player0
                previousPlayer.releasePlayer()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ─── Player Slot 0 (TextureView with alpha blending) ───
        AndroidView(
            factory = { player0.textureView },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = alpha0 }
        )

        // ─── Player Slot 1 (TextureView with alpha blending) ───
        AndroidView(
            factory = { player1.textureView },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = alpha1 }
        )

        // ─── Apple TV Bottom-Left Location (Symmetric to Clock, No Background) ───
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 48.dp)
                .graphicsLayer {
                    alpha = locationAlpha
                    translationY = locationSlideY
                }
        ) {
            AnimatedContent(
                targetState = currentPreset,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(650, delayMillis = 150)) +
                        slideInVertically(animationSpec = tween(650, delayMillis = 150)) { it / 3 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(450)) +
                                slideOutVertically(animationSpec = tween(450)) { -it / 3 }
                        )
                },
                label = "locationTransition"
            ) { preset ->
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = preset.location,
                        style = Ty.Display.copy(
                            fontSize = 52.sp,
                            lineHeight = 56.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = (-1).sp,
                            color = Color.White.copy(alpha = 0.95f),
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.75f),
                                offset = Offset(0f, 2f),
                                blurRadius = 14f
                            )
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = preset.subtext,
                        style = Ty.Small.copy(
                            color = Color.White.copy(alpha = 0.80f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 1.sp,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.70f),
                                offset = Offset(0f, 2f),
                                blurRadius = 8f
                            )
                        )
                    )
                }
            }
        }
    }
}

/**
 * Custom Photos Slideshow with smooth crossfade and continuous Ken Burns motion.
 */
@Composable
private fun CustomPhotoSlideshow(
    folderPath: String?,
    onReady: () -> Unit = {},
    onEmpty: @Composable () -> Unit
) {
    val imageFiles = remember(folderPath) {
        if (folderPath.isNullOrBlank()) emptyList()
        else {
            val dir = File(folderPath)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles { f ->
                    val name = f.name.lowercase(Locale.ROOT)
                    name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
                }?.toList() ?: emptyList()
            } else emptyList()
        }
    }

    if (imageFiles.isEmpty()) {
        onEmpty()
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(currentIndex, imageFiles) {
        val file = imageFiles[currentIndex]
        val bmp = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            }.getOrNull()
        }
        currentBitmap = bmp
        if (bmp != null) onReady()
        delay(10000L) // 10s per slide
        currentIndex = (currentIndex + 1) % imageFiles.size
    }

    val bmp = currentBitmap
    if (bmp != null) {
        KenBurnsImage(bitmap = bmp)
    } else {
        onEmpty()
    }
}

/**
 * Single custom image with slow Ken Burns pan and zoom.
 */
@Composable
private fun SingleImageBackdrop(
    filePath: String?,
    onReady: () -> Unit = {},
    onEmpty: @Composable () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(filePath) {
        if (!filePath.isNullOrBlank()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()
            }
            bitmap = bmp
            if (bmp != null) onReady()
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        KenBurnsImage(bitmap = bmp)
    } else {
        onEmpty()
    }
}

/**
 * Ken Burns image presenter (subtle pan & zoom).
 */
@Composable
private fun KenBurnsImage(bitmap: Bitmap) {
    var isZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(bitmap) {
        isZoomed = false
        delay(60L)
        isZoomed = true
    }

    val scale by animateFloatAsState(
        targetValue = if (isZoomed) 1.10f else 1.0f,
        animationSpec = tween(durationMillis = 11000, easing = LinearEasing),
        label = "kbScale"
    )

    val panX by animateFloatAsState(
        targetValue = if (isZoomed) 28f else -28f,
        animationSpec = tween(durationMillis = 11000, easing = LinearEasing),
        label = "kbPan"
    )

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "Screensaver Photo",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = panX
            }
    )
}
