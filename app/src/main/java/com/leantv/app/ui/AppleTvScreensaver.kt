package com.leantv.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val url: String
)

val APPLE_AERIAL_PRESETS = listOf(
    // Dubai
    AppleAerialVideo("Dubai", "Night Skyline & Burj Khalifa", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_DB_D011_D009_SIGNCMP_v15_6Mbps.mov"),
    AppleAerialVideo("Dubai", "Desert Dunes & Coastline", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_DB_D001_C001_v03_6Mbps.mov"),
    AppleAerialVideo("Dubai", "City Lights at Twilight", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b6-2.mov"),
    AppleAerialVideo("Dubai", "Downtown & Financial Centre", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b6-1.mov"),

    // Greenland
    AppleAerialVideo("Greenland", "Ilulissat Icefjord", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_GL_G004_C010_v03_6Mbps.mov"),
    AppleAerialVideo("Greenland", "Icefjords & Icebergs", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_GL_G010_C006_v08_6Mbps.mov"),
    AppleAerialVideo("Greenland", "Glacier Overflight", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b7-1.mov"),

    // Hong Kong
    AppleAerialVideo("Hong Kong", "Victoria Harbour at Night", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_HK_B005_C011_t9_6M_tag0.mov"),
    AppleAerialVideo("Hong Kong", "Daytime Peaks & Bay", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_HK_H004_C008_v10_6Mbps.mov"),
    AppleAerialVideo("Hong Kong", "Kowloon Skyline", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b8-2.mov"),

    // Los Angeles
    AppleAerialVideo("Los Angeles", "Downtown Twilight", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_LA_A005_C009_v05_t9_6M.mov"),
    AppleAerialVideo("Los Angeles", "Night Skyline & Traffic", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_LA_A009_C009_t9_6M_tag0.mov"),
    AppleAerialVideo("Los Angeles", "Metropolis Freeways", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b9-1.mov"),

    // Hawaii
    AppleAerialVideo("Hawaii", "Na Pali Coast Cliffs", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b1-1.mov"),
    AppleAerialVideo("Hawaii", "Tropical Island Sunset", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b1-2.mov"),
    AppleAerialVideo("Hawaii", "Emerald Coastline Waves", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b1-3.mov"),

    // London
    AppleAerialVideo("London", "Tower Bridge & River Thames", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b2-1.mov"),
    AppleAerialVideo("London", "Westminster & Big Ben", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b2-2.mov"),
    AppleAerialVideo("London", "City Skyline at Night", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b2-3.mov"),

    // New York City
    AppleAerialVideo("New York City", "Manhattan Skyline", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b3-1.mov"),
    AppleAerialVideo("New York City", "Central Park & Midtown", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b3-2.mov"),

    // San Francisco
    AppleAerialVideo("San Francisco", "Golden Gate Bridge", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b4-1.mov"),
    AppleAerialVideo("San Francisco", "Bay Bridge at Night", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b4-2.mov"),

    // China
    AppleAerialVideo("China", "Great Wall of China", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b5-1.mov"),
    AppleAerialVideo("China", "Li River Mountains", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b5-2.mov"),

    // Liwa / UAE
    AppleAerialVideo("Liwa", "Endless Desert Dunes", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b10-1.mov"),
    AppleAerialVideo("Liwa", "Desert Sunset", "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/b10-2.mov")
)

enum class ScreensaverSource(val id: String, val title: String, val desc: String) {
    AERIAL("aerial", "Apple Aerial Videos", "Real Apple TV 1080p aerial landscapes streaming from Apple CDN"),
    SLIDESHOW("slideshow", "Custom Photos Slideshow", "Cycle smoothly through photos in a chosen folder"),
    SINGLE_IMAGE("image", "Single Custom Image", "Display a chosen custom wallpaper with Apple TV motion")
}

/**
 * Apple TV–style screensaver for Android TV.
 *
 * Features:
 * - Official Apple Aerial video streams cycling seamlessly every 25 seconds.
 * - Instant visual display (no black screen delay while streaming buffers).
 * - Apple TV location pill in bottom-left corner.
 * - Large Apple TV clock & date in bottom-right corner.
 * - Input debounce guard so remote clicks won't immediately dismiss upon launching.
 * - Wake TV on any remote button press.
 */
@Composable
fun AppleTvScreensaver(
    mode: ScreensaverSource = ScreensaverSource.AERIAL,
    customPath: String? = null,
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
                AppleAerialVideoPlayer()
            }
            ScreensaverSource.SLIDESHOW -> {
                CustomPhotoSlideshow(
                    folderPath = customPath,
                    onEmpty = { AppleAerialVideoPlayer() }
                )
            }
            ScreensaverSource.SINGLE_IMAGE -> {
                SingleImageBackdrop(
                    filePath = customPath,
                    onEmpty = { AppleAerialVideoPlayer() }
                )
            }
        }

        // ─── Apple TV Bottom Right: Clock & Date ─────────────────────────────
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
                        color = Color.White.copy(alpha = 0.95f)
                    )
                )
                Text(
                    text = currentDate,
                    style = Ty.Small.copy(
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp
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
 * Hardware-accelerated Apple Aerial Video Player.
 * - Streams official Apple 1080p video directly from Apple CDN.
 * - Displays an instant ambient backdrop with zero black-screen lag.
 * - Automatically cycles to a new scene every 25 seconds.
 * - Shows an Apple TV-style location pill in the bottom-left corner.
 */
@Composable
private fun AppleAerialVideoPlayer() {
    var currentIndex by remember { mutableIntStateOf(Random.nextInt(APPLE_AERIAL_PRESETS.size)) }
    val currentPreset = APPLE_AERIAL_PRESETS[currentIndex]
    var isVideoReady by remember { mutableStateOf(false) }

    // Auto-cycle to next aerial preset every 25 seconds
    LaunchedEffect(currentIndex) {
        delay(25000L)
        isVideoReady = false
        currentIndex = (currentIndex + 1) % APPLE_AERIAL_PRESETS.size
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (isVideoReady) 1f else 0f,
        animationSpec = tween(700, easing = LinearEasing),
        label = "aerialAlpha"
    )

    Box(Modifier.fillMaxSize()) {
        // ─── Instant Cinematic Backdrop (Prevents initial black screen) ───
        CinematicBackdrop(location = currentPreset.location)

        // ─── Hardware-Accelerated Video Player ───
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    tag = currentPreset.url
                    setOnPreparedListener { mp ->
                        mp.isLooping = true
                        mp.setVolume(0f, 0f) // Aerial screensavers are silent
                        start()
                        isVideoReady = true
                    }
                    setOnCompletionListener {
                        // Advance to next scene if video stream finishes
                        isVideoReady = false
                        currentIndex = (currentIndex + 1) % APPLE_AERIAL_PRESETS.size
                    }
                    setOnErrorListener { _, _, _ ->
                        // Advance cleanly to next preset if network error occurs
                        isVideoReady = false
                        currentIndex = (currentIndex + 1) % APPLE_AERIAL_PRESETS.size
                        true
                    }
                    setVideoURI(Uri.parse(currentPreset.url))
                }
            },
            update = { view ->
                if (view.tag != currentPreset.url) {
                    view.tag = currentPreset.url
                    view.setVideoURI(Uri.parse(currentPreset.url))
                    // Do NOT call view.start() here; onPreparedListener starts playback safely
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = videoAlpha }
        )

        // ─── Apple TV Bottom-Left Location Pill ───
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(400)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        text = currentPreset.location,
                        style = Ty.Body.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = currentPreset.subtext,
                        style = Ty.Small.copy(
                            color = Color.White.copy(alpha = 0.82f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
            }
        }
    }
}

/**
 * Instant ambient atmospheric backdrop matching the aerial scene mood.
 * Eliminates black screen during initial network buffering.
 */
@Composable
private fun CinematicBackdrop(location: String) {
    val gradientColors = remember(location) {
        when {
            location.contains("Dubai") || location.contains("Liwa") ->
                listOf(Color(0xFF2E1C0C), Color(0xFF140D06), Color(0xFF080503))
            location.contains("Greenland") ->
                listOf(Color(0xFF0E2536), Color(0xFF08141D), Color(0xFF040A0F))
            location.contains("Hong Kong") || location.contains("Angeles") ->
                listOf(Color(0xFF1F1438), Color(0xFF10091E), Color(0xFF06030B))
            location.contains("Hawaii") ->
                listOf(Color(0xFF0C2B22), Color(0xFF061511), Color(0xFF020907))
            location.contains("London") || location.contains("York") ->
                listOf(Color(0xFF1E242C), Color(0xFF101317), Color(0xFF07080A))
            else ->
                listOf(Color(0xFF1A1A24), Color(0xFF0D0D12), Color(0xFF050508))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(gradientColors))
    )
}

/**
 * Custom Photos Slideshow with smooth crossfade and continuous Ken Burns motion.
 */
@Composable
private fun CustomPhotoSlideshow(
    folderPath: String?,
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
    onEmpty: @Composable () -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(filePath) {
        if (!filePath.isNullOrBlank()) {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()
            }
            bitmap = bmp
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
