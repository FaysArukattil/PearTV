package com.leantv.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreensaverSettingsDialog(
    currentMode: ScreensaverSource,
    customPath: String?,
    idleMinutes: Int,
    onSelectMode: (ScreensaverSource) -> Unit,
    onSelectIdleMinutes: (Int) -> Unit,
    onPickPath: () -> Unit,
    onStartScreensaverNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val autoFocus = rememberAutoFocus()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppleMotion.DurStandard, easing = AppleMotion.EaseOut),
        label = "ssScrim"
    )
    val panelScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = AppleMotion.SpringStandard,
        label = "ssScale"
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppleMotion.DurStandard, easing = AppleMotion.Decelerate),
        label = "ssAlpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000).copy(alpha = 0.92f * scrimAlpha))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(820.dp)
                .graphicsLayer {
                    scaleX = panelScale
                    scaleY = panelScale
                    alpha = panelAlpha
                }
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF26262B), Color(0xFF17171B))))
                .padding(horizontal = 40.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Screensaver Settings", style = Ty.Title.copy(color = Color.White, fontSize = 32.sp))
            Text("Choose what appears when your TV is left idle", style = Ty.Dim.copy(fontSize = 17.sp))

            Spacer(Modifier.height(4.dp))

            // ─── Source Mode Selection ───
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "SCREENSAVER STYLE",
                    style = Ty.Small.copy(color = Color(0x99FFFFFF), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                )

                ScreensaverSource.entries.forEachIndexed { index, mode ->
                    val isSelected = mode == currentMode
                    Focusable(
                        onClick = { onSelectMode(mode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { if (index == 0) it.focusRequester(autoFocus) else it },
                        shape = RoundedCornerShape(18.dp),
                        idle = if (isSelected) Color(0x330A84FF) else Color(0x14FFFFFF),
                        idleOutline = isSelected
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    style = Ty.Body.copy(
                                        color = if (isSelected) Color(0xFF64B5F6) else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 19.sp
                                    )
                                )
                                Text(
                                    text = mode.desc,
                                    style = Ty.Small.copy(color = Color(0x99FFFFFF), fontSize = 14.sp)
                                )
                            }
                            if (isSelected) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF0A84FF))
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Active", style = Ty.Small.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                                }
                            }
                        }
                    }
                }
            }

            // ─── Local Video Info Box ───
            if (currentMode == ScreensaverSource.LOCAL_VIDEO) {
                val LOCAL_DIR = "/sdcard/Movies/Screensaver"
                val videoCount = remember {
                    val dir = java.io.File(LOCAL_DIR)
                    if (dir.exists() && dir.isDirectory) {
                        dir.listFiles { f ->
                            val n = f.name.lowercase()
                            n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".mkv") || n.endsWith(".m4v")
                        }?.size ?: 0
                    } else 0
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x1A64B5F6))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(26.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Copy videos to your TV at:",
                            style = Ty.Body.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        )
                        Text(
                            LOCAL_DIR,
                            style = Ty.Small.copy(color = Color(0xFF64B5F6), fontSize = 14.sp)
                        )
                        Text(
                            if (videoCount == 0) "No videos found yet — add .mp4 / .mov / .mkv files to that folder"
                            else "$videoCount video${if (videoCount == 1) "" else "s"} found — ready to play!",
                            style = Ty.Small.copy(
                                color = if (videoCount == 0) Color(0xFFFF9800) else Color(0xFF81C784),
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }

            // ─── Custom Path Picker (when slideshow or single image is active) ───
            if (currentMode == ScreensaverSource.SLIDESHOW || currentMode == ScreensaverSource.SINGLE_IMAGE) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (currentMode == ScreensaverSource.SLIDESHOW) "PHOTOS FOLDER" else "IMAGE FILE",
                        style = Ty.Small.copy(color = Color(0x99FFFFFF), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    )
                    Focusable(
                        onClick = onPickPath,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        idle = Color(0x1AFFFFFF)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = customPath ?: "No folder selected (Click to browse)",
                                    style = Ty.Body.copy(color = Color.White, fontSize = 16.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text("Browse Files", style = Ty.Button.copy(color = Color(0xFF64B5F6), fontSize = 14.sp))
                        }
                    }
                }
            }

            // ─── Idle Timeout Selection ───
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "IDLE ACTIVATION TIMER",
                    style = Ty.Small.copy(color = Color(0x99FFFFFF), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val intervals = listOf(
                        2 to "2 Mins",
                        5 to "5 Mins",
                        10 to "10 Mins",
                        15 to "15 Mins",
                        0 to "Off"
                    )
                    intervals.forEach { (mins, label) ->
                        val selected = idleMinutes == mins
                        Focusable(
                            onClick = { onSelectIdleMinutes(mins) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            idle = if (selected) Color(0x330A84FF) else Color(0x14FFFFFF),
                            idleOutline = selected
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = Ty.Body.copy(
                                        color = if (selected) Color(0xFF64B5F6) else Color.White,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 15.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // ─── Bottom Action Buttons ───
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Focusable(
                    onClick = onStartScreensaverNow,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    idle = Color(0xFF0A84FF).copy(alpha = 0.35f)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start Screensaver Now", style = Ty.Button.copy(color = Color.White, fontSize = 18.sp))
                    }
                }

                TvButton(
                    text = "Close",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.width(180.dp)
                )
            }
        }
    }
}

