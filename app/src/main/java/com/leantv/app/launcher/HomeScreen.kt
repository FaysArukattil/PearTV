package com.leantv.app.launcher

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.data.IconCache
import com.leantv.app.data.InstalledAppInfo
import com.leantv.app.monitor.SystemMonitorPanel
import com.leantv.app.share.QuickSharePanel
import com.leantv.app.ui.Focusable
import com.leantv.app.ui.Pal
import com.leantv.app.ui.Safe
import com.leantv.app.ui.Spinner
import com.leantv.app.ui.TvButton
import com.leantv.app.ui.TvCanvas
import com.leantv.app.ui.Ty
import com.leantv.app.ui.rememberAutoFocus
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val s by vm.state.collectAsState()

    // Handle Back key: close share, monitor, action sheets or dialogs if open, never exit the launcher
    BackHandler(enabled = s.showQuickShare || s.showMonitorPanel || s.actionMenuApp != null || s.uninstallConfirmApp != null || s.showDefaultPrompt) {
        when {
            s.showQuickShare -> vm.closeQuickShare()
            s.showMonitorPanel -> vm.closeSystemMonitor()
            s.uninstallConfirmApp != null -> vm.dismissUninstallConfirm()
            s.actionMenuApp != null -> vm.dismissActionMenu()
            s.showDefaultPrompt -> vm.dismissDefaultPrompt(dontAskAgain = false)
        }
    }

    TvCanvas {
        HomeBackdrop(s.focusedApp) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Safe.H, vertical = Safe.V),
            ) {
                // Top Header Row: Branding, Action pills, Clock & Date
                TopHeaderBar(
                    timeText = s.timeText,
                    dateText = s.dateText,
                    onOpenQuickShare = vm::openQuickShare,
                    onOpenMonitor = vm::openSystemMonitor,
                    onOpenTools = vm::openLeanTvOptimizer,
                    onOpenSettings = vm::openSystemSettings,
                )

                Spacer(Modifier.height(28.dp))

                if (s.isLoading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Spinner()
                            Text("Loading your apps...", style = Ty.Heading)
                        }
                    }
                } else {
                    // Featured Hero Carousel (Apple tvOS Top Shelf)
                    if (s.featuredApps.isNotEmpty()) {
                        SectionTitle("Featured")
                        Spacer(Modifier.height(14.dp))
                        FeaturedAppsRow(
                            apps = s.featuredApps,
                            onFocus = vm::setFocusedApp,
                            onClick = vm::launch,
                            onLongClick = vm::showActionMenu,
                        )
                        Spacer(Modifier.height(32.dp))
                    }

                    // Favorites Row
                    if (s.favoriteApps.isNotEmpty()) {
                        SectionTitle("Favorites")
                        Spacer(Modifier.height(14.dp))
                        AppRow(
                            apps = s.favoriteApps,
                            onFocus = vm::setFocusedApp,
                            onClick = vm::launch,
                            onLongClick = vm::showActionMenu,
                        )
                        Spacer(Modifier.height(32.dp))
                    }

                    // All Applications Row
                    if (s.allApps.isNotEmpty()) {
                        SectionTitle("All Applications (${s.allApps.size})")
                        Spacer(Modifier.height(14.dp))
                        AppRow(
                            apps = s.allApps,
                            onFocus = vm::setFocusedApp,
                            onClick = vm::launch,
                            onLongClick = vm::showActionMenu,
                        )
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }

            // Default Launcher Prompt
            if (s.showDefaultPrompt) {
                DefaultLauncherDialog(
                    onConfirm = vm::requestSetDefaultLauncher,
                    onDismiss = { vm.dismissDefaultPrompt(dontAskAgain = true) },
                    onLater = { vm.dismissDefaultPrompt(dontAskAgain = false) },
                )
            }

            // Action Menu Dialog (Long-press / Menu key)
            s.actionMenuApp?.let { app ->
                LauncherActionDialog(
                    app = app,
                    isFavorite = vm.isFavorite(app.packageName),
                    onDismiss = vm::dismissActionMenu,
                    onOpen = {
                        vm.dismissActionMenu()
                        vm.launch(app)
                    },
                    onToggleFavorite = { vm.toggleFavorite(app) },
                    onUninstall = { vm.requestUninstall(app) },
                    onDetails = {
                        vm.dismissActionMenu()
                        vm.openAppDetails(app)
                    }
                )
            }

            // Uninstall confirmation dialog
            s.uninstallConfirmApp?.let { app ->
                LauncherUninstallDialog(
                    app = app,
                    onDismiss = vm::dismissUninstallConfirm,
                    onConfirm = { vm.confirmUninstall(app) }
                )
            }

            // Quick Share Slide-in Panel (Phase 4)
            if (s.showQuickShare) {
                QuickSharePanel(
                    state = s.quickShareState,
                    onClose = vm::closeQuickShare,
                    onOpenUrl = vm::openUrl
                )
            }

            // System Monitor Slide-in Panel (Phase 3)
            if (s.showMonitorPanel) {
                SystemMonitorPanel(
                    ram = s.ramSnapshot,
                    cpu = s.cpuSnapshot,
                    runningApps = s.runningApps,
                    isCleaning = s.isCleaning,
                    onClose = vm::closeSystemMonitor,
                    onKillApp = vm::killRunningApp,
                    onCleanAll = vm::cleanAllRunningApps,
                )
            }

            // Toast feedback
            s.toastMessage?.let { msg ->
                LaunchedEffect(msg) {
                    delay(3800)
                    vm.clearToast()
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = Safe.V + 8.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        msg,
                        style = Ty.Body,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Pal.SurfaceHi)
                            .padding(horizontal = 28.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = Ty.Heading.copy(
            color = Pal.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        ),
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
private fun TopHeaderBar(
    timeText: String,
    dateText: String,
    onOpenQuickShare: () -> Unit,
    onOpenMonitor: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // App title & Quick pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "LeanTV",
                style = Ty.Display.copy(
                    color = Pal.Amber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp
                )
            )

            TvPillButton(text = "Quick Share", onClick = onOpenQuickShare)
            TvPillButton(text = "System Monitor", onClick = onOpenMonitor)
            TvPillButton(text = "Tools & Debloat", onClick = onOpenTools)
            TvPillButton(text = "Settings", onClick = onOpenSettings)
        }

        // Live Clock & Date
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = timeText,
                style = Ty.Title.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = Pal.Text
                )
            )
            Text(
                text = dateText,
                style = Ty.Small.copy(color = Pal.Dim, fontSize = 15.sp)
            )
        }
    }
}

