package com.peartv.app.ui

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.peartv.app.adb.AdbClient
import com.peartv.app.data.AppClassifier
import com.peartv.app.data.AppGroup
import com.peartv.app.data.ClassifiedApp
import com.peartv.app.data.DebloatResult
import com.peartv.app.data.Journal
import com.peartv.app.data.Scanner
import com.peartv.app.data.SystemDebloater
import com.peartv.app.data.Tier
import com.peartv.app.storage.AppStorageDetail
import com.peartv.app.storage.StorageHelper
import com.peartv.app.storage.StorageOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class AppAction(
    val id: String,
    val text: String,
    val primary: Boolean,
    val destructive: Boolean,
    val run: () -> Unit,
)

private fun groupColor(g: AppGroup): Color = when (g) {
    AppGroup.INSTALLED -> Color(0xFF3B82F6)
    AppGroup.SYSTEM -> Color(0xFF30D158)
    AppGroup.BLOAT -> Color(0xFFFFA726)
    AppGroup.REMOVED -> Color(0xFFBF5AF2)
}

private fun tierLabel(t: Tier?): String = when (t) {
    Tier.RECOMMENDED -> "Safe to remove"
    Tier.OPTIONAL -> "Optional"
    Tier.ADVANCED -> "Review first"
    null -> ""
}

private fun appTierColor(t: Tier?): Color = when (t) {
    Tier.RECOMMENDED -> Color(0xFF30D158)
    Tier.OPTIONAL -> Color(0xFFFFA726)
    Tier.ADVANCED -> Color(0xFFFF453A)
    null -> Color.White
}

private fun describe(r: DebloatResult): String = when (r) {
    is DebloatResult.Success -> r.message
    is DebloatResult.NeedsPrivilege -> r.message
    is DebloatResult.Failure -> r.reason
}

/** D-pad helper: [handler] gets the key code on ACTION_DOWN and returns true if it consumed the key. */
private fun Modifier.dpad(handler: (Int) -> Boolean): Modifier = this.onPreviewKeyEvent { ev ->
    ev.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && handler(ev.nativeKeyEvent.keyCode)
}

/**
 * Storage -> Apps. CleanTV lives here now.
 *
 * Tabs:  Installed | System (protected) | Bloat (removable) | Removed (restorable)
 *
 * Remote-control rules (nothing can trap the cursor):
 *  - LEFT/RIGHT inside the list or tab row never leaves the screen; RIGHT jumps into the side panel.
 *  - LEFT / BACK from the side panel returns to the SAME app in the list.
 *  - UP from the first item goes to the tab row, UP from the tab row goes to Back.
 *  - Destructive buttons need a second OK press (auto-cancels when focus moves away).
 */
