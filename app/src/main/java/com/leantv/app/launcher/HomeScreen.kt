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
import com.leantv.app.ui.ScreensaverSettingsDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material.icons.rounded.Widgets
import com.leantv.app.ui.glassCard
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import com.leantv.app.ui.LocalDisableModalTraps
import com.leantv.app.share.TransferPhase
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.leantv.app.ui.AppleMotion
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
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import com.leantv.app.account.TvProfile
import com.leantv.app.data.AppVisuals
import com.leantv.app.data.IconCache
import com.leantv.app.data.InstalledAppInfo
import com.leantv.app.inputs.TvPort
import com.leantv.app.monitor.SystemMonitorPanel
import com.leantv.app.share.QuickSharePanel
import com.leantv.app.ui.AppGridSkeleton
import com.leantv.app.ui.CleanTvWizardDialog
import com.leantv.app.ui.HeroSkeleton
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
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val s by vm.state.collectAsState()
    val view = LocalView.current

    // ─── App Launch / Close Spatial Transition State ───
    // • Open: tactile press & pop with ascending chime + native makeScaleUpAnimation from tile bounds
    // • Close: soothing return droplet chime + 100% GPU-accelerated Apple depth breathe-in and landing spring
    var isAppLaunched by remember { mutableStateOf(false) }
    var isAppLaunching by remember { mutableStateOf(false) }
    var isReturningFromApp by remember { mutableStateOf(false) }
    var returningAppPackage by remember { mutableStateOf<String?>(null) }

    // Home layer zoom. It NEVER scales below 1 (that exposed dark edges around the screen) and has no dark scrim.
    //  • launching: Home eases slightly larger while the app zooms in over it
    //  • returning: Home starts a touch larger and settles to 1, like landing back on the iOS Home Screen
    // Return from an app: ONE short, calm settle (gentle fade + tiny scale, no bounce). Android already plays its own
    // close transition for the app, so a big zoom of ours on top of it looked busy. 0 = just returned, 1 = settled.
    val returnProgress = remember { Animatable(1f) }
    LaunchedEffect(isReturningFromApp) {
        if (isReturningFromApp) {
            returnProgress.snapTo(0f)
            returnProgress.animateTo(1f, tween(240, easing = AppleMotion.EaseOut))
        }
    }

    LifecycleResumeEffect(Unit) {
        if (isAppLaunched) {
            isReturningFromApp = true
            TvSound.playAppClose(view)
            isAppLaunched = false
        }
        isAppLaunching = false
        onPauseOrDispose { }
    }

    // Home key pressed on the remote: close menus, and if returning from an app, trigger return transition
    LaunchedEffect(s.homeResetTick) {
        if (isAppLaunched) {
            isReturningFromApp = true
            TvSound.playAppClose(view)
            isAppLaunched = false
        }
    }

    LaunchedEffect(isReturningFromApp) {
        if (isReturningFromApp) {
            delay(300L)
            isReturningFromApp = false
            returningAppPackage = null
        }
    }

    LaunchedEffect(isAppLaunching) {
        if (isAppLaunching) {
            delay(800)
            isAppLaunching = false
        }
    }

    // Tile press → launch with system scale-up animation and launcher depth zoom.
    val launchApp: (InstalledAppInfo, Rect?) -> Unit = { app, bounds ->
        isAppLaunched = true
        isAppLaunching = true
        isReturningFromApp = false
        returningAppPackage = app.packageName
        val options = if (bounds != null && bounds.width > 0 && bounds.height > 0) {
            android.app.ActivityOptions.makeScaleUpAnimation(
                view,
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.width.toInt().coerceAtLeast(1),
                bounds.height.toInt().coerceAtLeast(1)
            )
        } else null
        vm.launch(app, options)
    }

    // Back: close the top-most popup/page first. With nothing open we are on Home, the last stop,
    // so Back is safely consumed (the launcher never exits or finishes).
    BackHandler(enabled = !s.showFileManager && !s.showStorageManager) {
        vm.handleBackPress()
    }



    val modalOpen = s.showPersonalization || s.showQuickShare || s.nativeQuickShareState.incomingTransfer != null ||
            s.showInputsDialog || s.showProfileDialog || s.showMonitorPanel || s.showCleanTvWizard ||
            s.showCleanTvScreen || s.showFileManager || s.showStorageManager || s.showScreensaver || s.showScreensaverSettings ||
            s.debloatSuccessSummary != null || s.actionMenuApp != null ||
            s.uninstallConfirmApp != null || s.showDefaultPrompt
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

    val lastInteractionMs by vm.lastInteractionMs.collectAsState()
    val idleMinutes = s.screensaverIdleMinutes
    LaunchedEffect(idleMinutes, modalOpen, lastInteractionMs) {
        if (idleMinutes <= 0) return@LaunchedEffect
        while (true) {
            val idleFor = System.currentTimeMillis() - lastInteractionMs
            val remaining = (idleMinutes * 60 * 1000L) - idleFor
            if (remaining <= 0L) {
                val cur = vm.state.value
                if (!modalOpen && !cur.showScreensaver && !cur.showStorageManager && !cur.showFileManager && !cur.showCleanTvScreen) {
                    vm.openScreensaver()
                }
                vm.resetIdleTimer()
                break
            }
            delay(minOf(remaining, 5000L).coerceAtLeast(1000L))
        }
    }

    val isIncomingTransfer = s.nativeQuickShareState.incomingTransfer != null

    TvCanvas {
        Box(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalDisableModalTraps provides isIncomingTransfer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = !isIncomingTransfer }
                ) {
                    val activePage = when {
                        s.showScreensaver    -> "screensaver"
                        s.showTvPlayer       -> "tvplayer"
                        s.showCleanTvScreen  -> "cleantv"
                        s.showStorageManager -> "storage"
                        s.showFileManager    -> "filemanager"
                        else                 -> "home"
                    }
                    AnimatedContent(
                        targetState = activePage,
                        transitionSpec = {
                            val entering = targetState != "home"
                            if (entering) {
                                (fadeIn(AppleMotion.enterTween()) +
                                 slideInVertically(AppleMotion.enterTween()) { it / 20 })
                                    .togetherWith(
                                        fadeOut(AppleMotion.exitTween()) +
                                        scaleOut(AppleMotion.exitTween<Float>(), targetScale = 0.96f)
                                    )
                            } else {
                                (fadeIn(AppleMotion.enterTween()) +
                                 scaleIn(AppleMotion.enterTween<Float>(), initialScale = 0.96f))
                                    .togetherWith(
                                        fadeOut(AppleMotion.exitTween()) +
                                        slideOutVertically(AppleMotion.exitTween()) { it / 20 }
                                    )
                            }
                        },
                        label = "pageTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            "screensaver" -> {
                                AppleTvScreensaver(
                                    mode = s.screensaverMode,
                                    customPath = s.screensaverCustomPath,
                                    selectedUrl = s.screensaverSelectedUrl,
                                    autoCache = s.screensaverAutoCache,
                                    onDismiss = vm::closeScreensaver
                                )
                            }
                            "tvplayer" -> {
                                TvInputPlayer(
                                    ports = s.tvPorts,
                                    activeId = s.activeInputId,
                                    onSelect = vm::switchToPort,
                                    onFailed = vm::tvPlayerFailed,
                                    onClose = vm::closeTvPlayer
                                )
                            }
                            "cleantv" -> {
                                CleanTvScreen(
                                    adbClient = vm.adb,
                                    journal = vm.journal,
                                    scanner = vm.scanner,
                                    tweaks = vm.tweaks,
                                    onClose = vm::closeCleanTvScreen
                                )
                            }
                            "storage" -> {
                                StorageManagerScreen(
                                    adbClient = vm.adb,
                                    journal = vm.journal,
                                    onClose = vm::closeStorageManager
                                )
                            }
                            "filemanager" -> {
                                FileManagerScreen(
                                    initialDir = s.fileManagerInitialDir,
                                    highlightFile = s.fileManagerHighlightFile,
                                    onClose = vm::closeFileManager
                                )
                            }
                            else -> {
                    // Focus state lives in holders that are only read inside small child scopes (hero title, backdrop,
                    // scroll effect). Moving focus therefore never recomposes the whole grid.
                    val focusedAppState = remember { mutableStateOf<InstalledAppInfo?>(null) }
                    val focusedGridRowState = remember { mutableIntStateOf(-1) }
                    // Name of the focused top-bar pill (null when a tile has focus). Read only by HeroTitle.
                    val topBarLabel = remember { mutableStateOf<String?>(null) }
                    val listState = rememberLazyListState()

                    CleanTvBackdrop(
            themeId = s.wallpaperTheme,
            focusedAppProvider = { focusedAppState.value ?: s.dockApps.firstOrNull() ?: s.allApps.firstOrNull() }
        ) {
            // Layout follows the reference launcher (tvOS style), scaled x1.33 for the 1280dp canvas:
            // header, hero name, pinned glass dock, then a scrolling grid of banner tiles.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = returnProgress.value
                        val sc = 1.025f - 0.025f * p
                        scaleX = sc
                        scaleY = sc
                        alpha = 0.6f + 0.4f * p
                    }
            ) {
                // Window width in the scaled TV-canvas dp (replaces BoxWithConstraints: no sub-composition in layout).
                val maxWidth = (LocalContext.current.resources.displayMetrics.widthPixels / LocalDensity.current.density).dp
                val cols = 4
                val sideMargin = 72.dp
                val bandPad = 19.dp
                val gap = 21.dp
                val tileW = (maxWidth - sideMargin * 2 - bandPad * 2 - gap * (cols - 1)) / cols
                val tileH = tileW * 9f / 16f
                val radius = 22.dp
                val rowH = tileH + 42.dp
                val rowGap = 16.dp
                val gridViewportH = rowH * 2 + rowGap + 16.dp

                val dock = s.dockApps.ifEmpty { s.allApps.take(cols) }.take(cols)
                val gridApps = s.allApps // Show all installed applications on the home screen
                val gridRows = remember(gridApps) { gridApps.chunked(cols) }
                val hasDock = dock.isNotEmpty()

                // Suppress Compose's automatic partial-pixel bringIntoView scrolling so rows never get cut in half.
                @OptIn(ExperimentalFoundationApi::class)
                val noOpBringIntoViewSpec = remember {
                    object : BringIntoViewSpec {
                        override val scrollAnimationSpec: AnimationSpec<Float> = tween(0)
                        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
                    }
                }

                // Aligned row glide: Every scroll lands on item boundary (offset = 0) with smooth easing.
                // When on Dock or Row 0: Dock + Title + Row 0 fit on screen together without any row cut off!
                // When moving down to Row 1+: glides so Row (r - 1) is at the top and Row r is at bottom.
                // Exactly complete rows are visible without ever slicing through any tile or name area.
                LaunchedEffect(hasDock) {
                    snapshotFlow { focusedGridRowState.intValue }.collectLatest { r ->
                        val target = when {
                            r <= -1 -> 0 // Dock focused: dock at top
                            hasDock -> 1 + r // Row 0 -> target = 1 (header at top, Row 0 & 1 visible without clipping!); Row 1 -> target = 2; Row 2 -> target = 3
                            else -> r
                        }
                        smoothScrollToItem(listState, target)
                    }
                }

                val topBarPillRequesters = remember { List(9) { FocusRequester() } }
                val dockColRequesters = remember { List(cols) { FocusRequester() } }
                val gridRow0ColRequesters = remember { List(cols) { FocusRequester() } }

                var isDockVisible by remember { mutableStateOf(true) }
                var showSortMenu by remember { mutableStateOf(false) }

                Column(
                    Modifier
                        .fillMaxSize()
                        .focusProperties { canFocus = !modalOpen }
                        .focusRestorer()
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                vm.resetIdleTimer()
                            }
                            false
                        }
                        .padding(start = sideMargin, end = sideMargin, top = 34.dp)
                ) {
                    CleanTvTopBar(
                        greeting = if (s.showGreeting) s.greetingText else "",
                        timeText = s.timeText,
                        dateText = s.dateText,
                        networkLabel = s.networkLabel,
                        accountLabel = s.tvProfile.email ?: "Sign in",
                        onOpenAccount = vm::openAccountsNative,
                        sortMode = s.appSortMode,
                        pillRequesters = topBarPillRequesters,
                        dockRequesters = dockColRequesters,
                        dockSize = dock.size,
                        isQuickShareActive = s.isQuickShareActive,
                        onOpenQuickShare = { vm.openQuickShareControl() },
                        onOpenInputs = vm::openInputs,
                        onOpenNetwork = vm::openNetworkSettings,
                        onOpenCleanTv = vm::requestCleanTv,
                        onToggleSort = { vm.toggleAppSortMode() },
                        onOpenStorage = vm::openStorageManager,
                        onOpenFiles = vm::openFileManager,
                        onOpenScreensaver = vm::openScreensaverSettings,
                        onOpenSettings = vm::openSystemSettings,
                        onTopFocus = { label, hasFocus ->
                            if (hasFocus) topBarLabel.value = label
                            else if (topBarLabel.value == label) topBarLabel.value = null
                        }
                    )

                    Spacer(Modifier.height(14.dp))

                    HeroTitle(
                        nameProvider = {
                            topBarLabel.value
                                ?: (focusedAppState.value ?: dock.firstOrNull() ?: s.allApps.firstOrNull())?.appName
                                ?: ""
                        },
                        isLoading = s.isLoading,
                        modifier = Modifier.padding(start = 6.dp).height(72.dp)
                    )

                    AnimatedContent(
                        targetState = s.isLoading,
                        transitionSpec = {
                            fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = "loadingToContent"
                    ) { loading ->
                        if (loading) {
                            // Same geometry as the real dock + grid, so nothing moves when content arrives.
                            AppGridSkeleton(
                                tileW = tileW, tileH = tileH, radius = radius,
                                gap = gap, cols = cols, bandPad = bandPad
                            )
                        } else {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides noOpBringIntoViewSpec) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusProperties { canFocus = !modalOpen }
                                        .clipToBounds(),
                                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                            // ─── Pinned Glass Dock (Scrolls away smoothly when moving down into apps) ───
                            if (dock.isNotEmpty()) {
                                item(key = "dock_bar", contentType = "dock") {
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
                                            focusScale = 1.05f, showNames = false,
                                            returningPkg = returningAppPackage,
                                            tileModifier = { index, _ ->
                                                val topIdx = (index * 9 / dock.size.coerceAtLeast(1)).coerceIn(0, 8)
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
                                            onFocus = {
                                                focusedAppState.value = it
                                                focusedGridRowState.intValue = -1
                                                vm.prewarmLaunch(it.packageName)
                                            },
                                            onClick = launchApp,
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
                                itemsIndexed(gridRows, key = { _, row -> "g_" + row.first().packageName }, contentType = { _, _ -> "row" }) { rowIndex, rowApps ->
                                    AppRow(
                                        apps = rowApps, tileW = tileW, tileH = tileH, radius = radius, gap = gap,
                                        focusScale = 1.06f, showNames = true,
                                        returningPkg = returningAppPackage,
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
                                        badge = null,
                                        onFocus = {
                                            focusedAppState.value = it
                                            focusedGridRowState.intValue = rowIndex
                                            vm.prewarmLaunch(it.packageName)
                                        },
                                        onClick = launchApp,
                                        onLongClick = { vm.showActionMenu(it) },
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
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

            // Inputs Switcher Modal (Live physical hardware ports)
            if (s.showInputsDialog) {
                InputsSwitcherDialog(
                    ports = s.tvPorts,
                    onSelectPort = vm::switchToPort,
                    onDismiss = vm::closeInputs
                )
            }

            // Screensaver Configuration Modal
            if (s.showScreensaverSettings) {
                ScreensaverSettingsDialog(
                    currentMode = s.screensaverMode,
                    customPath = s.screensaverCustomPath,
                    idleMinutes = s.screensaverIdleMinutes,
                    autoCache = s.screensaverAutoCache,
                    selectedUrl = s.screensaverSelectedUrl,
                    onSelectMode = vm::setScreensaverMode,
                    onSelectIdleMinutes = vm::setScreensaverIdleMinutes,
                    onToggleAutoCache = vm::setScreensaverAutoCache,
                    onSelectVideoUrl = vm::setScreensaverSelectedUrl,
                    onPickPath = {
                        vm.closeScreensaverSettings()
                        vm.openFileManager(
                            initialDir = android.os.Environment.getExternalStorageDirectory() ?: java.io.File("/storage/emulated/0"),
                            highlightFile = null
                        )
                    },
                    onStartScreensaverNow = vm::startScreensaverNow,
                    onDismiss = vm::closeScreensaverSettings
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

            // Quick Share control panel (opened via Quick Share pill/tile)
            if (s.showQuickShare) {
                com.leantv.app.share.NativeQuickShareConsentDialog(
                    state = s.nativeQuickShareState,
                    isQuickShareActive = s.isQuickShareActive,
                    autoOffSeconds = s.quickShareAutoOffSeconds,
                    serverUrl = s.quickShareState.serverUrl,
                    pin = s.quickShareState.pin,
                    deviceName = s.detectedDeviceLabel.ifBlank { android.os.Build.MODEL },
                    onToggleActive = vm::toggleQuickShare,
                    onAccept = vm::acceptNativeTransfer,
                    onDecline = vm::rejectNativeTransfer,
                    onDismiss = vm::closeQuickShareControl,
                    onOpenFolder = {
                        vm.closeQuickShareControl()
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
                        launchApp(app, null)
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
}
            }
        }
    }



            // ─── Universal Quick Share Incoming Consent Popup ───
            // Appears on top of EVERYTHING across the entire app (File Manager, Settings, Screensaver, etc.)
            s.nativeQuickShareState.incomingTransfer?.let { transfer ->
                com.leantv.app.share.NativeQuickShareConsentPopup(
                    transfer = transfer,
                    onAccept = vm::acceptNativeTransfer,
                    onDecline = vm::rejectNativeTransfer,
                    onDismiss = vm::dismissIncomingTransfer,
                    onOpenFolder = {
                        vm.dismissIncomingTransfer()
                        vm.openFileManager(
                            initialDir = com.leantv.app.files.FileManagerHelper.ensureQuickShareDir(),
                            highlightFile = null
                        )
                    }
                )
            }

            // ─── Custom Apple TV / Google TV Volume HUD ───
            CustomVolumeHud(
                visible = s.showVolumeBar,
                level = s.volumeLevel,
                max = s.volumeMax,
                isMuted = s.volumeIsMuted
            )
        }
    }



/**
 * Apple TV / tvOS style vertical frosted glass volume HUD.
 * Renders on the right edge of the screen and glides out smoothly on volume changes.
 */
@Composable
private fun CustomVolumeHud(
    visible: Boolean,
    level: Int,
    max: Int,
    isMuted: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInHorizontally(tween(220, easing = AppleMotion.EaseOut)) { it / 2 },
        exit = fadeOut(tween(260)) + slideOutHorizontally(tween(220, easing = AppleMotion.EaseIn)) { it / 2 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 40.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            val safeMax = max.coerceAtLeast(1)
            val progress = if (isMuted) 0f else (level.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(140, easing = AppleMotion.EaseOut),
                label = "volProgress"
            )
            val pillShape = RoundedCornerShape(26.dp)

            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(200.dp)
                    .clip(pillShape)
                    .background(Color(0xCC16181F))
                    .border(1.5.dp, Color(0x38FFFFFF), pillShape)
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    val icon = when {
                        isMuted || level == 0 -> Icons.AutoMirrored.Rounded.VolumeOff
                        progress < 0.34f -> Icons.AutoMirrored.Rounded.VolumeMute
                        progress < 0.67f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    }
                    val iconTint = if (isMuted || level == 0) Color(0xFFFF5252) else Color.White

                    Icon(
                        imageVector = icon,
                        contentDescription = "Volume",
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )

                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .height(110.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x33FFFFFF)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(animatedProgress)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.White, Color(0xFFE0E0E0))
                                    )
                                )
                        )
                    }

                    Text(
                        text = if (isMuted) "MUTE" else "$level",
                        style = Ty.Small.copy(
                            color = if (isMuted) Color(0xFFFF5252) else Color(0xDEFFFFFF),
                            fontSize = if (isMuted) 10.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Big app name above the grid. Reads the focused name itself, so only this small scope recomposes on focus moves. */
@Composable
private fun HeroTitle(nameProvider: () -> String, isLoading: Boolean, modifier: Modifier = Modifier) {
    val name = nameProvider()
    if (isLoading && name.isEmpty()) {
        HeroSkeleton(modifier)
    } else {
        // One Text whose fade/glide restarts on every change. Much cheaper than AnimatedContent (no second text
        // layout, no overlapping texts) and it stays fluid when the D-pad is held down.
        val reveal = remember(name) { Animatable(0.5f) }
        LaunchedEffect(reveal) {
            reveal.animateTo(1f, tween(150, easing = AppleMotion.EaseOut))
        }
        Box(modifier, contentAlignment = Alignment.TopStart) {
            Text(
                text = name,
                style = Ty.Display.copy(fontSize = 58.sp, lineHeight = 68.sp, letterSpacing = (-1.3).sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    alpha = reveal.value
                    translationX = (1f - reveal.value) * 12f
                }
            )
        }
    }
}

private val TvGlideEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

/** Scrolls so [index] sits at the top (offset = 0), with silky smooth glide and no partial-row cuts. */
private suspend fun smoothScrollToItem(state: LazyListState, index: Int) {
    if (state.firstVisibleItemIndex == index && state.firstVisibleItemScrollOffset == 0) return
    state.animateScrollToItem(index, 0)
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
 * and glass pills on the right. Pills are ICON-ONLY; the name slides out when a pill gets focus
 * (and the same name is shown in the big hero title above the grid).
 */
@Composable
private fun CleanTvTopBar(
    greeting: String,
    timeText: String,
    dateText: String,
    networkLabel: String,
    accountLabel: String,
    onOpenAccount: () -> Unit,
    sortMode: AppSortMode,
    pillRequesters: List<FocusRequester> = emptyList(),
    dockRequesters: List<FocusRequester> = emptyList(),
    dockSize: Int = 4,
    isQuickShareActive: Boolean = false,
    onOpenQuickShare: () -> Unit,
    onOpenInputs: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenCleanTv: () -> Unit,
    onToggleSort: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenScreensaver: () -> Unit,
    onOpenSettings: () -> Unit,
    onTopFocus: (label: String, hasFocus: Boolean) -> Unit = { _, _ -> },
) {
    val parts = timeText.trim().split(" ")
    val clock = parts.firstOrNull().orEmpty()
    val ampm = parts.getOrNull(1).orEmpty()
    val netIcon = if (networkLabel.contains("Ethernet", ignoreCase = true)) Icons.Rounded.SettingsEthernet else Icons.Rounded.Wifi

    fun pillFocusModifier(index: Int): Modifier {
        if (pillRequesters.size <= index) return Modifier
        val targetCol = if (dockSize > 0) (index * dockSize / 9).coerceIn(0, dockSize - 1) else 0
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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
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
                    )
                }
                // Signed-in Google account: icon only, email slides out on focus.
                TvPill(
                    label = accountLabel,
                    icon = Icons.Rounded.AccountCircle,
                    contentDescription = "Google account",
                    onClick = onOpenAccount,
                    maxLabelWidth = 280.dp,
                    collapsible = true,
                    onFocusChange = { onTopFocus(accountLabel, it) },
                )
            }
        }

        // ─── Row 2: Icon-only action pills ───
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val qsLabel = if (isQuickShareActive) "Quick Share · On" else "Quick Share"
            TvPill(
                label = qsLabel,
                iconRes = R.drawable.ic_quick_share,
                contentDescription = "Quick Share",
                modifier = pillFocusModifier(0),
                onClick = onOpenQuickShare,
                collapsible = true,
                showDot = isQuickShareActive,
                onFocusChange = { onTopFocus(qsLabel, it) },
            )
            TvPill(
                label = "Clean TV",
                icon = Icons.Rounded.AutoAwesome,
                modifier = pillFocusModifier(1),
                onClick = onOpenCleanTv,
                collapsible = true,
                onFocusChange = { onTopFocus("Clean TV", it) },
            )
            TvPill(
                label = "Inputs",
                icon = Icons.AutoMirrored.Rounded.Input,
                modifier = pillFocusModifier(2),
                onClick = onOpenInputs,
                collapsible = true,
                onFocusChange = { onTopFocus("Inputs", it) },
            )
            val sortLabel = if (sortMode == AppSortMode.RECENCY) "Sort: Recent" else "Sort: A–Z"
            TvPill(
                label = sortLabel,
                icon = if (sortMode == AppSortMode.RECENCY) Icons.Rounded.Schedule else Icons.AutoMirrored.Rounded.Sort,
                modifier = pillFocusModifier(3),
                onClick = onToggleSort,
                collapsible = true,
                onFocusChange = { onTopFocus(sortLabel, it) },
            )
            val netText = networkLabel.ifBlank { "Wi-Fi" }
            TvPill(
                label = netText,
                icon = netIcon,
                modifier = pillFocusModifier(4),
                onClick = onOpenNetwork,
                maxLabelWidth = 200.dp,
                collapsible = true,
                onFocusChange = { onTopFocus(netText, it) },
            )
            TvPill(
                label = "Storage",
                icon = Icons.Rounded.Storage,
                modifier = pillFocusModifier(5),
                onClick = onOpenStorage,
                contentDescription = "Storage",
                collapsible = true,
                onFocusChange = { onTopFocus("Storage", it) },
            )
            TvPill(
                label = "Files",
                icon = Icons.Rounded.Folder,
                modifier = pillFocusModifier(6),
                onClick = onOpenFiles,
                contentDescription = "Files",
                collapsible = true,
                onFocusChange = { onTopFocus("Files", it) },
            )
            TvPill(
                label = "Screensaver",
                icon = Icons.Rounded.Tv,
                modifier = pillFocusModifier(7),
                onClick = onOpenScreensaver,
                contentDescription = "Screensaver",
                collapsible = true,
                onFocusChange = { onTopFocus("Screensaver", it) },
            )
            TvPill(
                label = "Settings",
                icon = Icons.Rounded.Settings,
                modifier = pillFocusModifier(8),
                onClick = onOpenSettings,
                contentDescription = "Settings",
                collapsible = true,
                onFocusChange = { onTopFocus("Settings", it) },
            )
        }
    }
}

/**
 * Glass pill. With [collapsible] = true it rests as a round icon button and the label slides out
 * to the right while focused (width is animated in the layout phase: no recomposition per frame).
 * With collapsible = false (Tools menu rows) it behaves as before.
 */
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
    collapsible: Boolean = false,
    showDot: Boolean = false,
    onFocusChange: (Boolean) -> Unit = {},
    onPillFocus: () -> Unit = {},
) {
    val view = LocalView.current
    var focused by remember { mutableStateOf(false) }
    val pillFocus by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(130, easing = AppleMotion.EaseOut),
        label = "pillFocus"
    )
    val fg by animateColorAsState(
        targetValue = if (focused) Color.Black else if (isSelected) Color.White else Color(0xEBFFFFFF),
        animationSpec = AppleMotion.microTween(),
        label = "pillFg"
    )
    // 0 = icon only, 1 = icon + label. Read only inside layout/graphicsLayer lambdas below.
    val expand = animateFloatAsState(
        targetValue = if (!collapsible || focused) 1f else 0f,
        // Short and plain: the pill's label slides out in 170 ms. (340 ms with a front-loaded curve felt stuck.)
        animationSpec = tween(170, easing = AppleMotion.EaseOut),
        label = "pillExpand"
    )
    val shape = RoundedCornerShape(50)
    val bg = when {
        focused -> Brush.verticalGradient(listOf(Color(0xF5FFFFFF), Color(0xF5FFFFFF)))
        isSelected -> Brush.verticalGradient(listOf(Color(0x4DFFFFFF), Color(0x24FFFFFF)))
        else -> Brush.verticalGradient(listOf(Color(0x2EFFFFFF), Color(0x14FFFFFF)))
    }
    val iconSize = 26.dp
    val sidePad = (height - iconSize) / 2   // centres the icon when collapsed
    Row(
        modifier
            .height(height)
            .graphicsLayer {
                val sc = 1f + 0.03f * pillFocus
                scaleX = sc
                scaleY = sc
            }
            .clip(shape)
            .background(bg)
            .border(
                if (focused) 1.5.dp else if (isSelected) 1.5.dp else 1.dp,
                if (focused) Color.White.copy(alpha = 0.95f) else if (isSelected) Color.White.copy(alpha = 0.65f) else Color(0x38FFFFFF),
                shape
            )
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    if (collapsible) {
                        TvSound.playTopOptionMove(view)
                    } else {
                        TvSound.playMove(view)
                    }
                    onPillFocus()
                }
                if (it.isFocused != focused) onFocusChange(it.isFocused)
                focused = it.isFocused
            }
            .clickable {
                TvSound.playClick(view)
                onClick()
            }
            .then(
                if (collapsible) {
                    // Report an animated width between "icon only" and "icon + label".
                    Modifier.layout { measurable, c ->
                        val p = measurable.measure(
                            Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = c.minHeight, maxHeight = c.maxHeight)
                        )
                        val iconW = height.roundToPx()
                        val w = (iconW + (p.width - iconW).coerceAtLeast(0) * expand.value).roundToInt()
                        layout(w, p.height) { p.place(0, 0) }
                    }.padding(start = sidePad, end = if (label != null) 24.dp else sidePad)
                } else if (label == null) Modifier.width(height)
                else Modifier.padding(start = 21.dp, end = 27.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (fill || collapsible) Arrangement.Start else Arrangement.Center,
    ) {
        Box(Modifier.size(iconSize), contentAlignment = Alignment.Center) {
            if (iconRes != null) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = contentDescription ?: label,
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription ?: label,
                    tint = fg,
                    modifier = Modifier.size(iconSize)
                )
            }
            if (showDot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF30D158))
                )
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = Ty.Button.copy(fontSize = textSize, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium, color = fg),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .widthIn(max = maxLabelWidth)
                    .graphicsLayer {
                        // Fades in slightly after the pill starts growing, so text never looks clipped.
                        if (collapsible) alpha = ((expand.value - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    }
            )
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
    onClick: (InstalledAppInfo, Rect?) -> Unit,
    onLongClick: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier,
    returningPkg: String? = null,
    badge: ((InstalledAppInfo) -> String?)? = null,
    tileModifier: ((index: Int, app: InstalledAppInfo) -> Modifier)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap)) {
        apps.forEachIndexed { index, app ->
            CleanTvAppTile(
                app = app, tileW = tileW, tileH = tileH, radius = radius,
                focusScale = focusScale, showName = showNames,
                badgeText = badge?.invoke(app),
                isReturning = (returningPkg == app.packageName),
                modifier = tileModifier?.invoke(index, app) ?: Modifier,
                onFocus = { onFocus(app) },
                onClick = { bounds -> onClick(app, bounds) },
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
    isReturning: Boolean = false,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: (Rect?) -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var visuals by remember(app.packageName) { mutableStateOf(IconCache.get(app.packageName)) }
    var loadFinished by remember(app.packageName) { mutableStateOf(visuals != null) }
    LaunchedEffect(app.packageName) {
        if (visuals == null) visuals = IconCache.loadVisuals(context, app.packageName)
        loadFinished = true
    }
    // Artwork fades in when it arrives instead of popping (already-cached art starts fully visible).
    val artAlpha by animateFloatAsState(
        if (visuals != null || loadFinished) 1f else 0f,
        tween(220),
        label = "tileArt"
    )

    var focused by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    var isLaunching by remember { mutableStateOf(false) }
    val coordsHolder = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var isLongPressHandled by remember { mutableStateOf(false) }

    // Apple TV-style welcome landing spring when returning to this app's tile from the app
    val landingScale = remember { Animatable(1f) }
    // (No separate bouncing landing on the tile any more: the whole Home screen settles once, see returnProgress.)

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = AppleMotion.SpringSnappy,
        label = "tilePressScale"
    )
    val launchPopScale by animateFloatAsState(
        targetValue = if (isLaunching) 1.05f else 1f,
        animationSpec = tween(180, easing = AppleMotion.EaseOut),
        label = "tileLaunchPop"
    )
    val launchSheen by animateFloatAsState(
        targetValue = if (isLaunching) 1f else 0f,
        animationSpec = tween(240, easing = AppleMotion.EaseOut),
        label = "tileLaunchSheen"
    )
    // ONE progress value (0..1) drives scale, ring, shadow and name: 1 animation per tile instead of 6.
    val focusProgress by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        // Critically damped: grows smoothly and stops dead, no wobble when D-pad moves quickly.
        animationSpec = spring(dampingRatio = 1f, stiffness = 520f),
        label = "tileFocus"
    )
    val shape = RoundedCornerShape(radius)

    Column(
        Modifier.width(tileW)
    ) {
        Box(
            modifier
                .size(tileW, tileH)
                .onGloballyPositioned { coordsHolder[0] = it } // plain array: no state write on every scroll frame
                .graphicsLayer {
                    val sc = (1f + (focusScale - 1f) * focusProgress) * pressScale * launchPopScale * landingScale.value
                    scaleX = sc
                    scaleY = sc
                    clip = false
                }
                .clip(shape)
                // Focus ring + soft highlight drawn in the draw phase: animating them never recomposes the tile.
                .drawWithContent {
                    drawContent()
                    if (launchSheen > 0.01f) {
                        drawRect(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = launchSheen * 0.40f),
                                    Color.Transparent
                                )
                            )
                        )
                    }
                    val a = focusProgress.coerceIn(0f, 1f)
                    if (a > 0.01f) {
                        val sw = 2.5.dp.toPx()
                        drawRect(
                            Brush.verticalGradient(listOf(Color(0x28FFFFFF), Color(0x04FFFFFF), Color.Transparent)),
                            alpha = a
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = a * 0.9f),
                            topLeft = Offset(sw / 2f, sw / 2f),
                            size = Size(size.width - sw, size.height - sw),
                            cornerRadius = CornerRadius(radius.toPx() - sw / 2f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw)
                        )
                    }
                }
                .background(Color(0xFF1C1C1E))
                .onFocusChanged {
                    if (it.isFocused && !focused) {
                        TvSound.playMove(view)
                    }
                    focused = it.isFocused
                    if (!it.isFocused) {
                        isPressed = false
                        isLaunching = false
                        longPressJob?.cancel()
                    }
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
                                isPressed = true
                                isLongPressHandled = false
                                longPressJob?.cancel()
                                longPressJob = scope.launch {
                                    delay(450)
                                    isPressed = false
                                    isLongPressHandled = true
                                    TvSound.playClick(view)
                                    onLongClick()
                                }
                            }
                            true
                        }
                        center && k.action == KeyEvent.ACTION_UP -> {
                            isPressed = false
                            val wasLong = isLongPressHandled
                            longPressJob?.cancel()
                            longPressJob = null
                            isLongPressHandled = false
                            if (!wasLong) {
                                TvSound.playAppOpen(view)
                                isLaunching = true
                                onClick(boundsOf(coordsHolder[0]))
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
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = artAlpha }
                )
            } else {
                val plateTop = v?.topColor ?: extractFallbackHue(app)
                val plateBottom = v?.bottomColor ?: Color(0xFF101216)
                // Large icon centred in plate with a frosted name strip at the bottom
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = artAlpha }
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
                    .graphicsLayer {
                        alpha = focusProgress.coerceIn(0f, 1f)
                        translationY = (1f - focusProgress) * 8f
                    }
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
/**
 * Soft round glow, pre-rendered ONCE as a tiny alpha mask. Drawing a stretched 128x72 texture tinted with the
 * app colour is far cheaper per frame than a full-screen radial-gradient shader, so focus changes stay smooth.
 */