@Composable
private fun TvPillButton(text: String, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        idle = Color(0x33FFFFFF),
        scaleOnFocus = 1.05f
    ) { focused ->
        Text(
            text = text,
            style = Ty.Small.copy(
                color = if (focused) Pal.Amber else Pal.Text,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
        )
    }
}

/** Featured apps carousel: large banner-style cards with focus scale & glow. */
@Composable
private fun FeaturedAppsRow(
    apps: List<InstalledAppInfo>,
    onFocus: (InstalledAppInfo?) -> Unit,
    onClick: (InstalledAppInfo) -> Unit,
    onLongClick: (InstalledAppInfo) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            FeaturedAppCard(
                app = app,
                onFocus = { onFocus(app) },
                onClick = { onClick(app) },
                onLongClick = { onLongClick(app) },
            )
        }
    }
}

@Composable
private fun FeaturedAppCard(
    app: InstalledAppInfo,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) { mutableStateOf(IconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            iconBitmap = IconCache.loadIcon(context, app.packageName)
        }
    }

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(150), label = "featScale")
    val borderRing by animateColorAsState(if (focused) Pal.Amber else Color(0x22FFFFFF), tween(150), label = "featRing")
    val cardBg by animateColorAsState(if (focused) Color(0x4D24566C) else Color(0x2617394A), tween(150), label = "featBg")

    Box(
        Modifier
            .width(280.dp)
            .height(160.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(if (focused) 3.dp else 1.dp, borderRing, RoundedCornerShape(22.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    onLongClick()
                    true
                } else false
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(bitmap = iconBitmap!!, contentDescription = app.appName, modifier = Modifier.size(54.dp))
                } else {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(Pal.Amber.copy(alpha = 0.4f)))
                }
            }

            Text(
                text = app.appName,
                style = Ty.Heading.copy(
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (focused) Pal.Amber else Pal.Text
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Standard shelf row for Favorites & All Applications. */
@Composable
private fun AppRow(
    apps: List<InstalledAppInfo>,
    onFocus: (InstalledAppInfo?) -> Unit,
    onClick: (InstalledAppInfo) -> Unit,
    onLongClick: (InstalledAppInfo) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
    ) {
        items(apps, key = { it.packageName }) { app ->
            StandardAppCard(
                app = app,
                onFocus = { onFocus(app) },
                onClick = { onClick(app) },
                onLongClick = { onLongClick(app) },
            )
        }
    }
}

@Composable
private fun StandardAppCard(
    app: InstalledAppInfo,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) { mutableStateOf(IconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            iconBitmap = IconCache.loadIcon(context, app.packageName)
        }
    }

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.09f else 1f, tween(140), label = "appScale")
    val ring by animateColorAsState(if (focused) Pal.Amber else Color(0x1AFFFFFF), tween(140), label = "appRing")
    val bg by animateColorAsState(if (focused) Color(0x5924566C) else Color(0x3317394A), tween(140), label = "appBg")

    Box(
        Modifier
            .width(170.dp)
            .height(130.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(if (focused) 3.dp else 1.dp, ring, RoundedCornerShape(18.dp))
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU && event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    onLongClick()
                    true
                } else false
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x2B000000)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(bitmap = iconBitmap!!, contentDescription = app.appName, modifier = Modifier.size(44.dp))
                } else {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(Pal.Amber.copy(alpha = 0.3f)))
                }
            }

            Text(
                text = app.appName,
                style = Ty.Body.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (focused) Pal.Amber else Pal.Text
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Action menu for a selected app (Open, Favorite, Uninstall, Details). */
@Composable
private fun LauncherActionDialog(
    app: InstalledAppInfo,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onUninstall: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC050E14)),
        contentAlignment = Alignment.Center
    ) {
        val cancelFocus = rememberAutoFocus(app.packageName)
        Column(
            Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Pal.Surface)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(app.appName, style = Ty.Title)
            Text(app.packageName, style = Ty.Small.copy(fontFamily = FontFamily.Monospace))

            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("Open Application", onClick = onOpen, primary = true, modifier = Modifier.fillMaxWidth())
                TvButton(
                    if (isFavorite) "Remove from Favorites" else "Add to Favorites",
                    onClick = onToggleFavorite,
                    primary = false,
                    modifier = Modifier.fillMaxWidth()
                )
                if (app.canUninstall) {
                    TvButton("Uninstall Application", onClick = onUninstall, primary = false, modifier = Modifier.fillMaxWidth())
                }
                TvButton("System App Info", onClick = onDetails, primary = false, modifier = Modifier.fillMaxWidth())
                TvButton("Close", onClick = onDismiss, primary = false, modifier = Modifier.fillMaxWidth().focusRequester(cancelFocus))
            }
        }
    }
}

