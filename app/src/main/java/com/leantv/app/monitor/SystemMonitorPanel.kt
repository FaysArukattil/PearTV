package com.leantv.app.monitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.data.IconCache
import com.leantv.app.ui.Focusable
import com.leantv.app.ui.Pal
import com.leantv.app.ui.Spinner
import com.leantv.app.ui.TvButton
import com.leantv.app.ui.Ty
import com.leantv.app.ui.rememberAutoFocus
import java.util.Locale

@Composable
fun SystemMonitorPanel(
    ram: RamSnapshot?,
    cpu: CpuSnapshot?,
    runningApps: List<RunningAppItem>,
    isCleaning: Boolean,
    onClose: () -> Unit,
    onKillApp: (String) -> Unit,
    onCleanAll: () -> Unit,
) {
    val closeFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            Modifier
                .width(620.dp)
                .fillMaxHeight()
                .background(Pal.Surface)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("System Monitor", style = Ty.Title)
                    Text("Live Memory & Process Telemetry", style = Ty.Dim)
                }

                TvButton("Close", onClick = onClose, primary = false, modifier = Modifier.focusRequester(closeFocus))
            }

            // RAM Live Card
            ram?.let { mem ->
                val ramColor by animateColorAsState(
                    when {
                        mem.percentUsed < 70 -> Pal.Mint
                        mem.percentUsed < 85 -> Pal.Amber
                        else -> Pal.Coral
                    },
                    tween(300), label = "ramColor"
                )
                val animatedProgress by animateFloatAsState(
                    targetValue = (mem.percentUsed / 100f).coerceIn(0f, 1f),
                    animationSpec = tween(400), label = "ramProgress"
                )

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Pal.SurfaceHi.copy(alpha = 0.5f))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("RAM Usage", style = Ty.Heading.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "${mem.percentUsed}% (${mem.formattedUsed} / ${mem.formattedTotal})",
                            style = Ty.Body.copy(color = ramColor, fontWeight = FontWeight.Bold)
                        )
                    }

                    // Progress bar
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x33000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(animatedProgress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(ramColor)
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Free Memory: ${mem.formattedAvail}", style = Ty.Small)
                        if (mem.isLowMem) {
                            Text("Low Memory Warning", style = Ty.Small.copy(color = Pal.Coral, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // CPU Live Card
            cpu?.let { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Pal.SurfaceHi.copy(alpha = 0.5f))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("CPU Architecture", style = Ty.Heading.copy(fontWeight = FontWeight.SemiBold))
                        Text(c.statusText, style = Ty.Small)
                    }

                    c.percent?.let { p ->
                        Text(
                            "$p%",
                            style = Ty.Title.copy(
                                color = if (p > 80) Pal.Coral else Pal.Amber,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            // Clean All Action Row
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Running Processes (${runningApps.size})",
                        style = Ty.Heading.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    )
                    Text("Safe background termination", style = Ty.Small.copy(fontSize = 12.sp))
                }

                TvButton(
                    text = if (isCleaning) "Cleaning..." else "Clean All",
                    onClick = onCleanAll,
                    primary = true,
                    enabled = !isCleaning && runningApps.isNotEmpty()
                )
            }

            // Running Processes List
            if (runningApps.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No killable background processes found.", style = Ty.Dim)
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(runningApps, key = { it.packageName }) { app ->
                        RunningProcessRow(app = app, onKill = { onKillApp(app.packageName) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningProcessRow(
    app: RunningAppItem,
    onKill: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) { mutableStateOf(IconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            iconBitmap = IconCache.loadIcon(context, app.packageName)
        }
    }

    Focusable(
        onClick = onKill,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        idle = Pal.SurfaceHi.copy(alpha = 0.4f),
        scaleOnFocus = 1.02f
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(bitmap = iconBitmap!!, contentDescription = app.appName, modifier = Modifier.size(32.dp))
                } else {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(Pal.Amber.copy(alpha = 0.3f)))
                }
            }

            // Details
            Column(Modifier.weight(1f)) {
                Text(
                    app.appName,
                    style = Ty.Body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = if (focused) Pal.Amber else Pal.Text,
                        fontSize = 17.sp
                    ),
                    maxLines = 1
                )
                Text(
                    app.packageName,
                    style = Ty.Small.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    maxLines = 1
                )
            }

            // Memory PSS
            if (app.pssMb > 0) {
                Text(
                    String.format(Locale.US, "%.1f MB", app.pssMb),
                    style = Ty.Small.copy(color = Pal.Sky, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                )
            }

            // Kill action hint
            TvButton("Kill", onClick = onKill, primary = false)
        }
    }
}

