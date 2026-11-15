package com.leantv.app.ui

import android.view.SoundEffectConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.data.Tier
import kotlinx.coroutines.delay

/** Requests focus shortly after the screen appears so the remote has somewhere to start. */
@Composable
fun rememberAutoFocus(key: Any? = Unit): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(key) {
        delay(140)
        runCatching { fr.requestFocus() }
    }
    return fr
}

fun tierColor(t: Tier): Color = when (t) {
    Tier.RECOMMENDED -> Pal.Mint
    Tier.OPTIONAL -> Pal.Sky
    Tier.ADVANCED -> Pal.Coral
}

/** Base for anything the remote can focus: Apple TV style soft shadow, crisp rim & sound feedback. */
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    idle: Color = Pal.Surface,
    enabled: Boolean = true,
    scaleOnFocus: Float = 1.07f,
    idleOutline: Boolean = false,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) scaleOnFocus else 1f, AppleMotion.SpringSnappy, label = "scale")
    val ring by animateColorAsState(
        if (focused) Color.White.copy(alpha = 0.85f) else if (idleOutline) Pal.Line else Color.Transparent,
        AppleMotion.microTween(), label = "ring",
    )
    val bg by animateColorAsState(if (focused) Pal.SurfaceHi else idle, AppleMotion.microTween(), label = "bg")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 24.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x66000000),
                spotColor = Color(0x99000000)
            )
            .clip(shape)
            .background(bg)
            .border(if (focused) 1.5.dp else if (idleOutline) 1.dp else 0.dp, ring, shape)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    TvSound.playMove(view)
                }
                focused = it.isFocused
            }
            .focusable(enabled = enabled)
            .clickable(
                enabled = enabled,
                onClick = {
                    TvSound.playClick(view)
                    onClick()
                }
            ),
    ) {
        this.content(focused)
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, AppleMotion.SpringSnappy, label = "btn")
    val shape = RoundedCornerShape(50)
    val bg by animateColorAsState(
        when {
            !enabled -> Pal.Surface
            focused -> Color.White
            primary -> Pal.Amber
            else -> Color(0x26FFFFFF)
        }, tween(140), label = "btnbg",
    )
    val fg = when {
        !enabled -> Pal.Dim
        focused -> Color.Black
        else -> Color.White
    }
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 20.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x66000000),
                spotColor = Color(0x99000000)
            )
            .clip(shape)
            .background(bg)
            .border(if (focused) 1.5.dp else 0.dp, Color.White.copy(alpha = 0.9f), shape)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    TvSound.playMove(view)
                }
                focused = it.isFocused
            }
            .focusable(enabled = enabled)
            .clickable(
                enabled = enabled,
                onClick = {
                    TvSound.playClick(view)
                    onClick()
                }
            )
            .padding(horizontal = 24.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ty.Button.copy(color = fg), maxLines = 1)
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        idle = if (selected) Pal.Amber.copy(alpha = 0.18f) else Pal.Surface,
        scaleOnFocus = 1.04f,
    ) {
        Text(
            text,
            style = Ty.Body.copy(
                color = if (selected) Pal.Amber else Pal.Text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = Ty.Small.copy(color = color, fontWeight = FontWeight.SemiBold),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun CheckMark(checked: Boolean) {
    val fill by animateColorAsState(if (checked) Pal.Mint else Color.Transparent, tween(120), label = "check")
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(28.dp)
            .clip(shape)
            .background(fill)
            .border(2.dp, if (checked) Pal.Mint else Pal.Dim, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", style = Ty.Body.copy(color = Pal.OnAmber, fontWeight = FontWeight.Bold))
    }
}

@Composable
fun TvSwitch(on: Boolean) {
    val track by animateColorAsState(if (on) Pal.Mint else Pal.Line, tween(160), label = "track")
    val x by animateDpAsState(if (on) 30.dp else 4.dp, tween(160), label = "thumb")
    Box(
        Modifier
            .size(width = 68.dp, height = 38.dp)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .offset(x = x, y = 4.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(if (on) Pal.OnAmber else Pal.Text),
        )
    }
}

@Composable
fun RailItem(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        idle = if (selected) Pal.Surface else Color.Transparent,
        scaleOnFocus = 1.03f,
        idleOutline = false,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 22.dp)
                    .clip(CircleShape)
                    .background(if (selected) Pal.Amber else Color.Transparent),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                style = Ty.Heading.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Pal.Text else Pal.Dim,
                ),
            )
        }
    }
}

/** Partial-circle gauge used for memory in use. */
@Composable
fun Gauge(fraction: Float, centerText: String, caption: String, modifier: Modifier = Modifier, diameter: Dp = 200.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "gauge")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = 18.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Pal.Line, startAngle = 150f, sweepAngle = 240f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = Pal.Amber, startAngle = 150f, sweepAngle = 240f * f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, style = Ty.Display.copy(fontSize = 42.sp, lineHeight = 46.sp))
            Text(caption, style = Ty.Small)
        }
    }
}

/**
 * Ambient to temporarily disarm modal traps (e.g. when an app-wide incoming transfer popup appears on top).
 */
val LocalDisableModalTraps = compositionLocalOf { false }

/**
 * Put on the scrim of every popup. Focus is pulled into the popup when it opens and can never
 * leave it with the D-pad, so the remote can no longer drive the screen underneath.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.modalTrap(enabled: Boolean = true, autoFocus: Boolean = true): Modifier {
    val disabledByParent = LocalDisableModalTraps.current
    if (!enabled || disabledByParent) return this
    val fr = remember { FocusRequester() }
    if (autoFocus) {
        LaunchedEffect(enabled, disabledByParent) {
            delay(60)
            runCatching { fr.requestFocus() }
        }
    }
    return this
        .then(if (autoFocus) Modifier.focusRequester(fr) else Modifier)
        .focusProperties { exit = { FocusRequester.Cancel } }
        .focusGroup()
}

/** Shared popup/panel surface: dark glass gradient, big rounded corners, hairline edge (reference "glass_panel"). */
fun Modifier.glassCard(radius: Dp = 32.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(Brush.verticalGradient(listOf(Color(0xFF26262B), Color(0xFF17171B))))
    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(radius))
