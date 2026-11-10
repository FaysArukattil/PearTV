package com.leantv.app.launcher

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.account.TvProfile
import com.leantv.app.data.AppVisuals
import com.leantv.app.data.IconCache
import com.leantv.app.data.InstalledAppInfo
import com.leantv.app.inputs.TvPort
import com.leantv.app.monitor.SystemMonitorPanel
import com.leantv.app.share.QuickSharePanel
import com.leantv.app.ui.CleanTvWizardDialog
import com.leantv.app.ui.FileManagerScreen
import com.leantv.app.ui.Focusable
import com.leantv.app.ui.Pal
import com.leantv.app.ui.PersonalizationDialog
import com.leantv.app.ui.Safe
import com.leantv.app.ui.Spinner
import com.leantv.app.ui.StorageManagerScreen
import com.leantv.app.ui.TvButton
import com.leantv.app.ui.TvCanvas
import com.leantv.app.ui.Ty
import com.leantv.app.ui.WALLPAPER_THEMES
import com.leantv.app.ui.rememberAutoFocus
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val s by vm.state.collectAsState()

    // Handle Back key: close any open dialog/screen first, never exit launcher
    BackHandler(
        enabled = s.showStorageManager || s.showFileManager || s.showPersonalization ||
                s.showQuickShare || s.showQuickShareHub || s.showInputsDialog ||
                s.showProfileDialog || s.showMonitorPanel || s.showCleanTvWizard ||
                s.debloatSuccessSummary != null || s.actionMenuApp != null ||
                s.uninstallConfirmApp != null || s.showDefaultPrompt
    ) {
        when {
            s.showStorageManager -> vm.closeStorageManager()
            s.showFileManager -> vm.closeFileManager()
            s.showPersonalization -> vm.closePersonalization()
            s.showQuickShare -> vm.closeQuickShare()
            s.showQuickShareHub -> vm.closeQuickShareHub()
            s.showInputsDialog -> vm.closeInputs()
            s.showProfileDialog -> vm.closeProfile()
            s.showCleanTvWizard -> vm.dismissCleanTvWizard()
            s.debloatSuccessSummary != null -> vm.dismissDebloatSummary()
            s.showMonitorPanel -> vm.closeSystemMonitor()
            s.uninstallConfirmApp != null -> vm.dismissUninstallConfirm()
            s.actionMenuApp != null -> vm.dismissActionMenu()
            s.showDefaultPrompt -> vm.dismissDefaultPrompt(dontAskAgain = false)
        }
    }

    // Fullscreen Overlay Screens
    if (s.showStorageManager) {
        StorageManagerScreen(
            adbClient = vm.adb,
            onClose = vm::closeStorageManager
        )
        return
    }

    if (s.showFileManager) {
        FileManagerScreen(
            onClose = vm::closeFileManager
        )
        return
    }

    TvCanvas {
        CleanTvBackdrop(
            themeId = s.wallpaperTheme,
            focusedApp = s.focusedApp ?: s.dockApps.firstOrNull() ?: s.allApps.firstOrNull()
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 26.dp),
            ) {
                // 1. CleanYourTV Top Bar: Kicker + Clock + Frosted Action Capsules
                CleanTvTopBar(
                    greeting = if (s.showGreeting) s.greetingText else "",
                    timeText = s.timeText,
                    dateText = s.dateText,
                    networkLabel = s.networkLabel,
                    profile = s.tvProfile,
                    connectedInputsCount = s.tvPorts.count { it.isConnected },
                    onOpenInputs = vm::openInputs,
                    onOpenNetwork = vm::openNetworkSettings,
                    onOpenQuickShare = vm::handleQuickShareClick,
                    onOpenStorage = vm::openStorageManager,
                    onOpenFiles = vm::openFileManager,
                    onOpenProfile = vm::openProfile,
                    onOpenPersonalization = vm::openPersonalization,
                    onOpenSettings = vm::openSystemSettings,
                    onOpenCleanTv = vm::requestCleanTv,
                )

                Spacer(Modifier.height(26.dp))

                // 2. Top Shelf Hero Name (CleanYourTV 46sp bold app title)
                val heroName = s.focusedApp?.appName
                    ?: s.dockApps.firstOrNull()?.appName
                    ?: s.allApps.firstOrNull()?.appName
                    ?: ""

                Text(
                    text = heroName,
                    style = Ty.Display.copy(
                        color = Color.White,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp).height(56.dp)
                )

                Spacer(Modifier.height(14.dp))

                if (s.isLoading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Spinner()
                            Text("Loading your apps...", style = Ty.Body.copy(color = Color(0x99FFFFFF)))
                        }
                    }
                } else {
                    // 3. The Dock Row (Pinned Favorites or Top Apps)
                    val dock = s.dockApps.ifEmpty { s.allApps.take(4) }
                    if (dock.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            dock.forEach { app ->
                                CleanTvAppTile(
                                    app = app,
                                    isDock = true,
                                    onFocus = { vm.setFocusedApp(app) },
                                    onClick = { vm.launch(app) },
                                    onLongClick = { vm.showActionMenu(app) }
                                )
                            }
                        }
                    }

                    // 4. Frequently Opened Apps Section (Feature #6)
                    val dockPkgSet = dock.map { it.packageName }.toSet()
                    val frequent = s.frequentApps.filter { it.packageName !in dockPkgSet }
                    if (frequent.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        AppSectionHeader("Frequently Used")
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            frequent.take(4).forEach { app ->
                                CleanTvAppTile(
                                    app = app,
                                    isDock = false,
                                    badgeText = "${vm.journal.getLaunchCount(app.packageName)}x",
                                    onFocus = { vm.setFocusedApp(app) },
                                    onClick = { vm.launch(app) },
                                    onLongClick = { vm.showActionMenu(app) }
                                )
                            }
                        }
                    }

                    // 5. Recently Opened Apps Section (Feature #6)
                    val recent = s.recentApps.filter { it.packageName !in dockPkgSet && it !in frequent }
                    if (recent.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        AppSectionHeader("Recently Used")
                        Spacer(Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            recent.take(4).forEach { app ->
                                CleanTvAppTile(
                                    app = app,
                                    isDock = false,
                                    onFocus = { vm.setFocusedApp(app) },
                                    onClick = { vm.launch(app) },
                                    onLongClick = { vm.showActionMenu(app) }
                                )
                            }
                        }
                    }

                    // 6. The Grid: All Installed Applications (4-column rows)
                    val gridApps = s.allApps.filter { it.packageName !in dockPkgSet }
                    if (gridApps.isNotEmpty()) {
                        Spacer(Modifier.height(26.dp))
                        AppSectionHeader("All Applications (${s.allApps.size})")
                        Spacer(Modifier.height(12.dp))

                        val gridRows = remember(gridApps) { gridApps.chunked(4) }
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            gridRows.forEach { rowApps ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                ) {
                                    rowApps.forEach { app ->
                                        CleanTvAppTile(
                                            app = app,
                                            isDock = false,
                                            onFocus = { vm.setFocusedApp(app) },
                                            onClick = { vm.launch(app) },
                                            onLongClick = { vm.showActionMenu(app) }
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }

            // CleanYourTV 1-Click Wizard Dialog
            if (s.showCleanTvWizard) {
                CleanTvWizardDialog(
                    majorOptions = s.majorOptions,
                    onToggleOption = vm::toggleMajorOption,
                    onConfirm = vm::executeOneClickCleanTv,
                    onDismiss = vm::dismissCleanTvWizard
                )
            }

            // CleanYourTV Success Modal
            s.debloatSuccessSummary?.let { summary ->
                CleanTvSuccessDialog(
                    summary = summary,
                    onDismiss = vm::dismissDebloatSummary
                )
            }

            // Inputs Switcher Modal (Live physical hardware ports only)
            if (s.showInputsDialog) {
                InputsSwitcherDialog(
                    ports = s.tvPorts,
                    onSelectPort = vm::switchToPort,
                    onDismiss = vm::closeInputs
                )
            }

            // Google Accounts Modal
            if (s.showProfileDialog) {
                ProfileAccountsDialog(
                    profile = s.tvProfile,
                    onAddAccount = vm::openAddGoogleAccount,
                    onManageSettings = vm::openSystemAccountsSettings,
                    onDismiss = vm::closeProfile
                )
            }

            // Quick Share Hub Modal
            if (s.showQuickShareHub) {
                QuickShareHubDialog(
                    onLaunchNative = vm::launchNativeQuickShare,
                    onLaunchUniversalAirShare = {
                        vm.closeQuickShareHub()
                        vm.openQuickShare()
                    },
                    onLaunchLocalSend = vm::launchLocalSend,
                    onLaunchGoogleStore = vm::launchGoogleQuickShareStore,
                    onDismiss = vm::closeQuickShareHub
                )
            }

            // Quick Share Universal Wi-Fi Slide-in Panel
            if (s.showQuickShare) {
                QuickSharePanel(
                    state = s.quickShareState,
                    onClose = vm::closeQuickShare,
                    onOpenUrl = vm::openUrl
                )
            }

            // Personalization & Wallpapers Dialog
            if (s.showPersonalization) {
                PersonalizationDialog(
                    currentTheme = s.wallpaperTheme,
                    is24h = s.is24hClock,
                    showGreeting = s.showGreeting,
                    onSelectTheme = vm::setWallpaperTheme,
                    onToggle24h = vm::toggleClockFormat,
                    onToggleGreeting = vm::toggleGreeting,
                    onOpenFileManagerForWallpaper = {
                        vm.closePersonalization()
                        vm.openFileManager()
                    },
                    onDismiss = vm::closePersonalization
                )
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
                    onClearData = { vm.clearAppDataAction(app) },
                    onClearCache = { vm.clearAppCacheAction(app) },
                    onHideApp = { vm.toggleHideApp(app) },
                    onUninstall = { vm.requestUninstall(app) },
                    onDetails = { vm.openAppDetails(app) }
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

            // System Monitor Slide-in Panel
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
                        style = Ty.Body.copy(color = Color.White),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0xE61F232B))
                            .padding(horizontal = 28.dp, vertical = 14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSectionHeader(title: String) {
    Text(
        text = title,
        style = Ty.Heading.copy(
            color = Color(0xCCFFFFFF),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        ),
        modifier = Modifier.padding(start = 4.dp)
    )
}

/**
 * CleanYourTV Top Bar:
 * Left: "GOOD AFTERNOON" + "1:13 PM | SATURDAY 17 OCTOBER"
 * Right: Inputs, Wi-Fi, Quick Share, Storage, Files, Profile, Themes, Settings, CleanTV.
 */
@Composable
private fun CleanTvTopBar(
    greeting: String,
    timeText: String,
    dateText: String,
    networkLabel: String,
    profile: TvProfile,
    connectedInputsCount: Int,
    onOpenInputs: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenQuickShare: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenPersonalization: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCleanTv: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: Kicker + Clock + Date
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (greeting.isNotBlank()) {
                Text(
                    text = greeting.uppercase(),
                    style = Ty.Small.copy(
                        color = Color(0xB3FFFFFF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.2.sp
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = timeText,
                    style = Ty.Display.copy(
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                )

                Box(
                    Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(Color(0x33FFFFFF))
                )

                Text(
                    text = dateText.uppercase(),
                    style = Ty.Small.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = Color(0x99FFFFFF)
                    )
                )
            }
        }

        // Right: Frosted Action Capsules (Apple TV style)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            val inputLabel = if (connectedInputsCount > 0) "Inputs ($connectedInputsCount)" else "Inputs"
            CleanTvTopCapsule(icon = "📺", label = inputLabel, onClick = onOpenInputs)

            val netIcon = if (networkLabel.contains("Ethernet", ignoreCase = true)) "🌐" else "📶"
            CleanTvTopCapsule(icon = netIcon, label = networkLabel, onClick = onOpenNetwork)

            CleanTvTopCapsule(icon = "⚡", label = "Quick Share", onClick = onOpenQuickShare)
            CleanTvTopCapsule(icon = "💾", label = "Storage", onClick = onOpenStorage)
            CleanTvTopCapsule(icon = "📁", label = "Files", onClick = onOpenFiles)
            CleanTvTopCapsule(icon = profile.initial, label = profile.displayName, onClick = onOpenProfile)
            CleanTvTopCapsule(icon = "🎨", label = "Theme", onClick = onOpenPersonalization)
            CleanTvTopCapsule(icon = "⚙", label = "Settings", onClick = onOpenSettings)
            CleanTvTopCapsule(icon = "✨", label = "CleanTV", onClick = onOpenCleanTv)
        }
    }
}

@Composable
private fun CleanTvTopCapsule(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        idle = Color(0x33222222),
        scaleOnFocus = 1.08f,
        idleOutline = false
    ) { focused ->
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (focused) Color(0xFF333333) else Color(0x33222222))
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) Color.White else Color(0x2BFFFFFF),
                    shape = RoundedCornerShape(50)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(icon, fontSize = 13.sp, color = if (focused) Color.White else Color(0xCCFFFFFF))
            Text(
                text = label,
                style = Ty.Small.copy(
                    color = if (focused) Color.White else Color(0xCCFFFFFF),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * CleanYourTV exact tile implementation:
 * - 180dp x 101dp aspect ratio (16:9)
 * - If app has a 16:9 banner: banner fills card completely with CenterCrop
 * - If app has no banner: tinted gradient plate + centered 46dp icon + bottom title
 * - On focus: 1.10x scale, glass specular highlight, 2.5dp crisp white rim
 * - In grid: shows tile name underneath (14sp, #F2FFFFFF) when focused
 */
@Composable
private fun CleanTvAppTile(
    app: InstalledAppInfo,
    isDock: Boolean,
    badgeText: String? = null,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    var visuals by remember(app.packageName) { mutableStateOf(IconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (visuals == null) {
            visuals = IconCache.loadVisuals(context, app.packageName)
        }
    }

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.10f else 1f,
        tween(150, easing = FastOutSlowInEasing),
        label = "tileScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(180.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (focused) 20f else 0f
            }
            .onPreviewKeyEvent {
                if (it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    if (it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU) {
                        onLongClick()
                        return@onPreviewKeyEvent true
                    }
                }
                false
            }
    ) {
        Box(
            modifier = Modifier
                .size(180.dp, 101.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(
                    width = if (focused) 2.5.dp else 1.dp,
                    color = if (focused) Color.White else Color(0x24FFFFFF),
                    shape = RoundedCornerShape(18.dp)
                )
                .onFocusChanged {
                    if (it.isFocused) {
                        focused = true
                        onFocus()
                    } else {
                        focused = false
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .focusable()
        ) {
            val v = visuals
            if (v != null && v.isBanner) {
                // TV Widescreen Banner
                Image(
                    bitmap = v.bitmap,
                    contentDescription = app.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Sideloaded / Mobile APK without TV banner:
                // Auto-generate 16:9 squircle gradient plate + centered 46dp icon + bottom title
                val plateTop = v?.topColor ?: extractFallbackHue(app)
                val plateBottom = v?.bottomColor ?: Color(0xFF101216)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(plateTop, plateBottom)))
                ) {
                    // Centered high-res app icon
                    val iconBitmap = v?.bitmap
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center)
                        )
                    }

                    // Bottom title tag
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(Color(0x73000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName,
                            style = Ty.Small.copy(
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Top-Right Badge (e.g. launch count)
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color(0xFF64B5F6),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Apple tvOS Frosted Glass Specular Highlight (Top 45% shine)
            if (focused) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(45.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0x66FFFFFF), Color(0x00FFFFFF))
                            )
                        )
                )
            }
        }

        // In Grid: focused tile title appears underneath
        if (!isDock && focused) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = app.appName,
                style = Ty.Small.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xF2FFFFFF)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (!isDock) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(18.dp)) // Reserve height to avoid grid jumping
        }
    }
}

