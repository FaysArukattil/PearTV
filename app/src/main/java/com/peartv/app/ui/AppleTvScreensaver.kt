package com.peartv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 * - ONE video decoder at a time (cheap TV chips crash with two 1080p decoders running together).
 * - A black cover fades over the video: video that can't load simply stays black, then fades in by
 *   itself the moment a scene is really on screen. Same path for streamed and downloaded scenes.
 * - The location text only shows (and changes) together with the scene that is actually visible.
 * - Any failure (network, bad file, decoder) is contained: black screen + retry, never a crash.
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
    var skipTrigger by remember { mutableIntStateOf(0) }

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
                        when (ev.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                skipTrigger++
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                skipTrigger++
                                true
                            }
                            else -> {
                                onDismiss()
                                true
                            }
                        }
                    } else true
                } else false
            }
    ) {
        when (mode) {
            ScreensaverSource.AERIAL -> {
                AppleAerialVideoPlayer(selectedUrl = selectedUrl, autoCache = autoCache, skipTrigger = skipTrigger)
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

        // ─── Apple TV Bottom Right: Clock & Date ───
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


private const val AERIAL_USER_AGENT =
    "AppleCoreMedia/1.0.0.18J386 (Apple TV; U; CPU OS 14_0 like Mac OS X; en_us)"
private const val SCENE_MS = 40_000L          // how long each scene plays
private const val FADE_MS = 800               // black cover fade (in and out)
private const val LOCAL_TIMEOUT_MS = 8_000L   // downloaded file must start fast
private const val STREAM_TIMEOUT_MS = 15_000L // network stream gets longer
private const val STALL_LIMIT_MS = 12_000L    // no playback progress for this long = failed

/**
 * Owns exactly ONE TextureView and ONE MediaPlayer at a time.
 * Every callback is detached before release and every call is guarded, so a bad stream, a bad file,
 * a lost surface or a decoder failure can never throw out of here — [play] just returns false.
 */
private class AerialPlayerHost(context: Context) {
    private var mp: MediaPlayer? = null
    private var surface: Surface? = null
    private var surfaceSignal = CompletableDeferred<Unit>()
    private var failSignal = CompletableDeferred<Unit>()
    private var skipSignal = CompletableDeferred<Unit>()

    fun triggerSkip() {
        if (!skipSignal.isCompleted) skipSignal.complete(Unit)
    }

    val textureView: TextureView = TextureView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                runCatching { surface?.release() }
                surface = Surface(st)
                if (!surfaceSignal.isCompleted) surfaceSignal.complete(Unit)
            }

            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                this@AerialPlayerHost.release()
                runCatching { surface?.release() }
                surface = null
                surfaceSignal = CompletableDeferred()
                failSignal.complete(Unit) // a scene that lost its surface is over: let the loop recover
                return true
            }

            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
    }

    /**
     * Prepares [source] and starts it. Returns true only once it is really playing
     * (prepared + started + first frame rendered), false on ANY problem.
     */
    suspend fun play(context: Context, source: String, isUrl: Boolean, timeoutMs: Long): Boolean {
        release()
        if (withTimeoutOrNull(3_000L) { surfaceSignal.await() } == null) return false
        val s = surface ?: return false

        val prepared = CompletableDeferred<Boolean>()
        val rendered = CompletableDeferred<Unit>()
        val fail = CompletableDeferred<Unit>()
        failSignal = fail
        skipSignal = CompletableDeferred()

        val player = MediaPlayer()
        mp = player
        try {
            player.setSurface(s)
            player.setVolume(0f, 0f) // Aerial videos are silent
            player.isLooping = true
            if (isUrl) {
                player.setDataSource(
                    context.applicationContext,
                    Uri.parse(source),
                    mapOf("User-Agent" to AERIAL_USER_AGENT)
                )
            } else {
                player.setDataSource(source)
            }
            player.setOnPreparedListener { p ->
                prepared.complete(runCatching { p.start() }.isSuccess)
            }
            player.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) rendered.complete(Unit)
                false
            }
            player.setOnErrorListener { _, _, _ ->
                prepared.complete(false)
                fail.complete(Unit)
                true // handled: never let MediaPlayer escalate an error
            }
            player.prepareAsync()
        } catch (_: Throwable) {
            release()
            return false
        }

        val ok = withTimeoutOrNull(timeoutMs) { prepared.await() } == true
        if (!ok) {
            release()
            return false
        }
        // Wait (briefly) for the first real frame so the fade-in never reveals an empty texture.
        withTimeoutOrNull(2_000L) { rendered.await() }
        if (fail.isCompleted) {
            release()
            return false
        }
        return true
    }

    /** Suspends while the current scene plays; returns on timeout, error, lost surface, skip trigger or stalled playback. */
    suspend fun holdScene(sceneMs: Long) {
        val fail = failSignal
        val skip = skipSignal
        var elapsed = 0L
        var lastPos = -1
        var stalledMs = 0L
        while (elapsed < sceneMs && !fail.isCompleted && !skip.isCompleted) {
            delay(500L)
            elapsed += 500L
            val pos = runCatching { mp?.currentPosition }.getOrNull() ?: -1
            if (pos >= 0 && pos != lastPos) {
                lastPos = pos
                stalledMs = 0L
            } else {
                stalledMs += 500L
            }
            if (stalledMs >= STALL_LIMIT_MS) break
        }
    }

    fun release() {
        val p = mp ?: return
        mp = null
        runCatching {
            p.setOnPreparedListener(null)
            p.setOnInfoListener(null)
            p.setOnErrorListener(null)
        }
        runCatching { p.reset() }   // valid in every state (stop() throws while still preparing)
        runCatching { p.release() }
    }

    fun releaseAll() {
        release()
        runCatching { surface?.release() }
        surface = null
    }
}

