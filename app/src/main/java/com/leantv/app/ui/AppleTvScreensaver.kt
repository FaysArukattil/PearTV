package com.leantv.app.ui

import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * Apple TV–style minimal screensaver for Android TV.
 *
 * Features:
 * - Pure deep black background with a subtle procedural star field.
 * - Ken Burns slow zoom & pan over the star field for depth.
 * - Large, crisp minimalist Apple TV clock (96sp) in the bottom-right corner.
 * - Dismisses instantly on any remote button press.
 */
@Composable
fun AppleTvScreensaver(
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    // Stable random star positions — generated once, never change
    val stars = remember {
        List(200) {
            Triple(
                Random.nextFloat(),                        // relative x (0..1)
                Random.nextFloat(),                        // relative y (0..1)
                Random.nextFloat() * 0.10f + 0.15f        // alpha 0.15–0.25
            )
        }
    }
    val starRadii = remember {
        List(200) { Random.nextFloat() * 1.4f + 0.6f }    // radius 0.6–2.0 px
    }

    // Time ticker
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

    // Focus for remote events
    LaunchedEffect(Unit) {
        delay(100L)
        runCatching { focusRequester.requestFocus() }
    }

    // Ken Burns: gentle continuous zoom & pan — cycles every 22 s
    var isZoomed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isZoomed = false
            delay(80L)
            isZoomed = true
            delay(22000L)
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (isZoomed) 1.12f else 1.0f,
        animationSpec = tween(durationMillis = 22000, easing = LinearEasing),
        label = "kenBurnsScale"
    )

    val panOffsetX by animateFloatAsState(
        targetValue = if (isZoomed) 36f else -36f,
        animationSpec = tween(durationMillis = 22000, easing = LinearEasing),
        label = "kenBurnsPan"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        // Star field with Ken Burns motion applied
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = panOffsetX
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

        // Apple TV Bottom Right: Minimalist Clock & Date
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
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        // Subtle hint at top: press any remote key to dismiss
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
