package com.leantv.app.launcher

import android.view.KeyEvent
import android.view.SoundEffectConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.mutableLongStateOf
import com.leantv.app.R
import com.leantv.app.ui.TvSound
import com.leantv.app.ui.CleanTvScreen
import com.leantv.app.ui.AppleTvScreensaver
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.Input
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import com.leantv.app.ui.glassCard
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Input
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import com.leantv.app.ui.modalTrap
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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val s by vm.state.collectAsState()
    var showTools by remember { mutableStateOf(false) }

    // Handle Back key: close any open dialog/screen first, never exit launcher
    BackHandler(
        enabled = s.showStorageManager || s.showFileManager || s.showPersonalization ||
                s.showCleanTvScreen || s.showScreensaver ||
                s.showNativeQuickShare || s.showInputsDialog ||
                s.showProfileDialog || s.showMonitorPanel || s.showCleanTvWizard ||
                s.debloatSuccessSummary != null || s.actionMenuApp != null ||
                s.uninstallConfirmApp != null || s.showDefaultPrompt
    ) {
        when {
            s.showScreensaver -> vm.closeScreensaver()
            s.showCleanTvScreen -> vm.closeCleanTvScreen()
            s.showStorageManager -> vm.closeStorageManager()
            s.showFileManager -> vm.closeFileManager()
            s.showPersonalization -> vm.closePersonalization()
            s.showNativeQuickShare -> vm.closeNativeQuickShare()
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

    BackHandler(enabled = showTools) { showTools = false }

    // Fullscreen Overlay Screens
    if (s.showScreensaver) {
        TvCanvas {
            AppleTvScreensaver(onDismiss = vm::closeScreensaver)
        }
        return
    }

    if (s.showCleanTvScreen) {
        TvCanvas {
            CleanTvScreen(
                adbClient = vm.adb,
                journal = vm.journal,
                scanner = vm.scanner,
                tweaks = vm.tweaks,
                onClose = vm::closeCleanTvScreen
            )
        }
        return
    }

    if (s.showStorageManager) {
        TvCanvas {
            StorageManagerScreen(
                adbClient = vm.adb,
                journal = vm.journal,
                onClose = vm::closeStorageManager
            )
        }
        return
    }

    if (s.showFileManager) {
        TvCanvas {
            FileManagerScreen(
                initialDir = s.fileManagerInitialDir,
                highlightFile = s.fileManagerHighlightFile,
                onClose = vm::closeFileManager
            )
        }
        return
    }

    val modalOpen = s.showPersonalization || s.showNativeQuickShare ||
            s.showInputsDialog || s.showProfileDialog || s.showMonitorPanel || s.showCleanTvWizard ||
            s.showCleanTvScreen || s.showScreensaver ||
            s.debloatSuccessSummary != null || s.actionMenuApp != null ||
            s.uninstallConfirmApp != null || s.showDefaultPrompt || showTools
    val focusManager = LocalFocusManager.current
    var wasModalOpen by remember { mutableStateOf(false) }
    LaunchedEffect(modalOpen) {
        if (wasModalOpen && !modalOpen) {
            // Popup just closed: hand focus back to the item the user was on.
            delay(120)
            runCatching { focusManager.moveFocus(FocusDirection.Enter) }
        }
        wasModalOpen = modalOpen
    }

    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastInteractionTime) {
        delay(3 * 60 * 1000L) // 3 mins of idle activates Apple TV Aerial screen saver
        if (!modalOpen && !s.showScreensaver && !s.showStorageManager && !s.showFileManager && !s.showCleanTvScreen) {
            vm.openScreensaver()
        }
    }

    TvCanvas {
        CleanTvBackdrop(
            themeId = s.wallpaperTheme,
            focusedApp = s.focusedApp ?: s.dockApps.firstOrNull() ?: s.allApps.firstOrNull()
        ) {
            // Layout follows the reference launcher (tvOS style), scaled x1.33 for the 1280dp canvas:
            // header, hero name, pinned glass dock, then a scrolling grid of banner tiles.
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cols = 4
                val sideMargin = 72.dp
                val bandPad = 19.dp
                val gap = 21.dp
                val tileW = (maxWidth - sideMargin * 2 - bandPad * 2 - gap * (cols - 1)) / cols
                val tileH = tileW * 9f / 16f
                val radius = 22.dp

                val dock = s.dockApps.ifEmpty { s.allApps.take(cols) }.take(cols)
                val gridApps = s.allApps // Show all installed applications on the home screen
                val gridRows = remember(gridApps) { gridApps.chunked(cols) }
                val heroName = s.focusedApp?.appName
                    ?: dock.firstOrNull()?.appName
                    ?: s.allApps.firstOrNull()?.appName
                    ?: ""

                val topBarPillRequesters = remember { List(7) { FocusRequester() } }
                val dockColRequesters = remember { List(cols) { FocusRequester() } }
                val gridRow0ColRequesters = remember { List(cols) { FocusRequester() } }

                var isDockVisible by remember { mutableStateOf(true) }
                var showSortMenu by remember { mutableStateOf(false) }

                Column(
                    Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = !modalOpen }
                        .focusRestorer()
                        .padding(start = sideMargin, end = sideMargin, top = 34.dp)
                ) {
                    CleanTvTopBar(
                        greeting = if (s.showGreeting) s.greetingText else "",
                        timeText = s.timeText,
                        dateText = s.dateText,
                        networkLabel = s.networkLabel,
                        sortMode = s.appSortMode,
                        pillRequesters = topBarPillRequesters,
                        dockRequesters = dockColRequesters,
                        dockSize = dock.size,
                        onOpenQuickShare = { vm.openAndroidQuickShare() },
                        onOpenInputs = vm::openInputs,
                        onOpenNetwork = vm::openNetworkSettings,
                        onOpenCleanTv = vm::requestCleanTv,
                        onToggleSort = { vm.toggleAppSortMode() },
                        onOpenTools = { showTools = true },
                        onOpenSettings = vm::openSystemSettings
                    )

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = heroName,
                        style = Ty.Display.copy(fontSize = 58.sp, lineHeight = 68.sp, letterSpacing = (-1.3).sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp).height(72.dp)
                    )

                    if (s.isLoading) {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Spinner()
                                Text("Loading your apps...", style = Ty.Body.copy(color = Color(0x99FFFFFF)))
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // ─── Pinned Glass Dock (Scrolls away smoothly when moving down into apps) ───
                            if (dock.isNotEmpty()) {
                                item(key = "dock_bar") {
                                    val bandShape = RoundedCornerShape(radius + bandPad)
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                            .background(Color(0xE6141416), bandShape)
                                            .border(1.dp, Color(0x1FFFFFFF), bandShape)
                                            .padding(bandPad)
                                    ) {
                                        AppRow(
                                            apps = dock, tileW = tileW, tileH = tileH, radius = radius, gap = gap,
                                            focusScale = 1.03f, showNames = false,
                                            tileModifier = { index, _ ->
                                                val topIdx = (index * 7 / dock.size.coerceAtLeast(1)).coerceIn(0, 6)
                                                val gridTargetCol = index.coerceAtMost((gridRows.firstOrNull()?.size ?: 1) - 1)
                                                Modifier
                                                    .focusRequester(dockColRequesters[index.coerceIn(0, cols - 1)])
                                                    .focusProperties {
                                                        up = topBarPillRequesters[topIdx]
                                                        if (gridRows.isNotEmpty()) {
                                                            down = gridRow0ColRequesters[gridTargetCol]
                                                        }
                                                    }
                                                    .onPreviewKeyEvent { ev ->
                                                        val k = ev.nativeKeyEvent
                                                        if (k.action == KeyEvent.ACTION_DOWN) {
                                                            when (k.keyCode) {
                                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                                    topBarPillRequesters[topIdx].requestFocus()
                                                                    true
                                                                }
                                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                                    if (gridRows.isNotEmpty()) {
                                                                        gridRow0ColRequesters[gridTargetCol].requestFocus()
                                                                        true
                                                                    } else false
                                                                }
                                                                else -> false
                                                            }
                                                        } else false
                                                    }
                                            },
                                            onFocus = { vm.setFocusedApp(it) },
                                            onClick = { vm.launch(it) },
                                            onLongClick = { vm.showActionMenu(it) },
                                        )
                                    }
                                }
                            }

                            // ─── Grid Section Title ───
                            if (gridRows.isNotEmpty()) {
                                item(key = "h_all") {
                                    Text(
                                        text = if (s.appSortMode == AppSortMode.RECENCY) "Recent Applications (${s.allApps.size})" else "All Applications (${s.allApps.size})",
                                        style = Ty.Heading.copy(
                                            color = Color(0xB3FFFFFF),
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Medium,
                                            letterSpacing = 0.4.sp
                                        ),
                                        modifier = Modifier.padding(start = bandPad + 6.dp, top = 6.dp, bottom = 4.dp)
                                    )
                                }
                                itemsIndexed(gridRows, key = { _, row -> "g_" + row.first().packageName }) { rowIndex, rowApps ->
                                    AppRow(
                                        apps = rowApps, tileW = tileW, tileH = tileH, radius = radius, gap = gap,
                                        focusScale = 1.06f, showNames = true,
                                        modifier = Modifier.padding(start = bandPad),
                                        tileModifier = if (rowIndex == 0) { index, _ ->
                                            val dockTargetCol = index.coerceAtMost((dock.size - 1).coerceAtLeast(0))
                                            Modifier
                                                .focusRequester(gridRow0ColRequesters[index.coerceIn(0, cols - 1)])
                                                .focusProperties {
                                                    if (dock.isNotEmpty()) {
                                                        up = dockColRequesters[dockTargetCol]
                                                    }
                                                }
                                                .onPreviewKeyEvent { ev ->
                                                    val k = ev.nativeKeyEvent
                                                    if (k.action == KeyEvent.ACTION_DOWN && k.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                                        if (dock.isNotEmpty()) {
                                                            dockColRequesters[dockTargetCol].requestFocus()
                                                            true
                                                        } else false
                                                    } else false
                                                }
                                        } else null,
                                        badge = if (s.appSortMode == AppSortMode.RECENCY) {
                                            { app ->
                                                val count = vm.journal.getLaunchCount(app.packageName)
                                                if (count > 0) "${count}x" else null
                                            }
                                        } else null,
                                        onFocus = { vm.setFocusedApp(it) },
                                        onClick = { vm.launch(it) },
                                        onLongClick = { vm.showActionMenu(it) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showTools) {
                ToolsMenuDialog(
                    accountName = s.tvProfile.displayName,
                    onQuickShare = { showTools = false; vm.openAndroidQuickShare() },
                    onStorage = { showTools = false; vm.openStorageManager() },
                    onFiles = { showTools = false; vm.openFileManager() },
                    onTheme = { showTools = false; vm.openPersonalization() },
                    onAccount = { showTools = false; vm.openProfile() },
                    onScreensaver = { showTools = false; vm.openScreensaver() },
                    onDismiss = { showTools = false },
                )
            }

            // CleanYourTV 1-Click Wizard Dialog
            if (s.showCleanTvWizard) {
                CleanTvWizardDialog(
                    deviceLabel = s.detectedDeviceLabel,
                    majorOptions = s.majorOptions,
                    onToggleOption = vm::toggleMajorOption,
                    onOpenDeveloperOptions = vm::openDeveloperOptions,
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

            // Native Android Quick Share – consent / discoverable dialog
            if (s.showNativeQuickShare) {
                com.leantv.app.share.NativeQuickShareConsentDialog(
                    state = s.nativeQuickShareState,
                    onAccept = vm::acceptNativeTransfer,
                    onDecline = vm::rejectNativeTransfer,
                    onDismiss = vm::closeNativeQuickShare,
                    onOpenFolder = {
                        vm.closeNativeQuickShare()
                        vm.openFileManager(
                            initialDir = com.leantv.app.files.FileManagerHelper.ensureQuickShareDir(),
                            highlightFile = null
                        )
                    }
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

@Composable
private fun SectionTitle(title: String, startPad: Dp) {
    Text(
        text = title,
        style = Ty.Heading.copy(
            color = Color(0xB3FFFFFF),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp
        ),
        modifier = Modifier.padding(start = startPad + 6.dp, top = 4.dp)
    )
}

/**
 * Header like the reference launcher: small greeting, a thin light clock with AM/PM and date,
 * and glass pills on the right. Everything else lives behind the Tools button.
 */
@Composable
private fun CleanTvTopBar(
    greeting: String,
    timeText: String,
    dateText: String,
    networkLabel: String,
    sortMode: AppSortMode,
    pillRequesters: List<FocusRequester> = emptyList(),
    dockRequesters: List<FocusRequester> = emptyList(),
    dockSize: Int = 4,
    onOpenQuickShare: () -> Unit,
    onOpenInputs: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenCleanTv: () -> Unit,
    onToggleSort: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val parts = timeText.trim().split(" ")
    val clock = parts.firstOrNull().orEmpty()
    val ampm = parts.getOrNull(1).orEmpty()
    val netIcon = if (networkLabel.contains("Ethernet", ignoreCase = true)) Icons.Rounded.SettingsEthernet else Icons.Rounded.Wifi

    fun pillFocusModifier(index: Int): Modifier {
        if (pillRequesters.size <= index) return Modifier
        val targetCol = if (dockSize > 0) (index * dockSize / 7).coerceIn(0, dockSize - 1) else 0
        return Modifier
            .focusRequester(pillRequesters[index])
            .focusProperties {
                if (dockSize > 0 && dockRequesters.isNotEmpty()) {
                    down = dockRequesters[targetCol]
                }
            }
            .onPreviewKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                if (k.action == KeyEvent.ACTION_DOWN && k.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (dockSize > 0 && dockRequesters.isNotEmpty()) {
                        dockRequesters[targetCol].requestFocus()
                        true
                    } else false
                } else false
            }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ─── Row 1: Clock, Greeting & Date (Full width row, zero overlap with options) ───
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = clock,
                    style = Ty.Display.copy(fontSize = 68.sp, lineHeight = 76.sp, letterSpacing = (-2.7).sp)
                )
                if (ampm.isNotEmpty()) {
                    Text(
                        text = ampm.uppercase(),
                        style = Ty.Small.copy(
                            color = Color(0xB3FFFFFF),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.6.sp
                        ),
                        modifier = Modifier.padding(start = 8.dp, bottom = 13.dp)
                    )
                }
                Box(
                    Modifier
                        .padding(start = 20.dp, end = 20.dp, bottom = 14.dp)
                        .width(1.dp)
                        .height(34.dp)
                        .background(Color(0x33FFFFFF))
                )
                Text(
                    text = dateText.uppercase(),
                    style = Ty.Small.copy(
                        color = Color(0xC0FFFFFF),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.0.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (greeting.isNotBlank()) {
                Text(
                    text = greeting.uppercase(),
                    style = Ty.Small.copy(
                        color = Color(0x8CFFFFFF),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 3.2.sp
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }

        // ─── Row 2: Top Action Pills & Settings Row ───
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Real Quick Share icon pill
            TvPill(
                label = null,
                iconRes = R.drawable.ic_quick_share,
                contentDescription = "Quick Share",
                modifier = pillFocusModifier(0),
                onClick = onOpenQuickShare,
            )
            TvPill(
                label = "Clean TV",
                icon = Icons.Rounded.AutoAwesome,
                modifier = pillFocusModifier(1),
                onClick = onOpenCleanTv,
            )
            TvPill(
                label = "Inputs",
                icon = Icons.AutoMirrored.Rounded.Input,
                modifier = pillFocusModifier(2),
                onClick = onOpenInputs,
            )
            TvPill(
                label = if (sortMode == AppSortMode.RECENCY) "Recent" else "A-Z",
                icon = if (sortMode == AppSortMode.RECENCY) Icons.Rounded.Schedule else Icons.AutoMirrored.Rounded.Sort,
                isSelected = true,
                modifier = pillFocusModifier(3),
                onClick = onToggleSort,
            )
            TvPill(
                label = networkLabel.ifBlank { "Wi-Fi" },
                icon = netIcon,
                modifier = pillFocusModifier(4),
                onClick = onOpenNetwork,
                maxLabelWidth = 140.dp,
            )
            TvPill(
                label = null,
                icon = Icons.Rounded.Apps,
                modifier = pillFocusModifier(5),
                onClick = onOpenTools,
                contentDescription = "Tools",
            )
            TvPill(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                modifier = pillFocusModifier(6),
                onClick = onOpenSettings,
            )
        }
    }
}

/** Glass pill: Apple TV soft elevation, hairline border, subtle audio feedback. */
@Composable
private fun TvPill(
    label: String?,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    height: Dp = 56.dp,
    textSize: TextUnit = 20.sp,
    maxLabelWidth: Dp = 240.dp,
    fill: Boolean = false,
    contentDescription: String? = null,
    isSelected: Boolean = false,
    onPillFocus: () -> Unit = {},
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.07f else 1f,
        spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "pillScale"
    )
    val fg by animateColorAsState(
        if (focused) Color.Black else if (isSelected) Color.White else Color(0xEBFFFFFF), tween(120), label = "pillFg"
    )
    val shape = RoundedCornerShape(50)
    val bg = when {
        focused -> Brush.verticalGradient(listOf(Color(0xF5FFFFFF), Color(0xF5FFFFFF)))
        isSelected -> Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x24FFFFFF)))
        else -> Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF)))
    }
    Row(
        modifier
            .height(height)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (focused) 16.dp else 0.dp, shape = shape, clip = false,
                ambientColor = Color(0x66000000), spotColor = Color(0x99000000)
            )
            .clip(shape)
            .background(bg)
            .border(
                if (focused) 1.5.dp else if (isSelected) 1.5.dp else 1.dp,
                if (focused) Color.White.copy(alpha = 0.95f) else if (isSelected) Color.White.copy(alpha = 0.65f) else Color(0x38FFFFFF),
                shape
            )
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    TvSound.playMove(view)
                    onPillFocus()
                }
                focused = it.isFocused
            }
            .clickable {
                TvSound.playClick(view)
                onClick()
            }
            .then(if (label == null) Modifier.width(height) else Modifier.padding(start = 21.dp, end = 27.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fill) Arrangement.Start else Arrangement.Center,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription ?: label,
                tint = fg,
                modifier = Modifier.size(26.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription ?: label,
                tint = fg,
                modifier = Modifier.size(26.dp)
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = Ty.Button.copy(fontSize = textSize, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, color = fg),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp).widthIn(max = maxLabelWidth)
            )
        }
    }
}

