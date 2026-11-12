package com.leantv.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.data.AppSortOrder
import com.leantv.app.data.AppTypeFilter
import com.leantv.app.data.IconCache
import com.leantv.app.data.InstalledAppInfo
import com.leantv.app.data.UiState

@Composable
fun InstalledAppsScreen(s: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    var uninstallConfirmApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var actionSheetApp by remember { mutableStateOf<InstalledAppInfo?>(null) }
    var showSearchBar by remember { mutableStateOf(false) }

    val autoFocus = rememberAutoFocus()

    LaunchedEffect(Unit) {
        if (s.installedApps.isEmpty()) {
            vm.loadInstalledApps()
        }
    }

    // Filter and sort items
    val displayedApps = remember(s.installedApps, s.appTypeFilter, s.appSortOrder, s.appSearchQuery) {
        var list = when (s.appTypeFilter) {
            AppTypeFilter.ALL -> s.installedApps
            AppTypeFilter.USER -> s.installedApps.filter { !it.isSystemApp || it.isUpdatedSystemApp }
            AppTypeFilter.SYSTEM -> s.installedApps.filter { it.isSystemApp && !it.isUpdatedSystemApp }
        }

        if (s.appSearchQuery.isNotBlank()) {
            val q = s.appSearchQuery.trim()
            list = list.filter {
                it.appName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)
            }
        }

        when (s.appSortOrder) {
            AppSortOrder.NAME -> list.sortedBy { it.appName.lowercase() }
            AppSortOrder.SIZE -> list.sortedByDescending { it.sizeBytes }
            AppSortOrder.DATE -> list.sortedByDescending { it.installTimeMillis }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(ContentPad)
    ) {
        // Top Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Installed Applications", style = Ty.Display.copy(fontSize = 36.sp))
                Spacer(Modifier.height(4.dp))
                Text(
                    "${displayedApps.size} apps shown (${s.installedApps.size} installed on TV)",
                    style = Ty.Dim.copy(fontSize = 19.sp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    text = if (showSearchBar) "Hide Search" else "Search",
                    onClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) vm.setAppSearchQuery("")
                    },
                    primary = false
                )

                TvButton(
                    text = if (s.installedAppsLoading) "Refreshing..." else "Refresh List",
                    onClick = { vm.loadInstalledApps(forceRefresh = true) },
                    primary = false,
                    enabled = !s.installedAppsLoading
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Remote-Friendly Filter & Sort Chips Row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Filter:", style = Ty.Small.copy(color = Pal.Dim, fontWeight = FontWeight.SemiBold))
            AppTypeFilter.entries.forEach { f ->
                Chip(
                    text = f.label,
                    selected = s.appTypeFilter == f,
                    onClick = { vm.setAppTypeFilter(f) }
                )
            }

            Spacer(Modifier.width(16.dp))

            Text("Sort:", style = Ty.Small.copy(color = Pal.Dim, fontWeight = FontWeight.SemiBold))
            AppSortOrder.entries.forEach { so ->
                Chip(
                    text = so.label,
                    selected = s.appSortOrder == so,
                    onClick = { vm.setAppSortOrder(so) }
                )
            }
        }

        // Optional Search Bar (remote friendly)
        AnimatedVisibility(visible = showSearchBar) {
            Column {
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Pal.Surface)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Search: ", style = Ty.Body.copy(color = Pal.Amber, fontWeight = FontWeight.SemiBold))
                    BasicTextField(
                        value = s.appSearchQuery,
                        onValueChange = { vm.setAppSearchQuery(it) },
                        textStyle = Ty.Body.copy(color = Pal.Text),
                        cursorBrush = SolidColor(Pal.Amber),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (s.appSearchQuery.isEmpty()) {
                                Text("Type app or package name...", style = Ty.Dim)
                            }
                            innerTextField()
                        }
                    )
                    if (s.appSearchQuery.isNotEmpty()) {
                        TvButton(
                            text = "Clear",
                            onClick = { vm.setAppSearchQuery("") },
                            primary = false
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Applications List (pure TV card items without nested buttons)
        if (s.installedAppsLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spinner()
                    Text("Loading installed TV applications...", style = Ty.Heading)
                }
            }
        } else if (displayedApps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No applications found matching your criteria.", style = Ty.Dim)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 36.dp)
            ) {
                items(displayedApps, key = { it.packageName }) { app ->
                    InstalledAppCard(
                        app = app,
                        onClick = { actionSheetApp = app }
                    )
                }
            }
        }
    }

    // Remote-Optimized Quick Action Sheet
    actionSheetApp?.let { app ->
        AppActionDialog(
            app = app,
            onDismiss = { actionSheetApp = null },
            onOpen = {
                actionSheetApp = null
                vm.launchApp(app.packageName)
            },
            onUninstall = {
                actionSheetApp = null
                uninstallConfirmApp = app
            },
            onDetails = {
                actionSheetApp = null
                vm.openAppDetails(app.packageName)
            }
        )
    }

    // Uninstallation Confirmation Dialog
    uninstallConfirmApp?.let { app ->
        UninstallConfirmDialog(
            app = app,
            onDismiss = { uninstallConfirmApp = null },
            onConfirm = {
                uninstallConfirmApp = null
                vm.uninstallApp(app)
            }
        )
    }
}

