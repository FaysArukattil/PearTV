package com.peartv.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
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
import com.peartv.app.data.Conn
import com.peartv.app.data.Screen
import com.peartv.app.data.Tier
import com.peartv.app.data.UiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    PearTvSpinner(modifier = modifier, diameter = 44.dp)
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
                "Pear TV needs USB debugging switched on so it can manage apps for you. It takes about a minute.",
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
        // Device Title Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(d.displayName, style = Ty.Display.copy(fontSize = 38.sp, lineHeight = 44.sp))
                Spacer(Modifier.height(4.dp))
                Text("Model ${d.model} • Android ${d.android} (API ${d.sdk})", style = Ty.Dim)
            }

            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Pal.Mint.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text("TV CONNECTED & READY", style = Ty.Small.copy(color = Pal.Mint, fontWeight = FontWeight.SemiBold))
            }
        }

        Spacer(Modifier.height(18.dp))

        // ⚡ CleanYourTV 1-Click Transformation Hero Card
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF1E4359), Color(0xFF122C3B))
                    )
                )
                .border(2.dp, Pal.Amber, RoundedCornerShape(22.dp))
                .padding(24.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Box(
                        Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Pal.Amber.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("", fontSize = 32.sp)
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "CLEANYOURTV AUTO PRESET",
                                style = Ty.Small.copy(color = Pal.Amber, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                            )
                            Pill("RECOMMENDED", Pal.Mint)
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            "Clean Your TV in One Single Click",
                            style = Ty.Heading.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            "Safely strip all ad-serving feeds, PatchWall, telemetry & bloatware. Enables 2x animation speeds and AdGuard DNS.",
                            style = Ty.Small.copy(color = Pal.Dim, fontSize = 17.sp)
                        )
                    }
                }

                Spacer(Modifier.width(20.dp))

                TvButton(
                    text = "Clean TV Now",
                    onClick = vm::requestCleanTvWizard,
                    primary = true,
                    modifier = Modifier.focusRequester(fr)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Hardware Specs and Live RAM Gauge
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Gauge(frac, "${(frac * 100).roundToInt()}%", "memory in use")
                Spacer(Modifier.height(10.dp))
                Text("${gb(used)} of ${gb(d.ramTotalMb)}", style = Ty.Body.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(6.dp))
                Text(d.advice, style = Ty.Small, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            Column(Modifier.weight(1f)) {
                Spec("Chipset & Hardware", d.hardware.ifBlank { "Unknown" })
                Spec("Processor & Cores", "${d.cores} cores, ${d.abi}")
                Spec("Storage free", "%.1f of %.1f GB free".format(d.storageFreeGb, d.storageTotalGb))
                Spec("Display Resolution", d.resolution)
                Spec("Security Patch", d.patch.ifBlank { "Unknown" })
                Spec("System Uptime", "${d.uptimeHours} hours")
            }
        }

        Spacer(Modifier.height(26.dp))

        // Navigation shortcuts
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvButton(
                "Review Debloat Catalog ($reviewable)",
                onClick = { vm.go(Screen.Debloat) },
                primary = false
            )
            TvButton("Installed Apps", onClick = { vm.go(Screen.InstalledApps) }, primary = false)
            TvButton("Speed & DNS Tweaks", onClick = { vm.go(Screen.Tweaks) }, primary = false)
            TvButton("Safety Restore Journal", onClick = { vm.go(Screen.Restore) }, primary = false)
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