private val GlowMask: androidx.compose.ui.graphics.ImageBitmap by lazy {
    val w = 128
    val h = 72
    val px = IntArray(w * h)
    val cx = w * 0.32f
    val cy = h * 0.42f
    val r = w * 0.62f
    for (y in 0 until h) for (x in 0 until w) {
        val dx = x + 0.5f - cx
        val dy = y + 0.5f - cy
        val t = kotlin.math.sqrt(dx * dx + dy * dy) / r
        val a = when {
            t <= 0.45f -> 0.36f + (0.15f - 0.36f) * (t / 0.45f)
            t <= 1f -> 0.15f * (1f - (t - 0.45f) / 0.55f)
            else -> 0f
        }
        px[y * w + x] = ((a * 255f).toInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
    }
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.setPixels(px, 0, w, 0, 0, w, h)
    bmp.asImageBitmap()
}

@Composable
private fun CleanTvBackdrop(
    themeId: String,
    focusedAppProvider: () -> InstalledAppInfo?,
    content: @Composable () -> Unit
) {
    // The focus-dependent glow lives in its OWN composable, as a sibling behind the content.
    // Moving focus recomposes only that tiny layer, never the dock, grid or top bar.
    Box(Modifier.fillMaxSize()) {
        AmbientBackdropLayer(themeId, focusedAppProvider)
        content()
    }
}

@Composable
private fun AmbientBackdropLayer(
    themeId: String,
    focusedAppProvider: () -> InstalledAppInfo?,
) {
    val theme = WALLPAPER_THEMES.find { it.id == themeId } ?: WALLPAPER_THEMES.first()
    val ctx = LocalContext.current
    val focusedApp = focusedAppProvider()
    val fallbackHue = extractFallbackHue(focusedApp)
    val ambientColor by produceState(
        initialValue = focusedApp?.let { IconCache.get(it.packageName)?.glow } ?: fallbackHue,
        key1 = focusedApp?.packageName
    ) {
        val cached = focusedApp?.let { IconCache.get(it.packageName)?.glow }
        if (cached != null) {
            value = cached
        } else {
            value = fallbackHue
            focusedApp?.let { app ->
                IconCache.loadVisuals(ctx, app.packageName)?.glow?.let { value = it }
            }
        }
    }
    val animatedAmbient = animateColorAsState(
        targetValue = ambientColor,
        animationSpec = tween(260, easing = AppleMotion.EaseOut),
        label = "ambientGlow"
    )
    Box(Modifier.fillMaxSize()) {
        // Layer 1: the wallpaper gradient. Static, so it is recorded once and never redrawn while the glow fades.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val themeBrush = Brush.verticalGradient(
                        colors = theme.gradientColors,
                        startY = 0f,
                        endY = size.height
                    )
                    onDrawBehind { drawRect(brush = themeBrush) }
                }
        )
        // Layer 2: only the tinted glow texture is redrawn while the colour animates.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawImage(
                        image = GlowMask,
                        dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(animatedAmbient.value),
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
                    )
                }
        )
    }
}

