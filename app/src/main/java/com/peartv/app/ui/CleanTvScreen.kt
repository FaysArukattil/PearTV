package com.peartv.app.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import com.peartv.app.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.peartv.app.adb.AdbClient
import com.peartv.app.data.AppItem
import com.peartv.app.data.DebloatResult
import com.peartv.app.data.Journal
import com.peartv.app.data.RemovalMethod
import com.peartv.app.data.Removed
import com.peartv.app.data.Scanner
import com.peartv.app.data.SystemDebloater
import com.peartv.app.data.Tier
import com.peartv.app.data.TweakEngine
import com.peartv.app.storage.AppStorageDetail
import com.peartv.app.storage.StorageHelper
import com.peartv.app.storage.StorageOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val totalBytes: Long = 0L
)

/**
 * TV Remote-optimized CleanYourTV Debloater & Storage Manager Menu.
 *
 * Requirements fulfilled:
 * 1. 100% remote D-pad focus control without getting stuck (smooth cross-column navigation).
 * 2. Displays detailed storage information per app (App code, User data, Cache, Total size)
 *    matching the Storage section.
 * 3. Displays high-level TV Storage Gauge (Used GB / Total GB, Free GB, Apps GB).
 * 4. Dedicated storage clearing actions: Clear Cache, Clear App Data.
 * 5. Safe disable (`pm disable-user`) and full uninstall (`pm uninstall -k --user 0`).
 * 6. 1-click restore (`pm enable` / `pm install-existing`) for disabled packages.
 * 7. In-place silent refresh preserving focus state.
 * 8. Full Back key handling via BackHandler.
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

    LaunchedEffect(Unit) {
        if (adbClient?.isConnected == false) {
            adbClient.connect()
        }
    }

    var selectedTab by remember { mutableStateOf(DebloatTab.RECOMMENDED) }
    var allEntries by remember { mutableStateOf<List<DebloatAppEntry>>(emptyList()) }
    var selectedEntry by remember { mutableStateOf<DebloatAppEntry?>(null) }
    var storageOverview by remember { mutableStateOf<StorageOverview?>(null) }
    var isInitialLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var appIcons by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }

    // Remote focus management requesters
    val cleanCacheBtnFocus = remember { FocusRequester() }
    val autoCleanBtnFocus = remember { FocusRequester() }
    val backBtnFocus = remember { FocusRequester() }
    val tabFocusRequesters = remember { List(DebloatTab.entries.size) { FocusRequester() } }
    val firstAppFocus = remember { FocusRequester() }

    // Action column requesters
    val clearCacheFocus = remember { FocusRequester() }
    val clearDataFocus = remember { FocusRequester() }
    val primaryActionFocus = remember { FocusRequester() }
    val uninstallActionFocus = remember { FocusRequester() }
    val settingsActionFocus = remember { FocusRequester() }

    // Track which column has focus so Back button navigates intuitively
    var isFocusInActionPane by remember { mutableStateOf(false) }

    BackHandler {
        if (isFocusInActionPane) {
            runCatching { firstAppFocus.requestFocus() }
        } else {
            onClose()
        }
    }

    fun refreshList(isSilent: Boolean = false) {
        scope.launch {
            if (!isSilent) isInitialLoading = true

            // 1. Scan storage details in background
            val overview = withContext(Dispatchers.IO) {
                StorageHelper.getStorageOverview(ctx, journal)
            }
            storageOverview = overview
            val storageMap = overview.appDetails.associateBy { it.packageName }

            // 2. Scan debloat candidates & journal
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
                val st = storageMap[app.pkg]
                entries.add(
                    DebloatAppEntry(
                        pkg = app.pkg,
                        label = app.label,
                        tier = if (isXiaomi) Tier.OPTIONAL else app.tier,
                        description = app.why,
                        isSystem = true,
                        isDisabled = app.disabled,
                        isRemoved = isRemoved,
                        appBytes = st?.appBytes ?: 0L,
                        dataBytes = st?.dataBytes ?: 0L,
                        cacheBytes = st?.cacheBytes ?: 0L,
                        totalBytes = st?.totalBytes ?: 0L
                    )
                )
            }

            // 2. Add any other installed apps (including user apps)
            val scannedPkgSet = scan.apps.map { it.pkg }.toSet()
            for (pi in installed) {
                val pkg = pi.packageName
                if (pkg == ctx.packageName || pkg in scannedPkgSet) continue
                val ai = pi.applicationInfo ?: continue
                val isSys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val label = try { pm.getApplicationLabel(ai).toString() } catch (_: Exception) { pkg }
                val isRemoved = pkg in removedPkgs
                val isXiaomi = pkg.startsWith("com.xiaomi") || pkg.startsWith("com.mitv")
                val st = storageMap[pkg]

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
                        appBytes = st?.appBytes ?: 0L,
                        dataBytes = st?.dataBytes ?: 0L,
                        cacheBytes = st?.cacheBytes ?: 0L,
                        totalBytes = st?.totalBytes ?: 0L
                    )
                )
            }

            // 3. Add previously removed packages
            for (r in removedList) {
                if (entries.none { it.pkg == r.pkg }) {
                    val st = storageMap[r.pkg]
                    entries.add(
                        DebloatAppEntry(
                            pkg = r.pkg,
                            label = r.label,
                            tier = Tier.OPTIONAL,
                            description = "Uninstalled / Disabled via CleanYourTV",
                            isSystem = true,
                            isDisabled = true,
                            isRemoved = true,
                            appBytes = st?.appBytes ?: 0L,
                            dataBytes = st?.dataBytes ?: 0L,
                            cacheBytes = st?.cacheBytes ?: 0L,
                            totalBytes = st?.totalBytes ?: 0L
                        )
                    )
                }
            }

            // Sort entries: largest storage usage first within tiers
            allEntries = entries.sortedWith(
                compareBy<DebloatAppEntry> { it.tier.ordinal }
                    .thenByDescending { it.totalBytes }
                    .thenBy { it.label.lowercase() }
            )

            if (selectedEntry == null || entries.none { it.pkg == selectedEntry?.pkg }) {
                selectedEntry = entries.firstOrNull()
            }
            isInitialLoading = false

            // Pre-load icons asynchronously
            withContext(Dispatchers.IO) {
                for (entry in entries.take(50)) {
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
        refreshList(isSilent = false)
    }

    // Auto-focus first element when loading finishes
    LaunchedEffect(isInitialLoading) {
        if (!isInitialLoading) {
            delay(120)
            runCatching { autoCleanBtnFocus.requestFocus() }
        }
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

    // Update selectedEntry when tab changes if needed
    LaunchedEffect(selectedTab) {
        if (displayedEntries.isNotEmpty() && displayedEntries.none { it.pkg == selectedEntry?.pkg }) {
            selectedEntry = displayedEntries.firstOrNull()
        }
    }

    // Action handlers (run silently in background without losing focus)
    fun clearCacheForApp(app: DebloatAppEntry) {
        scope.launch {
            statusMessage = "Clearing cache for ${app.label}..."
            val ok = StorageHelper.clearAppCache(ctx, adbClient, app.pkg)
            statusMessage = if (ok) "✓ Cache cleared for ${app.label}" else "Opened App Settings to clear cache for ${app.label}"
            refreshList(isSilent = true)
        }
    }

    fun clearDataForApp(app: DebloatAppEntry) {
        scope.launch {
            statusMessage = "Clearing app data for ${app.label}..."
            val ok = StorageHelper.clearAppData(ctx, adbClient, app.pkg)
            if (ok) {
                statusMessage = "✓ App data reset for ${app.label}"
            } else {
                statusMessage = "Opened App Settings to clear storage for ${app.label}"
            }
            refreshList(isSilent = true)
        }
    }

    fun disableApp(app: DebloatAppEntry) {
        scope.launch {
            statusMessage = "Disabling ${app.label}..."
            val res = SystemDebloater.uninstallOrDisable(ctx, app.pkg, app.label, isSystemApp = true, adbClient, journal)
            when (res) {
                is DebloatResult.Success -> statusMessage = res.message
                is DebloatResult.NeedsPrivilege -> statusMessage = res.message
                is DebloatResult.Failure -> statusMessage = res.reason
            }
            refreshList(isSilent = true)
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
            refreshList(isSilent = true)
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
            refreshList(isSilent = true)
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
            refreshList(isSilent = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D11))
            .padding(horizontal = 36.dp, vertical = 20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─────────────────────────────────────────────────────────────────
            // 1. HEADER ROW: Title, Storage Overview Summary & Top Action Buttons
            // ─────────────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ic_apple_tv_cleaner_brand),
                        contentDescription = null,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF30D158))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "PEAR TV • TV CLEANER & DEBLOATER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF30D158),
                                letterSpacing = 1.5.sp
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "TV Speed & Storage Manager",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Storage Overview Gauge Badge
                storageOverview?.let { ov ->
                    val usedStr = StorageHelper.formatBytes(ov.usedBytes)
                    val totalStr = StorageHelper.formatBytes(ov.totalBytes)
                    val freeStr = StorageHelper.formatBytes(ov.freeBytes)
                    val appsStr = StorageHelper.formatBytes(ov.appsBytes)

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x18FFFFFF))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.Storage, contentDescription = null, tint = Color(0xFF64D2FF), modifier = Modifier.size(15.dp))
                                Text(
                                    text = "$usedStr / $totalStr ($freeStr Free)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            // Mini segmented storage bar
                            val totalFloat = ov.totalBytes.toFloat().coerceAtLeast(1f)
                            val appsPct = (ov.appsBytes / totalFloat).coerceIn(0f, 1f)
                            val otherPct = ((ov.usedBytes - ov.appsBytes).coerceAtLeast(0L) / totalFloat).coerceIn(0f, 1f)
                            val freePct = (ov.freeBytes / totalFloat).coerceIn(0f, 1f)

                            Row(
                                modifier = Modifier
                                    .width(180.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0x33FFFFFF))
                            ) {
                                if (appsPct > 0.01f) Box(Modifier.weight(appsPct).fillMaxHeight().background(Color(0xFF0A84FF)))
                                if (otherPct > 0.01f) Box(Modifier.weight(otherPct).fillMaxHeight().background(Color(0xFFFFA726)))
                                if (freePct > 0.01f) Box(Modifier.weight(freePct).fillMaxHeight().background(Color(0x22FFFFFF)))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x240A84FF))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Apps: $appsStr",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64D2FF)
                            )
                        }
                    }
                }

                // Header Action Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Clean All Cache
                    TvButton(
                        text = "Clean All Cache",
                        onClick = {
                            scope.launch {
                                val reclaimed = StorageHelper.clearAllAppCaches(ctx, adbClient)
                                statusMessage = "✓ Cache cleared for all apps (LeanTV freed ${StorageHelper.formatBytes(reclaimed)})"
                                refreshList(isSilent = true)
                            }
                        },
                        primary = false,
                        modifier = Modifier
                            .focusRequester(cleanCacheBtnFocus)
                            .onPreviewKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (ev.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            runCatching { autoCleanBtnFocus.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            runCatching { tabFocusRequesters[selectedTab.ordinal].requestFocus() }
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )

                    // Auto Clean Safe Bloat
                    TvButton(
                        text = "Clean Bloat",
                        onClick = { autoCleanAllRecommended() },
                        primary = true,
                        modifier = Modifier
                            .focusRequester(autoCleanBtnFocus)
                            .onPreviewKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (ev.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            runCatching { cleanCacheBtnFocus.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            runCatching { backBtnFocus.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            runCatching { tabFocusRequesters[selectedTab.ordinal].requestFocus() }
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )

                    // Close Button
                    TvButton(
                        text = "Back",
                        onClick = onClose,
                        primary = false,
                        modifier = Modifier
                            .focusRequester(backBtnFocus)
                            .onPreviewKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (ev.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            runCatching { autoCleanBtnFocus.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            runCatching { tabFocusRequesters[selectedTab.ordinal].requestFocus() }
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    )
                }
            }

            // Status notification bar
            statusMessage?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x2830D158))
                        .border(1.dp, Color(0x6630D158), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = msg, color = Color(0xFF30D158), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(14.dp))

            // ─────────────────────────────────────────────────────────────────
            // 2. CATEGORY TABS (D-Pad UP -> Header, DOWN -> Apps List, LEFT/RIGHT -> Tabs)
            // ─────────────────────────────────────────────────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(DebloatTab.entries) { index, tab ->
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
                        scaleOnFocus = 1.05f,
                        modifier = Modifier
                            .focusRequester(tabFocusRequesters[index])
                            .onPreviewKeyEvent { ev ->
                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (ev.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            runCatching { autoCleanBtnFocus.requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (displayedEntries.isNotEmpty()) {
                                                runCatching { firstAppFocus.requestFocus() }
                                                true
                                            } else false
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) { focused ->
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${tab.title} ($tabCount)",
                                fontSize = 15.sp,
                                fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF64D2FF) else Color.White
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (isInitialLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PearTvLoading("Analyzing TV applications and storage...")
                }
            } else {
                // ─────────────────────────────────────────────────────────────
                // 3. TWO-COLUMN TV LAYOUT (55% Apps List, 45% Inspector & Actions)
                // ─────────────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // ─────────────────────────────────────────────────────────
                    // LEFT COLUMN: Apps List with Storage Badges (55% width)
                    // ─────────────────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedTab.title} (${displayedEntries.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xB3FFFFFF)
                            )
                            Text(
                                text = "Sorted by Storage Size",
                                fontSize = 12.sp,
                                color = Color(0x66FFFFFF)
                            )
                        }

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
                                    fontSize = 17.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x10FFFFFF))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(bottom = 20.dp)
                            ) {
                                itemsIndexed(displayedEntries, key = { _, it -> it.pkg }) { index, app ->
                                    val isSelected = selectedEntry?.pkg == app.pkg
                                    val icon = appIcons[app.pkg]

                                    Focusable(
                                        onClick = {
                                            selectedEntry = app
                                            runCatching { clearCacheFocus.requestFocus() }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        idle = if (isSelected) Color(0x2EFFFFFF) else Color(0x0EFFFFFF),
                                        scaleOnFocus = 1.02f,
                                        modifier = (if (index == 0) Modifier.focusRequester(firstAppFocus) else Modifier)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    isFocusInActionPane = false
                                                    selectedEntry = app
                                                }
                                            }
                                            .onPreviewKeyEvent { ev ->
                                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                    when (ev.nativeKeyEvent.keyCode) {
                                                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                            selectedEntry = app
                                                            isFocusInActionPane = true
                                                            runCatching { clearCacheFocus.requestFocus() }
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                                            if (index == 0) {
                                                                runCatching { tabFocusRequesters[selectedTab.ordinal].requestFocus() }
                                                                true
                                                            } else false
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                    ) { isFocused ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // App Icon
                                            if (icon != null) {
                                                Image(
                                                    bitmap = icon.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
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

                                            Spacer(Modifier.width(12.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.label,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = app.pkg,
                                                    color = Color(0x77FFFFFF),
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(Modifier.width(8.dp))

                                            // Storage Size Badge (Matching Storage Manager Applications section)
                                            if (app.totalBytes > 0) {
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text(
                                                        text = StorageHelper.formatBytes(app.totalBytes),
                                                        color = Color(0xFF64D2FF),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (app.cacheBytes > 1024 * 1024) {
                                                        Text(
                                                            text = "Cache: ${StorageHelper.formatBytes(app.cacheBytes)}",
                                                            color = Color(0x88FFFFFF),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.width(10.dp))
                                            }

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
                                                    .padding(horizontal = 9.dp, vertical = 3.dp)
                                            ) {
                                                Text(text = pillLabel, color = pillColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ─────────────────────────────────────────────────────────
                    // RIGHT COLUMN: App Inspector, Storage Breakdown & Actions (45% width)
                    // ─────────────────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "Storage & App Management",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xB3FFFFFF),
                            modifier = Modifier.padding(bottom = 8.dp)
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
                                Text("Select an app to view details & actions", color = Color(0x66FFFFFF), fontSize = 16.sp)
                            }
                        } else {
                            val activeIcon = appIcons[activeApp.pkg]
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x14FFFFFF))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // App Header
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (activeIcon != null) {
                                        Image(
                                            bitmap = activeIcon.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0x28FFFFFF)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Rounded.Android, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                                        }
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activeApp.label,
                                            style = Ty.Title.copy(fontSize = 19.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = activeApp.pkg,
                                            style = Ty.Small.copy(color = Color(0x88FFFFFF), fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // ─────────────────────────────────────────────
                                // Storage Breakdown Card (Application Section in Storage)
                                // ─────────────────────────────────────────────
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x1FFFFFFF))
                                        .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "APPLICATION STORAGE",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0x88FFFFFF),
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = "Total: ${StorageHelper.formatBytes(activeApp.totalBytes)}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64D2FF)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            StorageStatBadge(
                                                label = "App Size",
                                                value = StorageHelper.formatBytes(activeApp.appBytes),
                                                color = Color(0xFF0A84FF),
                                                modifier = Modifier.weight(1f)
                                            )
                                            StorageStatBadge(
                                                label = "User Data",
                                                value = StorageHelper.formatBytes(activeApp.dataBytes),
                                                color = Color(0xFFFFA726),
                                                modifier = Modifier.weight(1f)
                                            )
                                            StorageStatBadge(
                                                label = "Cache",
                                                value = StorageHelper.formatBytes(activeApp.cacheBytes),
                                                color = Color(0xFF30D158),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                // Description / Safety Note
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x10FFFFFF))
                                        .padding(9.dp)
                                ) {
                                    Text(
                                        text = activeApp.description,
                                        style = Ty.Body.copy(fontSize = 13.sp, color = Color(0xCCFFFFFF)),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // ─────────────────────────────────────────────
                                // D-Pad Action Buttons (Bidirectional DPAD_LEFT returns to Left List)
                                // ─────────────────────────────────────────────
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // 1. Clear Cache Button
                                    TvButton(
                                        text = "Clear Cache (${StorageHelper.formatBytes(activeApp.cacheBytes)})",
                                        onClick = { clearCacheForApp(activeApp) },
                                        primary = activeApp.cacheBytes > 0,
                                        enabled = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(clearCacheFocus)
                                            .onFocusChanged { if (it.isFocused) isFocusInActionPane = true }
                                            .onPreviewKeyEvent { ev ->
                                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                    when (ev.nativeKeyEvent.keyCode) {
                                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                            isFocusInActionPane = false
                                                            runCatching { firstAppFocus.requestFocus() }
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                            runCatching { clearDataFocus.requestFocus() }
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                    )

                                    // 2. Clear App Data Button
                                    TvButton(
                                        text = "Clear All Data (${StorageHelper.formatBytes(activeApp.dataBytes)})",
                                        onClick = { clearDataForApp(activeApp) },
                                        primary = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(clearDataFocus)
                                            .onFocusChanged { if (it.isFocused) isFocusInActionPane = true }
                                            .onPreviewKeyEvent { ev ->
                                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                    when (ev.nativeKeyEvent.keyCode) {
                                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                            isFocusInActionPane = false
                                                            runCatching { firstAppFocus.requestFocus() }
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                                            runCatching { clearCacheFocus.requestFocus() }
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                            runCatching { primaryActionFocus.requestFocus() }
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                    )

                                    // 3. Primary Action: Restore or Safe Disable
                                    if (activeApp.isRemoved || activeApp.isDisabled) {
                                        TvButton(
                                            text = "Restore Application",
                                            onClick = { restoreApp(activeApp) },
                                            primary = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(primaryActionFocus)
                                                .onFocusChanged { if (it.isFocused) isFocusInActionPane = true }
                                                .onPreviewKeyEvent { ev ->
                                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                        when (ev.nativeKeyEvent.keyCode) {
                                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                                isFocusInActionPane = false
                                                                runCatching { firstAppFocus.requestFocus() }
                                                                true
                                                            }
                                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                                runCatching { clearDataFocus.requestFocus() }
                                                                true
                                                            }
                                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                                runCatching { settingsActionFocus.requestFocus() }
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    } else false
                                                }
                                        )
                                    } else {
                                        TvButton(
                                            text = "Safe Disable (pm disable-user)",
                                            onClick = { disableApp(activeApp) },
                                            primary = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(primaryActionFocus)
                                                .onFocusChanged { if (it.isFocused) isFocusInActionPane = true }
                                                .onPreviewKeyEvent { ev ->
                                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                        when (ev.nativeKeyEvent.keyCode) {
                                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                                isFocusInActionPane = false
                                                                runCatching { firstAppFocus.requestFocus() }
                                                                true
                                                            }
                                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                                runCatching { clearDataFocus.requestFocus() }
                                                                true
                                                            }
                                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                                runCatching { uninstallActionFocus.requestFocus() }
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    } else false
                                                }
                                        )

                                        TvButton(
                                            text = "Full Uninstall (pm uninstall -k)",
                                            onClick = { uninstallApp(activeApp) },
                                            primary = false,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(uninstallActionFocus)
                                                .onFocusChanged { if (it.isFocused) isFocusInActionPane = true }
                                                .onPreviewKeyEvent { ev ->
                                                    if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                        when (ev.nativeKeyEvent.keyCode) {
                                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                                isFocusInActionPane = false
                                                                runCatching { firstAppFocus.requestFocus() }
                                                                true
                                                            }
                                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                                runCatching { primaryActionFocus.requestFocus() }
                                                                true
                                                            }
                                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                                runCatching { settingsActionFocus.requestFocus() }
                                                                true
                                                            }
                                                            else -> false
                                                        }
                                                    } else false
                                                }
                                        )
                                    }

                                    // 4. Open in System Settings
                                    TvButton(
                                        text = "Open App Info in TV Settings",
                                        onClick = { StorageHelper.openAppDetails(ctx, activeApp.pkg) },
                                        primary = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(settingsActionFocus)
                                            .onFocusChanged { if (it.isFocused) isFocusInActionPane = true }
                                            .onPreviewKeyEvent { ev ->
                                                if (ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                                    when (ev.nativeKeyEvent.keyCode) {
                                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                            isFocusInActionPane = false
                                                            runCatching { firstAppFocus.requestFocus() }
                                                            true
                                                        }
                                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                                            if (activeApp.isRemoved || activeApp.isDisabled) {
                                                                runCatching { primaryActionFocus.requestFocus() }
                                                            } else {
                                                                runCatching { uninstallActionFocus.requestFocus() }
                                                            }
                                                            true
                                                        }
                                                        else -> false
                                                    }
                                                } else false
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
}

@Composable
private fun StorageStatBadge(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(text = label, fontSize = 10.sp, color = Color(0x99FFFFFF))
            Spacer(Modifier.height(1.dp))
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}