/**
 * CleanYourTV Backdrop:
 * Renders the chosen theme background (Pitch Black, Aurora, Deep Space, Sunset, Apple Dark Glass)
 * with an ambient radial blur matching the focused app's dominant color.
 */
@Composable
private fun CleanTvBackdrop(
    themeId: String,
    focusedApp: InstalledAppInfo?,
    content: @Composable () -> Unit
) {
    val theme = WALLPAPER_THEMES.find { it.id == themeId } ?: WALLPAPER_THEMES.first()
    val ambientColor = extractFallbackHue(focusedApp)
    val animatedAmbient by animateColorAsState(
        targetValue = ambientColor,
        animationSpec = tween(500),
        label = "ambientGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // 1. Theme gradient base
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = theme.gradientColors,
                        startY = 0f,
                        endY = size.height
                    )
                )

                // 2. Ambient radial hero glow behind top-left title
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedAmbient.copy(alpha = 0.40f),
                            animatedAmbient.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.28f),
                        radius = size.width * 0.45f
                    )
                )

                // 3. Subtle dark vignette towards bottom edges
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xB3000000)),
                        startY = size.height * 0.5f,
                        endY = size.height
                    )
                )
            }
    ) {
        content()
    }
}

private fun extractFallbackHue(app: InstalledAppInfo?): Color {
    if (app == null) return Color(0xFF1E1E1E)
    val name = app.appName.lowercase(Locale.ROOT)
    val pkg = app.packageName.lowercase(Locale.ROOT)
    return when {
        name.contains("netflix") || pkg.contains("netflix") -> Color(0xFFE50914)
        name.contains("youtube") || pkg.contains("youtube") -> Color(0xFFFF0000)
        name.contains("prime") || pkg.contains("amazonvideo") -> Color(0xFF00A8E1)
        name.contains("hotstar") || pkg.contains("hotstar") -> Color(0xFF1F44E5)
        name.contains("fancode") || pkg.contains("fancode") -> Color(0xFFFF5200)
        name.contains("iptv") || name.contains("smart iptv") -> Color(0xFF007AFF)
        name.contains("apple") || pkg.contains("appletv") -> Color(0xFF64D2FF)
        name.contains("play") || pkg.contains("vending") -> Color(0xFF00C853)
        name.contains("xstream") || name.contains("airtel") -> Color(0xFFE50000)
        name.contains("spotify") || pkg.contains("spotify") -> Color(0xFF1DB954)
        name.contains("plex") || pkg.contains("plex") -> Color(0xFFE5A00D)
        name.contains("kodi") || pkg.contains("kodi") -> Color(0xFF17B2E7)
        name.contains("twitch") || pkg.contains("twitch") -> Color(0xFF9146FF)
        name.contains("disney") || pkg.contains("disney") -> Color(0xFF113CCF)
        name.contains("jio") || pkg.contains("jio") -> Color(0xFF0A38B8)
        name.contains("sony") || pkg.contains("sonyliv") -> Color(0xFF00E5FF)
        name.contains("zee") || pkg.contains("zee5") -> Color(0xFF8E24AA)
        else -> {
            val hash = Math.abs(pkg.hashCode())
            val hues = listOf(
                Color(0xFF3B82F6),
                Color(0xFFEF4444),
                Color(0xFF10B981),
                Color(0xFFF59E0B),
                Color(0xFF8B5CF6),
                Color(0xFFEC4899),
                Color(0xFF06B6D4)
            )
            hues[hash % hues.size]
        }
    }
}

