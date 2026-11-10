package com.leantv.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.data.Conn
import com.leantv.app.data.Screen
import com.leantv.app.data.Tier
import com.leantv.app.data.UiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier = modifier.size(40.dp),
        color = Pal.Amber,
        trackColor = Pal.Line,
        strokeWidth = 4.dp,
    )
}

// ---------------------------------------------------------------- Welcome

@Composable
fun WelcomeScreen(vm: MainViewModel) {
    val fr = rememberAutoFocus()
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Safe.H, vertical = Safe.V),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1.2f)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Lean TV", style = Ty.Heading.copy(color = Pal.Amber))
            Spacer(Modifier.height(14.dp))
            Text("A faster TV, without the clutter.", style = Ty.Display)
            Spacer(Modifier.height(18.dp))
            Text(
                "Lean TV removes preinstalled apps you don't use, switches off background trackers, " +
                    "and keeps every change reversible.",
                style = Ty.Dim,
            )
            Spacer(Modifier.height(22.dp))
            Fact("Every change is logged and can be undone.")
            Fact("A safety list protects your remote, inputs and home screen.")
            Fact("No account needed. The app sends nothing anywhere.")
            Spacer(Modifier.height(28.dp))
            TvButton("Get started", onClick = vm::begin, modifier = Modifier.focusRequester(fr))
            Spacer(Modifier.height(12.dp))
            Text("Use the arrow keys and OK on your remote.", style = Ty.Small)
        }
        Spacer(Modifier.width(32.dp))
        Box(Modifier.weight(0.8f), contentAlignment = Alignment.Center) { OrbitArt() }
    }
}

@Composable
private fun Fact(text: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Pal.Mint)
        )
        Spacer(Modifier.width(14.dp))
        Text(text, style = Ty.Body)
    }
}

@Composable
private fun OrbitArt() {
    val t = rememberInfiniteTransition(label = "orbit")
    val a by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )
    Canvas(Modifier.size(300.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val sweep = Brush.sweepGradient(listOf(Color.Transparent, Pal.Amber, Color.Transparent), c)
        val radii = listOf(0.46f, 0.34f, 0.22f)
        val speeds = listOf(1f, -1.6f, 2.4f)
        radii.forEachIndexed { i, r ->
            val d = size.width * r * 2
            rotate(a * speeds[i], c) {
                drawArc(
                    brush = sweep, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(c.x - d / 2, c.y - d / 2), size = Size(d, d),
                    style = Stroke(width = (6 - i * 1.5f).dp.toPx()),
                )
            }
        }
        drawCircle(Pal.Amber.copy(alpha = 0.9f), radius = 9.dp.toPx(), center = c)
    }
}

// ---------------------------------------------------------------- Setup

@Composable
fun SetupScreen(s: UiState, vm: MainViewModel) {
    val retryFr = remember { FocusRequester() }
    val aboutFr = remember { FocusRequester() }
    LaunchedEffect(s.conn) {
        delay(160)
        runCatching { if (s.conn is Conn.Failed) retryFr.requestFocus() else aboutFr.requestFocus() }
    }

    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Safe.H, vertical = Safe.V),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Connect to your TV", style = Ty.Title)
            Spacer(Modifier.height(12.dp))
            Text(
                "Lean TV needs USB debugging switched on so it can manage apps for you. It takes about a minute.",
                style = Ty.Dim,
            )
            Spacer(Modifier.height(22.dp))
            Step(1, "Open About and click Build seven times.")
            Step(2, "Open Developer options and turn on USB debugging.")
            Step(3, "Come back here and choose Allow on the TV's prompt. Tick Always allow.")
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Open About", onClick = vm::openAbout, modifier = Modifier.focusRequester(aboutFr), primary = false)
                TvButton("Developer options", onClick = vm::openDeveloper, primary = false)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Pal.Surface)
                    .padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (val c = s.conn) {
                    Conn.Idle, Conn.Checking -> {
                        Spinner()
                        Text("Connecting", style = Ty.Heading)
                        Text("If a prompt appears on screen, choose Allow.", style = Ty.Dim)
                    }
                    is Conn.Failed -> {
                        Text(c.title, style = Ty.Heading.copy(color = Pal.Coral))
                        Text(c.detail, style = Ty.Dim)
                        Spacer(Modifier.height(2.dp))
                        TvButton("Try again", onClick = vm::connect, modifier = Modifier.focusRequester(retryFr))
                    }
                    is Conn.Connected -> {
                        Text("Connected", style = Ty.Heading.copy(color = Pal.Mint))
                        Text("Reading your TV.", style = Ty.Dim)
                        Spinner()
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Pal.Surface),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = Ty.Heading.copy(color = Pal.Amber)) }
        Spacer(Modifier.width(16.dp))
        Text(text, style = Ty.Body, modifier = Modifier.padding(top = 4.dp))
    }
}

// ---------------------------------------------------------------- Dashboard

private fun gb(mb: Long) = "%.1f GB".format(mb / 1024.0)

@Composable
fun DashboardScreen(s: UiState, vm: MainViewModel) {
    val d = s.device
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
        return
    }
    val fr = rememberAutoFocus()
    val used = (d.ramTotalMb - d.ramFreeMb).coerceAtLeast(0)
    val frac = if (d.ramTotalMb > 0) used.toFloat() / d.ramTotalMb else 0f
    val reviewable = s.apps.count { it.tier != Tier.ADVANCED && !it.disabled }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ContentPad)
    ) {
        Text(d.displayName, style = Ty.Display.copy(fontSize = 42.sp, lineHeight = 48.sp))
        Spacer(Modifier.height(4.dp))
        Text("Model ${d.model}, Android ${d.android}", style = Ty.Dim)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(Modifier.width(230.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Gauge(frac, "${(frac * 100).roundToInt()}%", "memory in use")
                Spacer(Modifier.height(10.dp))
                Text("${gb(used)} of ${gb(d.ramTotalMb)}", style = Ty.Body)
                Spacer(Modifier.height(8.dp))
                Text(d.advice, style = Ty.Small)
            }
            Column(Modifier.weight(1f)) {
                Spec("Chipset", d.hardware.ifBlank { "Unknown" })
                Spec("Processor", "${d.cores} cores, ${d.abi}")
                Spec("Storage free", "%.1f of %.1f GB".format(d.storageFreeGb, d.storageTotalGb))
                Spec("Screen", d.resolution)
                Spec("Android", "${d.android} (API ${d.sdk})")
                Spec("Security patch", d.patch.ifBlank { "Unknown" })
                Spec("Running for", "${d.uptimeHours} hours")
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                "Review $reviewable app${if (reviewable == 1) "" else "s"}",
                onClick = { vm.go(Screen.Debloat) },
                modifier = Modifier.focusRequester(fr),
            )
            TvButton("Speed tweaks", onClick = { vm.go(Screen.Tweaks) }, primary = false)
        }
    }
}

@Composable
private fun Spec(label: String, value: String) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = Ty.Dim)
            Text(value, style = Ty.Body)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Pal.Line)
        )
    }
}