/** Everything that used to crowd the top bar, in one tidy menu. */
@Composable
private fun ToolsMenuDialog(
    accountName: String,
    onQuickShare: () -> Unit,
    onStorage: () -> Unit,
    onFiles: () -> Unit,
    onTheme: () -> Unit,
    onAccount: () -> Unit,
    onScreensaver: () -> Unit,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF26262B), Color(0xFF17171B))))
                .padding(horizontal = 36.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tools", style = Ty.Title.copy(fontSize = 38.sp), modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
            TvPill("Quick Share", Icons.Rounded.Bolt, onQuickShare, Modifier.fillMaxWidth().focusRequester(autoFocus), height = 64.dp, textSize = 22.sp, fill = true)
            TvPill("Storage", Icons.Rounded.Storage, onStorage, Modifier.fillMaxWidth(), height = 64.dp, textSize = 22.sp, fill = true)
            TvPill("Files", Icons.Rounded.Folder, onFiles, Modifier.fillMaxWidth(), height = 64.dp, textSize = 22.sp, fill = true)
            TvPill("Theme", Icons.Rounded.Palette, onTheme, Modifier.fillMaxWidth(), height = 64.dp, textSize = 22.sp, fill = true)
            TvPill("Aerial Screen Saver", Icons.Rounded.Tv, onScreensaver, Modifier.fillMaxWidth(), height = 64.dp, textSize = 22.sp, fill = true)
            TvPill(accountName.ifBlank { "Account" }, Icons.Rounded.Person, onAccount, Modifier.fillMaxWidth(), height = 64.dp, textSize = 22.sp, fill = true, maxLabelWidth = 380.dp)
        }
    }
}

