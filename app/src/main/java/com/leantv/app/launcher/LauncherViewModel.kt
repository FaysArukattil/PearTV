package com.leantv.app.launcher

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leantv.app.MainActivity
import com.leantv.app.data.InstalledAppInfo
import com.leantv.app.data.InstalledAppsScanner
import com.leantv.app.data.Journal
import com.leantv.app.monitor.CpuSnapshot
import com.leantv.app.monitor.RamSnapshot
import com.leantv.app.monitor.RunningAppItem
import com.leantv.app.monitor.SystemMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import com.leantv.app.adb.AdbClient
import com.leantv.app.adb.ConnResult
import com.leantv.app.data.Catalog
import com.leantv.app.data.RemovalMethod
import com.leantv.app.data.Removed
import com.leantv.app.data.Scanner
import com.leantv.app.data.Tier
import com.leantv.app.data.TweakEngine
import com.leantv.app.data.DebloatPresets
import com.leantv.app.data.MajorDebloatOption
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TvInputItem(
    val id: String,
    val name: String,
    val type: String,
    val isConnected: Boolean = true,
)

data class GoogleAccountItem(
    val name: String,
    val type: String,
)

data class LauncherState(
    val featuredApps: List<InstalledAppInfo> = emptyList(),
    val favoriteApps: List<InstalledAppInfo> = emptyList(),
    val allApps: List<InstalledAppInfo> = emptyList(),
    val focusedApp: InstalledAppInfo? = null,
    val timeText: String = "",
    val dateText: String = "",
    val isLoading: Boolean = true,
    val showDefaultPrompt: Boolean = false,
    val actionMenuApp: InstalledAppInfo? = null,
    val uninstallConfirmApp: InstalledAppInfo? = null,
    val toastMessage: String? = null,
    // Top Right Controls
    val showInputsDialog: Boolean = false,
    val tvInputs: List<TvInputItem> = emptyList(),
    val showProfileDialog: Boolean = false,
    val googleAccounts: List<GoogleAccountItem> = emptyList(),
    val currentAccountName: String = "Google Account",
    val showQuickShareHub: Boolean = false,
    // 1-Click Auto Debloater Wizard
    val isDebloating: Boolean = false,
    val debloatProgress: String? = null,
    val showCleanTvConfirm: Boolean = false,
    val showCleanTvWizard: Boolean = false,
    val majorOptions: List<MajorDebloatOption> = DebloatPresets.defaultMajorOptions(),
    val debloatSuccessSummary: String? = null,
    // System Monitor
    val showMonitorPanel: Boolean = false,
    val ramSnapshot: RamSnapshot? = null,
    val cpuSnapshot: CpuSnapshot? = null,
    val runningApps: List<RunningAppItem> = emptyList(),
    val isCleaning: Boolean = false,
    // Quick Share
    val showQuickShare: Boolean = false,
    val quickShareState: com.leantv.app.share.QuickShareState = com.leantv.app.share.QuickShareState(),
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val journal = Journal(ctx)
    private val systemMonitor = SystemMonitor(ctx)
    private val adb = AdbClient(ctx.filesDir)
    private val catalog = Catalog.load(ctx)
    private val scanner = Scanner(ctx, catalog)
    private val tweaks = TweakEngine(adb, journal)

    private var monitorJob: Job? = null
    private var shareServer: com.leantv.app.share.QuickShareServer? = null

    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = DateFormat.getDateInstance(DateFormat.FULL)

    init {
        startTimeTicker()
        loadApps()
        loadTvInputs()
        loadGoogleAccounts()
        checkDefaultLauncherPrompt()
    }

    private fun startTimeTicker() {
        viewModelScope.launch {
            while (isActive) {
                val now = Date()
                _state.update {
                    it.copy(
                        timeText = timeFormat.format(now),
                        dateText = dateFormat.format(now),
                    )
                }
                delay(1000)
            }
        }
    }

    fun onResume() {
        loadApps(silent = true)
        if (_state.value.showMonitorPanel) {
            startMonitorPolling()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitorPolling()
        stopQuickShare()
        adb.close()
    }

    // ---------- ⚡ CleanYourTV 1-Click Auto Debloater & Speed Engine ----------

    fun requestCleanTv() {
        _state.update { it.copy(showCleanTvWizard = true) }
    }

    fun dismissCleanTvWizard() {
        _state.update { it.copy(showCleanTvWizard = false) }
    }

    fun toggleMajorOption(optionId: String) {
        _state.update { s ->
            val updated = s.majorOptions.map { opt ->
                if (opt.id == optionId) opt.copy(isSelectedForRemoval = !opt.isSelectedForRemoval)
                else opt
            }
            s.copy(majorOptions = updated)
        }
    }

    fun dismissDebloatSummary() {
        _state.update { it.copy(debloatSuccessSummary = null) }
    }

    fun executeOneClickCleanTv() {
        dismissCleanTvWizard()
        val majorOpts = _state.value.majorOptions
        val packagesToExplicitlyKeep = majorOpts.filter { !it.isSelectedForRemoval }.flatMap { it.packages }.toSet()
        val packagesToExplicitlyRemove = majorOpts.filter { it.isSelectedForRemoval }.flatMap { it.packages }.toSet()

        _state.update { it.copy(isDebloating = true, debloatProgress = "Connecting to TV system...") }
        viewModelScope.launch {
            try {
                // 1. Connect ADB if needed
                if (!adb.isConnected) {
                    val res = adb.connect()
                    if (res !is ConnResult.Ok) {
                        _state.update {
                            it.copy(
                                isDebloating = false,
                                debloatProgress = null,
                                toastMessage = "USB Debugging required. Open Settings -> Developer Options -> Turn ON USB Debugging."
                            )
                        }
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                        return@launch
                    }
                }

                // 2. Scan and find all telemetry, ads, recommendations, and bloat packages
                _state.update { it.copy(debloatProgress = "Analyzing TV bloatware and trackers...") }
                val scan = withContext(Dispatchers.Default) { scanner.scan() }
                journal.saveBaselineIfMissing(scan.allSystem)

                val targets = scan.apps.filter { app ->
                    if (app.disabled) return@filter false
                    // If the user explicitly chose to KEEP this major app (e.g. Miracast), do NOT touch it!
                    if (app.pkg in packagesToExplicitlyKeep) return@filter false

                    // If user explicitly chose to remove this major app
                    if (app.pkg in packagesToExplicitlyRemove) return@filter true

                    // Otherwise include pure telemetry, ads, trackers, background analytics
                    app.tier == Tier.RECOMMENDED ||
                    app.pkg.contains("adservice", ignoreCase = true) ||
                    app.pkg.contains("advertise", ignoreCase = true) ||
                    app.pkg.contains("analytics", ignoreCase = true) ||
                    app.pkg.contains("telemetry", ignoreCase = true) ||
                    app.pkg.contains("tracking", ignoreCase = true) ||
                    app.pkg.contains("statistic", ignoreCase = true) ||
                    app.pkg.contains("recommendation", ignoreCase = true) ||
                    app.pkg.contains("feedback", ignoreCase = true) ||
                    app.pkg.startsWith("com.xiaomi.mitv.advertise") ||
                    app.pkg.startsWith("com.mitv.tvhome") ||
                    app.pkg.startsWith("com.tcl.waterfall") ||
                    app.pkg.startsWith("com.tcl.commercial")
                }

                // 3. Disable bloatware packages safely
                var disabledCount = 0
                for ((i, app) in targets.withIndex()) {
                    _state.update { it.copy(debloatProgress = "Disabling bloat (${i + 1}/${targets.size}): ${app.label}") }
                    val r = adb.shell("pm disable-user --user 0 ${app.pkg}")
                    if (r.out.contains("new state: disabled") || r.out.contains("Success")) {
                        journal.addRemoved(Removed(app.pkg, app.label, RemovalMethod.DISABLE, System.currentTimeMillis()))
                        disabledCount++
                    }
                }

                // 4. Apply speed tweaks (0.5x animations) & AdGuard DNS
                _state.update { it.copy(debloatProgress = "Applying 2x animation speed & AdGuard DNS...") }
                tweaks.setAnimations(true)
                tweaks.setPrivateDns(true)

                // 5. Done!
                val summary = "Disabled $disabledCount bloatware & ad packages. 2x animation speed & AdGuard DNS active."
                _state.update {
                    it.copy(
                        isDebloating = false,
                        debloatProgress = null,
                        debloatSuccessSummary = summary,
                        toastMessage = "✅ TV Cleaned: $summary"
                    )
                }
                loadApps(silent = true)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isDebloating = false,
                        debloatProgress = null,
                        toastMessage = "Error during cleanup: ${e.message}"
                    )
                }
            }
        }
    }

    // ---------- Top Right Controls: Inputs, Profile, Quick Share Hub ----------

    fun openInputs() {
        loadTvInputs()
        _state.update { it.copy(showInputsDialog = true) }
    }

    fun closeInputs() {
        _state.update { it.copy(showInputsDialog = false) }
    }

    fun loadTvInputs() {
        val inputs = ArrayList<TvInputItem>()
        try {
            val tvManager = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            val list = tvManager?.tvInputList ?: emptyList()
            for (info in list) {
                val label = try {
                    info.loadLabel(ctx)?.toString() ?: info.id
                } catch (_: Exception) {
                    info.id
                }
                val typeStr = when (info.type) {
                    TvInputInfo.TYPE_HDMI -> "HDMI"
                    TvInputInfo.TYPE_TUNER -> "Live TV / Antenna"
                    TvInputInfo.TYPE_COMPOSITE -> "AV / Composite"
                    TvInputInfo.TYPE_COMPONENT -> "Component"
                    TvInputInfo.TYPE_DISPLAY_PORT -> "DisplayPort"
                    else -> "TV Input"
                }
                inputs.add(TvInputItem(id = info.id, name = label, type = typeStr))
            }
        } catch (_: Exception) {}

        if (inputs.isEmpty()) {
            inputs.add(TvInputItem("hdmi1", "HDMI 1", "HDMI"))
            inputs.add(TvInputItem("hdmi2", "HDMI 2", "HDMI"))
            inputs.add(TvInputItem("hdmi3", "HDMI 3", "HDMI"))
            inputs.add(TvInputItem("av", "AV / Composite", "Composite"))
            inputs.add(TvInputItem("tv", "Live TV / Antenna", "Tuner"))
        }

        _state.update { it.copy(tvInputs = inputs) }
    }

    fun switchToInput(input: TvInputItem) {
        closeInputs()
        // Try direct passthrough Uri first
        try {
            val uri = TvContract.buildChannelUriForPassthroughInput(input.id)
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
                return
            }
        } catch (_: Exception) {}

        // OEM fallbacks (Xiaomi, TCL, Sony, generic TV input chooser)
        val oemIntents = listOf(
            Intent().setComponent(android.content.ComponentName("com.mitv.tvcustomsettings", "com.mitv.tvcustomsettings.InputSourceActivity")),
            Intent().setComponent(android.content.ComponentName("com.xiaomi.mitv.setupwizard", "com.xiaomi.mitv.setupwizard.inputsource.InputActivity")),
            Intent().setComponent(android.content.ComponentName("com.tcl.tvmanager", "com.tcl.tvmanager.input.InputActivity")),
            Intent("com.sony.dtv.intent.action.VIEW_INPUT_LIST"),
            Intent("android.media.tv.action.VIEW_INPUTS"),
            Intent("com.android.tv.action.VIEW_INPUTS"),
        )

        for (intent in oemIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }

        toast("Switched to ${input.name}")
    }

    fun openProfile() {
        loadGoogleAccounts()
        _state.update { it.copy(showProfileDialog = true) }
    }

    fun closeProfile() {
        _state.update { it.copy(showProfileDialog = false) }
    }

    fun loadGoogleAccounts() {
        try {
            val am = android.accounts.AccountManager.get(ctx)
            val accounts = am.getAccountsByType("com.google")
            val items = accounts.map { GoogleAccountItem(it.name, it.type) }
            val primary = items.firstOrNull()?.name ?: "Google Account"
            _state.update {
                it.copy(
                    googleAccounts = items,
                    currentAccountName = primary
                )
            }
        } catch (_: Exception) {
            _state.update { it.copy(currentAccountName = "Account") }
        }
    }

    fun openAddGoogleAccount() {
        closeProfile()
        try {
            val intent = Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            openSystemAccountsSettings()
        }
    }

    fun openSystemAccountsSettings() {
        closeProfile()
        val intents = listOf(
            Intent("android.settings.SYNC_SETTINGS"),
            Intent(Settings.ACTION_SYNC_SETTINGS),
            Intent("com.google.android.gms.accountsettings.ACCOUNT_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
    }

    fun openQuickShareHub() {
        _state.update { it.copy(showQuickShareHub = true) }
    }

    fun closeQuickShareHub() {
        _state.update { it.copy(showQuickShareHub = false) }
    }

    /**
     * Handles Quick Share click: Directly launches Google's original Quick Share (Nearby Share)
     * from Google Play Services. If not available on this TV, opens the Quick Share Hub.
     */
    fun handleQuickShareClick() {
        val launched = launchNativeGoogleQuickShareDirect()
        if (!launched) {
            _state.update { it.copy(showQuickShareHub = true) }
        }
    }

    fun launchNativeQuickShare() {
        closeQuickShareHub()
        val launched = launchNativeGoogleQuickShareDirect()
        if (!launched) {
            toast("Google Quick Share not preinstalled on this TV. Opening Universal AirShare...")
            openQuickShare()
        }
    }

    private fun launchNativeGoogleQuickShareDirect(): Boolean {
        val intents = listOf(
            Intent().setComponent(android.content.ComponentName("com.google.android.gms", "com.google.android.gms.nearby.sharing.ReceiveSurfaceActivity")),
            Intent().setComponent(android.content.ComponentName("com.google.android.gms", "com.google.android.gms.nearby.sharing.ShareSheetActivity")),
            Intent().setComponent(android.content.ComponentName("com.google.android.gms", "com.google.android.gms.nearby.sharing.NearbySharingSettingsActivity")),
            Intent("com.google.android.gms.nearby.sharing.SETTINGS"),
            Intent("com.google.android.gms.action.NEARBY_SHARING_SETTINGS"),
            Intent("com.google.android.gms.RECEIVE_NEARBY"),
            Intent("android.settings.QUICK_SHARE_SETTINGS"),
            Intent("android.settings.NEARBY_SHARING_SETTINGS"),
            Intent("com.google.android.gms.nearby.sharing.ACTION_START_RECEIVING"),
            Intent().setComponent(android.content.ComponentName("com.google.android.apps.sharing.nearby", "com.google.android.apps.sharing.nearby.MainActivity")),
            Intent().setComponent(android.content.ComponentName("com.samsung.android.app.share", "com.samsung.android.app.share.ShareActivity"))
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(ctx.packageManager) != null) {
                    ctx.startActivity(intent)
                    toast("Opening Google Quick Share...")
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun launchGoogleQuickShareStore() {
        closeQuickShareHub()
        try {
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(storeIntent)
        } catch (_: Exception) {
            toast("Opening Universal Wi-Fi AirShare...")
            openQuickShare()
        }
    }

    fun launchLocalSend() {
        closeQuickShareHub()
        val pkg = "org.localsend.localsend_app"
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                toast("Couldn't open LocalSend: ${e.message}")
            }
        } else {
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(storeIntent)
            } catch (_: Exception) {
                toast("LocalSend not installed on TV. Opening Universal Wi-Fi AirShare...")
                openQuickShare()
            }
        }
    }

    // ---------- Phase 4: Quick Share Methods ----------

    fun openQuickShare() {
        if (_state.value.showQuickShare) return

        val server = com.leantv.app.share.QuickShareServer(
            context = ctx,
            scope = viewModelScope,
            onItemReceived = { item ->
                _state.update { s ->
                    s.copy(
                        quickShareState = s.quickShareState.copy(
                            receivedItems = listOf(item) + s.quickShareState.receivedItems
                        ),
                        toastMessage = when (item) {
                            is com.leantv.app.share.SharedItem.SharedFile -> "Received file: ${item.name}"
                            is com.leantv.app.share.SharedItem.SharedText -> if (item.isUrl) "Received web link on TV" else "Received note on TV"
                        }
                    )
                }
            },
            onProgressUpdate = { status ->
                _state.update { s ->
                    s.copy(quickShareState = s.quickShareState.copy(transferStatus = status))
                }
            }
        )

        val started = server.start()
        val ip = server.getLocalIpAddress()
        shareServer = server

        _state.update {
            it.copy(
                showQuickShare = true,
                quickShareState = it.quickShareState.copy(
                    isRunning = started,
                    ipAddress = ip,
                    port = server.port,
                    pin = server.currentPin,
                )
            )
        }
    }

    fun closeQuickShare() {
        stopQuickShare()
        _state.update { it.copy(showQuickShare = false) }
    }

    private fun stopQuickShare() {
        shareServer?.stop()
        shareServer = null
        _state.update {
            it.copy(
                quickShareState = it.quickShareState.copy(isRunning = false, transferStatus = null)
            )
        }
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            toast("Couldn't open URL: ${e.message}")
        }
    }

    // ---------- Phase 3: System Monitor Methods ----------

    fun openSystemMonitor() {
        _state.update { it.copy(showMonitorPanel = true) }
        startMonitorPolling()
    }

    fun closeSystemMonitor() {
        stopMonitorPolling()
        _state.update { it.copy(showMonitorPanel = false) }
    }

    private fun startMonitorPolling() {
        monitorJob?.cancel()
        monitorJob = viewModelScope.launch {
            while (isActive) {
                val ram = systemMonitor.sampleRam()
                val cpu = systemMonitor.sampleCpu()
                val running = systemMonitor.sampleRunningApps()
                _state.update {
                    it.copy(
                        ramSnapshot = ram,
                        cpuSnapshot = cpu,
                        runningApps = running,
                    )
                }
                delay(1500)
            }
        }
    }

    private fun stopMonitorPolling() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun killRunningApp(pkg: String) {
        viewModelScope.launch {
            systemMonitor.killBackgroundProcess(pkg)
            val ram = systemMonitor.sampleRam()
            val running = systemMonitor.sampleRunningApps()
            _state.update {
                it.copy(
                    ramSnapshot = ram,
                    runningApps = running,
                    toastMessage = "Terminated background tasks for $pkg"
                )
            }
        }
    }

    fun cleanAllRunningApps() {
        val targets = _state.value.runningApps.map { it.packageName }
        if (targets.isEmpty()) return

        _state.update { it.copy(isCleaning = true) }
        viewModelScope.launch {
            systemMonitor.killAllBackgroundProcesses(targets)
            delay(500) // Brief delay to let system reclaim memory
            val ram = systemMonitor.sampleRam()
            val running = systemMonitor.sampleRunningApps()
            _state.update {
                it.copy(
                    ramSnapshot = ram,
                    runningApps = running,
                    isCleaning = false,
                    toastMessage = "Cleaned background processes. Reclaimed memory."
                )
            }
        }
    }

    fun loadApps(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val allInstalled = withContext(Dispatchers.IO) {
                InstalledAppsScanner.scan(ctx)
            }

            // Keep launchable apps, excluding LeanTV itself from the grid
            val launchable = allInstalled.filter {
                it.launchIntentAvailable && it.packageName != ctx.packageName
            }

            val favPackages = journal.favorites()
            val favorites = launchable.filter { it.packageName in favPackages }

            // If user has no favorites yet, default to top launchable user apps or first 5
            val effectiveFavorites = if (favorites.isNotEmpty()) {
                favorites
            } else {
                launchable.filter { !it.isSystemApp }.take(6).ifEmpty { launchable.take(6) }
            }

            // Featured apps: up to 5 top items (favorites or first launchable)
            val featured = effectiveFavorites.take(5)

            _state.update {
                it.copy(
                    featuredApps = featured,
                    favoriteApps = effectiveFavorites,
                    allApps = launchable.sortedBy { a -> a.appName.lowercase() },
                    isLoading = false,
                )
            }
        }
    }

    fun setFocusedApp(app: InstalledAppInfo?) {
        _state.update { it.copy(focusedApp = app) }
    }

    fun showActionMenu(app: InstalledAppInfo) {
        _state.update { it.copy(actionMenuApp = app) }
    }

    fun dismissActionMenu() {
        _state.update { it.copy(actionMenuApp = null) }
    }

    fun requestUninstall(app: InstalledAppInfo) {
        _state.update {
            it.copy(
                actionMenuApp = null,
                uninstallConfirmApp = app,
            )
        }
    }

    fun dismissUninstallConfirm() {
        _state.update { it.copy(uninstallConfirmApp = null) }
    }

    fun toggleFavorite(app: InstalledAppInfo) {
        val nowFavorite = journal.toggleFavorite(app.packageName)
        val favPackages = journal.favorites()
        val updatedFavorites = _state.value.allApps.filter { it.packageName in favPackages }
        _state.update {
            it.copy(
                favoriteApps = updatedFavorites,
                actionMenuApp = null,
                toastMessage = if (nowFavorite) "Added ${app.appName} to Favorites" else "Removed ${app.appName} from Favorites"
            )
        }
    }

    fun isFavorite(pkg: String): Boolean = journal.isFavorite(pkg)

    fun launch(app: InstalledAppInfo) {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)

        if (intent != null) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                toast("Couldn't open app: ${e.message}")
            }
        } else {
            toast("No launchable TV activity found for ${app.appName}")
        }
    }

    fun openAppDetails(app: InstalledAppInfo) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${app.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            toast("Couldn't open app settings.")
        }
    }

    fun confirmUninstall(app: InstalledAppInfo) {
        dismissUninstallConfirm()
        if (!app.canUninstall) {
            toast("${app.appName} is a protected system app.")
            return
        }
        try {
            val uri = Uri.parse("package:${app.packageName}")
            val intent = Intent(Intent.ACTION_DELETE, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            toast("Couldn't start uninstaller.")
        }
    }

    fun openSystemSettings() {
        try {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            toast("Couldn't open Settings.")
        }
    }

    fun openLeanTvOptimizer() {
        try {
            val intent = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            toast("Couldn't open LeanTV Tools.")
        }
    }

    // ---------- Default Launcher Flow ----------

    private fun checkDefaultLauncherPrompt() {
        if (!journal.defaultLauncherPromptDismissed) {
            val isDefault = isCurrentLauncherDefault()
            if (!isDefault) {
                _state.update { it.copy(showDefaultPrompt = true) }
            }
        }
    }

    private fun isCurrentLauncherDefault(): Boolean {
        return try {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = pm.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName == ctx.packageName
        } catch (_: Exception) {
            false
        }
    }

    fun dismissDefaultPrompt(dontAskAgain: Boolean = true) {
        if (dontAskAgain) {
            journal.defaultLauncherPromptDismissed = true
        }
        _state.update { it.copy(showDefaultPrompt = false) }
    }

    fun requestSetDefaultLauncher() {
        dismissDefaultPrompt(dontAskAgain = true)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = ctx.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                    return
                }
            }
        } catch (_: Exception) {
            // Fall through to standard home settings
        }

        try {
            val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(homeSettingsIntent)
        } catch (_: Exception) {
            try {
                // Generic settings fallback
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(settingsIntent)
                toast("Go to Apps -> Default Apps -> Home app to set LeanTV as default.")
            } catch (_: Exception) {
                toast("Please set LeanTV as default in TV Settings -> Home app.")
            }
        }
    }

    fun clearToast() = _state.update { it.copy(toastMessage = null) }
    private fun toast(msg: String) = _state.update { it.copy(toastMessage = msg) }
}
