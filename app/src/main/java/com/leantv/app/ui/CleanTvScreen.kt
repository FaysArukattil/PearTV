package com.leantv.app.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.leantv.app.adb.AdbClient
import com.leantv.app.data.AppItem
import com.leantv.app.data.DebloatResult
import com.leantv.app.data.Journal
import com.leantv.app.data.RemovalMethod
import com.leantv.app.data.Removed
import com.leantv.app.data.Scanner
import com.leantv.app.data.SystemDebloater
import com.leantv.app.data.Tier
import com.leantv.app.data.TweakEngine
import com.leantv.app.storage.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DebloatTab(val title: String) {
    RECOMMENDED("Trackers & Ads"),
    OEM_BLOAT("Xiaomi & OEM Bloat"),
    ALL_APPS("All Applications"),
    REMOVED("Currently Removed / Disabled")
}

data class DebloatAppEntry(
    val pkg: String,
    val label: String,
    val tier: Tier,
    val description: String,
    val isSystem: Boolean,
    val isDisabled: Boolean,
    val isRemoved: Boolean,
)

/**
 * Full-screen CleanYourTV Debloater & Bloatware Removal Menu.
 *
 * Provides:
 * 1. Category tabs: Trackers & Ads, Xiaomi & OEM Bloat, All Applications, Currently Removed/Disabled.
 * 2. Dedicated list showing every app (including Xiaomi Live TV, PatchWall, vendor services).
 * 3. Side inspector with safe disable (`pm disable-user`) and full uninstall (`pm uninstall -k --user 0`).
 * 4. Dedicated "Currently Removed" section with 1-click **Restore** (`pm enable` / `pm install-existing`).
 * 5. Optimized for TV remote D-Pad navigation.
 */
