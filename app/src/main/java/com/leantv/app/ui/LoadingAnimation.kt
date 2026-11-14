package com.leantv.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Lean TV loading visuals.
 *
 * Everything here animates ONLY in the draw phase (animated values are read inside draw lambdas),
 * so a running loader never triggers recomposition or layout -> no jitter, no dropped frames.
 */

private val LogoAmber = Color(0xFFFFB347)
private val LogoInk = Color(0xFFEAF3F2)
private val SkeletonBase = Color(0xFF1C1C1E)
private val SkeletonHighlight = Color(0xFF34343A)

/** The Lean TV logo as a spinner: a sweeping amber arc around a softly pulsing play mark. */
@Composable
fun LeanTvSpinner(modifier: Modifier = Modifier, diameter: Dp = 48.dp) {
    val transition = rememberInfiniteTransition(label = "leanSpinner")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotation",
    )
    val sweep = transition.animateFloat(
        initialValue = 50f,
        targetValue = 250f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val pulse = transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )

    Canvas(modifier.size(diameter)) {
        val dim = this.size.minDimension
        val stroke = dim * 0.11f
        val arcSize = Size(dim - stroke, dim - stroke)
        val topLeft = Offset(stroke / 2f, stroke / 2f)
        val ringRadius = arcSize.width / 2f

        // faint track
        drawCircle(
            color = LogoAmber.copy(alpha = 0.16f),
            radius = ringRadius,
            center = Offset(dim / 2f, dim / 2f),
            style = Stroke(width = stroke),
        )
        // sweeping arc
        drawArc(
            color = LogoAmber,
            startAngle = rotation.value,
            sweepAngle = sweep.value,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // play mark (same proportions as the app icon)
        val k = (ringRadius / 52f) * pulse.value
        val cx = dim / 2f
        val cy = dim / 2f
        val play = Path().apply {
            moveTo(cx - 12f * k, cy - 21f * k)
            lineTo(cx - 12f * k, cy + 21f * k)
            lineTo(cx + 24f * k, cy)
            close()
        }
        drawPath(play, LogoInk)
    }
}

/** Spinner + label, centred. Use for any full-area "loading…" state. */
@Composable
fun LeanTvLoading(label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LeanTvSpinner(diameter = 52.dp)
        Text(text = label, color = Color(0x99FFFFFF), fontSize = 18.sp)
    }
}

/** One shared 0..1 phase that every skeleton block sweeps with, so they shimmer in sync. */
@Composable
fun rememberShimmerPhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "shimmer")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "shimmerPhase",
    )
}

/** Skeleton block with a moving highlight. Phase is read in the draw phase only. */
fun Modifier.shimmer(phase: State<Float>, shape: Shape): Modifier =
    this
        .clip(shape)
        .drawBehind {
            drawRect(SkeletonBase)
            val w = size.width
            val x = -w + phase.value * w * 3f
            drawRect(
                Brush.linearGradient(
                    colors = listOf(Color.Transparent, SkeletonHighlight, Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + w, size.height * 0.45f),
                )
            )
        }

/** Placeholder for the big app name above the grid. */
@Composable
fun HeroSkeleton(modifier: Modifier = Modifier) {
    val phase = rememberShimmerPhase()
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.size(320.dp, 42.dp).shimmer(phase, RoundedCornerShape(12.dp)))
    }
}

/**
 * Home-screen skeleton. Uses exactly the same sizes / paddings as the real dock + grid, so when the real
 * content replaces it nothing moves.
 */
@Composable
fun AppGridSkeleton(
    tileW: Dp,
    tileH: Dp,
    radius: Dp,
    gap: Dp,
    cols: Int,
    bandPad: Dp,
    modifier: Modifier = Modifier,
) {
    val phase = rememberShimmerPhase()
    val tileShape = RoundedCornerShape(radius)
    val bandShape = RoundedCornerShape(radius + bandPad)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // dock band
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .background(Color(0xE6141416), bandShape)
                .border(1.dp, Color(0x1FFFFFFF), bandShape)
                .padding(bandPad)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                repeat(cols) { Box(Modifier.size(tileW, tileH).shimmer(phase, tileShape)) }
            }
        }

        // section title
        Box(
            Modifier
                .padding(start = bandPad + 6.dp, top = 6.dp, bottom = 4.dp)
                .size(240.dp, 22.dp)
                .shimmer(phase, RoundedCornerShape(8.dp))
        )

        // two rows of tiles
        repeat(2) {
            Row(
                Modifier.padding(start = bandPad),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                repeat(cols) {
                    Column(Modifier.width(tileW)) {
                        Box(Modifier.size(tileW, tileH).shimmer(phase, tileShape))
                        Spacer(Modifier.height(38.dp))
                    }
                }
            }
        }
    }
}
