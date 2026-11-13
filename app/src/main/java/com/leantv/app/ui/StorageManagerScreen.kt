package com.leantv.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.SoundEffectConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.leantv.app.adb.AdbClient
import com.leantv.app.data.DebloatResult
import com.leantv.app.data.Journal
import com.leantv.app.data.RemovalMethod
import com.leantv.app.data.Removed
import com.leantv.app.data.SystemDebloater
import com.leantv.app.storage.AppStorageDetail
import com.leantv.app.storage.StorageHelper
import com.leantv.app.storage.StorageOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TV Remote-optimized Storage Manager (Samsung Device Care style).
 *
 * Features:
 * - Shows app icons for every installed application.
 * - Two-column TV remote layout: Left list navigates apps, Right panel displays storage breakdown.
 * - Clickable action buttons for Clear Cache, Clear Data, Uninstall, and App Info.
 * - D-Pad Right moves focus directly to the Action Buttons; D-Pad Left returns to the app list.
 */
@Composable
fun StorageManagerScreen(
    adbClient: AdbClient?,
    journal: Journal,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    LaunchedEffect(Unit) {
        if (adbClient?.isConnected == false) {
            adbClient.connect()
        }
    }

    var overview by remember { mutableStateOf<StorageOverview?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var optimizeMessage by remember { mutableStateOf<String?>(null) }
    var selectedApp by remember { mutableStateOf<AppStorageDetail?>(null) }
    var appIcons by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    val actionFocusRequester = remember { FocusRequester() }

    fun refresh() {
        scope.launch {
            isLoading = true
            val o = StorageHelper.getStorageOverview(ctx, journal)
            overview = o
            if (selectedApp == null || o.appDetails.none { it.packageName == selectedApp?.packageName }) {
                selectedApp = o.appDetails.firstOrNull()
            }
            isLoading = false

            // Preload icons asynchronously
            withContext(Dispatchers.IO) {
                val pm = ctx.packageManager
                val icons = mutableMapOf<String, Bitmap>()
                for (app in o.appDetails.take(40)) {
                    try {
                        val d = pm.getApplicationIcon(app.packageName)
                        icons[app.packageName] = d.toBitmap(96, 96)
                    } catch (_: Exception) {}
                }
                appIcons = icons
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF64D2FF))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "DEVICE CARE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64D2FF),
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Storage Management",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        text = "Clean All Cache",
                        onClick = {
                            scope.launch {
                                val reclaimed = StorageHelper.cleanAllAccessibleCaches(ctx)
                                optimizeMessage = "Cleaned ${StorageHelper.formatBytes(reclaimed)} of cache!"
                                refresh()
                            }
                        },
                        primary = true
                    )

                    TvButton(
                        text = "Back to Home",
                        onClick = onClose,
                        primary = false
                    )
                }
            }

            optimizeMessage?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x334CAF50))
                        .border(1.dp, Color(0x664CAF50), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(text = msg, color = Color(0xFF30D158), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(20.dp))

            val currentOverview = overview
            if (currentOverview == null || isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Analyzing TV storage...", color = Color(0x99FFFFFF), fontSize = 20.sp)
                }
            } else {
                // Two-Column TV Layout
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Left Column: Apps List with Icons (55% width)
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "Installed Applications by Size (${currentOverview.appDetails.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xB3FFFFFF),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x14FFFFFF))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(currentOverview.appDetails, key = { it.packageName }) { app ->
                                val isSelected = selectedApp?.packageName == app.packageName
                                val icon = appIcons[app.packageName]

                                Focusable(
                                    onClick = {
                                        selectedApp = app
                                        actionFocusRequester.requestFocus()
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    idle = if (isSelected) Color(0x2EFFFFFF) else Color(0x0EFFFFFF),
                                    scaleOnFocus = 1.02f,
                                    modifier = Modifier.onPreviewKeyEvent { ev ->
                                        if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                                            ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                                        ) {
                                            selectedApp = app
                                            actionFocusRequester.requestFocus()
                                            true
                                        } else false
                                    }
                                ) { isFocused ->
                                    if (isFocused && selectedApp?.packageName != app.packageName) {
                                        selectedApp = app
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // App Icon
                                        if (icon != null) {
                                            Image(
                                                bitmap = icon.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0x28FFFFFF)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Android,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        Spacer(Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = app.appLabel,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (app.isSystemApp) "System App • ${app.packageName}" else app.packageName,
                                                color = Color(0x77FFFFFF),
                                                fontSize = 14.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        // Total Storage Badge
                                        Text(
                                            text = StorageHelper.formatBytes(app.totalBytes),
                                            color = Color(0xFF64D2FF),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Column: Storage Inspector & Actions (45% width)
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "Storage Breakdown & Actions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xB3FFFFFF),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        val activeApp = selectedApp
                        if (activeApp == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x14FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Select an app to inspect", color = Color(0x66FFFFFF), fontSize = 18.sp)
                            }
                        } else {
                            val activeIcon = appIcons[activeApp.packageName]
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x1AFFFFFF))
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // App Header
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (activeIcon != null) {
                                        Image(
                                            bitmap = activeIcon.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0x28FFFFFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Android, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                        }
                                    }

                                    Spacer(Modifier.width(16.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activeApp.appLabel,
                                            style = Ty.Title.copy(fontSize = 24.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = activeApp.packageName,
                                            style = Ty.Small.copy(color = Color(0x88FFFFFF), fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Storage Category Breakdown Cards
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StorageMetricRow(
                                        title = "App Size (APK)",
                                        size = StorageHelper.formatBytes(activeApp.appBytes),
                                        color = Color(0xFF64D2FF)
                                    )
                                    StorageMetricRow(
                                        title = "User Data",
                                        size = StorageHelper.formatBytes(activeApp.dataBytes),
                                        color = Color(0xFFFFA726)
                                    )
                                    StorageMetricRow(
                                        title = "Cache",
                                        size = StorageHelper.formatBytes(activeApp.cacheBytes),
                                        color = Color(0xFF30D158)
                                    )
                                }

                                Spacer(Modifier.weight(1f))

                                // D-Pad Reachable Action Buttons
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Clear Cache Button
                                    TvButton(
                                        text = "Clear Cache (${StorageHelper.formatBytes(activeApp.cacheBytes)})",
                                        onClick = {
                                            scope.launch {
                                                optimizeMessage = "Clearing cache for ${activeApp.appLabel}..."
                                                if (adbClient != null && adbClient.isConnected) {
                                                    adbClient.shell("pm trim-caches 9999999999")
                                                }
                                                optimizeMessage = "✓ Cache cleared for ${activeApp.appLabel}"
                                                refresh()
                                            }
                                        },
                                        primary = true,
                                        modifier = Modifier.fillMaxWidth().focusRequester(actionFocusRequester)
                                    )

                                    // Clear Data Button
                                    TvButton(
                                        text = "Clear All App Data",
                                        onClick = {
                                            scope.launch {
                                                optimizeMessage = "Clearing data for ${activeApp.appLabel}..."
                                                val ok = StorageHelper.clearAppData(ctx, adbClient, activeApp.packageName)
                                                optimizeMessage = if (ok) "✓ Cleared data for ${activeApp.appLabel}" else "Opened App Settings"
                                                refresh()
                                            }
                                        },
                                        primary = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Uninstall Button (Supports Shizuku, ADB, and direct removal)
                                    TvButton(
                                        text = if (activeApp.isSystemApp) "Uninstall / Remove Bloatware" else "Uninstall Application",
                                        onClick = {
                                            scope.launch {
                                                optimizeMessage = "Uninstalling ${activeApp.appLabel}..."
                                                val res = SystemDebloater.uninstallOrDisable(
                                                    ctx = ctx,
                                                    pkg = activeApp.packageName,
                                                    label = activeApp.appLabel,
                                                    isSystemApp = activeApp.isSystemApp,
                                                    adbClient = adbClient,
                                                    journal = journal
                                                )
                                                when (res) {
                                                    is DebloatResult.Success -> {
                                                        optimizeMessage = res.message
                                                        overview = overview?.copy(appDetails = overview!!.appDetails.filter { it.packageName != activeApp.packageName })
                                                        refresh()
                                                    }
                                                    is DebloatResult.NeedsPrivilege -> {
                                                        optimizeMessage = res.message
                                                    }
                                                    is DebloatResult.Failure -> {
                                                        optimizeMessage = res.reason
                                                    }
                                                }
                                            }
                                        },
                                        primary = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    // Open Settings Button
                                    TvButton(
                                        text = "App Info in TV Settings",
                                        onClick = { StorageHelper.openAppDetails(ctx, activeApp.packageName) },
                                        primary = false,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageMetricRow(title: String, size: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))
            Text(text = title, color = Color(0xDDFFFFFF), fontSize = 16.sp)
        }
        Text(text = size, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