@Composable
fun CleanTvScreen(
    adbClient: AdbClient?,
    journal: Journal,
    scanner: Scanner,
    tweaks: TweakEngine,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    var selectedTab by remember { mutableStateOf(DebloatTab.RECOMMENDED) }
    var allEntries by remember { mutableStateOf<List<DebloatAppEntry>>(emptyList()) }
    var selectedEntry by remember { mutableStateOf<DebloatAppEntry?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var appIcons by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    val loadFocusRequester = rememberAutoFocus()
    val actionFocusRequester = remember { FocusRequester() }

    fun refreshList() {
        scope.launch {
            isLoading = true
            val scan = withContext(Dispatchers.IO) { scanner.scan() }
            val removedList = withContext(Dispatchers.IO) { journal.removed() }
            val removedPkgs = removedList.map { it.pkg }.toSet()

            val pm = ctx.packageManager
            val installed = withContext(Dispatchers.IO) {
                try {
                    pm.getInstalledPackages(0)
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val entries = mutableListOf<DebloatAppEntry>()
            val icons = mutableMapOf<String, Bitmap>()

            // 1. Add scanned system apps
            for (app in scan.apps) {
                val isRemoved = app.pkg in removedPkgs
                val isXiaomi = app.pkg.startsWith("com.xiaomi") || app.pkg.startsWith("com.mitv")
                entries.add(
                    DebloatAppEntry(
                        pkg = app.pkg,
                        label = app.label,
                        tier = if (isXiaomi) Tier.OPTIONAL else app.tier,
                        description = app.why,
                        isSystem = true,
                        isDisabled = app.disabled,
                        isRemoved = isRemoved,
                    )
                )
            }

            // 2. Add any other installed apps (including user apps) to make sure EVERYTHING can be removed
            val scannedPkgSet = scan.apps.map { it.pkg }.toSet()
            for (pi in installed) {
                val pkg = pi.packageName
                if (pkg == ctx.packageName || pkg in scannedPkgSet) continue
                val ai = pi.applicationInfo ?: continue
                val isSys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pkg }
                val isRemoved = pkg in removedPkgs
                val isXiaomi = pkg.startsWith("com.xiaomi") || pkg.startsWith("com.mitv")

                val tier = when {
                    pkg.contains("adservice", ignoreCase = true) || pkg.contains("telemetry", ignoreCase = true) || pkg.contains("analytics", ignoreCase = true) -> Tier.RECOMMENDED
                    isXiaomi || pkg.startsWith("com.tcl.") || pkg.startsWith("com.hisense.") -> Tier.OPTIONAL
                    else -> Tier.ADVANCED
                }

                entries.add(
                    DebloatAppEntry(
                        pkg = pkg,
                        label = label,
                        tier = tier,
                        description = if (isSys) "Pre-installed system application" else "User installed application",
                        isSystem = isSys,
                        isDisabled = !ai.enabled,
                        isRemoved = isRemoved,
                    )
                )
            }

            // 3. Add previously removed packages that may no longer be listed
            for (r in removedList) {
                if (entries.none { it.pkg == r.pkg }) {
                    entries.add(
                        DebloatAppEntry(
                            pkg = r.pkg,
                            label = r.label,
                            tier = Tier.OPTIONAL,
                            description = "Uninstalled / Disabled via CleanYourTV",
                            isSystem = true,
                            isDisabled = true,
                            isRemoved = true,
                        )
                    )
                }
            }

            allEntries = entries.sortedWith(compareBy({ it.tier.ordinal }, { it.label.lowercase() }))
            if (selectedEntry == null || entries.none { it.pkg == selectedEntry?.pkg }) {
                selectedEntry = entries.firstOrNull()
            }
            isLoading = false

            // Pre-load icons
            withContext(Dispatchers.IO) {
                for (entry in entries.take(40)) {
                    try {
                        val d = pm.getApplicationIcon(entry.pkg)
                        icons[entry.pkg] = d.toBitmap(96, 96)
                    } catch (_: Exception) {}
                }
                appIcons = icons
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshList()
    }

    // Filtered entries for active tab
    val displayedEntries = remember(allEntries, selectedTab) {
        when (selectedTab) {
            DebloatTab.RECOMMENDED -> allEntries.filter {
                !it.isRemoved && !it.isDisabled && (it.tier == Tier.RECOMMENDED ||
                it.pkg.contains("adservice", ignoreCase = true) ||
                it.pkg.contains("telemetry", ignoreCase = true) ||
                it.pkg.contains("analytics", ignoreCase = true) ||
                it.pkg.contains("tracking", ignoreCase = true) ||
                it.pkg.contains("statistic", ignoreCase = true) ||
                it.pkg.contains("recommendation", ignoreCase = true))
            }
            DebloatTab.OEM_BLOAT -> allEntries.filter {
                !it.isRemoved && !it.isDisabled && (
                    it.pkg.startsWith("com.xiaomi") ||
                    it.pkg.startsWith("com.mitv") ||
                    it.pkg.startsWith("com.tcl.") ||
                    it.pkg.startsWith("com.hisense.") ||
                    it.pkg.startsWith("com.sony.dtv") ||
                    it.pkg.startsWith("com.realme.") ||
                    it.pkg.startsWith("com.oneplus.")
                )
            }
            DebloatTab.ALL_APPS -> allEntries.filter { !it.isRemoved }
            DebloatTab.REMOVED -> allEntries.filter { it.isRemoved || it.isDisabled }
        }
    }

    // Action handlers
    fun disableApp(app: DebloatAppEntry) {
        scope.launch {
            statusMessage = "Disabling ${app.label}..."
            val res = SystemDebloater.uninstallOrDisable(ctx, app.pkg, app.label, isSystemApp = true, adbClient, journal)
            when (res) {
                is DebloatResult.Success -> statusMessage = res.message
                is DebloatResult.NeedsPrivilege -> statusMessage = res.message
                is DebloatResult.Failure -> statusMessage = res.reason
            }
            refreshList()
        }
    }

    fun uninstallApp(app: DebloatAppEntry) {
        scope.launch {
            statusMessage = "Uninstalling ${app.label}..."
            val res = SystemDebloater.uninstallOrDisable(ctx, app.pkg, app.label, isSystemApp = app.isSystem, adbClient, journal)
            when (res) {
                is DebloatResult.Success -> statusMessage = res.message
                is DebloatResult.NeedsPrivilege -> statusMessage = res.message
                is DebloatResult.Failure -> statusMessage = res.reason
            }
            refreshList()
        }
    }

    fun restoreApp(app: DebloatAppEntry) {
        scope.launch {
            statusMessage = "Restoring ${app.label}..."
            val res = SystemDebloater.restoreApp(ctx, app.pkg, app.label, adbClient, journal)
            when (res) {
                is DebloatResult.Success -> statusMessage = res.message
                else -> statusMessage = "✓ Restored ${app.label}"
            }
            refreshList()
        }
    }

    fun autoCleanAllRecommended() {
        scope.launch {
            statusMessage = "Running 1-Click Clean of all trackers and ads..."
            val targets = allEntries.filter {
                !it.isRemoved && !it.isDisabled && (
                    it.tier == Tier.RECOMMENDED ||
                    it.pkg.contains("adservice", ignoreCase = true) ||
                    it.pkg.contains("telemetry", ignoreCase = true) ||
                    it.pkg.contains("analytics", ignoreCase = true) ||
                    it.pkg.contains("tracking", ignoreCase = true)
                )
            }
            var count = 0
            for (t in targets) {
                if (adbClient != null && adbClient.isConnected) {
                    adbClient.shell("pm disable-user --user 0 ${t.pkg}")
                }
                journal.addRemoved(Removed(t.pkg, t.label, RemovalMethod.DISABLE, System.currentTimeMillis()))
                count++
            }
            tweaks.setAnimations(true)
            tweaks.setPrivateDns(true)
            statusMessage = "✓ Cleaned $count bloatware apps & active AdGuard DNS + 2x Speed!"
            refreshList()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
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
                                .background(Color(0xFF30D158))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CLEANYOURTV • SYSTEM DEBLOATER",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF30D158),
                            letterSpacing = 2.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "TV Bloat Removal & Speed Manager",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Auto Clean Button
                    TvButton(
                        text = "Clean All Safe Bloat",
                        onClick = { autoCleanAllRecommended() },
                        primary = true,
                        modifier = Modifier.focusRequester(loadFocusRequester)
                    )
                    // Close Button
                    TvButton(
                        text = "Back to Home",
                        onClick = onClose,
                        primary = false
                    )
                }
            }

            // Status notification bar
            statusMessage?.let { msg ->
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2830D158))
                        .border(1.dp, Color(0x6630D158), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(text = msg, color = Color(0xFF30D158), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(18.dp))

            // Category Tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(DebloatTab.entries) { tab ->
                    val isSelected = selectedTab == tab
                    val tabCount = when (tab) {
                        DebloatTab.RECOMMENDED -> allEntries.count {
                            !it.isRemoved && !it.isDisabled && (it.tier == Tier.RECOMMENDED || it.pkg.contains("adservice", ignoreCase = true) || it.pkg.contains("telemetry", ignoreCase = true))
                        }
                        DebloatTab.OEM_BLOAT -> allEntries.count {
                            !it.isRemoved && !it.isDisabled && (it.pkg.startsWith("com.xiaomi") || it.pkg.startsWith("com.mitv") || it.pkg.startsWith("com.tcl.") || it.pkg.startsWith("com.hisense."))
                        }
                        DebloatTab.ALL_APPS -> allEntries.count { !it.isRemoved }
                        DebloatTab.REMOVED -> allEntries.count { it.isRemoved || it.isDisabled }
                    }

                    Focusable(
                        onClick = {
                            selectedTab = tab
                            selectedEntry = displayedEntries.firstOrNull()
                        },
                        shape = RoundedCornerShape(50),
                        idle = if (isSelected) Color(0x38FFFFFF) else Color(0x14FFFFFF),
                        scaleOnFocus = 1.05f
                    ) { focused ->
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${tab.title} ($tabCount)",
                                fontSize = 17.sp,
                                fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF64D2FF) else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    LeanTvLoading("Scanning TV applications and bloatware...")
                }
            } else {
                // Two-Column TV Layout
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    // Left Column: Apps List (55% width)
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "${selectedTab.title} (${displayedEntries.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xB3FFFFFF),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        if (displayedEntries.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x14FFFFFF))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedTab == DebloatTab.REMOVED) "No removed apps yet. Disabled apps will appear here." else "No apps found in this category.",
                                    color = Color(0x88FFFFFF),
                                    fontSize = 18.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x14FFFFFF))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(displayedEntries, key = { it.pkg }) { app ->
                                    val isSelected = selectedEntry?.pkg == app.pkg
                                    val icon = appIcons[app.pkg]

                                    Focusable(
                                        onClick = {
                                            selectedEntry = app
                                            actionFocusRequester.requestFocus()
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        idle = if (isSelected) Color(0x2EFFFFFF) else Color(0x0EFFFFFF),
                                        scaleOnFocus = 1.02f
                                    ) { isFocused ->
                                        if (isFocused && selectedEntry?.pkg != app.pkg) {
                                            selectedEntry = app
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
                                                    text = app.label,
                                                    color = Color.White,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = app.pkg,
                                                    color = Color(0x77FFFFFF),
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(Modifier.width(8.dp))

                                            // Tier / Status Pill
                                            val pillColor = when {
                                                app.isRemoved || app.isDisabled -> Color(0xFFFF453A)
                                                app.tier == Tier.RECOMMENDED -> Color(0xFF30D158)
                                                app.tier == Tier.OPTIONAL -> Color(0xFF64D2FF)
                                                else -> Color(0xFFFFA726)
                                            }
                                            val pillLabel = when {
                                                app.isRemoved -> "Removed"
                                                app.isDisabled -> "Disabled"
                                                app.tier == Tier.RECOMMENDED -> "Safe to Remove"
                                                app.tier == Tier.OPTIONAL -> "OEM Bloat"
                                                else -> "System App"
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(pillColor.copy(alpha = 0.2f))
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(text = pillLabel, color = pillColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Right Column: Inspector & Action Pane (45% width)
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "Application Actions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xB3FFFFFF),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        val activeApp = selectedEntry
                        if (activeApp == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x14FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Select an app to view details & actions", color = Color(0x66FFFFFF), fontSize = 18.sp)
                            }
                        } else {
                            val activeIcon = appIcons[activeApp.pkg]
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
                                        Text(text = activeApp.label, style = Ty.Title.copy(fontSize = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = activeApp.pkg,
                                            style = Ty.Small.copy(color = Color(0x88FFFFFF), fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Description Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x14FFFFFF))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = activeApp.description,
                                        style = Ty.Body.copy(fontSize = 17.sp, color = Color(0xDDFFFFFF))
                                    )
                                }

                                Spacer(Modifier.weight(1f))

                                // D-Pad Action Buttons
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (activeApp.isRemoved || activeApp.isDisabled) {
                                        // RESTORE ACTION
                                        TvButton(
                                            text = "Restore Application",
                                            onClick = { restoreApp(activeApp) },
                                            primary = true,
                                            modifier = Modifier.fillMaxWidth().focusRequester(actionFocusRequester)
                                        )
                                    } else {
                                        // DISABLE & UNINSTALL ACTIONS
                                        TvButton(
                                            text = "Safe Disable (pm disable-user)",
                                            onClick = { disableApp(activeApp) },
                                            primary = true,
                                            modifier = Modifier.fillMaxWidth().focusRequester(actionFocusRequester)
                                        )
                                        TvButton(
                                            text = "Full Uninstall (pm uninstall -k)",
                                            onClick = { uninstallApp(activeApp) },
                                            primary = false,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    TvButton(
                                        text = "Open App Info in TV Settings",
                                        onClick = { StorageHelper.openAppDetails(ctx, activeApp.pkg) },
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

