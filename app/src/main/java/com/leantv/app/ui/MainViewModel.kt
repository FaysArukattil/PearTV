package com.leantv.app.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leantv.app.adb.AdbClient
import com.leantv.app.adb.ConnResult
import com.leantv.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val journal = Journal(ctx)
    private val adb = AdbClient(ctx.filesDir)
    private val catalog = Catalog.load(ctx)
    private val scanner = Scanner(ctx, catalog)
    private val tweaks = TweakEngine(adb, journal)

    private val pkgRegex = Regex("^[A-Za-z0-9_.]+$")

    private val _state = MutableStateFlow(
        UiState(
            screen = if (journal.onboarded) Screen.Setup else Screen.Welcome,
            removed = journal.removed(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (journal.onboarded) connect()
    }

    // ---------- navigation ----------

    fun go(screen: Screen) {
        _state.update { it.copy(screen = screen) }
        if (screen == Screen.InstalledApps && _state.value.installedApps.isEmpty()) {
            loadInstalledApps()
        }
    }

    fun begin() {
        go(Screen.Setup)
        connect()
    }

    fun clearToast() = _state.update { it.copy(toast = null) }
    private fun toast(msg: String) = _state.update { it.copy(toast = msg) }

    // ---------- setup helpers ----------

    fun openAbout() = openSettings(Settings.ACTION_DEVICE_INFO_SETTINGS)
    fun openDeveloper() = openSettings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)

    private fun openSettings(action: String) {
        try {
            ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast("Couldn't open that screen. Open Settings yourself and follow the steps.")
        }
    }

    /** Coming back from system settings or uninstaller: refresh state. */
    fun onResume() {
        val s = _state.value
        if (s.screen == Screen.Setup && s.conn is Conn.Failed) connect()
        if (s.screen == Screen.InstalledApps) loadInstalledApps(forceRefresh = true)
    }

    // ---------- installed apps (Phase 1) ----------

    fun loadInstalledApps(forceRefresh: Boolean = false) {
        if (!forceRefresh && _state.value.installedApps.isNotEmpty() && !_state.value.installedAppsLoading) return
        _state.update { it.copy(installedAppsLoading = true) }
        viewModelScope.launch {
            val apps = InstalledAppsScanner.scan(ctx)
            _state.update {
                it.copy(
                    installedApps = apps,
                    installedAppsLoading = false,
                )
            }
        }
    }

    fun setAppTypeFilter(filter: AppTypeFilter) {
        _state.update { it.copy(appTypeFilter = filter) }
    }

    fun setAppSortOrder(sortOrder: AppSortOrder) {
        _state.update { it.copy(appSortOrder = sortOrder) }
    }

    fun setAppSearchQuery(query: String) {
        _state.update { it.copy(appSearchQuery = query) }
    }

    fun uninstallApp(app: InstalledAppInfo) {
        if (!app.canUninstall) {
            toast("${app.appName} is a protected system app and cannot be uninstalled normally.")
            return
        }
        try {
            val uri = android.net.Uri.parse("package:${app.packageName}")
            val intent = Intent(Intent.ACTION_DELETE, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                val fallback = Intent(Intent.ACTION_UNINSTALL_PACKAGE, android.net.Uri.parse("package:${app.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                }
                ctx.startActivity(fallback)
            } catch (_: Exception) {
                toast("Couldn't start uninstaller for ${app.appName}")
            }
        }
    }

    fun launchApp(pkg: String) {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                toast("Couldn't launch app: ${e.message}")
            }
        } else {
            toast("No launchable TV or mobile activity for this app.")
        }
    }

    fun openAppDetails(pkg: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$pkg")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            toast("Couldn't open app settings.")
        }
    }

    // ---------- connection ----------

    fun connect() {
        if (_state.value.conn is Conn.Checking) return
        _state.update { it.copy(conn = Conn.Checking) }
        viewModelScope.launch {
            when (val r = adb.connect()) {
                is ConnResult.Ok -> {
                    journal.onboarded = true
                    _state.update { it.copy(conn = Conn.Connected(r.host)) }
                    loadAll()
                    delay(700)
                    if (_state.value.screen == Screen.Setup) go(Screen.Dashboard)
                }
                is ConnResult.PortClosed -> _state.update {
                    it.copy(
                        conn = Conn.Failed(
                            "The TV isn't accepting debug connections",
                            "Turn on USB debugging in Developer options, then try again. " +
                                "If it is already on, some TVs need a one-time command from a computer: adb tcpip 5555"
                        )
                    )
                }
                is ConnResult.Failed -> _state.update {
                    it.copy(
                        conn = Conn.Failed(
                            "Couldn't connect",
                            "${r.reason}\nIf a prompt appeared on screen, choose Allow and tick Always allow."
                        )
                    )
                }
            }
        }
    }

    private suspend fun loadAll() {
        _state.update { it.copy(scanning = true) }
        val dev = withContext(Dispatchers.Default) { DeviceProbe.read(ctx) }
        val scan = withContext(Dispatchers.Default) { scanner.scan() }
        journal.saveBaselineIfMissing(scan.allSystem)
        val tw = tweaks.read()
        _state.update {
            it.copy(
                device = dev,
                apps = scan.apps,
                protectedCount = scan.protectedCount,
                scanning = false,
                tweaks = tw,
                removed = journal.removed(),
                selected = it.selected.filter { p -> scan.apps.any { a -> a.pkg == p } }.toSet(),
            )
        }
    }

    private suspend fun rescan() {
        val dev = withContext(Dispatchers.Default) { DeviceProbe.read(ctx) }
        val scan = withContext(Dispatchers.Default) { scanner.scan() }
        _state.update {
            it.copy(
                device = dev,
                apps = scan.apps,
                protectedCount = scan.protectedCount,
                removed = journal.removed(),
                selected = it.selected.filter { p -> scan.apps.any { a -> a.pkg == p && !a.disabled } }.toSet(),
            )
        }
    }

    // ---------- selection ----------

    fun setFilter(f: Filter) = _state.update { it.copy(filter = f) }
    fun setMethod(m: RemovalMethod) = _state.update { it.copy(method = m) }

    fun toggle(pkg: String) = _state.update {
        it.copy(selected = if (pkg in it.selected) it.selected - pkg else it.selected + pkg)
    }

    fun selectRecommended() = _state.update { s ->
        s.copy(selected = s.apps.filter { it.tier == Tier.RECOMMENDED && !it.disabled }.map { it.pkg }.toSet())
    }

    // ---------- removal ----------

    fun requestRemove() {
        val s = _state.value
        val sel = s.apps.filter { it.pkg in s.selected }
        if (sel.isEmpty()) {
            toast("Pick at least one app first.")
            return
        }
        val adv = sel.count { it.tier == Tier.ADVANCED }
        val verb = s.method.label.lowercase()
        val body = buildString {
            append("${sel.size} app${if (sel.size == 1) "" else "s"} will be ${if (verb == "remove") "removed" else "disabled"}. ")
            append("Everything is logged and can be undone from Restore.")
            if (adv > 0) append("\n\n$adv of them ${if (adv == 1) "is" else "are"} marked Advanced. If you aren't sure what an app does, leave it out.")
        }
        _state.update {
            it.copy(confirm = ConfirmInfo("${s.method.label} ${sel.size} app${if (sel.size == 1) "" else "s"}?", body, s.method.label))
        }
    }

    fun cancelConfirm() = _state.update { it.copy(confirm = null) }

    fun confirmRemove() {
        val s = _state.value
        val sel = s.apps.filter { it.pkg in s.selected && pkgRegex.matches(it.pkg) }
        val method = s.method
        _state.update { it.copy(confirm = null) }
        runBusy(if (method == RemovalMethod.UNINSTALL) "Removing apps" else "Disabling apps") {
            var ok = 0
            val failed = ArrayList<String>()
            for ((i, app) in sel.withIndex()) {
                setProgress(i + 1, sel.size)
                val cmd = when (method) {
                    RemovalMethod.UNINSTALL -> "pm uninstall -k --user 0 ${app.pkg}"
                    RemovalMethod.DISABLE -> "pm disable-user --user 0 ${app.pkg}"
                }
                val r = adb.shell(cmd)
                if (r.out.contains("Success") || r.out.contains("new state: disabled")) {
                    journal.addRemoved(Removed(app.pkg, app.label, method, System.currentTimeMillis()))
                    ok++
                } else {
                    failed.add(app.label)
                }
            }
            rescan()
            toast(
                if (failed.isEmpty()) "Done. $ok app${if (ok == 1) "" else "s"} handled."
                else "$ok done, ${failed.size} couldn't be changed: ${failed.take(2).joinToString()}"
            )
        }
    }

    // ---------- restore ----------

    private suspend fun restoreCommand(pkg: String, method: RemovalMethod?): Boolean {
        if (!pkgRegex.matches(pkg)) return false
        val cmd = when (method) {
            RemovalMethod.DISABLE -> "pm enable --user 0 $pkg"
            else -> "cmd package install-existing --user 0 $pkg"
        }
        val r = adb.shell(cmd)
        val ok = r.out.contains("installed for user") || r.out.contains("new state: enabled")
        if (ok) journal.dropRemoved(pkg)
        return ok
    }

    fun restoreOne(pkg: String) {
        val rec = journal.removed().firstOrNull { it.pkg == pkg } ?: return
        runBusy("Restoring ${rec.label}") {
            val ok = restoreCommand(rec.pkg, rec.method)
            rescan()
            toast(if (ok) "${rec.label} is back." else "Couldn't restore ${rec.label}.")
        }
    }

    fun restoreAll() {
        runBusy("Restoring apps") {
            val recs = journal.removed()
            val journalPkgs = recs.map { it.pkg }.toSet()
            val missing = withContext(Dispatchers.Default) {
                (journal.baseline() - scanner.installedPackages()) - journalPkgs
            }
            val total = recs.size + missing.size
            var i = 0
            var failed = 0
            for (r in recs) {
                setProgress(++i, total)
                if (!restoreCommand(r.pkg, r.method)) failed++
            }
            for (p in missing) {
                setProgress(++i, total)
                if (!restoreCommand(p, null)) failed++
            }
            rescan()
            toast(
                when {
                    total == 0 -> "Nothing to restore. Your apps are as they were."
                    failed == 0 -> "All $total app${if (total == 1) "" else "s"} restored."
                    else -> "${total - failed} restored, $failed couldn't be restored."
                }
            )
        }
    }

    // ---------- tweaks ----------

    fun setAnimations(on: Boolean) = runBusy("Applying") {
        val ok = tweaks.setAnimations(on)
        _state.update { it.copy(tweaks = TweakStates(animations = on && ok, dns = it.tweaks.dns)) }
        refreshTweaks()
        if (!ok) toast("That setting couldn't be changed on this TV.")
    }

    fun setDns(on: Boolean) = runBusy("Applying") {
        val ok = tweaks.setPrivateDns(on)
        refreshTweaks()
        if (!ok) toast("That setting couldn't be changed on this TV.")
    }

    fun clearCaches() = runBusy("Clearing caches") {
        toast(if (tweaks.trimCaches()) "Caches cleared." else "Couldn't clear caches on this TV.")
        rescan()
    }

    fun resetTweaks() = runBusy("Resetting tweaks") {
        val ok = tweaks.resetAll()
        refreshTweaks()
        toast(if (ok) "Speed tweaks reset to the original settings." else "Some settings couldn't be reset.")
    }

    private suspend fun refreshTweaks() {
        val tw = tweaks.read()
        _state.update { it.copy(tweaks = tw) }
    }

    // ---------- helpers ----------

    private fun setProgress(done: Int, total: Int) = _state.update { it.copy(progress = done to total) }

    private fun runBusy(label: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = label, progress = null) }
            try {
                block()
            } finally {
                _state.update { it.copy(busy = null, progress = null) }
            }
        }
    }

    override fun onCleared() {
        adb.close()
    }
}