/** Presets this run may play. Downloaded-only never touches the network. */
private fun aerialCandidates(
    context: Context,
    selected: Set<String>,
    downloadedOnly: Boolean
): List<AppleAerialVideo> {
    val base = APPLE_AERIAL_PRESETS.filter { selected.isEmpty() || it.url in selected }
        .ifEmpty { APPLE_AERIAL_PRESETS }
    return if (downloadedOnly) base.filter { AerialVideoCache.isCached(context, it.url) } else base
}

/**
 * Seamless Apple Aerial player.
 *
 *  fade to black → load next scene (local file = near-instant, stream = whenever it is ready)
 *  → first frame renders → fade in, location text changes with it.
 *
 * Can't load? The screen simply stays black (clock still shows), the next scene is tried after a short
 * back-off, and the moment one loads it fades in on its own.
 */
@Composable
private fun AppleAerialVideoPlayer(
    selectedUrl: String? = null,
    autoCache: Boolean = true,
    skipTrigger: Int = 0
) {
    val ctx = LocalContext.current
    val appCtx = remember { ctx.applicationContext }
    val host = remember { AerialPlayerHost(ctx) }

    DisposableEffect(host) {
        onDispose { host.releaseAll() }
    }

    LaunchedEffect(skipTrigger) {
        if (skipTrigger > 0) {
            host.triggerSkip()
        }
    }

    val selectedSet = remember(selectedUrl) {
        selectedUrl?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
    }

    var shownPreset by remember { mutableStateOf<AppleAerialVideo?>(null) }
    var videoVisible by remember { mutableStateOf(false) }

    // Black cover over the (always opaque) TextureView: no alpha on the video surface itself.
    val coverAlpha by animateFloatAsState(
        targetValue = if (videoVisible) 0f else 1f,
        animationSpec = tween(FADE_MS, easing = FastOutSlowInEasing),
        label = "coverAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (videoVisible) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "locationAlpha"
    )
    val textSlideY by animateFloatAsState(
        targetValue = if (videoVisible) 0f else 28f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "locationSlideY"
    )

    LaunchedEffect(selectedSet, autoCache) {
        // Give a closing settings popup a moment to free its preview decoder.
        delay(350L)

        val brokenLocal = HashSet<String>()   // downloaded files that would not play this session
        val skipUrls = HashSet<String>()      // scenes that failed since the last success
        var lastUrl: String? = null
        var failures = 0

        while (isActive) {
            // Fade the current scene out BEFORE touching the player.
            if (videoVisible) {
                videoVisible = false
                delay(FADE_MS + 50L)
            }

            val candidates = withContext(Dispatchers.IO) { aerialCandidates(appCtx, selectedSet, autoCache) }
            if (candidates.isEmpty()) {
                // Downloaded-only with nothing downloaded yet: stay black, re-check later.
                host.release()
                delay(5_000L)
                continue
            }

            var pool = candidates.filter { it.url != lastUrl && it.url !in skipUrls }
            if (pool.isEmpty()) pool = candidates.filter { it.url !in skipUrls }
            if (pool.isEmpty()) {
                skipUrls.clear()
                pool = candidates
            }
            val preset = pool.random()

            // Prefer the saved copy (instant, zero data); fall back to the stream when allowed.
            val localPath = withContext(Dispatchers.IO) {
                val f = AerialVideoCache.getCacheFile(appCtx, preset.url)
                if (f.exists() && f.length() >= AerialVideoCache.MIN_VALID_SIZE) f.absolutePath else null
            }
            var ok = false
            if (localPath != null && preset.url !in brokenLocal) {
                ok = host.play(appCtx, localPath, isUrl = false, timeoutMs = LOCAL_TIMEOUT_MS)
                if (!ok) brokenLocal += preset.url
            }
            if (!ok && !autoCache) {
                ok = host.play(appCtx, preset.url, isUrl = true, timeoutMs = STREAM_TIMEOUT_MS)
            }

            if (ok) {
                failures = 0
                skipUrls.clear()
                lastUrl = preset.url
                shownPreset = preset      // text swaps while still hidden…
                videoVisible = true       // …then fades/slides in together with the video
                host.holdScene(if (candidates.size <= 1) Long.MAX_VALUE / 4 else SCENE_MS)
            } else {
                failures++
                skipUrls += preset.url
                host.release()
                // Stay on black; back off 3s, 6s, 12s … capped at 30s, then try another scene.
                val backoff = (3_000L shl (failures - 1).coerceAtMost(4)).coerceAtMost(30_000L)
                delay(backoff)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                host.textureView.also { tv -> (tv.parent as? ViewGroup)?.removeView(tv) }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Black cover (drawn, not composed per frame)
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = coverAlpha) }
        )

        // ─── Bottom-left location: changes with every scene, only visible with the video ───
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 48.dp)
                .graphicsLayer {
                    alpha = textAlpha
                    translationY = textSlideY
                }
        ) {
            AnimatedContent(
                targetState = shownPreset,
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
                if (preset != null) {
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
                } else {
                    Spacer(Modifier.size(1.dp))
                }
            }
        }
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
    private const val PUBLIC_DIR_NAME = "PearTvScreensaver"
    private const val LEGACY_CACHE_DIR_NAME = "aerial_cache"
    const val MIN_VALID_SIZE = 10_000_000L // 10MB minimum to be considered a complete valid video

    fun getCacheDir(context: Context): File {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val dir = File(moviesDir, PUBLIC_DIR_NAME)
        val legacyDir = File(moviesDir, "LeanTVScreensaver")
        if (legacyDir.exists() && !dir.exists()) {
            try { legacyDir.renameTo(dir) } catch (_: Exception) {}
        }
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
                            // Lets the user cancel a running download (throws if the job was cancelled).
                            ensureActive()
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

// ─────────────────────────────────────────────────────────────────────────────
// Photo modes
// ─────────────────────────────────────────────────────────────────────────────

/** Decodes a photo near screen size (never full 12MP+ originals, which blow the TV's heap). */
private fun decodeScreenSized(path: String, maxW: Int = 1920, maxH: Int = 1080): Bitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW && bounds.outHeight / (sample * 2) >= maxH) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)
    }.getOrNull() // also swallows OutOfMemoryError
}