private fun boundsOf(c: LayoutCoordinates?): Rect? {
    if (c == null || !c.isAttached) return null
    val pos = c.positionInWindow()
    return Rect(pos.x, pos.y, pos.x + c.size.width, pos.y + c.size.height)
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
                .width(720.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF26262B), Color(0xFF17171B))))
                .padding(horizontal = 40.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("TV Inputs", style = Ty.Title.copy(color = Color.White, fontSize = 32.sp))
            Text("Switch to external devices connected to this TV", style = Ty.Dim.copy(fontSize = 17.sp))
            Spacer(Modifier.height(10.dp))

            if (ports.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Rounded.SettingsInputHdmi, contentDescription = null, tint = Color(0x66FFFFFF), modifier = Modifier.size(64.dp))
                    Text("No Hardware Inputs Detected", style = Ty.Heading.copy(color = Color.White, fontSize = 24.sp))
                    Text(
                        "No external HDMI or AV sources detected on this TV.",
                        style = Ty.Dim.copy(fontSize = 16.sp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (ports.isNotEmpty()) {
                val firstConnectedId = ports.firstOrNull { it.isConnected }?.id ?: ports.firstOrNull()?.id
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ports.chunked(3).forEach { rowPorts ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            rowPorts.forEach { port ->
                                TvPortCard(
                                    port = port,
                                    onClick = { onSelectPort(port) },
                                    modifier = Modifier
                                        .width(204.dp)
                                        .let { if (port.id == firstConnectedId) it.focusRequester(autoFocus) else it }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))
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
    val isConnected = port.isConnected
    Focusable(
        onClick = onClick,
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(24.dp),
        idle = if (isConnected) Color(0x1F22C55E) else Color(0x0AFFFFFF),
        idleOutline = isConnected
    ) { focused ->
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PortIcon(
                kind = port.kind,
                color = when {
                    isConnected -> Color(0xFF4ADE80)
                    focused -> Color.White
                    else -> Color(0x35FFFFFF)
                },
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                port.label,
                style = Ty.Heading.copy(
                    color = when {
                        isConnected -> Color.White
                        focused -> Color.White
                        else -> Color(0x55FFFFFF)
                    },
                    fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 22.sp
                ),
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isConnected) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = port.subtitle ?: "Connected",
                        style = Ty.Small.copy(
                            color = Color(0xFF4ADE80),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                } else {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0x30FFFFFF)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = port.subtitle ?: "Not connected",
                        style = Ty.Small.copy(
                            color = Color(0x45FFFFFF),
                            fontSize = 13.sp
                        ),
                        maxLines = 1
                    )
                }
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
    // Prevent the held down OK/Enter key from immediately triggering "Open"!
    var hasKeyReleasedAfterOpen by remember { mutableStateOf(false) }
    var armedByTimer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(400) // Grace period for the physical long-press release
        armedByTimer = true
    }

    val isArmed = hasKeyReleasedAfterOpen || armedByTimer

    fun guardAction(action: () -> Unit): () -> Unit = {
        if (isArmed) action()
    }

    val firstFocus = rememberAutoFocus(app.packageName)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .modalTrap()
            .onPreviewKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                if (k.action == KeyEvent.ACTION_UP && (k.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || k.keyCode == KeyEvent.KEYCODE_ENTER)) {
                    hasKeyReleasedAfterOpen = true
                }
                false
            },
        contentAlignment = Alignment.Center
    ) {
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
            TvPill("Open", Icons.Rounded.PlayArrow, guardAction(onOpen), Modifier.fillMaxWidth().focusRequester(firstFocus), height = h, textSize = ts, fill = true)
            TvPill(if (isFavorite) "Unpin from Dock" else "Pin to Dock", Icons.Rounded.PushPin, guardAction(onToggleFavorite), Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
            TvPill("Clear Data", Icons.Rounded.DeleteSweep, guardAction(onClearData), Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
            TvPill("Clear Cache", Icons.Rounded.CleaningServices, guardAction(onClearCache), Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
            TvPill("Hide from Home", Icons.Rounded.VisibilityOff, guardAction(onHideApp), Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
            val currentContext = LocalContext.current
            if (app.packageName != currentContext.packageName) {
                TvPill(
                    label = if (app.isSystemApp) "Uninstall / Remove Bloatware" else "Uninstall",
                    icon = Icons.Rounded.Delete,
                    onClick = guardAction(onUninstall),
                    modifier = Modifier.fillMaxWidth(),
                    height = h,
                    textSize = ts,
                    fill = true
                )
            }
            TvPill("App Info", Icons.Rounded.Info, guardAction(onDetails), Modifier.fillMaxWidth(), height = h, textSize = ts, fill = true)
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


/**
 * Full-screen player for a real hardware input (HDMI / AV / component). Shows the live picture through
 * the TV's own input service, and lets the user flip between ports without leaving the screen.
 * Back (or the remote's Home key) returns to Home.
 */
@Composable
private fun TvInputPlayer(
    ports: List<TvPort>,
    activeId: String?,
    onSelect: (TvPort) -> Unit,
    onFailed: (TvPort?) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val active = ports.firstOrNull { it.id == activeId }
    var overlayVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>("Connecting...") }
    val rootFocus = remember { FocusRequester() }
    val activeFocus = remember { FocusRequester() }

    val tvView = remember {
        android.media.tv.TvView(ctx).apply { setZOrderMediaOverlay(true) }
    }
    val currentActive by rememberUpdatedState(active)
    val currentOnFailed by rememberUpdatedState(onFailed)

    DisposableEffect(tvView) {
        tvView.setCallback(object : android.media.tv.TvView.TvInputCallback() {
            override fun onConnectionFailed(inputId: String?) { currentOnFailed(currentActive) }
            override fun onDisconnected(inputId: String?) { status = "Disconnected" }
            override fun onVideoAvailable(inputId: String?) { status = null }
            override fun onVideoUnavailable(inputId: String?, reason: Int) {
                status = if (reason == android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING ||
                    reason == android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING) "Connecting..." else "No signal"
            }
        })
        onDispose { runCatching { tvView.reset() } }
    }

    LaunchedEffect(activeId) {
        val port = ports.firstOrNull { it.id == activeId } ?: return@LaunchedEffect
        status = "Connecting..."
        try {
            tvView.tune(port.id, android.media.tv.TvContract.buildChannelUriForPassthroughInput(port.id))
        } catch (_: Exception) {
            currentOnFailed(port)
        }
        // Once selected, show bottom bar for 1 second, then dismiss automatically
        overlayVisible = true
        delay(1000L)
        overlayVisible = false
    }

    // Auto-hide the bottom bar after 1 second of inactivity so it never interferes with the input
    LaunchedEffect(overlayVisible, interactionTick) {
        if (overlayVisible) {
            delay(1000L)
            overlayVisible = false
        }
    }
    LaunchedEffect(overlayVisible) {
        runCatching {
            if (overlayVisible) activeFocus.requestFocus()
            else {
                tvView.requestFocus()
                rootFocus.requestFocus()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { ev ->
                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val code = ev.nativeKeyEvent.keyCode
                    // Back always exits directly back to LeanTV Home
                    if (code == KeyEvent.KEYCODE_BACK) {
                        onClose()
                        return@onPreviewKeyEvent true
                    }
                    // Menu or TV Input button brings up the input switcher
                    if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_TV_INPUT || code == 178) {
                        overlayVisible = !overlayVisible
                        if (overlayVisible) interactionTick++
                        return@onPreviewKeyEvent true
                    }
                    if (overlayVisible) {
                        interactionTick++
                        return@onPreviewKeyEvent false
                    }
                    // When overlay is hidden, DO NOT consume D-pad / Enter / Media keys!
                    // Let all keys pass straight through to the connected HDMI / external device!
                    return@onPreviewKeyEvent false
                }
                false
            }
            .focusable()
    ) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { tvView }, modifier = Modifier.fillMaxSize())

        status?.let {
            Text(
                it,
                style = Ty.Heading.copy(color = Color.White, fontSize = 28.sp),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(tween(150)) + slideInVertically(tween(200)) { it },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2000000))))
                    .padding(horizontal = 48.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Now watching: ${active?.label ?: "-"}   (Back = Home)",
                    style = Ty.Dim.copy(fontSize = 17.sp)
                )
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(ports.size) { i ->
                        val port = ports[i]
                        TvPortCard(
                            port = port,
                            onClick = { if (port.id != activeId) onSelect(port) },
                            modifier = Modifier
                                .width(190.dp)
                                .let { if (port.id == activeId) it.focusRequester(activeFocus) else it }
                        )
                    }
                }
            }
        }
    }
}