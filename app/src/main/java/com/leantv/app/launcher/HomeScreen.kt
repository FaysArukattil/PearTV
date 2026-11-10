package com.leantv.app.launcher

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.leantv.app.ui.CleanTvWizardDialog
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

    // Handle Back key: close any open dialog/panel first, never exit launcher
    BackHandler(
        enabled = s.showQuickShare || s.showQuickShareHub || s.showInputsDialog ||
            s.showProfileDialog || s.showMonitorPanel || s.showCleanTvWizard ||
            s.debloatSuccessSummary != null || s.actionMenuApp != null ||
            s.uninstallConfirmApp != null || s.showDefaultPrompt
    ) {
        when {
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

    TvCanvas {
        HomeBackdrop(s.focusedApp) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Safe.H, vertical = Safe.V),
            ) {
                // Apple TV Control Center & Navigation Top Bar
                AppleTvTopBar(
                    timeText = s.timeText,
                    dateText = s.dateText,
                    currentAccountName = s.currentAccountName,
                    onOpenInputs = vm::openInputs,
                    onOpenQuickShare = vm::handleQuickShareClick,
                    onOpenProfile = vm::openProfile,
                    onOpenSettings = vm::openSystemSettings,
                    onOpenTools = vm::openLeanTvOptimizer,
                    onOpenMonitor = vm::openSystemMonitor,
                )

                Spacer(Modifier.height(24.dp))

                // ⚡ CleanYourTV Transformation Hero Banner
                CleanTvHeroBanner(
                    isDebloating = s.isDebloating,
                    progressText = s.debloatProgress,
                    onClick = vm::requestCleanTv
                )

                Spacer(Modifier.height(28.dp))

                if (s.isLoading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(380.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Spinner()
                            Text("Loading your apps...", style = Ty.Heading)
                        }
                    }
                } else {
                    // Apple TV Top Shelf (Featured Row)
                    if (s.featuredApps.isNotEmpty()) {
                        SectionTitle("Top Shelf")
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

            // ⚡ CleanYourTV Wizard Modal (Pick what to keep/remove)
            if (s.showCleanTvWizard) {
                CleanTvWizardDialog(
                    majorOptions = s.majorOptions,
                    onToggleOption = vm::toggleMajorOption,
                    onConfirm = vm::executeOneClickCleanTv,
                    onDismiss = vm::dismissCleanTvWizard
                )
            }

            // ⚡ CleanYourTV Success Celebratory Modal
            s.debloatSuccessSummary?.let { summary ->
                CleanTvSuccessDialog(
                    summary = summary,
                    onDismiss = vm::dismissDebloatSummary
                )
            }

            // Inputs Switcher Modal
            if (s.showInputsDialog) {
                InputsSwitcherDialog(
                    inputs = s.tvInputs,
                    onSelectInput = vm::switchToInput,
                    onDismiss = vm::closeInputs
                )
            }

            // Profile & Google Account Switcher Modal
            if (s.showProfileDialog) {
                ProfileAccountsDialog(
                    currentAccount = s.currentAccountName,
                    accounts = s.googleAccounts,
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
private fun CleanTvHeroBanner(
    isDebloating: Boolean,
    progressText: String?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.02f else 1f,
        tween(180, easing = FastOutSlowInEasing),
        label = "heroScale"
    )
    val ringColor by animateColorAsState(
        if (focused) Pal.Amber else Color(0x22FFFFFF),
        tween(180),
        label = "heroRing"
    )
    val bgBrush = Brush.horizontalGradient(
        if (focused) listOf(Color(0xFF264E66), Color(0xFF17384A))
        else listOf(Color(0xFF1A384B), Color(0xFF0E2330))
    )

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .background(bgBrush)
            .border(if (focused) 2.5.dp else 1.dp, ringColor, RoundedCornerShape(22.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = !isDebloating, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Glowing Icon Badge
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Pal.Amber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚡", fontSize = 32.sp)
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "CLEANYOURTV OPTIMIZER",
                            style = Ty.Small.copy(
                                color = Pal.Amber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Pal.Mint.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("100% REVERSIBLE", style = Ty.Small.copy(color = Pal.Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Transform Your TV into a Clean Apple TV Experience",
                        style = Ty.Heading.copy(
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (focused) Pal.Amber else Pal.Text
                        )
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "Disable manufacturer ads, PatchWall, telemetry & bloatware • 2x remote responsiveness • AdGuard DNS",
                        style = Ty.Small.copy(color = Pal.Dim, fontSize = 14.sp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Action Pill / Progress Indicator
            if (isDebloating) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spinner(Modifier.size(28.dp))
                    Text(
                        progressText ?: "Optimizing TV...",
                        style = Ty.Small.copy(color = Pal.Amber, fontWeight = FontWeight.SemiBold)
                    )
                }
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (focused) Pal.Amber else Color(0x33FFFFFF))
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(
                        "⚡ Transform My TV ➔",
                        style = Ty.Button.copy(
                            color = if (focused) Pal.OnAmber else Pal.Text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanTvSuccessDialog(
    summary: String,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Pal.Surface)
                .border(2.dp, Pal.Mint, RoundedCornerShape(26.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Pal.Mint.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", style = Ty.Display.copy(color = Pal.Mint, fontSize = 38.sp))
            }

            Text("Your TV is Transformed!", style = Ty.Title.copy(fontSize = 28.sp))

            Text(
                summary,
                style = Ty.Body,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Text(
                "Telemetry daemons and background ads are now disabled. All original packages are saved in the restore journal and can be re-enabled anytime.",
                style = Ty.Small.copy(color = Pal.Dim),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            TvButton(
                text = "Enjoy CleanTV ➔",
                onClick = onDismiss,
                primary = true,
                modifier = Modifier.fillMaxWidth().focusRequester(autoFocus)
            )
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

/**
 * Apple TV-styled navigation and Control Center bar.
 * Contains Top Right main controls: Inputs, Quick Share, Google Account Profile, and System Settings.
 */
@Composable
private fun AppleTvTopBar(
    timeText: String,
    dateText: String,
    currentAccountName: String,
    onOpenInputs: () -> Unit,
    onOpenQuickShare: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenMonitor: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left Side: Sleek Apple TV-style Brand Logo & Utility Pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF1E3A4A), Color(0xFF132833))))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📺", fontSize = 18.sp)
                    Text(
                        "LeanTV",
                        style = Ty.Display.copy(
                            color = Pal.Amber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 22.sp
                        )
                    )
                }
            }

            TvPillButton(text = "⚡ CleanYourTV", onClick = onOpenTools)
            TvPillButton(text = "📊 Monitor", onClick = onOpenMonitor)
        }

        // Right Side: Quick Action Pills & Clock
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Inputs Quick Switcher
            TvTopControlPill(
                icon = "📺",
                label = "Inputs",
                onClick = onOpenInputs
            )

            // 2. Quick Share (Google Quick Share / Nearby Share)
            TvTopControlPill(
                icon = "⚡",
                label = "Quick Share",
                onClick = onOpenQuickShare
            )

            // 3. Profile / Google Account Switcher
            val shortAccount = remember(currentAccountName) {
                if (currentAccountName.contains("@")) {
                    currentAccountName.substringBefore("@")
                } else {
                    currentAccountName
                }
            }
            TvTopControlPill(
                icon = "👤",
                label = shortAccount,
                onClick = onOpenProfile
            )

            // 4. System Settings
            TvTopControlPill(
                icon = "⚙",
                label = "Settings",
                onClick = onOpenSettings
            )

            Spacer(Modifier.width(8.dp))

            // Time & Date Display
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeText,
                    style = Ty.Title.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Pal.Text
                    )
                )
                Text(
                    text = dateText,
                    style = Ty.Small.copy(color = Pal.Dim, fontSize = 13.sp)
                )
            }
        }
    }
}

@Composable
private fun TvTopControlPill(
    icon: String,
    label: String,
    onClick: () -> Unit,
) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        idle = Color(0x2BFFFFFF),
        scaleOnFocus = 1.08f
    ) { focused ->
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(icon, fontSize = 16.sp)
            Text(
                text = label,
                style = Ty.Small.copy(
                    color = if (focused) Pal.Amber else Pal.Text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 0.3.sp
                )
            )
        }
    }
}