/** CleanYourTV Celebratory Success Modal */
@Composable
private fun CleanTvSuccessDialog(
    summary: String,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF15181E))
                .border(2.dp, Color.White, RoundedCornerShape(26.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0x3300C853)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = Ty.Display.copy(color = Color(0xFF00C853), fontSize = 38.sp))
            }

            Text("Your TV is Transformed!", style = Ty.Title.copy(color = Color.White, fontSize = 28.sp))

            Text(
                summary,
                style = Ty.Body.copy(color = Color(0xCCFFFFFF)),
                textAlign = TextAlign.Center
            )

            Text(
                "Telemetry daemons and background ads are now disabled. 0.5x animation scales applied. 100% reversible anytime.",
                style = Ty.Small.copy(color = Color(0x99FFFFFF)),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            TvButton(
                text = "Enjoy CleanTV ➔",
                onClick = onDismiss,
                primary = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(autoFocus)
            )
        }
    }
}

/**
 * Inputs Switcher Dialog (Feature #3):
 * Only shows real hardware passthrough ports (HDMI 1, HDMI 2, Tuner, AV).
 * Connected ports are fully colored with active indicators. Disconnected ports are grayed out.
 */
@Composable
private fun InputsSwitcherDialog(
    ports: List<TvPort>,
    onSelectPort: (TvPort) -> Unit,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF15181E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("TV Inputs", style = Ty.Title.copy(color = Color.White, fontSize = 26.sp))
            Text("Physically detected TV hardware ports", style = Ty.Dim.copy(fontSize = 14.sp))

            Spacer(Modifier.height(6.dp))

            if (ports.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No external hardware inputs found on this device.", color = Color(0x88FFFFFF), fontSize = 14.sp)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ports.forEachIndexed { index, port ->
                        TvPortRow(
                            port = port,
                            onClick = { onSelectPort(port) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { if (index == 0) it.focusRequester(autoFocus) else it }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TvButton(
                text = "Close",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                primary = false
            )
        }
    }
}

@Composable
private fun TvPortRow(
    port: TvPort,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    val isConnected = port.isConnected
    val bg = if (isFocused) Color.White
    else if (isConnected) Color(0x282979FF)
    else Color(0x0EFFFFFF)

    val border = if (isFocused) Color.White
    else if (isConnected) Color(0x662979FF)
    else Color(0x18FFFFFF)

    Box(
        modifier = modifier
            .scale(if (isFocused) 1.02f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(if (isFocused) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📺  ${port.label}",
                    color = if (isFocused) Color.Black else if (isConnected) Color.White else Color(0x66FFFFFF),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Connection indicator badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isConnected) Color(0x334CAF50)
                        else Color(0x22FFFFFF)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isConnected) "● Connected" else "○ Disconnected",
                    color = if (isConnected) Color(0xFF81C784) else Color(0x55FFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** Google Account Switcher & Profile Dialog (Feature #5) */
@Composable
private fun ProfileAccountsDialog(
    profile: TvProfile,
    onAddAccount: () -> Unit,
    onManageSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF15181E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Google Accounts & Profile", style = Ty.Title.copy(color = Color.White, fontSize = 26.sp))
            Text("Manage accounts linked to this Android TV system", style = Ty.Dim.copy(fontSize = 14.sp))

            Spacer(Modifier.height(4.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x33000000))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Currently Active:", style = Ty.Small.copy(color = Color(0xFF64D2FF), fontWeight = FontWeight.Bold))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1976D2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(profile.initial, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(profile.displayName, style = Ty.Heading.copy(color = Color.White, fontSize = 17.sp))
                        profile.email?.let {
                            Text(it, style = Ty.Small.copy(color = Color(0x99FFFFFF), fontSize = 13.sp))
                        }
                    }
                }

                if (profile.allAccounts.size > 1) {
                    Spacer(Modifier.height(6.dp))
                    Text("All Synced Accounts (${profile.allAccounts.size}):", style = Ty.Small.copy(color = Pal.Dim))
                    profile.allAccounts.forEach { acc ->
                        Text("• $acc", style = Ty.Body.copy(color = Color.White, fontSize = 14.sp))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TvButton(
                    text = "➕ Add or Switch Google Account",
                    onClick = onAddAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(autoFocus),
                    primary = true
                )

                TvButton(
                    text = "⚙ Manage TV Accounts in System Settings",
                    onClick = onManageSettings,
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )

                TvButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )
            }
        }
    }
}

/** Quick Share Hub Dialog */
@Composable
private fun QuickShareHubDialog(
    onLaunchNative: () -> Unit,
    onLaunchUniversalAirShare: () -> Unit,
    onLaunchLocalSend: () -> Unit,
    onLaunchGoogleStore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF15181E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Quick Share Hub", style = Ty.Title.copy(color = Color.White, fontSize = 28.sp))
            Text(
                "Send files, APKs, videos, and links to your TV from any Android, iPhone, iPad, Mac, or PC.",
                style = Ty.Dim.copy(fontSize = 14.sp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvButton(
                    text = "🌐  Universal Wi-Fi AirShare (iPhone & Android - No App Needed)",
                    onClick = onLaunchUniversalAirShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(autoFocus),
                    primary = true
                )

                TvButton(
                    text = "⚡  Launch Android Quick Share (Nearby Share)",
                    onClick = onLaunchNative,
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )

                TvButton(
                    text = "📲  Launch / Install LocalSend (Cross-Platform AirDrop)",
                    onClick = onLaunchLocalSend,
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )

                TvButton(
                    text = "🛒  Update / Install via Google Play Store",
                    onClick = onLaunchGoogleStore,
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )

                TvButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    primary = false
                )
            }
        }
    }
}