/**
 * TV Remote-friendly card. Contains NO nested buttons.
 * Clicking anywhere on the card opens the full TV Action Sheet.
 */
@Composable
private fun InstalledAppCard(
    app: InstalledAppInfo,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) { mutableStateOf(IconCache.getIcon(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            iconBitmap = IconCache.loadIcon(context, app.packageName)
        }
    }

    var focused by remember { mutableStateOf(false) }
    val cardBg by animateColorAsState(
        if (focused) Pal.SurfaceHi else Pal.Surface,
        tween(140), label = "instCardBg"
    )
    val ringColor by animateColorAsState(
        if (focused) Pal.Amber else Color.Transparent,
        tween(140), label = "instCardRing"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(if (focused) 2.dp else 1.dp, ringColor, RoundedCornerShape(16.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // App Icon
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33000000)),
                contentAlignment = Alignment.Center
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap!!,
                        contentDescription = app.appName,
                        modifier = Modifier.size(46.dp)
                    )
                } else {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Pal.Amber.copy(alpha = 0.3f))
                    )
                }
            }

            // Info details
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        app.appName,
                        style = Ty.Heading.copy(
                            color = if (focused) Pal.Amber else Pal.Text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Type tag
                    val (tagText, tagColor) = when {
                        !app.isSystemApp -> "User App" to Pal.Mint
                        app.isUpdatedSystemApp -> "Updated System" to Pal.Sky
                        else -> "System" to Pal.Dim
                    }
                    Pill(tagText, tagColor)
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    app.packageName,
                    style = Ty.Small.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        color = Pal.Dim
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("v${app.versionName}", style = Ty.Small.copy(fontSize = 16.sp))
                    Text("•", style = Ty.Small.copy(fontSize = 16.sp))
                    Text(app.formattedSize, style = Ty.Small.copy(fontSize = 16.sp))
                    Text("•", style = Ty.Small.copy(fontSize = 16.sp))
                    Text("Installed: ${app.formattedInstallDate}", style = Ty.Small.copy(fontSize = 16.sp))
                }
            }

            // Remote hint indicator
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) Pal.Amber.copy(alpha = 0.2f) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (focused) "Press OK for Options" else "",
                    style = Ty.Small.copy(
                        color = if (focused) Pal.Amber else Pal.Dim,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}

/**
 * TV Action Dialog with clear remote focus on each action.
 */
@Composable
private fun AppActionDialog(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onDetails: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
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
        ) {
            Text(app.appName, style = Ty.Title.copy(fontSize = 26.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(app.packageName, style = Ty.Small.copy(fontFamily = FontFamily.Monospace, fontSize = 16.sp))

            Spacer(Modifier.height(24.dp))

            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (app.launchIntentAvailable) {
                    TvButton(
                        text = "▶  Launch Application",
                        onClick = onOpen,
                        modifier = Modifier.fillMaxWidth().focusRequester(autoFocus),
                        primary = true
                    )
                }

                TvButton(
                    text = "Open TV App Settings",
                    onClick = onDetails,
                    modifier = Modifier.fillMaxWidth().let {
                        if (!app.launchIntentAvailable) it.focusRequester(autoFocus) else it
                    },
                    primary = false
                )

                if (app.canUninstall) {
                    TvButton(
                        text = "Uninstall Application",
                        onClick = onUninstall,
                        modifier = Modifier.fillMaxWidth(),
                        primary = false
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

@Composable
private fun UninstallConfirmDialog(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(500.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Pal.Surface)
                .border(2.dp, Pal.Coral, RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Uninstall ${app.appName}?", style = Ty.Title.copy(fontSize = 24.sp, color = Pal.Coral))
            Text(
                "Are you sure you want to uninstall this application? This will remove its data from the TV.",
                style = Ty.Body,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(text = "Cancel", onClick = onDismiss, primary = false)
                TvButton(text = "Uninstall", onClick = onConfirm, primary = true, modifier = Modifier.focusRequester(autoFocus))
            }
        }
    }
}