@Composable
private fun TvPillButton(text: String, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        idle = Color(0x22FFFFFF),
        scaleOnFocus = 1.05f
    ) { focused ->
        Text(
            text = text,
            style = Ty.Small.copy(
                color = if (focused) Pal.Amber else Pal.Text,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/** Featured apps carousel: large banner-style cards with butter-smooth focus scale & glow. */
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
    val scale by animateFloatAsState(
        if (focused) 1.08f else 1f,
        tween(200, easing = FastOutSlowInEasing),
        label = "featScale"
    )
    val borderRing by animateColorAsState(
        if (focused) Pal.Amber else Color(0x1FFFFFFF),
        tween(200),
        label = "featRing"
    )
    val cardBg by animateColorAsState(
        if (focused) Color(0x4D24566C) else Color(0x2617394A),
        tween(200),
        label = "featBg"
    )

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
    val scale by animateFloatAsState(
        if (focused) 1.09f else 1f,
        tween(180, easing = FastOutSlowInEasing),
        label = "appScale"
    )
    val ring by animateColorAsState(
        if (focused) Pal.Amber else Color(0x1AFFFFFF),
        tween(180),
        label = "appRing"
    )
    val bg by animateColorAsState(
        if (focused) Color(0x5924566C) else Color(0x3317394A),
        tween(180),
        label = "appBg"
    )

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

/** Inputs Switcher Dialog (Apple TV Control Center style) */
@Composable
private fun InputsSwitcherDialog(
    inputs: List<TvInputItem>,
    onSelectInput: (TvInputItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Pal.Surface)
                .border(1.dp, Pal.Line, RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("TV Inputs", style = Ty.Title.copy(fontSize = 26.sp))
            Text("Switch to external devices connected to this TV", style = Ty.Dim.copy(fontSize = 14.sp))

            Spacer(Modifier.height(6.dp))

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                inputs.forEachIndexed { index, input ->
                    TvButton(
                        text = "📺  ${input.name} (${input.type})",
                        onClick = { onSelectInput(input) },
                        modifier = Modifier.fillMaxWidth().let {
                            if (index == 0) it.focusRequester(autoFocus) else it
                        },
                        primary = index == 0
                    )
                }

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

/** Google Account Switcher & Profile Dialog */
@Composable
private fun ProfileAccountsDialog(
    currentAccount: String,
    accounts: List<GoogleAccountItem>,
    onAddAccount: () -> Unit,
    onManageSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Pal.Surface)
                .border(1.dp, Pal.Line, RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Google Accounts & Profile", style = Ty.Title.copy(fontSize = 26.sp))
            Text("Manage accounts linked to this Android TV system", style = Ty.Dim.copy(fontSize = 14.sp))

            Spacer(Modifier.height(4.dp))

            // Display accounts
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x33000000))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Currently Active:", style = Ty.Small.copy(color = Pal.Amber, fontWeight = FontWeight.Bold))
                Text(currentAccount, style = Ty.Heading.copy(fontSize = 18.sp))

                if (accounts.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text("All Synced Accounts (${accounts.size}):", style = Ty.Small.copy(color = Pal.Dim))
                    accounts.forEach { acc ->
                        Text("• ${acc.name}", style = Ty.Body.copy(fontSize = 15.sp))
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
                    modifier = Modifier.fillMaxWidth().focusRequester(autoFocus),
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
            .background(Color(0xD9050E14))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(540.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Pal.Surface)
                .border(1.dp, Pal.Line, RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Quick Share Hub", style = Ty.Title.copy(fontSize = 28.sp))
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
                    modifier = Modifier.fillMaxWidth().focusRequester(autoFocus),
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
                            Pal.Amber.copy(alpha = if (focusedApp != null) 0.14f else 0.07f),
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