/** Action menu for a selected app (Open, Favorite, Clear Data, Clear Cache, Hide, Uninstall, Details). */
@Composable
private fun LauncherActionDialog(
    app: InstalledAppInfo,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onClearData: () -> Unit,
    onClearCache: () -> Unit,
    onHideApp: () -> Unit,
    onUninstall: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        val cancelFocus = rememberAutoFocus(app.packageName)
        Column(
            Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF15181E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(app.appName, style = Ty.Title.copy(color = Color.White))
            Text(app.packageName, style = Ty.Small.copy(fontFamily = FontFamily.Monospace, color = Color(0x99FFFFFF)))

            Spacer(Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton("Open Application", onClick = onOpen, primary = true, modifier = Modifier.fillMaxWidth())
                TvButton(
                    if (isFavorite) "Unpin from Dock / Favorites" else "Pin to Dock / Favorites",
                    onClick = onToggleFavorite,
                    primary = false,
                    modifier = Modifier.fillMaxWidth()
                )
                TvButton("Clear Application Data", onClick = onClearData, primary = false, modifier = Modifier.fillMaxWidth())
                TvButton("Clear Cache", onClick = onClearCache, primary = false, modifier = Modifier.fillMaxWidth())
                TvButton("Hide from Launcher Grid", onClick = onHideApp, primary = false, modifier = Modifier.fillMaxWidth())
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
            .background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        val focusRequester = rememberAutoFocus()
        Column(
            Modifier
                .width(580.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF15181E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Set CleanYourTV as Default Home?", style = Ty.Title.copy(color = Color.White))
            Text(
                "Enjoy this fast, clean, Apple TV-style home launcher every time you turn on your TV or press the Home button on your remote.\n\nAndroid will prompt you to select CleanYourTV as the default Home app in system settings.",
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
            .background(Color(0xD9000000)),
        contentAlignment = Alignment.Center
    ) {
        val cancelFocus = rememberAutoFocus(app.packageName)
        Column(
            Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF15181E))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Uninstall ${app.appName}?", style = Ty.Title.copy(color = Color.White))
            Text(
                "CleanYourTV will open the Android TV uninstaller for ${app.packageName}.\nConfirm the prompt to remove the application.",
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
