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
import com.leantv.app.account.TvAccountHelper
import com.leantv.app.account.TvProfile
import com.leantv.app.adb.AdbClient
import com.leantv.app.adb.ConnResult
import com.leantv.app.data.Catalog
import com.leantv.app.data.DebloatPresets
import com.leantv.app.data.DeviceProbe
import com.leantv.app.data.InstalledAppInfo
import com.leantv.app.data.InstalledAppsScanner
import com.leantv.app.data.Journal
import com.leantv.app.data.MajorDebloatOption
import com.leantv.app.data.RemovalMethod
import com.leantv.app.data.Removed
import com.leantv.app.data.Scanner
import com.leantv.app.data.Tier
import com.leantv.app.data.TweakEngine
import com.leantv.app.inputs.TvInputHelper
import com.leantv.app.inputs.TvPort
import com.leantv.app.monitor.CpuSnapshot
import com.leantv.app.monitor.RamSnapshot
import com.leantv.app.monitor.RunningAppItem
import com.leantv.app.monitor.SystemMonitor
import com.leantv.app.storage.StorageHelper
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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class LauncherState(
    val featuredApps: List<InstalledAppInfo> = emptyList(),
    val favoriteApps: List<InstalledAppInfo> = emptyList(),
    val frequentApps: List<InstalledAppInfo> = emptyList(),
    val recentApps: List<InstalledAppInfo> = emptyList(),
    val dockApps: List<InstalledAppInfo> = emptyList(),
    val allApps: List<InstalledAppInfo> = emptyList(),
    val focusedApp: InstalledAppInfo? = null,
    val greetingText: String = "GOOD AFTERNOON",
    val timeText: String = "",
    val dateText: String = "",
    val networkLabel: String = "Wi-Fi",
    val networkIsConnected: Boolean = true,
    val isLoading: Boolean = true,
    val showDefaultPrompt: Boolean = false,
    val actionMenuApp: InstalledAppInfo? = null,
    val uninstallConfirmApp: InstalledAppInfo? = null,
    val toastMessage: String? = null,

    // Live TV Hardware Inputs
    val showInputsDialog: Boolean = false,
    val tvPorts: List<TvPort> = emptyList(),

    // Google Account Profile
    val showProfileDialog: Boolean = false,
    val tvProfile: TvProfile = TvProfile(null, "Profile", "👤", emptyList()),
    val currentAccountName: String = "Google Account",

    // Quick Share Hub & Panel
    val showQuickShareHub: Boolean = false,
    val showQuickShare: Boolean = false,
    val quickShareState: com.leantv.app.share.QuickShareState = com.leantv.app.share.QuickShareState(),

    // Storage Manager Screen (Samsung Device Care style)
    val showStorageManager: Boolean = false,

    // File Manager Screen (Samsung My Files style)
    val showFileManager: Boolean = false,

    // Personalization & Wallpapers
    val showPersonalization: Boolean = false,
    val wallpaperTheme: String = "aurora",
    val customWallpaperUri: String? = null,
    val is24hClock: Boolean = false,
    val showGreeting: Boolean = true,

    // 1-Click Auto Debloater Wizard
    val isDebloating: Boolean = false,
    val debloatProgress: String? = null,
    val showCleanTvConfirm: Boolean = false,
    val showCleanTvWizard: Boolean = false,
    val majorOptions: List<MajorDebloatOption> = DebloatPresets.defaultMajorOptions(),
    val debloatSuccessSummary: String? = null,
    val detectedDeviceLabel: String = "Android TV",

    // System Monitor
    val showMonitorPanel: Boolean = false,
    val ramSnapshot: RamSnapshot? = null,
    val cpuSnapshot: CpuSnapshot? = null,
    val runningApps: List<RunningAppItem> = emptyList(),
    val isCleaning: Boolean = false,
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    val journal = Journal(ctx)
    private val systemMonitor = SystemMonitor(ctx)
    val adb = AdbClient(ctx.filesDir)
    private val catalog = Catalog.load(ctx)
    private val scanner = Scanner(ctx, catalog)
    private val tweaks = TweakEngine(adb, journal)

    private var monitorJob: Job? = null
    private var shareServer: com.leantv.app.share.QuickShareServer? = null

    private val _state = MutableStateFlow(
        LauncherState(
            wallpaperTheme = journal.wallpaperTheme,
            customWallpaperUri = journal.customWallpaperUri,
            is24hClock = journal.clockFormat24h,
            showGreeting = journal.showGreeting
        )
    )
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    init {
        detectDevice()
        startTimeTicker()
        loadApps()
        startObservingTvInputs()
        loadProfile()
        checkDefaultLauncherPrompt()
        checkFirstRunDebloat()
    }

    private fun detectDevice() {
        val profile = DeviceProbe.read(ctx)
        val brand = profile.brand.replaceFirstChar { it.uppercase() }
        val model = profile.model
        _state.update {
            it.copy(detectedDeviceLabel = if (model.contains(brand, ignoreCase = true)) model else "$brand $model")
        }
    }

    private fun checkFirstRunDebloat() {
        if (!journal.debloatWizardDismissed) {
            viewModelScope.launch {
                delay(1200)
                _state.update { it.copy(showCleanTvWizard = true) }
            }
        }
    }

    private fun startTimeTicker() {
        viewModelScope.launch {
            while (isActive) {
                val now = Date()
                val cal = Calendar.getInstance()
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val greeting = when (hour) {
                    in 4..11 -> "GOOD MORNING"
                    in 12..16 -> "GOOD AFTERNOON"
                    else -> "GOOD EVENING"
                }

                val pattern = if (journal.clockFormat24h) "HH:mm" else "h:mm a"
                val timeStr = SimpleDateFormat(pattern, Locale.getDefault()).format(now).uppercase()
                val dateStr = SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(now).uppercase()
                val (netLabel, netConnected) = getNetworkStatus()

                _state.update {
                    it.copy(
                        greetingText = greeting,
                        timeText = timeStr,
                        dateText = dateStr,
                        networkLabel = netLabel,
                        networkIsConnected = netConnected
                    )
                }
                delay(1000)
            }
        }
    }

    private fun getNetworkStatus(): Pair<String, Boolean> {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val active = cm?.activeNetwork ?: return Pair("No Network", false)
            val caps = cm.getNetworkCapabilities(active) ?: return Pair("No Network", false)
            when {
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> Pair("Ethernet", true)
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val wifiMgr = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
                    val info = wifiMgr?.connectionInfo
                    val ssid = info?.ssid?.removeSurrounding("\"")
                    if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                        Pair(ssid, true)
                    } else {
                        Pair("Wi-Fi", true)
                    }
                }
                else -> Pair("Connected", true)
            }
        } catch (_: Exception) {
            Pair("Wi-Fi", true)
        }
    }

    fun openNetworkSettings() {
        val intents = listOf(
            Intent("android.settings.WIFI_SETTINGS"),
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
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

    fun onResume() {
        loadApps(silent = true)
        loadProfile()
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

    // ---------- App Organization: Frequently Used, Recently Used, Dock, All Apps ----------

    fun loadApps(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val allInstalled = withContext(Dispatchers.IO) {
                InstalledAppsScanner.scan(ctx)
            }

            val hiddenSet = journal.hiddenApps()

            // Keep launchable apps, excluding LeanTV itself and hidden apps
            val launchable = allInstalled.filter {
                it.launchIntentAvailable && it.packageName != ctx.packageName && it.packageName !in hiddenSet
            }

            val favPackages = journal.favorites()
            val favorites = launchable.filter { it.packageName in favPackages }

            // Dock apps: first 4 favorites or top launchable
            val dock = if (favorites.isNotEmpty()) {
                favorites.take(4)
            } else {
                launchable.filter { !it.isSystemApp }.take(4).ifEmpty { launchable.take(4) }
            }

            // Frequently Used: ranked by launch count > 0
            val frequent = launchable
                .filter { journal.getLaunchCount(it.packageName) > 0 }
                .sortedByDescending { journal.getLaunchCount(it.packageName) }
                .take(6)

            // Recently Used: ranked by last launch timestamp > 0
            val recent = launchable
                .filter { journal.getLastLaunchTime(it.packageName) > 0L }
                .sortedByDescending { journal.getLastLaunchTime(it.packageName) }
                .take(6)

            val sortedAll = launchable.sortedBy { it.appName.lowercase(Locale.ROOT) }

            _state.update {
                it.copy(
                    featuredApps = favorites.take(5),
                    favoriteApps = favorites,
                    dockApps = dock,
                    frequentApps = frequent,
                    recentApps = recent,
                    allApps = sortedAll,
                    isLoading = false,
                )
            }
        }
    }

    fun launch(app: InstalledAppInfo) {
        // Record launch count and timestamp
        journal.recordAppLaunch(app.packageName)
        loadApps(silent = true)

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

    fun setFocusedApp(app: InstalledAppInfo?) {
        _state.update { it.copy(focusedApp = app) }
    }

    // ---------- Context Action Menu ----------

    fun showActionMenu(app: InstalledAppInfo) {
        _state.update { it.copy(actionMenuApp = app) }
    }

    fun dismissActionMenu() {
        _state.update { it.copy(actionMenuApp = null) }
    }

    fun toggleFavorite(app: InstalledAppInfo) {
        val nowFavorite = journal.toggleFavorite(app.packageName)
        loadApps(silent = true)
        _state.update {
            it.copy(
                actionMenuApp = null,
                toastMessage = if (nowFavorite) "Pinned ${app.appName} to Favorites" else "Unpinned ${app.appName}"
            )
        }
    }

    fun isFavorite(pkg: String): Boolean = journal.isFavorite(pkg)

    fun toggleHideApp(app: InstalledAppInfo) {
        val nowHidden = journal.toggleHideApp(app.packageName)
        loadApps(silent = true)
        _state.update {
            it.copy(
                actionMenuApp = null,
                toastMessage = if (nowHidden) "Hidden ${app.appName} from launcher" else "Restored ${app.appName}"
            )
        }
    }

    fun clearAppDataAction(app: InstalledAppInfo) {
        dismissActionMenu()
        viewModelScope.launch {
            val ok = StorageHelper.clearAppData(ctx, adb, app.packageName)
            if (ok) {
                toast("Cleared data for ${app.appName}")
            }
        }
    }

    fun clearAppCacheAction(app: InstalledAppInfo) {
        dismissActionMenu()
        viewModelScope.launch {
            StorageHelper.cleanAllAccessibleCaches(ctx)
            toast("Cleared cache for ${app.appName}")
        }
    }

    fun openAppDetails(app: InstalledAppInfo) {
        dismissActionMenu()
        StorageHelper.openAppDetails(ctx, app.packageName)
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

    fun confirmUninstall(app: InstalledAppInfo) {
        dismissUninstallConfirm()
        StorageHelper.requestUninstall(ctx, app.packageName)
    }

    // ---------- Hardware TV Input Switcher (Feature #3) ----------

    private fun startObservingTvInputs() {
        viewModelScope.launch {
            TvInputHelper.observeInputs(ctx).collect { ports ->
                _state.update { it.copy(tvPorts = ports) }
            }
        }
    }

    fun openInputs() {
        val ports = TvInputHelper.queryHardwareInputs(ctx)
        _state.update { it.copy(tvPorts = ports, showInputsDialog = true) }
    }

    fun closeInputs() {
        _state.update { it.copy(showInputsDialog = false) }
    }

    fun switchToPort(port: TvPort) {
        closeInputs()
        val switched = TvInputHelper.switchToPort(ctx, port)
        if (switched) {
            toast("Switched to ${port.label}")
        } else {
            toast("Unable to switch directly to ${port.label}")
        }
    }

    // ---------- Google Account Profile (Feature #6) ----------

    fun loadProfile() {
        val profile = TvAccountHelper.getConnectedProfile(ctx)
        _state.update {
            it.copy(
                tvProfile = profile,
                currentAccountName = profile.displayName
            )
        }
    }

    fun openProfile() {
        loadProfile()
        _state.update { it.copy(showProfileDialog = true) }
    }

    fun closeProfile() {
        _state.update { it.copy(showProfileDialog = false) }
    }

    fun openAddGoogleAccount() {
        closeProfile()
        val ok = TvAccountHelper.openAddAccount(ctx)
        if (!ok) toast("Please add Google Account in TV Settings -> Accounts.")
    }

    fun openSystemAccountsSettings() {
        closeProfile()
        val ok = TvAccountHelper.openAccountSettings(ctx)
        if (!ok) toast("Please manage Accounts in TV Settings.")
    }

    // ---------- Storage Manager Screen (Samsung Device Care - Feature #4) ----------

    fun openStorageManager() {
        _state.update { it.copy(showStorageManager = true) }
    }

    fun closeStorageManager() {
        _state.update { it.copy(showStorageManager = false) }
        loadApps(silent = true)
    }

    // ---------- TV File Manager Screen (Samsung My Files - Feature #7) ----------

    fun openFileManager() {
        _state.update { it.copy(showFileManager = true) }
    }

    fun closeFileManager() {
        _state.update { it.copy(showFileManager = false) }
    }

    // ---------- Personalization & Wallpapers (Feature #12) ----------

    fun openPersonalization() {
        _state.update { it.copy(showPersonalization = true) }
    }

    fun closePersonalization() {
        _state.update { it.copy(showPersonalization = false) }
    }

    fun setWallpaperTheme(themeId: String) {
        journal.wallpaperTheme = themeId
        journal.customWallpaperUri = null
        _state.update { it.copy(wallpaperTheme = themeId, customWallpaperUri = null) }
        toast("Applied $themeId theme")
    }

    fun setCustomWallpaper(uri: String) {
        journal.wallpaperTheme = "custom"
        journal.customWallpaperUri = uri
        _state.update { it.copy(wallpaperTheme = "custom", customWallpaperUri = uri) }
        toast("Applied custom wallpaper")
    }

    fun toggleClockFormat() {
        val newFormat = !journal.clockFormat24h
        journal.clockFormat24h = newFormat
        _state.update { it.copy(is24hClock = newFormat) }
    }

    fun toggleGreeting() {
        val newGreeting = !journal.showGreeting
        journal.showGreeting = newGreeting
        _state.update { it.copy(showGreeting = newGreeting) }
    }

    // ---------- Quick Share Dual-Mode (Feature #8) ----------

    fun handleQuickShareClick() {
        val launched = launchNativeGoogleQuickShareDirect()
        if (!launched) {
            _state.update { it.copy(showQuickShareHub = true) }
        }
    }

    fun openQuickShareHub() {
        _state.update { it.copy(showQuickShareHub = true) }
    }

    fun closeQuickShareHub() {
        _state.update { it.copy(showQuickShareHub = false) }
    }

    fun launchNativeQuickShare() {
        closeQuickShareHub()
        val launched = launchNativeGoogleQuickShareDirect()
        if (!launched) {
            toast("Google Quick Share not preinstalled on this TV. Opening Universal Wi-Fi AirShare...")
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

        val pm = ctx.packageManager
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(pm) != null) {
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

    // ---------- Native TV Settings (Feature #5) ----------

    fun openSystemSettings() {
        val settingsIntents = listOf(
            Intent().setComponent(android.content.ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")).setAction("android.intent.action.MAIN"),
            Intent().setComponent(android.content.ComponentName("com.android.tv.settings", "com.android.tv.settings.SettingsActivity")),
            Intent("android.settings.SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        val pm = ctx.packageManager
        for (intent in settingsIntents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(pm) != null) {
                    ctx.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            if (!pressRemoteKey(176)) { // 176 = KEYCODE_SETTINGS
                toast("Couldn't open Settings.")
            }
        }
    }

    private suspend fun pressRemoteKey(keyCode: Int): Boolean {
        return try {
            if (!adb.isConnected) adb.connect()
            val res = adb.shell("input keyevent $keyCode")
            res.code == 0
        } catch (_: Exception) {
            false
        }
    }

    // ---------- ⚡ CleanYourTV 1-Click Auto Debloater Wizard (Feature #9) ----------

    fun requestCleanTv() {
        _state.update { it.copy(showCleanTvWizard = true) }
    }

    fun dismissCleanTvWizard() {
        journal.debloatWizardDismissed = true
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
        journal.debloatWizardDismissed = true
        _state.update { it.copy(showCleanTvWizard = false) }

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
                    if (app.pkg in packagesToExplicitlyKeep) return@filter false
                    if (app.pkg in packagesToExplicitlyRemove) return@filter true

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

    // ---------- System Monitor ----------

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
            delay(500)
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
        } catch (_: Exception) {}

        try {
            val homeSettingsIntent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(homeSettingsIntent)
        } catch (_: Exception) {
            try {
                val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(settingsIntent)
                toast("Go to Apps -> Default Apps -> Home app to set CleanYourTV as default.")
            } catch (_: Exception) {
                toast("Please set CleanYourTV as default in TV Settings -> Home app.")
            }
        }
    }

    fun clearToast() = _state.update { it.copy(toastMessage = null) }
    private fun toast(msg: String) = _state.update { it.copy(toastMessage = msg) }
}