@Composable
private fun AppRow(
    apps: List<InstalledAppInfo>,
    tileW: Dp,
    tileH: Dp,
    radius: Dp,
    gap: Dp,
    focusScale: Float,
    showNames: Boolean,
    onFocus: (InstalledAppInfo) -> Unit,
    onClick: (InstalledAppInfo) -> Unit,
    onLongClick: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
    badge: ((InstalledAppInfo) -> String?)? = null,
    tileModifier: ((index: Int, app: InstalledAppInfo) -> Modifier)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        apps.forEachIndexed { index, app ->
            CleanTvAppTile(
                app = app, tileW = tileW, tileH = tileH, radius = radius,
                focusScale = focusScale, showName = showNames,
                badgeText = badge?.invoke(app),
                modifier = tileModifier?.invoke(index, app) ?: Modifier,
                onFocus = { onFocus(app) },
                onClick = { onClick(app) },
                onLongClick = { onLongClick(app) },
            )
        }
    }
}

/**
 * Tile like the reference launcher: banner artwork fills a 16:9 card (or a tinted plate with the icon
 * and name), the focused tile scales up with a white glass highlight and glow, and its name appears below.
 * Menu key or long-press OK opens the app menu without triggering a tap on release.
 */
@Composable
private fun CleanTvAppTile(
    app: InstalledAppInfo,
    tileW: Dp,
    tileH: Dp,
    radius: Dp,
    focusScale: Float,
    showName: Boolean,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var visuals by remember(app.packageName) { mutableStateOf(IconCache.get(app.packageName)) }
    LaunchedEffect(app.packageName) {
        if (visuals == null) visuals = IconCache.loadVisuals(context, app.packageName)
    }

    var focused by remember { mutableStateOf(false) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var isLongPressHandled by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        if (focused) focusScale else 1f,
        spring(dampingRatio = 0.8f, stiffness = 420f),
        label = "tileScale"
    )
    val nameAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "tileName")
    val shape = RoundedCornerShape(radius)

    Column(
        Modifier.width(tileW)
    ) {
        Box(
            modifier
                .size(tileW, tileH)
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
                .background(Color(0xFF1C1C1E))
                .onFocusChanged {
                    if (it.isFocused && !focused) {
                        TvSound.playMove(view)
                    }
                    focused = it.isFocused
                    if (it.isFocused) onFocus()
                }
                .focusable()
                .onPreviewKeyEvent { ev ->
                    val k = ev.nativeKeyEvent
                    val center = k.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || k.keyCode == KeyEvent.KEYCODE_ENTER
                    val menu = k.keyCode == KeyEvent.KEYCODE_MENU
                    when {
                        menu && k.action == KeyEvent.ACTION_DOWN -> {
                            TvSound.playClick(view)
                            onLongClick()
                            true
                        }
                        center && k.action == KeyEvent.ACTION_DOWN -> {
                            if (k.repeatCount == 0) {
                                isLongPressHandled = false
                                longPressJob?.cancel()
                                longPressJob = scope.launch {
                                    delay(450)
                                    isLongPressHandled = true
                                    TvSound.playClick(view)
                                    onLongClick()
                                }
                            }
                            true
                        }
                        center && k.action == KeyEvent.ACTION_UP -> {
                            val wasLong = isLongPressHandled
                            longPressJob?.cancel()
                            longPressJob = null
                            isLongPressHandled = false
                            if (!wasLong) {
                                TvSound.playClick(view)
                                onClick()
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            val v = visuals
            if (v != null && v.isBanner) {
                Image(
                    bitmap = v.bitmap,
                    contentDescription = app.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val plateTop = v?.topColor ?: extractFallbackHue(app)
                val plateBottom = v?.bottomColor ?: Color(0xFF101216)
                // Large icon centred in plate with a frosted name strip at the bottom
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    plateTop.copy(alpha = 0.95f),
                                    plateBottom.copy(alpha = 1f)
                                )
                            )
                        )
                ) {
                    // ── Icon ──────────────────────────────────────────────
                    // No bitmap yet: show a placeholder initial circle
                    if (v?.bitmap == null) {
                        Box(
                            Modifier
                                .size(tileH * 0.56f)
                                .align(Alignment.Center)
                                .absoluteOffset(y = (-10).dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.appName.take(1).uppercase(),
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Image(
                            bitmap = v.bitmap,
                            contentDescription = app.appName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(tileH * 0.60f)        // bigger – fills ~60 % of tile height
                                .align(Alignment.Center)
                                .absoluteOffset(y = (-10).dp)        // shift up slightly to leave room for name strip
                        )
                    }

                    // ── Name strip ────────────────────────────────────────
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC000000))
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName,
                            style = Ty.Small.copy(
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.1.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (badgeText != null) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(badgeText, color = Color(0xFF64B5F6), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Apple TV style soft specular highlight + delicate rim border
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x04FFFFFF), Color.Transparent)))
                        .border(1.5.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.5f))), shape)
                )
            }
        }

        if (showName) {
            Text(
                text = app.appName,
                style = Ty.Small.copy(color = Color(0xF2FFFFFF), fontSize = 19.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(28.dp)
                    .graphicsLayer { alpha = nameAlpha }
            )
        } else {
            Spacer(Modifier.height(4.dp))
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
    val ctx = LocalContext.current
    val fallbackHue = extractFallbackHue(focusedApp)
    val ambientColor by produceState(fallbackHue, focusedApp?.packageName) {
        value = fallbackHue
        focusedApp?.let { app ->
            IconCache.loadVisuals(ctx, app.packageName)?.glow?.let { value = it }
        }
    }
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

                // 2. Ambient glow in the focused app's colour (same shape as the reference launcher)
                drawRect(
                    brush = Brush.radialGradient(
                        0f to animatedAmbient.copy(alpha = 0.36f),
                        0.45f to animatedAmbient.copy(alpha = 0.15f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.32f, size.height * 0.42f),
                        radius = size.width * 0.62f
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
            .background(Color(0xE6000000))
            .modalTrap()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(640.dp)
                .glassCard()
                .padding(horizontal = 48.dp, vertical = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(Color(0x3330D158)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Pal.Mint, modifier = Modifier.size(52.dp))
            }
            Text(
                "Your TV is Transformed",
                style = Ty.Title.copy(fontSize = 38.sp),
                textAlign = TextAlign.Center
            )
            Text(
                summary,
                style = Ty.Body.copy(color = Color(0xCCFFFFFF), fontSize = 22.sp, lineHeight = 30.sp),
                textAlign = TextAlign.Center
            )
            Text(
                "Telemetry and background ads are now disabled, with faster animations. Fully reversible anytime.",
                style = Ty.Dim.copy(fontSize = 18.sp, lineHeight = 26.sp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            TvButton(
                text = "Done",
                onClick = onDismiss,
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(autoFocus)
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
            .background(Color(0xE6000000))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(760.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF26262B), Color(0xFF17171B))))
                .padding(horizontal = 40.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Inputs", style = Ty.Title.copy(color = Color.White, fontSize = 34.sp))
            Text("Switch to a TV source", style = Ty.Dim.copy(fontSize = 18.sp))
            Spacer(Modifier.height(14.dp))

            if (ports.isEmpty()) {
                Text(
                    "No hardware inputs on this device",
                    style = Ty.Dim.copy(fontSize = 18.sp),
                    modifier = Modifier.padding(vertical = 28.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ports.chunked(3).forEachIndexed { rowIdx, rowPorts ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowPorts.forEachIndexed { colIdx, port ->
                                TvPortCard(
                                    port = port,
                                    onClick = { onSelectPort(port) },
                                    modifier = Modifier
                                        .width(216.dp)
                                        .let { if (rowIdx == 0 && colIdx == 0) it.focusRequester(autoFocus) else it }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            TvButton(
                text = "Close",
                onClick = onDismiss,
                primary = false,
                modifier = Modifier.width(240.dp)
            )
        }
    }
}

@Composable
private fun TvPortCard(
    port: TvPort,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Focusable(
        onClick = onClick,
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(24.dp),
    ) { focused ->
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PortIcon(
                kind = port.kind,
                color = if (port.isConnected || focused) Color.White else Color(0x66FFFFFF),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                port.label,
                style = Ty.Heading.copy(
                    color = if (port.isConnected || focused) Color.White else Color(0x80FFFFFF),
                    fontSize = 22.sp
                ),
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (port.isConnected && port.kind != "TV") {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Pal.Mint))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = port.subtitle
                        ?: if (port.kind == "TV") "Live TV"
                        else if (port.isConnected) "Connected" else "No signal",
                    style = Ty.Dim.copy(fontSize = 14.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/** Simple line icons drawn in code, so no emoji and no extra icon dependency. */
@Composable
private fun PortIcon(kind: String, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 3.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round
        )
        when (kind) {
            "TV" -> {
                drawRoundRect(
                    color, topLeft = Offset(w * 0.04f, h * 0.10f),
                    size = androidx.compose.ui.geometry.Size(w * 0.92f, h * 0.65f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f), style = stroke
                )
                drawLine(color, Offset(w * 0.30f, h * 0.92f), Offset(w * 0.70f, h * 0.92f), stroke.width, androidx.compose.ui.graphics.StrokeCap.Round)
            }
            "HDMI" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.10f, h * 0.22f)
                    lineTo(w * 0.90f, h * 0.22f)
                    lineTo(w * 0.90f, h * 0.55f)
                    lineTo(w * 0.72f, h * 0.78f)
                    lineTo(w * 0.28f, h * 0.78f)
                    lineTo(w * 0.10f, h * 0.55f)
                    close()
                }
                drawPath(path, color, style = stroke)
            }
            else -> { // AV / Component: three jacks
                val r = w * 0.13f
                listOf(0.22f, 0.50f, 0.78f).forEach { x ->
                    drawCircle(color, radius = r, center = Offset(w * x, h * 0.5f), style = stroke)
                }
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
            .background(Color(0xE6000000))
            .modalTrap()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(660.dp)
                .glassCard()
                .padding(horizontal = 44.dp, vertical = 38.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Accounts", style = Ty.Title.copy(fontSize = 38.sp))
            Text("Google accounts linked to this TV", style = Ty.Dim.copy(fontSize = 19.sp))
            Spacer(Modifier.height(6.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(Pal.Amber),
                    contentAlignment = Alignment.Center
                ) {
                    Text(profile.initial, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName, style = Ty.Heading.copy(fontSize = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    profile.email?.let {
                        Text(it, style = Ty.Dim.copy(fontSize = 18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (profile.allAccounts.size > 1) {
                        Text("${profile.allAccounts.size} accounts synced", style = Ty.Small.copy(fontSize = 16.sp))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            TvPill("Add or Switch Account", Icons.Rounded.PersonAdd, onAddAccount, Modifier.fillMaxWidth().focusRequester(autoFocus), height = 62.dp, textSize = 21.sp, fill = true)
            TvPill("Manage in System Settings", Icons.Rounded.Settings, onManageSettings, Modifier.fillMaxWidth(), height = 62.dp, textSize = 21.sp, fill = true)
            TvPill("Close", Icons.Rounded.Close, onDismiss, Modifier.fillMaxWidth(), height = 62.dp, textSize = 21.sp, fill = true)
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
            .background(Color(0xE6000000))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        val firstFocus = rememberAutoFocus(app.packageName)
        Column(
            Modifier
                .width(560.dp)
                .glassCard()
                .padding(horizontal = 36.dp, vertical = 30.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(app.appName, style = Ty.Title.copy(fontSize = 34.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                style = Ty.Small.copy(fontFamily = FontFamily.Monospace, color = Color(0x99FFFFFF), fontSize = 15.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))

            val h = 52.dp
            val ts = 20.sp
            TvPill("Open", Icons.Rounded.PlayArrow, onOpen, Modifier.fillMaxWidth().focusRequester(firstFocus), height = h, textSize = ts, fill = true)
            TvPill(if (isFavorite) "Unpin from Dock" else "Pin to Dock", Icons.Rounded.PushPin, onToggleFavorite, Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
            TvPill("Clear Data", Icons.Rounded.DeleteSweep, onClearData, Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
            TvPill("Clear Cache", Icons.Rounded.CleaningServices, onClearCache, Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
            TvPill("Hide from Home", Icons.Rounded.VisibilityOff, onHideApp, Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
            val currentContext = LocalContext.current
            if (app.packageName != currentContext.packageName) {
                TvPill(
                    label = if (app.isSystemApp) "Uninstall / Remove Bloatware" else "Uninstall",
                    icon = Icons.Rounded.Delete,
                    onClick = onUninstall,
                    modifier = Modifier.fillMaxWidth(),
                    height = h,
                    textSize = ts,
                    fill = true
                )
            }
            TvPill("App Info", Icons.Rounded.Info, onDetails, Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
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
            .background(Color(0xE6000000))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        val focusRequester = rememberAutoFocus()
        Column(
            Modifier
                .width(640.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF26262B), Color(0xFF17171B)))
                )
                .padding(horizontal = 44.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Simple home glyph on an accent tile
            Box(
                Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF3DA0FF), Color(0xFF0A62E0)))),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(38.dp)) {
                    val w = size.width
                    val h = size.height
                    val roof = androidx.compose.ui.graphics.Path().apply {
                        moveTo(w * 0.5f, h * 0.06f)
                        lineTo(w * 0.96f, h * 0.48f)
                        lineTo(w * 0.84f, h * 0.48f)
                        lineTo(w * 0.84f, h * 0.94f)
                        lineTo(w * 0.60f, h * 0.94f)
                        lineTo(w * 0.60f, h * 0.64f)
                        lineTo(w * 0.40f, h * 0.64f)
                        lineTo(w * 0.40f, h * 0.94f)
                        lineTo(w * 0.16f, h * 0.94f)
                        lineTo(w * 0.16f, h * 0.48f)
                        lineTo(w * 0.04f, h * 0.48f)
                        close()
                    }
                    drawPath(roof, Color.White)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Make CleanYourTV your home screen",
                style = Ty.Title.copy(color = Color.White, fontSize = 30.sp, lineHeight = 36.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                "Open straight into a fast, clean launcher whenever you turn on the TV or press Home.",
                style = Ty.Dim.copy(fontSize = 18.sp, lineHeight = 26.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            TvButton(
                "Set as Default",
                onClick = onConfirm,
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvButton("Maybe Later", onClick = onLater, primary = false, modifier = Modifier.weight(1f))
                TvButton("Don't Ask Again", onClick = onDismiss, primary = false, modifier = Modifier.weight(1f))
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
            .background(Color(0xE6000000))
            .modalTrap(),
        contentAlignment = Alignment.Center
    ) {
        val cancelFocus = rememberAutoFocus(app.packageName)
        Column(
            Modifier
                .width(640.dp)
                .glassCard()
                .padding(horizontal = 46.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Uninstall ${app.appName}?", style = Ty.Title.copy(fontSize = 34.sp), textAlign = TextAlign.Center, maxLines = 2)
            Text(
                "Android will ask you to confirm the removal.",
                style = Ty.Dim.copy(fontSize = 20.sp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton("Cancel", onClick = onDismiss, primary = false, modifier = Modifier.weight(1f).focusRequester(cancelFocus))
                TvButton("Uninstall", onClick = onConfirm, primary = true, modifier = Modifier.weight(1f))
            }
        }
    }
}