private fun listImages(folderPath: String?): List<File> {
    if (folderPath.isNullOrBlank()) return emptyList()
    return runCatching {
        val dir = File(folderPath)
        if (!dir.exists() || !dir.isDirectory) return@runCatching emptyList<File>()
        dir.listFiles { f ->
            val name = f.name.lowercase(Locale.ROOT)
            f.isFile && (name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".png") || name.endsWith(".webp"))
        }?.sortedBy { it.name.lowercase(Locale.ROOT) } ?: emptyList()
    }.getOrDefault(emptyList())
}

/**
 * Custom Photos Slideshow: black while the first photo loads (no video flash), 1.2s crossfade between
 * photos, Ken Burns motion. Falls back to the aerial videos only if nothing in the folder can be shown.
 */
@Composable
private fun CustomPhotoSlideshow(
    folderPath: String?,
    onEmpty: @Composable () -> Unit
) {
    var files by remember(folderPath) { mutableStateOf<List<File>?>(null) }
    LaunchedEffect(folderPath) {
        files = withContext(Dispatchers.IO) { listImages(folderPath) }
    }

    val list = files
    if (list == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
    } else if (list.isEmpty()) {
        onEmpty()
    } else {
        var bitmap by remember(list) { mutableStateOf<Bitmap?>(null) }
        var allFailed by remember(list) { mutableStateOf(false) }

        LaunchedEffect(list) {
            var index = 0
            var failures = 0
            while (isActive) {
                val bmp = withContext(Dispatchers.IO) { decodeScreenSized(list[index].absolutePath) }
                if (bmp != null) {
                    failures = 0
                    bitmap = bmp
                    delay(10_000L) // 10s per slide
                } else {
                    failures++
                    if (failures >= list.size) {
                        allFailed = true
                        return@LaunchedEffect
                    }
                }
                index = (index + 1) % list.size
            }
        }

        if (allFailed) {
            onEmpty()
        } else {
            Crossfade(
                targetState = bitmap,
                animationSpec = tween(1200),
                label = "slideshowCrossfade"
            ) { bmp ->
                if (bmp != null) KenBurnsImage(bitmap = bmp)
                else Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }
}

/**
 * Single custom image with slow Ken Burns pan and zoom.
 */
@Composable
private fun SingleImageBackdrop(
    filePath: String?,
    onEmpty: @Composable () -> Unit
) {
    var bitmap by remember(filePath) { mutableStateOf<Bitmap?>(null) }
    var finished by remember(filePath) { mutableStateOf(false) }

    LaunchedEffect(filePath) {
        bitmap = if (filePath.isNullOrBlank()) null
        else withContext(Dispatchers.IO) { decodeScreenSized(filePath) }
        finished = true
    }

    val bmp = bitmap
    when {
        bmp != null -> KenBurnsImage(bitmap = bmp)
        finished -> onEmpty()
        else -> Box(Modifier.fillMaxSize().background(Color.Black))
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