@Composable
fun StorageAppsView(
    overview: StorageOverview,
    adbClient: AdbClient?,
    journal: Journal,
    scanner: Scanner,
    initialGroup: AppGroup = AppGroup.INSTALLED,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var group by remember { mutableStateOf(initialGroup) }
    var apps by remember { mutableStateOf<List<ClassifiedApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmId by remember { mutableStateOf<String?>(null) }
    var inActionPane by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableStateOf(0) }
    val icons = remember { mutableStateMapOf<String, Bitmap>() }
    val listState = rememberLazyListState()

    val backFocus = remember { FocusRequester() }
    val tabFocus = remember { List(AppGroup.entries.size) { FocusRequester() } }
    val actionFocus = remember { List(6) { FocusRequester() } }
    val itemFocus = remember { HashMap<String, FocusRequester>() }
    fun itemRequester(pkg: String): FocusRequester = itemFocus.getOrPut(pkg) { FocusRequester() }

    // ---------- data ----------
    suspend fun load(storage: Map<String, AppStorageDetail>) {
        apps = AppClassifier.classify(ctx, journal, scanner, storage)
        loading = false
    }

    suspend fun reloadFresh() {
        val st = withContext(Dispatchers.IO) {
            StorageHelper.getStorageOverview(ctx, journal).appDetails.associateBy { it.packageName }
        }
        load(st)
    }

    LaunchedEffect(Unit) { load(overview.appDetails.associateBy { it.packageName }) }

    // Refresh when the user comes back from App Info / the system uninstall dialog.
    DisposableEffect(ctx) {
        val owner = ctx as? LifecycleOwner
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    LaunchedEffect(resumeTick) { if (resumeTick > 1 && !loading) reloadFresh() }

    LaunchedEffect(status) {
        if (status != null) {
            delay(5000)
            status = null
        }
    }

    // ---------- derived ----------
    val counts = remember(apps) { apps.groupingBy { it.group }.eachCount() }
    val shown = remember(apps, group) {
        val l = apps.filter { it.group == group }
        when (group) {
            AppGroup.INSTALLED -> l.sortedByDescending { it.totalBytes }
            AppGroup.SYSTEM -> l.sortedBy { it.label.lowercase() }
            AppGroup.BLOAT -> l.sortedWith(
                compareBy<ClassifiedApp> { it.tier?.ordinal ?: 9 }
                    .thenByDescending { it.totalBytes }
                    .thenBy { it.label.lowercase() }
            )
            AppGroup.REMOVED -> l.sortedBy { it.label.lowercase() }
        }
    }
    val shownNow by rememberUpdatedState(shown)
    val selected: ClassifiedApp? = shown.firstOrNull { it.pkg == selectedPkg } ?: shown.firstOrNull()

    LaunchedEffect(shown) {
        withContext(Dispatchers.IO) {
            for (a in shown.take(80)) {
                if (a.pkg in icons) continue
                runCatching { icons[a.pkg] = ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(96, 96) }
            }
        }
    }
    LaunchedEffect(selected?.pkg, group) { confirmId = null }

    // ---------- focus helpers ----------
    fun focusListItem(index: Int) {
        scope.launch {
            val list = shownNow
            if (list.isEmpty()) {
                runCatching { tabFocus[group.ordinal].requestFocus() }
                return@launch
            }
            val i = index.coerceIn(0, list.lastIndex)
            runCatching { listState.scrollToItem(i) }
            delay(60)
            val l2 = shownNow
            if (l2.isNotEmpty()) runCatching { itemRequester(l2[i.coerceAtMost(l2.lastIndex)].pkg).requestFocus() }
        }
    }

    fun focusSelectedInList() {
        val idx = shownNow.indexOfFirst { it.pkg == selected?.pkg }.coerceAtLeast(0)
        focusListItem(idx)
    }

    fun focusActions() {
        runCatching { actionFocus[0].requestFocus() }
    }

    LaunchedEffect(loading) {
        if (!loading) {
            delay(150)
            focusListItem(0)
        }
    }

    StorageBackHandler {
        if (inActionPane) focusSelectedInList() else onBack()
        true
    }

    // ---------- actions ----------
    fun runAction(returnToList: Boolean, block: suspend () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            val idx = shownNow.indexOfFirst { it.pkg == selected?.pkg }.coerceAtLeast(0)
            status = try {
                block()
            } catch (e: Exception) {
                "Failed: ${e.message}"
            }
            confirmId = null
            reloadFresh()
            busy = false
            if (returnToList) {
                delay(120)
                focusListItem(idx)
            }
        }
    }

    suspend fun clearCache(): String {
        if (adbClient != null) {
            if (!adbClient.isConnected) adbClient.connect()
            if (adbClient.isConnected) {
                adbClient.shell("pm trim-caches 9999999999")
                return "✓ Cache cleared"
            }
        }
        StorageHelper.cleanAllAccessibleCaches(ctx)
        return "Cleaned what Android allows. Enable Wireless Debugging in TV Settings to clear every app's cache."
    }

    val actions: List<AppAction> = selected?.let { app ->
        buildList<AppAction> {
            when (app.group) {
                AppGroup.INSTALLED -> {
                    add(AppAction("cache", "Clear Cache (${StorageHelper.formatBytes(app.cacheBytes)})", true, false) {
                        runAction(false) { clearCache() }
                    })
                    add(AppAction("data", "Clear App Data", false, true) {
                        runAction(false) {
                            if (StorageHelper.clearAppData(ctx, adbClient, app.pkg)) "✓ Cleared data for ${app.label}"
                            else "Opened App Settings"
                        }
                    })
                    add(AppAction("remove", "Uninstall", false, true) {
                        runAction(true) {
                            describe(SystemDebloater.uninstallOrDisable(ctx, app.pkg, app.label, app.isSystemFlag, adbClient, journal))
                        }
                    })
                    add(AppAction("info", "App Info in TV Settings", false, false) {
                        StorageHelper.openAppDetails(ctx, app.pkg)
                    })
                }
                AppGroup.SYSTEM -> {
                    add(AppAction("cache", "Clear Cache (${StorageHelper.formatBytes(app.cacheBytes)})", true, false) {
                        runAction(false) { clearCache() }
                    })
                    add(AppAction("info", "App Info in TV Settings", false, false) {
                        StorageHelper.openAppDetails(ctx, app.pkg)
                    })
                }
                AppGroup.BLOAT -> {
                    add(AppAction("remove", "Remove (can be restored)", true, true) {
                        runAction(true) {
                            describe(SystemDebloater.uninstallOrDisable(ctx, app.pkg, app.label, app.isSystemFlag, adbClient, journal))
                        }
                    })
                    add(AppAction("cache", "Clear Cache (${StorageHelper.formatBytes(app.cacheBytes)})", false, false) {
                        runAction(false) { clearCache() }
                    })
                    add(AppAction("info", "App Info in TV Settings", false, false) {
                        StorageHelper.openAppDetails(ctx, app.pkg)
                    })
                }
                AppGroup.REMOVED -> {
                    add(AppAction("restore", "Restore", true, false) {
                        runAction(true) {
                            describe(SystemDebloater.restoreApp(ctx, app.pkg, app.label, adbClient, journal))
                        }
                    })
                    if (app.installed) {
                        add(AppAction("info", "App Info in TV Settings", false, false) {
                            StorageHelper.openAppDetails(ctx, app.pkg)
                        })
                    }
                }
            }
        }
    } ?: emptyList()

    // ======================================================================
    // UI
    // ======================================================================
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PearTvLoading("Sorting your apps...")
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Focusable(
                onClick = onBack,
                modifier = Modifier
                    .focusRequester(backFocus)
                    .dpad { key ->
                        when (key) {
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                runCatching { tabFocus[group.ordinal].requestFocus() }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP -> true
                            else -> false
                        }
                    },
                shape = RoundedCornerShape(14.dp),
                idle = Color(0x22FFFFFF)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Back to Storage", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text("Apps", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "${apps.size} apps • ${StorageHelper.formatBytes(overview.appsBytes)} used • CleanTV built in",
                    fontSize = 14.sp, color = Color(0xAAFFFFFF)
                )
            }
        }

        // ---- Status banner ----
        status?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x334CAF50))
                    .border(1.dp, Color(0x664CAF50), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(msg, color = Color(0xFF30D158), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---- Tabs ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AppGroup.entries.forEachIndexed { i, g ->
                val c = groupColor(g)
                Focusable(
                    onClick = { focusListItem(0) },
                    modifier = Modifier
                        .focusRequester(tabFocus[i])
                        .dpad { key ->
                            when (key) {
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    if (i > 0) runCatching { tabFocus[i - 1].requestFocus() }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (i < tabFocus.lastIndex) runCatching { tabFocus[i + 1].requestFocus() }
                                    else if (actions.isNotEmpty()) focusActions()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (shownNow.isNotEmpty()) focusListItem(0)
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    runCatching { backFocus.requestFocus() }
                                    true
                                }
                                else -> false
                            }
                        },
                    shape = RoundedCornerShape(50),
                    idle = if (group == g) Color(0x33FFFFFF) else Color(0x14FFFFFF),
                    scaleOnFocus = 1.05f
                ) { focused ->
                    // Switching tabs follows focus: no extra OK press needed.
                    LaunchedEffect(focused) {
                        if (focused && group != g) {
                            group = g
                            selectedPkg = null
                        }
                    }
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${g.title}  ${counts[g] ?: 0}",
                            color = Color.White, fontSize = 16.sp,
                            fontWeight = if (group == g) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---- Two panels ----
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // LEFT: list
            Box(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x0EFFFFFF))
            ) {
                if (shown.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF30D158), modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (group) {
                                AppGroup.INSTALLED -> "No user-installed apps"
                                AppGroup.SYSTEM -> "No system apps found"
                                AppGroup.BLOAT -> "No bloat left - your TV is clean"
                                AppGroup.REMOVED -> "Nothing removed yet"
                            },
                            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(shown, key = { _, it -> it.pkg }) { index, app ->
                            val icon = icons[app.pkg]
                            Focusable(
                                onClick = { focusActions() },
                                shape = RoundedCornerShape(14.dp),
                                idle = if (selected?.pkg == app.pkg) Color(0x2EFFFFFF) else Color(0x0EFFFFFF),
                                scaleOnFocus = 1.02f,
                                modifier = Modifier
                                    .focusRequester(itemRequester(app.pkg))
                                    .dpad { key ->
                                        when (key) {
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                if (actions.isNotEmpty()) focusActions()
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_LEFT -> true
                                            KeyEvent.KEYCODE_DPAD_UP -> if (index == 0) {
                                                runCatching { tabFocus[group.ordinal].requestFocus() }
                                                true
                                            } else false
                                            KeyEvent.KEYCODE_DPAD_DOWN -> index == shownNow.lastIndex
                                            else -> false
                                        }
                                    }
                            ) { focused ->
                                LaunchedEffect(focused) { if (focused) selectedPkg = app.pkg }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconBox(icon, 44)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(app.pkg, color = Color(0x77FFFFFF), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    if (app.group == AppGroup.BLOAT) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(appTierColor(app.tier)))
                                        Spacer(Modifier.width(10.dp))
                                    }
                                    if (app.group == AppGroup.SYSTEM) {
                                        Icon(Icons.Rounded.Lock, contentDescription = "Protected", tint = Color(0xFF30D158), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    if (app.group != AppGroup.REMOVED) {
                                        Text(StorageHelper.formatBytes(app.totalBytes), color = Color(0xFF64D2FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // RIGHT: inspector + actions (side panel)
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x16FFFFFF))
                    .onFocusChanged { inActionPane = it.hasFocus }
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selected == null) {
                    Text("Nothing selected", color = Color(0x66FFFFFF), fontSize = 18.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconBox(icons[selected.pkg], 56)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selected.label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(selected.pkg, color = Color(0x88FFFFFF), fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoChip(selected.group.title, groupColor(selected.group))
                        if (selected.group == AppGroup.BLOAT) InfoChip(tierLabel(selected.tier), appTierColor(selected.tier))
                    }

                    Text(selected.reason, color = Color(0xCCFFFFFF), fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)

                    if (selected.group != AppGroup.REMOVED) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("App", StorageHelper.formatBytes(selected.appBytes), Color(0xFF64D2FF), Modifier.weight(1f))
                            Metric("Data", StorageHelper.formatBytes(selected.dataBytes), Color(0xFFFFA726), Modifier.weight(1f))
                            Metric("Cache", StorageHelper.formatBytes(selected.cacheBytes), Color(0xFF30D158), Modifier.weight(1f))
                        }
                    }

                    actions.forEachIndexed { i, act ->
                        TvButton(
                            text = if (busy) "Working..." else act.text,
                            onClick = {
                                if (!busy) {
                                    if (act.destructive) confirmId = act.id else act.run()
                                }
                            },
                            primary = act.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(actionFocus[i])
                                .dpad { key ->
                                    when (key) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            focusSelectedInList()
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (i > 0) runCatching { actionFocus[i - 1].requestFocus() }
                                            else runCatching { tabFocus[group.ordinal].requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (i < actions.lastIndex) runCatching { actionFocus[i + 1].requestFocus() }
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> true
                                        else -> false
                                    }
                                }
                        )
                    }
                }
            }
        }
    }

    // ---- confirm dialog (single OK press, no hold) ----
    val cAct = actions.firstOrNull { it.id == confirmId }
    val cApp = selected
    if (cAct != null && cApp != null) {
        val actIdx = actions.indexOf(cAct).coerceAtLeast(0)
        val restoreFocus: () -> Unit = {
            scope.launch {
                delay(100)
                runCatching { actionFocus[actIdx.coerceAtMost(actionFocus.lastIndex)].requestFocus() }
            }
        }
        ConfirmDialog(
            title = when (cAct.id) {
                "data" -> "Clear all data of ${cApp.label}?"
                else -> if (cApp.group == AppGroup.INSTALLED) "Uninstall ${cApp.label}?" else "Remove ${cApp.label}?"
            },
            message = when {
                cAct.id == "data" -> "Logins, settings and downloads of this app will be erased."
                cApp.group == AppGroup.BLOAT && cApp.tier == Tier.ADVANCED ->
                    "This app is not recognised. Removing it may affect some TV features. You can bring it back from the Removed tab."
                cApp.group == AppGroup.BLOAT -> "You can bring it back any time from the Removed tab."
                else -> "The app will be uninstalled from this TV."
            },
            confirmText = when {
                cAct.id == "data" -> "Clear data"
                cApp.group == AppGroup.INSTALLED -> "Uninstall"
                else -> "Remove"
            },
            onConfirm = {
                confirmId = null
                cAct.run()
                restoreFocus()
            },
            onDismiss = {
                confirmId = null
                restoreFocus()
            }
        )
    }
    }
}

@Composable
private fun AppIconBox(icon: Bitmap?, size: Int) {
    if (icon != null) {
        Image(
            bitmap = icon.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp))
        )
    } else {
        Box(
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(Color(0x28FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Android, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size((size / 2).dp))
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Metric(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(title, color = Color(0xAAFFFFFF), fontSize = 12.sp)
        }
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}