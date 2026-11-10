package com.leantv.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
        // Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("Installed Apps", style = Ty.Display)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${displayedApps.size} apps shown (${s.installedApps.size} total installed)",
                    style = Ty.Dim
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    text = if (s.installedAppsLoading) "Refreshing..." else "Refresh List",
                    onClick = { vm.loadInstalledApps(forceRefresh = true) },
                    primary = false,
                    enabled = !s.installedAppsLoading
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Filter and Sort row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Filter:", style = Ty.Small.copy(color = Pal.Dim))
            AppTypeFilter.entries.forEach { f ->
                Chip(
                    text = f.label,
                    selected = s.appTypeFilter == f,
                    onClick = { vm.setAppTypeFilter(f) }
                )
            }

            Spacer(Modifier.width(16.dp))

            Text("Sort:", style = Ty.Small.copy(color = Pal.Dim))
            AppSortOrder.entries.forEach { so ->
                Chip(
                    text = so.label,
                    selected = s.appSortOrder == so,
                    onClick = { vm.setAppSortOrder(so) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Search Bar row
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
                Focusable(
                    onClick = { vm.setAppSearchQuery("") },
                    shape = RoundedCornerShape(8.dp),
                    idle = Pal.SurfaceHi,
                    scaleOnFocus = 1.05f
                ) {
                    Text("Clear", style = Ty.Small.copy(color = Pal.Coral), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // App List
        if (s.installedAppsLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Spinner()
                    Text("Loading installed packages...", style = Ty.Heading)
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
                        onOpen = { vm.launchApp(app.packageName) },
                        onUninstall = { uninstallConfirmApp = app },
                        onDetails = { vm.openAppDetails(app.packageName) },
                        onClick = { actionSheetApp = app }
                    )
                }
            }
        }
    }

    // Quick Action Dialog for selected app
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

    // Remote-safe uninstallation confirmation dialog
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

@Composable
private fun InstalledAppCard(
    app: InstalledAppInfo,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onDetails: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    var iconBitmap by remember(app.packageName) { mutableStateOf(IconCache.get(app.packageName)) }

    LaunchedEffect(app.packageName) {
        if (iconBitmap == null) {
            iconBitmap = IconCache.loadIcon(context, app.packageName)
        }
    }

    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        scaleOnFocus = 1.02f,
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Icon
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Pal.SurfaceHi),
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
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                    )

                    // Type tag
                    val (tagText, tagColor) = when {
                        !app.isSystemApp -> "User" to Pal.Mint
                        app.isUpdatedSystemApp -> "Updated System" to Pal.Sky
                        else -> "System" to Pal.Dim
                    }
                    Text(
                        tagText,
                        style = Ty.Small.copy(color = tagColor, fontSize = 13.sp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tagColor.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    app.packageName,
                    style = Ty.Small.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                    maxLines = 1
                )

                Spacer(Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Ver: ${app.versionName}", style = Ty.Small.copy(fontSize = 13.sp))
                    Text("•", style = Ty.Small.copy(fontSize = 13.sp))
                    Text("Size: ${app.formattedSize}", style = Ty.Small.copy(fontSize = 13.sp))
                    Text("•", style = Ty.Small.copy(fontSize = 13.sp))
                    Text("Installed: ${app.formattedInstallDate}", style = Ty.Small.copy(fontSize = 13.sp))
                }
            }

            // Action hints when focused
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.launchIntentAvailable) {
                    TvButton(
                        text = "Open",
                        onClick = onOpen,
                        primary = true,
                    )
                }
                if (app.canUninstall) {
                    TvButton(
                        text = "Uninstall",
                        onClick = onUninstall,
                        primary = false,
                    )
                } else {
                    TvButton(
                        text = "Details",
                        onClick = onDetails,
                        primary = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppActionDialog(
    app: InstalledAppInfo,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onDetails: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14)),
        contentAlignment = Alignment.Center,
    ) {
        val cancelFocus = rememberAutoFocus(app.packageName)
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Pal.Surface)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(app.appName, style = Ty.Title)
            Text(app.packageName, style = Ty.Small.copy(fontFamily = FontFamily.Monospace))
            Text(
                "Version ${app.versionName} • ${app.formattedSize} • ${if (app.isSystemApp) "System app" else "User app"}",
                style = Ty.Dim
            )

            Spacer(Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (app.launchIntentAvailable) {
                    TvButton("Launch Application", onClick = onOpen, primary = true, modifier = Modifier.fillMaxWidth())
                }
                if (app.canUninstall) {
                    TvButton("Uninstall Application", onClick = onUninstall, primary = false, modifier = Modifier.fillMaxWidth())
                }
                TvButton("System App Info", onClick = onDetails, primary = false, modifier = Modifier.fillMaxWidth())
                TvButton("Close", onClick = onDismiss, primary = false, modifier = Modifier.fillMaxWidth().focusRequester(cancelFocus))
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14)),
        contentAlignment = Alignment.Center,
    ) {
        val cancelFocus = rememberAutoFocus(app.packageName)
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Pal.Surface)
                .padding(36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Uninstall ${app.appName}?", style = Ty.Title)
            Text(
                "LeanTV will request the Android TV package installer to remove ${app.packageName}.\n\nYou can confirm the removal on the next system prompt.",
                style = Ty.Dim
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    "Cancel",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.focusRequester(cancelFocus)
                )
                TvButton("Proceed with Uninstall", onClick = onConfirm, primary = true)
            }
        }
    }
}