/** Default Launcher setup dialog. */
@Composable
private fun DefaultLauncherDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14)),
        contentAlignment = Alignment.Center
    ) {
        val focusRequester = rememberAutoFocus()
        Column(
            Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Pal.Surface)
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Set LeanTV as Default Home?", style = Ty.Title)
            Text(
                "Enjoy this fast, clean, Apple TV-style home launcher every time you turn on your TV or press the Home button on your remote.\n\nAndroid will prompt you to select LeanTV as the default Home app in system settings.",
                style = Ty.Dim
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Set as Default", onClick = onConfirm, primary = true, modifier = Modifier.focusRequester(focusRequester))
                TvButton("Maybe Later", onClick = onLater, primary = false)
                TvButton("Don't Ask Again", onClick = onDismiss, primary = false)
            }
        }
    }
}

@Composable
private fun LauncherUninstallDialog(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14)),
        contentAlignment = Alignment.Center
    ) {
        val cancelFocus = rememberAutoFocus(app.packageName)
        Column(
            Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Pal.Surface)
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Uninstall ${app.appName}?", style = Ty.Title)
            Text(
                "LeanTV will open the Android TV uninstaller for ${app.packageName}.\nConfirm the prompt to remove the application.",
                style = Ty.Dim
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton("Cancel", onClick = onDismiss, primary = false, modifier = Modifier.focusRequester(cancelFocus))
                TvButton("Uninstall", onClick = onConfirm, primary = true)
            }
        }
    }
}

/** Apple tvOS-inspired ambient backdrop with subtle shifting glow. */
@Composable
private fun HomeBackdrop(focusedApp: InstalledAppInfo?, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF07141E),
                        Color(0xFF030A0F)
                    )
                )
            )
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(
                            Pal.Amber.copy(alpha = if (focusedApp != null) 0.12f else 0.06f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.25f),
                        radius = size.width * 0.65f
                    )
                )
            }
    ) {
        content()
    }
}
