package com.leantv.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.adb.AdbClient
import com.leantv.app.storage.AppStorageDetail
import com.leantv.app.storage.StorageHelper
import com.leantv.app.storage.StorageOverview
import kotlinx.coroutines.launch

@Composable
fun StorageManagerScreen(
    adbClient: AdbClient?,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<StorageOverview?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var optimizeMessage by remember { mutableStateOf<String?>(null) }
    var selectedApp by remember { mutableStateOf<AppStorageDetail?>(null) }

    fun refresh() {
        scope.launch {
            isLoading = true
            overview = StorageHelper.getStorageOverview(ctx)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DEVICE CARE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64D2FF),
                        letterSpacing = 2.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Storage Management",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Optimize Button
                    StorageGlassButton(
                        text = "Clean Cache",
                        accent = Color(0xFF30D158),
                        onClick = {
                            scope.launch {
                                val reclaimed = StorageHelper.cleanAllAccessibleCaches(ctx)
                                optimizeMessage = "Cleaned ${StorageHelper.formatBytes(reclaimed)} of cache!"
                                refresh()
                            }
                        }
                    )

                    // Close Button
                    StorageGlassButton(
                        text = "Back",
                        accent = Color.White,
                        onClick = onClose
                    )
                }
            }

            optimizeMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x334CAF50))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(text = msg, color = Color(0xFF30D158), fontSize = 17.sp)
                }
            }

            Spacer(Modifier.height(24.dp))

            val currentOverview = overview
            if (currentOverview == null || isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Analyzing TV storage...",
                        color = Color(0x99FFFFFF),
                        fontSize = 20.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Left Column: Segmented Bar & Categories
                    Column(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                    ) {
                        SamsungStorageBarCard(currentOverview)
                        Spacer(Modifier.height(16.dp))
                        SamsungStorageCategories(currentOverview)
                    }

                    // Right Column: Per-App breakdown list
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "Installed Applications by Size (${currentOverview.appDetails.size})",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xCCFFFFFF),
                            modifier = Modifier.padding(bottom = 12.dp)
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
                                AppStorageRow(
                                    app = app,
                                    isSelected = selectedApp?.packageName == app.packageName,
                                    onSelect = { selectedApp = if (selectedApp?.packageName == app.packageName) null else app },
                                    onClearData = {
                                        scope.launch {
                                            StorageHelper.clearAppData(ctx, adbClient, app.packageName)
                                            refresh()
                                        }
                                    },
                                    onUninstall = {
                                        StorageHelper.requestUninstall(ctx, app.packageName)
                                    },
                                    onDetails = {
                                        StorageHelper.openAppDetails(ctx, app.packageName)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SamsungStorageBarCard(overview: StorageOverview) {
    val total = overview.totalBytes.coerceAtLeast(1L)
    val used = overview.usedBytes
    val percent = ((used.toDouble() / total) * 100).toInt().coerceIn(0, 100)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "INTERNAL STORAGE",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0x88FFFFFF),
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${StorageHelper.formatBytes(used)} / ${StorageHelper.formatBytes(total)}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
                Text(
                    text = "$percent%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (percent > 90) Color(0xFFFF453A) else Color(0xFF64D2FF)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Multi-segment progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0x22FFFFFF))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    val appsWeight = (overview.appsBytes.toFloat() / total).coerceAtLeast(0f)
                    val videosWeight = (overview.videosBytes.toFloat() / total).coerceAtLeast(0f)
                    val audioWeight = (overview.audioBytes.toFloat() / total).coerceAtLeast(0f)
                    val imagesWeight = (overview.imagesBytes.toFloat() / total).coerceAtLeast(0f)
                    val apksWeight = (overview.apksBytes.toFloat() / total).coerceAtLeast(0f)
                    val systemWeight = (overview.systemBytes.toFloat() / total).coerceAtLeast(0f)
                    val freeWeight = (overview.freeBytes.toFloat() / total).coerceAtLeast(0f)

                    if (appsWeight > 0.005f) Box(Modifier.weight(appsWeight).fillMaxHeight().background(Color(0xFF000000)))
                    if (videosWeight > 0.005f) Box(Modifier.weight(videosWeight).fillMaxHeight().background(Color(0xFFBF5AF2)))
                    if (audioWeight > 0.005f) Box(Modifier.weight(audioWeight).fillMaxHeight().background(Color(0xFF64D2FF)))
                    if (imagesWeight > 0.005f) Box(Modifier.weight(imagesWeight).fillMaxHeight().background(Color(0xFFFF9F0A)))
                    if (apksWeight > 0.005f) Box(Modifier.weight(apksWeight).fillMaxHeight().background(Color(0xFFFFD60A)))
                    if (systemWeight > 0.005f) Box(Modifier.weight(systemWeight).fillMaxHeight().background(Color(0xFF8E8E93)))
                    if (freeWeight > 0.005f) Box(Modifier.weight(freeWeight).fillMaxHeight().background(Color(0x11FFFFFF)))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "${StorageHelper.formatBytes(overview.freeBytes)} Available Free Space",
                fontSize = 16.sp,
                color = Color(0x99FFFFFF)
            )
        }
    }
}

@Composable
private fun SamsungStorageCategories(overview: StorageOverview) {
    val items = listOf(
        CategoryItem("Apps", StorageHelper.formatBytes(overview.appsBytes), Color(0xFF000000)),
        CategoryItem("Videos", StorageHelper.formatBytes(overview.videosBytes), Color(0xFFBF5AF2)),
        CategoryItem("Audio", StorageHelper.formatBytes(overview.audioBytes), Color(0xFF64D2FF)),
        CategoryItem("Images", StorageHelper.formatBytes(overview.imagesBytes), Color(0xFFFF9F0A)),
        CategoryItem("Installation Files", StorageHelper.formatBytes(overview.apksBytes), Color(0xFFFFD60A)),
        CategoryItem("System OS", StorageHelper.formatBytes(overview.systemBytes), Color(0xFF8E8E93))
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x14FFFFFF))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { cat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(cat.color)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(text = cat.name, color = Color(0xDDFFFFFF), fontSize = 17.sp)
                }
                Text(text = cat.size, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private data class CategoryItem(val name: String, val size: String, val color: Color)

@Composable
private fun AppStorageRow(
    app: AppStorageDetail,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClearData: () -> Unit,
    onUninstall: () -> Unit,
    onDetails: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.02f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) Color(0x3DFFFFFF)
                else if (isSelected) Color(0x28FFFFFF)
                else Color(0x0FFFFFFF)
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x1FFFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onSelect)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    color = Color(0x88FFFFFF),
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = StorageHelper.formatBytes(app.totalBytes),
                color = Color(0xFF64D2FF),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Expanded detail & actions
        if (isSelected || isFocused) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "App: ${StorageHelper.formatBytes(app.appBytes)}  |  Data: ${StorageHelper.formatBytes(app.dataBytes)}  |  Cache: ${StorageHelper.formatBytes(app.cacheBytes)}",
                    color = Color(0xAAFFFFFF),
                    fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StorageActionButton("Clear Data", onClearData)
                if (!app.isSystemApp) {
                    StorageActionButton("Uninstall", onUninstall)
                }
                StorageActionButton("App Info", onDetails)
            }
        }
    }
}

@Composable
private fun StorageActionButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White else Color(0x24FFFFFF))
            .border(1.dp, if (isFocused) Color.White else Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StorageGlassButton(
    text: String,
    accent: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .scale(if (isFocused) 1.05f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) accent else Color(0x1FFFFFFF))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = if (isFocused && accent == Color.White) Color.Black else Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
