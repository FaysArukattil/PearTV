package com.leantv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
 * Curated list of official Apple TV Aerial video streams from Apple CDN (a1.phobos.apple.com).
 * Reference: https://github.com/johncoates/aerial
 */
data class AppleAerialVideo(
    val location: String,
    val subtext: String,
    val url: String
)

val APPLE_AERIAL_PRESETS = listOf(
    AppleAerialVideo(
        location = "Dubai",
        subtext = "Night Skyline & Burj Khalifa",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_DB_D011_D009_SIGNCMP_v15_6Mbps.mov"
    ),
    AppleAerialVideo(
        location = "Greenland",
        subtext = "Ilulissat Icefjord",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_GL_G004_C010_v03_6Mbps.mov"
    ),
    AppleAerialVideo(
        location = "Los Angeles",
        subtext = "Downtown Twilight",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_LA_A005_C009_v05_t9_6M.mov"
    ),
    AppleAerialVideo(
        location = "Hong Kong",
        subtext = "Victoria Harbour at Night",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_HK_B005_C011_t9_6M_tag0.mov"
    ),
    AppleAerialVideo(
        location = "New York City",
        subtext = "Manhattan Skyline",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_NY_C011_C010_v10_6Mbps.mov"
    ),
    AppleAerialVideo(
        location = "San Francisco",
        subtext = "Golden Gate & Pacific Ocean",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_C002_C005_0818SC_001_v01_6M_t9.mov"
    ),
    AppleAerialVideo(
        location = "Hawaii",
        subtext = "Na Pali Coast Cliffs",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_HW_H001_C001_v02_6M_t9.mov"
    ),
    AppleAerialVideo(
        location = "London",
        subtext = "Tower Bridge & River Thames",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_LD_A004_C010_v02_6Mbps.mov"
    ),
    AppleAerialVideo(
        location = "China",
        subtext = "The Great Wall at Sunrise",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_CH_C004_C021_v04_6Mbps.mov"
    ),
    AppleAerialVideo(
        location = "Dubai",
        subtext = "Desert Dunes & Coastline",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_DB_D001_C001_v03_6Mbps.mov"
    ),
    AppleAerialVideo(
        location = "Hong Kong",
        subtext = "Daytime Peaks and Bay",
        url = "http://a1.phobos.apple.com/us/r1000/000/Features/atv/AutumnResources/videos/comp_HK_H004_C008_v10_6Mbps.mov"
    )
)

enum class ScreensaverSource(val id: String, val title: String, val desc: String) {
    AERIAL("aerial", "Apple Aerial Videos", "Real Apple TV 1080p aerial landscapes streaming from Apple CDN"),
    SLIDESHOW("slideshow", "Custom Photos Slideshow", "Cycle smoothly through photos in a chosen folder"),
    SINGLE_IMAGE("image", "Single Custom Image", "Display a chosen custom wallpaper with Apple TV motion"),
    STARS("stars", "Deep Space Stars", "Minimal pure black OLED backdrop with subtle twinkling stars")
}

/**
 * Apple TV–style screensaver for Android TV.
 *
 * Supports:
 * - Official Apple Aerial Video playback (VideoView) with smooth looping & auto-advance.
 * - Photo slideshow / single image with smooth Ken Burns pan & zoom + crossfade.
 * - Subtle Apple TV location pill (fades out after 8s).
 * - Large Apple TV clock & date in bottom-right corner.
 * - Instant wake on ANY remote button press.
 */
@Composable
fun AppleTvScreensaver(
    mode: ScreensaverSource = ScreensaverSource.AERIAL,
    customPath: String? = null,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

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

    // Auto-focus so remote keys dismiss screensaver
    LaunchedEffect(Unit) {
        delay(120L)
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
                    onDismiss()
                    true
                } else false
            }
    ) {
        when (mode) {
            ScreensaverSource.AERIAL -> {
                AppleAerialVideoPlayer(onFallback = { StarsBackdrop() })
            }
            ScreensaverSource.SLIDESHOW -> {
                CustomPhotoSlideshow(folderPath = customPath, onEmpty = { StarsBackdrop() })
            }
            ScreensaverSource.SINGLE_IMAGE -> {
                SingleImageBackdrop(filePath = customPath, onEmpty = { StarsBackdrop() })
            }
            ScreensaverSource.STARS -> {
                StarsBackdrop()
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
 * Official Apple Aerial Video player via hardware-accelerated VideoView.
 */
@Composable
private fun AppleAerialVideoPlayer(
    onFallback: @Composable () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(Random.nextInt(APPLE_AERIAL_PRESETS.size)) }
    val currentPreset = APPLE_AERIAL_PRESETS[currentIndex]
    var showLocation by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // Location tag fades out after 9 seconds, like real Apple TV
    LaunchedEffect(currentIndex) {
        showLocation = true
        delay(9000L)
        showLocation = false
    }

    if (hasError) {
        onFallback()
    } else {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            mp.setVolume(0f, 0f) // Screensavers are silent
                            start()
                        }
                        setOnCompletionListener {
                            // Advance to next video
                            currentIndex = (currentIndex + 1) % APPLE_AERIAL_PRESETS.size
                        }
                        setOnErrorListener { _, _, _ ->
                            hasError = true
                            true
                        }
                        setVideoURI(Uri.parse(currentPreset.url))
                    }
                },
                update = { view ->
                    val uri = Uri.parse(currentPreset.url)
                    view.setVideoURI(uri)
                    view.start()
                },
                modifier = Modifier.fillMaxSize()
            )

            // Apple TV Location Pill (Bottom Left)
            AnimatedVisibility(
                visible = showLocation,
                enter = fadeIn(tween(800)),
                exit = fadeOut(tween(1200)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 64.dp, bottom = 48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Column {
                        Text(
                            text = currentPreset.location,
                            style = Ty.Heading.copy(
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = currentPreset.subtext,
                            style = Ty.Small.copy(
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 15.sp
                            )
                        )
                    }
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
 * Ken Burns image presenter (pan & zoom).
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

/**
 * Pure OLED black background with subtle twinkling stars.
 */
@Composable
fun StarsBackdrop() {
    val stars = remember {
        List(220) {
            Triple(
                Random.nextFloat(),
                Random.nextFloat(),
                Random.nextFloat() * 0.12f + 0.15f
            )
        }
    }
    val starRadii = remember {
        List(220) { Random.nextFloat() * 1.4f + 0.6f }
    }

    var isZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isZoomed = false
            delay(80L)
            isZoomed = true
            delay(24000L)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isZoomed) 1.12f else 1.0f,
        animationSpec = tween(durationMillis = 24000, easing = LinearEasing),
        label = "starScale"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val w = size.width
        val h = size.height
        stars.forEachIndexed { i, (rx, ry, alpha) ->
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = starRadii[i],
                center = Offset(rx * w, ry * h)
            )
        }
    }
}
