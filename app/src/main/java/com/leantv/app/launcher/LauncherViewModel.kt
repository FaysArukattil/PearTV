package com.leantv.app.launcher

import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
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
import com.leantv.app.data.IconCache
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AppSortMode(val label: String) {
    RECENCY("Recent"),
    ALPHABETICAL("A-Z"),
}

/**
 * Packages that "close all apps" never touches. Killing these only makes the TV slower (they restart straight away)
 * or breaks input / settings / Google services. Everything else is closed when you return to Home.
 */
private val PROTECTED_PREFIXES = listOf(
    "android",
    "com.android.systemui",
    "com.android.tv",
    "com.android.settings",
    "com.android.providers",
    "com.android.inputmethod",
    "com.android.bluetooth",
    "com.android.keychain",
    "com.android.phone",
    "com.android.server",
    "com.android.packageinstaller",
    "com.android.permissioncontroller",
    "com.google.android.gms",
    "com.google.android.gsf",
    "com.google.android.inputmethod",
    "com.google.android.tvlauncher",
    "com.google.android.tvrecommendations",
    "com.google.android.apps.tv.launcherx",
    "com.google.android.katniss",
    "com.google.android.packageinstaller",
    "com.google.android.permissioncontroller",
    "com.google.android.tts",
    "com.google.android.webview",
    "com.google.android.trichromelibrary",
    "com.google.android.overlay",
    "com.mediatek",
    "com.amlogic",
    "com.droidlogic",
    "com.realtek",
    "com.hisilicon",
    "com.broadcom",
    "com.nvidia",
    "com.shizuku",
    "moe.shizuku",
    "rikka.shizuku",
)

private fun isProtectedPackage(pkg: String): Boolean {
    for (p in PROTECTED_PREFIXES) {
        if (pkg == p || pkg.startsWith("$p.")) return true
    }
    return false
}

/** How long Quick Share stays on without any activity. */
private const val QS_AUTO_OFF_MS = 15L * 60L * 1000L

data class LauncherState(
    val featuredApps: List<InstalledAppInfo> = emptyList(),
    val favoriteApps: List<InstalledAppInfo> = emptyList(),
    val frequentApps: List<InstalledAppInfo> = emptyList(),
    val recentApps: List<InstalledAppInfo> = emptyList(),
    val dockApps: List<InstalledAppInfo> = emptyList(),
    val allApps: List<InstalledAppInfo> = emptyList(),
    val appSortMode: AppSortMode = AppSortMode.RECENCY,
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

    // Custom TV Volume HUD
    val showVolumeBar: Boolean = false,
    val volumeLevel: Int = 0,
    val volumeMax: Int = 100,
    val volumeIsMuted: Boolean = false,

    // Live TV Hardware Inputs
    val showInputsDialog: Boolean = false,
    val tvPorts: List<TvPort> = emptyList(),
    val showTvPlayer: Boolean = false,
    val activeInputId: String? = null,
    /** Bumped whenever the remote's Home key is pressed so Home can reset its own local UI. */
    val homeResetTick: Int = 0,

    // Google Account Profile
    val showProfileDialog: Boolean = false,
    val tvProfile: TvProfile = TvProfile(null, "Profile", "👤", emptyList()),
    val currentAccountName: String = "Google Account",

    // Quick Share Hub & Panel
    val showQuickShareHub: Boolean = false,
    val showQuickShare: Boolean = false,
    val isQuickShareActive: Boolean = false,
    val quickShareAutoOffSeconds: Int? = null,
    val quickShareState: com.leantv.app.share.QuickShareState = com.leantv.app.share.QuickShareState(),

    // Native Quick Share (mDNS / BLE receiver)
    val showNativeQuickShare: Boolean = false,
    val nativeQuickShareState: com.leantv.app.share.NativeQuickShareState = com.leantv.app.share.NativeQuickShareState(),

    // Storage Manager Screen (Samsung Device Care style)
    val showStorageManager: Boolean = false,

    // File Manager Screen (Amaze TV style)
    val showFileManager: Boolean = false,
    val fileManagerInitialDir: java.io.File? = null,
    val fileManagerHighlightFile: String? = null,

    // Clean TV Full Screen Debloater Menu
    val showCleanTvScreen: Boolean = false,

    // Apple TV Aerial Live Screensaver & Settings
    val showScreensaver: Boolean = false,
    val showScreensaverSettings: Boolean = false,
    val screensaverMode: com.leantv.app.ui.ScreensaverSource = com.leantv.app.ui.ScreensaverSource.AERIAL,
    val screensaverCustomPath: String? = null,
    val screensaverIdleMinutes: Int = 3,
    val screensaverAutoCache: Boolean = false,
    val screensaverSelectedUrl: String? = null,

    // Personalization & Wallpapers
    val showPersonalization: Boolean = false,
    val wallpaperTheme: String = "aurora",
    val customWallpaperUri: String? = null,
    val is24hClock: Boolean = false,
    val showGreeting: Boolean = true,
    /** Returning to Home closes every other app and frees its memory. */
    val closeAppsOnHome: Boolean = true,

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
    // Only needed by the Clean TV screens: built on first use instead of blocking app start.
    val catalog by lazy { Catalog.load(ctx) }
    val scanner by lazy { Scanner(ctx, catalog) }
    val tweaks by lazy { TweakEngine(adb, journal) }

    /** True once the first real frame of Home can be shown (apps + their artwork ready). Read by the splash screen. */
    @Volatile
    var splashReady: Boolean = false
        private set

    private var loadJob: Job? = null
    private var prefetching = false
    private var resumedOnce = false

    /** True while Home is the visible screen. "Close all apps" only runs while this is true. */
    @Volatile private var homeVisible = false
    private val _homeVisibleFlow = MutableStateFlow(false)
    /** True only while the launcher is the foreground activity (the idle timer must not run otherwise). */
    val homeVisibleFlow: StateFlow<Boolean> = _homeVisibleFlow.asStateFlow()
    private var closeAppsJob: Job? = null

    /** Set when the app list may be out of date (an app was launched, installed, removed or updated). */
    @Volatile private var appsDirty = true
    @Volatile private var lastLoadAt = 0L
    private var pkgReloadJob: Job? = null
    private var lastSnapshotSig = 0

    // Clock formatters, created once. They MUST be declared above the init block, which already reads the clock.
    private var timeFmt: SimpleDateFormat? = null
    private var timeFmtIs24h = false
    private val dateFmt = SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH)
    private var lastCloseAt = -10_000L

    /** Apps this launcher has started during this process, so they can be closed again even if hidden from Home. */
    private val launchedPkgs: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private val pkgReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val changed = i?.data?.schemeSpecificPart
            if (changed == ctx.packageName) return
            appsDirty = true
            pkgReloadJob?.cancel()
            // Debounced: installing an app fires several broadcasts in a row.
            pkgReloadJob = viewModelScope.launch {
                delay(1500)
                loadApps(silent = true)
            }
        }
    }

    @Volatile private var usageCache: Map<String, SysUsage>? = null
    @Volatile private var usageCacheAt = 0L

    private var monitorJob: Job? = null
    private var shareServer: com.leantv.app.share.QuickShareServer? = null

    // Native Quick Share receiver service binding
    private var nativeQsService: com.leantv.app.share.NativeQuickShareReceiver? = null
    private var nativeQsConnection: android.content.ServiceConnection? = null

    private val _state = MutableStateFlow(
        LauncherState(
            wallpaperTheme = journal.wallpaperTheme,
            customWallpaperUri = journal.customWallpaperUri,
            is24hClock = journal.clockFormat24h,
            showGreeting = journal.showGreeting,
            closeAppsOnHome = journal.closeAppsOnHome
        )
    )
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    init {
        // 1) Everything needed for the first frame, nothing else.
        val (greeting, time, date) = clockFields()
        _state.update {
            it.copy(
                greetingText = greeting, timeText = time, dateText = date,
                screensaverMode = com.leantv.app.ui.ScreensaverSource.entries
                    .find { m -> m.id == journal.screensaverMode } ?: com.leantv.app.ui.ScreensaverSource.AERIAL,
                screensaverCustomPath = journal.screensaverCustomPath,
                screensaverIdleMinutes = journal.screensaverIdleMinutes,
                screensaverAutoCache = journal.screensaverAutoCache,
                screensaverSelectedUrl = journal.screensaverSelectedUrl
            )
        }
        startTimeTicker()
        val restored = applySnapshot()
        loadApps(silent = restored)
        if (restored) prefetchAndRelease((_state.value.dockApps + _state.value.allApps).distinct())

        // Installs / uninstalls / updates refresh Home by themselves (instead of re-scanning on every resume).
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            androidx.core.content.ContextCompat.registerReceiver(
                ctx, pkgReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}

        // 2) Everything else waits until Home is already on screen.
        deferredStartup()
    }

    /**
     * Start-up work that is NOT needed for the first frame. It runs in small steps, spread over the first seconds,
     * so none of it competes with the splash hand-off, the first scroll or launching your first app.
     */
    private fun deferredStartup() {
        checkFirstRunDebloat()
        viewModelScope.launch {
            delay(1500)
            launch(Dispatchers.IO) { detectDevice() }
            checkDefaultLauncherPrompt()
            loadProfile()

            delay(700)
            startObservingTvInputs()
            fixKeyboard(userInitiated = false)
            launch(Dispatchers.IO) {
                try {
                    com.leantv.app.files.FileManagerHelper.ensureQuickShareDir()
                } catch (_: Exception) {}
            }

            delay(1500)
            syncAccountsToApps()
        }
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

    @Synchronized
    private fun clockFields(): Triple<String, String, String> {
        val now = Date()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 4..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }
        val is24h = journal.clockFormat24h
        val cachedFmt = timeFmt
        val fmt = if (cachedFmt != null && timeFmtIs24h == is24h) {
            cachedFmt
        } else {
            SimpleDateFormat(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()).also {
                timeFmt = it
                timeFmtIs24h = is24h
            }
        }
        val timeStr = fmt.format(now).uppercase()
        val dateStr = dateFmt.format(now).uppercase()
        return Triple(greeting, timeStr, dateStr)
    }

    private fun startTimeTicker() {
        // Off the main thread: the network lookup is a binder call and must never delay a frame.
        // The clock text only changes once a minute, so there is no reason to wake up every second.
        viewModelScope.launch(Dispatchers.Default) {
            var net: Pair<String, Boolean> = Pair("Wi-Fi", true)
            var lastNetAt = 0L
            while (isActive) {
                val (greeting, timeStr, dateStr) = clockFields()
                val nowMs = SystemClock.elapsedRealtime()
                if (nowMs - lastNetAt >= 5_000L) {
                    net = getNetworkStatus()
                    lastNetAt = nowMs
                }
                _state.update {
                    if (it.greetingText == greeting && it.timeText == timeStr && it.dateText == dateStr &&
                        it.networkLabel == net.first && it.networkIsConnected == net.second
                    ) it
                    else it.copy(
                        greetingText = greeting,
                        timeText = timeStr,
                        dateText = dateStr,
                        networkLabel = net.first,
                        networkIsConnected = net.second
                    )
                }
                val msToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L
                delay(minOf(5_000L, msToNextMinute))
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

    /** Starts the first intent an installed app can handle. Back from it always lands on our Home. */
    private fun launchFirstResolvable(intents: List<Intent>): Boolean =
        TvInputHelper.startFirstResolvable(ctx, intents)

    fun openNetworkSettings() {
        // Direct, universal Wi-Fi screen first; OEM-specific / generic screens only as fallbacks.
        val ok = launchFirstResolvable(
            listOf(
                Intent(Settings.ACTION_WIFI_SETTINGS),
                Intent("android.settings.WIFI_SETTINGS"),
                Intent().setComponent(android.content.ComponentName("com.android.tv.settings", "com.android.tv.settings.connectivity.NetworkActivity")),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
        )
        if (!ok) toast("Couldn't open Network settings.")
    }

    /** Remote Home key: close every popup / full-screen page and show plain Home. */
    fun goHome() {
        if (_state.value.showMonitorPanel) stopMonitorPolling()
        stopQsCountdownTicker()
        _state.update {
            it.copy(
                showStorageManager = false,
                showFileManager = false,
                fileManagerInitialDir = null,
                fileManagerHighlightFile = null,
                showPersonalization = false,
                showCleanTvScreen = false,
                showScreensaver = false,
                showScreensaverSettings = false,
                showNativeQuickShare = false,
                showInputsDialog = false,
                showTvPlayer = false,
                activeInputId = null,
                showProfileDialog = false,
                showMonitorPanel = false,
                showCleanTvWizard = false,
                showQuickShareHub = false,
                showQuickShare = false,
                debloatSuccessSummary = null,
                actionMenuApp = null,
                uninstallConfirmApp = null,
                showDefaultPrompt = false,
                homeResetTick = it.homeResetTick + 1
            )
        }
    }

    /**
     * Handles the Back key from the remote.
     * Dismisses any active modal/dialog/sub-screen.
     * If already at the root Home screen, does nothing (never exits or restarts).
     */
    fun handleBackPress(): Boolean {
        val s = _state.value
        return when {
            s.nativeQuickShareState.incomingTransfer != null -> {
                val t = s.nativeQuickShareState.incomingTransfer
                if (t?.phase == com.leantv.app.share.TransferPhase.WAITING_CONSENT) {
                    rejectNativeTransfer()
                } else {
                    dismissIncomingTransfer()
                }
                true
            }
            s.showScreensaver -> { closeScreensaver(); true }
            s.showTvPlayer -> { closeTvPlayer(); true }
            s.showScreensaverSettings -> { closeScreensaverSettings(); true }
            s.showCleanTvScreen -> { closeCleanTvScreen(); true }
            s.showStorageManager -> { closeStorageManager(); true }
            s.showFileManager -> { closeFileManager(); true }
            s.showPersonalization -> { closePersonalization(); true }
            s.showQuickShare -> { closeQuickShareControl(); true }
            s.showNativeQuickShare -> { closeNativeQuickShare(); true }
            s.showInputsDialog -> { closeInputs(); true }
            s.showProfileDialog -> { closeProfile(); true }
            s.showCleanTvWizard -> { dismissCleanTvWizard(); true }
            s.debloatSuccessSummary != null -> { dismissDebloatSummary(); true }
            s.showMonitorPanel -> { closeSystemMonitor(); true }
            s.uninstallConfirmApp != null -> { dismissUninstallConfirm(); true }
            s.actionMenuApp != null -> { dismissActionMenu(); true }
            s.showDefaultPrompt -> { dismissDefaultPrompt(dontAskAgain = false); true }
            s.showQuickShareHub -> { closeQuickShareHub(); true }
            else -> false // At root Home: safely consumed with no action
        }
    }

    fun onResume() {
        homeVisible = true
        _homeVisibleFlow.value = true
        // Coming back from another app / input: the idle countdown starts fresh from NOW,
        // otherwise a stale timestamp would pop the screensaver up the instant Home appears.
        resetIdleTimer()
        // The very first resume happens right after init, which already loaded everything.
        if (resumedOnce) {
            com.leantv.app.ui.TvSound.playAppClose()
            viewModelScope.launch(Dispatchers.IO) {
                // Defer background sync so resuming and landing animations stay 100% fluid at 60fps
                delay(1800)
                // Re-scan only when something changed (app launched / installed / removed) or the list is old.
                if (appsDirty || SystemClock.elapsedRealtime() - lastLoadAt > 60_000L) loadApps(silent = true)
                loadProfile()
                syncAccountsToApps()
            }
        }
        resumedOnce = true
        if (_state.value.showMonitorPanel) {
            startMonitorPolling()
        }
    }

    fun onPause() {
        homeVisible = false
        _homeVisibleFlow.value = false
        // Another app is coming to the front: never kill anything while it is starting.
        closeAppsJob?.cancel()
        closeAppsJob = null
    }

    // ---------- Close all apps when returning to Home ----------

    fun toggleCloseAppsOnHome() {
        val enabled = !journal.closeAppsOnHome
        journal.closeAppsOnHome = enabled
        _state.update {
            it.copy(
                closeAppsOnHome = enabled,
                toastMessage = if (enabled) "Apps now close when you press Home" else "Apps stay open in the background"
            )
        }
    }

    /** Safe no-op: apps are left in their natural Android task lifecycle so the OS never restarts them. */
    fun closeAppsSoon() {
        // No-op: Killing background processes caused Android to resurrect paused tasks
    }

    /** Packages of the default keyboard and enabled accessibility services (remote-mapper apps etc.). */
    private fun dynamicProtectedPackages(): Set<String> {
        val out = HashSet<String>()
        // Every other Home launcher on the TV (stock launcher, vendor launchers) must never be killed.
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            @Suppress("DEPRECATION")
            ctx.packageManager.queryIntentActivities(homeIntent, 0).forEach { ri ->
                ri.activityInfo?.packageName?.let { out.add(it) }
            }
        } catch (_: Throwable) {}
        try {
            val cr = ctx.contentResolver
            Settings.Secure.getString(cr, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')
                ?.forEach { s -> s.substringBefore('/').takeIf { it.isNotBlank() }?.let { out.add(it) } }
        } catch (_: Throwable) {}
        return out
    }

    /**
     * Ends the background processes of every app we know about except this launcher, system essentials and the
     * keyboard / accessibility services. Apps playing music or running a foreground service are left alone by Android.
     */
    private fun closeAllAppsNow(): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return 0
        val targets = LinkedHashSet<String>()
        for (a in _state.value.allApps) targets.add(a.packageName)
        targets.addAll(launchedPkgs)
        try {
            am.runningAppProcesses?.forEach { p -> p.pkgList?.forEach { targets.add(it) } }
        } catch (_: Throwable) {}

        val keep = dynamicProtectedPackages()
        val me = ctx.packageName
        var closed = 0
        for (pkg in targets) {
            if (pkg == me || pkg in keep || isProtectedPackage(pkg)) continue
            try {
                am.killBackgroundProcesses(pkg)
                closed++
            } catch (_: Throwable) {}
        }
        return closed
    }

    override fun onCleared() {
        super.onCleared()
        try { ctx.unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        stopMonitorPolling()
        stopQuickShare()
        adb.close()
    }

    // ---------- App Organization: Frequently Used, Recently Used, Dock, All Apps (Usage-based) ----------

    private data class SysUsage(val timeInForeground: Long, val lastTimeUsed: Long)

    private fun getSystemUsageMap(): Map<String, SysUsage> {
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                ?: return emptyMap()
            val now = System.currentTimeMillis()
            val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_WEEKLY, thirtyDaysAgo, now)
            val map = mutableMapOf<String, SysUsage>()
            stats?.forEach { s ->
                val prev = map[s.packageName]
                val time = (prev?.timeInForeground ?: 0L) + s.totalTimeInForeground
                val last = maxOf(prev?.lastTimeUsed ?: 0L, s.lastTimeUsed)
                map[s.packageName] = SysUsage(time, last)
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun getSystemUsageMapCached(): Map<String, SysUsage> {
        val now = SystemClock.elapsedRealtime()
        val cached = usageCache
        if (cached != null && now - usageCacheAt < 20_000L) return cached
        val fresh = getSystemUsageMap()
        usageCache = fresh
        usageCacheAt = now
        return fresh
    }

    private fun computeUsageScore(pkg: String, sys: SysUsage?): Long {
        val internalCount = journal.getLaunchCount(pkg)
        val internalLast = journal.getLastLaunchTime(pkg)
        val effectiveLast = maxOf(internalLast, sys?.lastTimeUsed ?: 0L)
        val fgTime = sys?.timeInForeground ?: 0L

        val now = System.currentTimeMillis()
        val recencyScore = when {
            effectiveLast <= 0L -> 0L
            now - effectiveLast < 24L * 60 * 60 * 1000 -> 500_000L
            now - effectiveLast < 7L * 24 * 60 * 60 * 1000 -> 200_000L
            now - effectiveLast < 30L * 24 * 60 * 60 * 1000 -> 50_000L
            else -> 10_000L
        }

        // TV streaming and entertainment apps get a welcoming initial boost if brand new install
        val streamingBoost = if (internalCount == 0 && fgTime == 0L) {
            when {
                pkg.contains("netflix") || pkg.contains("youtube") || pkg.contains("amazonvideo") ||
                pkg.contains("hulu") || pkg.contains("disney") || pkg.contains("hotstar") ||
                pkg.contains("plex") || pkg.contains("kodi") || pkg.contains("smarttube") ||
                pkg.contains("spotify") || pkg.contains("twitch") || pkg.contains("vlc") -> 40_000L
                else -> 0L
            }
        } else 0L

        return (internalCount * 100_000L) + (fgTime / 60_000L * 1_000L) + recencyScore + streamingBoost
    }

    fun setAppSortMode(mode: AppSortMode) {
        journal.appSortMode = mode.name
        _state.update { it.copy(appSortMode = mode) }
        loadApps(silent = true)
    }

    fun toggleAppSortMode() {
        val next = if (_state.value.appSortMode == AppSortMode.RECENCY) AppSortMode.ALPHABETICAL else AppSortMode.RECENCY
        setAppSortMode(next)
    }

    private class AppLists(
        val favorites: List<InstalledAppInfo>,
        val dock: List<InstalledAppInfo>,
        val frequent: List<InstalledAppInfo>,
        val recent: List<InstalledAppInfo>,
        val all: List<InstalledAppInfo>,
    )

    private fun currentSortMode(): AppSortMode = try {
        AppSortMode.valueOf(journal.appSortMode)
    } catch (_: Exception) {
        AppSortMode.RECENCY
    }

    private fun sortLaunchable(
        apps: List<InstalledAppInfo>,
        mode: AppSortMode,
        sysUsage: Map<String, SysUsage>?,
    ): List<InstalledAppInfo> = when (mode) {
        AppSortMode.RECENCY -> {
            // Compute each sort key once instead of inside the comparator.
            val keys = HashMap<String, Long>(apps.size * 2)
            for (a in apps) {
                keys[a.packageName] = maxOf(
                    journal.getLastLaunchTime(a.packageName),
                    sysUsage?.get(a.packageName)?.lastTimeUsed ?: 0L,
                    if (journal.getLaunchCount(a.packageName) > 0) 1000L else 0L
                )
            }
            apps.sortedWith(
                compareByDescending<InstalledAppInfo> { keys[it.packageName] ?: 0L }
                    .thenBy { it.appName.lowercase(Locale.ROOT) }
            )
        }
        AppSortMode.ALPHABETICAL -> apps.sortedBy { it.appName.lowercase(Locale.ROOT) }
    }

    /** Heavy part of loading: pure computation, runs on a background dispatcher. */
    private fun computeLists(launchable: List<InstalledAppInfo>, mode: AppSortMode): AppLists {
        val favPackages = journal.favorites()
        val favorites = launchable.filter { it.packageName in favPackages }
        val sysUsage = getSystemUsageMapCached()

        val scores = HashMap<String, Long>(launchable.size * 2)
        for (a in launchable) scores[a.packageName] = computeUsageScore(a.packageName, sysUsage[a.packageName])
        val byScore = compareByDescending<InstalledAppInfo> { scores[it.packageName] ?: 0L }
            .thenBy { it.appName.lowercase(Locale.ROOT) }

        // Dock: first 4 favorites, otherwise top launchable ranked by usage
        val dock = if (favorites.isNotEmpty()) favorites.take(4) else launchable.sortedWith(byScore).take(4)

        // Frequently used: launch count > 0 or foreground time > 0
        val frequent = launchable
            .filter { journal.getLaunchCount(it.packageName) > 0 || (sysUsage[it.packageName]?.timeInForeground ?: 0L) > 0L }
            .sortedWith(byScore)
            .take(6)

        // Recently used: last launch timestamp > 0
        val recent = launchable
            .filter { journal.getLastLaunchTime(it.packageName) > 0L || (sysUsage[it.packageName]?.lastTimeUsed ?: 0L) > 0L }
            .sortedByDescending {
                maxOf(journal.getLastLaunchTime(it.packageName), sysUsage[it.packageName]?.lastTimeUsed ?: 0L)
            }
            .take(6)

        return AppLists(favorites, dock, frequent, recent, sortLaunchable(launchable, mode, sysUsage))
    }

    fun loadApps(silent: Boolean = false) {
        if (!silent && _state.value.allApps.isEmpty()) _state.update { it.copy(isLoading = true) }
        loadJob?.cancel()
        appsDirty = false
        lastLoadAt = SystemClock.elapsedRealtime()
        loadJob = viewModelScope.launch {
            val installed = InstalledAppsScanner.scanLaunchable(ctx)

            val lists = withContext(Dispatchers.Default) {
                val hiddenSet = journal.hiddenApps()
                // Keep launchable apps, excluding LeanTV itself and hidden apps
                val launchable = installed.filter {
                    it.launchIntentAvailable && it.packageName != ctx.packageName && it.packageName !in hiddenSet
                }
                val mode = currentSortMode()

                if (_state.value.allApps.isEmpty()) {
                    // Very first start (no saved snapshot yet): paint right away with a cheap ordering.
                    val favPackages = journal.favorites()
                    val quickFav = launchable.filter { it.packageName in favPackages }
                    val quickAll = sortLaunchable(launchable, mode, null)
                    val quickDock = if (quickFav.isNotEmpty()) quickFav.take(4) else quickAll.take(4)
                    _state.update {
                        it.copy(
                            appSortMode = mode,
                            featuredApps = quickFav.take(5),
                            favoriteApps = quickFav,
                            dockApps = quickDock,
                            allApps = quickAll,
                            isLoading = false,
                        )
                    }
                    withContext(Dispatchers.Main) { prefetchAndRelease((quickDock + quickAll).distinct()) }
                }
                computeLists(launchable, mode)
            }

            _state.update {
                it.copy(
                    appSortMode = currentSortMode(),
                    featuredApps = lists.favorites.take(5),
                    favoriteApps = lists.favorites,
                    dockApps = lists.dock,
                    frequentApps = lists.frequent,
                    recentApps = lists.recent,
                    allApps = lists.all,
                    isLoading = false,
                )
            }
            // Only rewrite the saved layout when it really changed (it is rebuilt after every launch otherwise).
            val sig = layoutSignature(_state.value)
            if (sig != lastSnapshotSig) {
                lastSnapshotSig = sig
                saveSnapshot()
            }
            if (!prefetching) splashReady = true
        }
    }

    private fun layoutSignature(s: LauncherState): Int {
        var h = s.allApps.size
        for (a in s.allApps) h = h * 31 + a.packageName.hashCode()
        for (a in s.dockApps) h = h * 31 + a.packageName.hashCode() + 7
        for (a in s.favoriteApps) h = h * 31 + a.packageName.hashCode() + 13
        for (a in s.frequentApps) h = h * 31 + a.packageName.hashCode() + 17
        for (a in s.recentApps) h = h * 31 + a.packageName.hashCode() + 19
        return h
    }

    // ---------- Instant start: remember the last home layout ----------

    private fun saveSnapshot() {
        val s = _state.value
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val apps = JSONArray()
                for (a in s.allApps) {
                    apps.put(
                        JSONObject()
                            .put("p", a.packageName)
                            .put("n", a.appName)
                            .put("s", a.isSystemApp)
                            .put("u", a.isUpdatedSystemApp)
                    )
                }
                fun pkgs(list: List<InstalledAppInfo>): JSONArray {
                    val arr = JSONArray()
                    for (a in list) arr.put(a.packageName)
                    return arr
                }
                val json = JSONObject()
                    .put("apps", apps)
                    .put("dock", pkgs(s.dockApps))
                    .put("fav", pkgs(s.favoriteApps))
                    .put("freq", pkgs(s.frequentApps))
                    .put("recent", pkgs(s.recentApps))
                journal.appSnapshot = json.toString()
            } catch (_: Throwable) {}
        }
    }

    /** Fills the state from the last saved layout. Returns true if Home can paint immediately. */
    private fun applySnapshot(): Boolean {
        val raw = journal.appSnapshot ?: return false
        try {
            val root = JSONObject(raw)
            val arr = root.getJSONArray("apps")
            val byPkg = LinkedHashMap<String, InstalledAppInfo>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val pkg = o.getString("p")
                if (pkg == ctx.packageName) continue
                byPkg[pkg] = InstalledAppInfo(
                    packageName = pkg,
                    appName = o.optString("n", pkg),
                    versionName = "",
                    versionCode = 0L,
                    isSystemApp = o.optBoolean("s", false),
                    isUpdatedSystemApp = o.optBoolean("u", false),
                    installTimeMillis = 0L,
                    lastUpdateTimeMillis = 0L,
                    sizeBytes = 0L,
                    launchIntentAvailable = true,
                )
            }
            if (byPkg.isEmpty()) return false

            fun pick(key: String): List<InstalledAppInfo> {
                val ja = root.optJSONArray(key) ?: return emptyList()
                val out = ArrayList<InstalledAppInfo>()
                for (i in 0 until ja.length()) {
                    val app = byPkg[ja.optString(i)]
                    if (app != null) out.add(app)
                }
                return out
            }

            val fav = pick("fav")
            _state.update {
                it.copy(
                    appSortMode = currentSortMode(),
                    featuredApps = fav.take(5),
                    favoriteApps = fav,
                    dockApps = pick("dock"),
                    frequentApps = pick("freq"),
                    recentApps = pick("recent"),
                    allApps = byPkg.values.toList(),
                    isLoading = false,
                )
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    /**
     * Loads the artwork for what is visible on the first screen (dock + first rows) in parallel, then releases the
     * splash screen. Home therefore appears with its banners already in place instead of popping in tile by tile.
     * Bounded by a timeout so a slow icon can never hold the splash.
     */
    private fun prefetchAndRelease(apps: List<InstalledAppInfo>) {
        if (splashReady || prefetching) return
        prefetching = true
        viewModelScope.launch {
            // Short on purpose: opening fast matters more than having every banner ready on frame one.
            withTimeoutOrNull(300L) {
                apps.take(12).map { app ->
                    async(Dispatchers.IO) { IconCache.loadVisuals(ctx, app.packageName) }
                }.awaitAll()
            }
            splashReady = true
            // Warm the rest of the artwork one-by-one in the background so scrolling never shows tiles popping in.
            launch(Dispatchers.IO) {
                // Launch intents first (cheap), so pressing OK never waits on the package manager.
                for (app in apps.take(60)) {
                    if (!launchIntentCache.containsKey(app.packageName)) {
                        resolveLaunchIntent(app.packageName)?.let { launchIntentCache[app.packageName] = it }
                    }
                }
                for (app in apps.drop(12).take(48)) {
                    IconCache.loadVisuals(ctx, app.packageName)
                    kotlinx.coroutines.yield()
                }
            }
        }
    }

    // ---------- Fast launch ----------
    // Launch intents are resolved ahead of time (when a tile gets focus, and in the background after start-up),
    // so pressing OK never waits on the package manager.
    private val launchIntentCache = java.util.concurrent.ConcurrentHashMap<String, Intent>()
    private val resolvingLaunch: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun resolveLaunchIntent(pkg: String): Intent? {
        val pm = ctx.packageManager
        var intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            try {
                val mainIntent = Intent(Intent.ACTION_MAIN).setPackage(pkg)
                val resolves = pm.queryIntentActivities(mainIntent, 0)
                val act = resolves.firstOrNull()?.activityInfo
                    ?: runCatching {
                        pm.getPackageInfo(pkg, android.content.pm.PackageManager.GET_ACTIVITIES).activities?.firstOrNull { it.exported }
                    }.getOrNull()
                if (act != null) {
                    intent = Intent(Intent.ACTION_MAIN).apply {
                        component = android.content.ComponentName(pkg, act.name)
                    }
                }
            } catch (_: Exception) {}
        }
        return intent
    }

    /** Called when a tile gets focus: resolves its launch intent off the main thread. */
    fun prewarmLaunch(pkg: String) {
        if (launchIntentCache.containsKey(pkg) || !resolvingLaunch.add(pkg)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                resolveLaunchIntent(pkg)?.let { launchIntentCache[pkg] = it }
            } finally {
                resolvingLaunch.remove(pkg)
            }
        }
    }

    fun launch(app: InstalledAppInfo, options: android.app.ActivityOptions? = null) {
        // 1) Start the app FIRST. Nothing else may run on the main thread before startActivity.
        val base = launchIntentCache[app.packageName] ?: resolveLaunchIntent(app.packageName)
        if (base == null) {
            toast("No launchable TV activity found for ${app.appName}")
            return
        }
        try {
            val intent = Intent(base)
            // Anchor launched app directly on top of Home task so Back button always returns directly to Home!
            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                Intent.FLAG_ACTIVITY_TASK_ON_HOME
            )
            intent.putExtra("leantv_launched", true)
            // With tile bounds the caller passes makeScaleUpAnimation (native zoom from the tile).
            // Without bounds, use an instant cut so the app's own splash appears immediately.
            val launchOptions = options?.toBundle()
                ?: android.app.ActivityOptions.makeCustomAnimation(ctx, 0, 0).toBundle()
            ctx.startActivity(intent, launchOptions)
            launchIntentCache.putIfAbsent(app.packageName, base)
        } catch (e: Exception) {
            launchIntentCache.remove(app.packageName)
            toast("Couldn't open app: ${e.message}")
            return
        }

        // 2) Bookkeeping AFTER the app is starting, and never while it is starting: the disk write goes to IO and the
        //    list refresh (re-sort by recency) waits until the app is fully up (8 s), and only if Home is still hidden.
        launchedPkgs.add(app.packageName)
        appsDirty = true
        closeAppsJob?.cancel()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { journal.recordAppLaunch(app.packageName) }
            delay(8000)
            if (!homeVisible) loadApps(silent = true)
        }
    }

    /** Returns ActivityOptions that zoom from the given screen rect (Apple TV launch zoom). */
    fun makeLaunchOptions(view: android.view.View, x: Int, y: Int, w: Int, h: Int): android.app.ActivityOptions =
        android.app.ActivityOptions.makeScaleUpAnimation(view, x, y, w, h)

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
        viewModelScope.launch {
            toast("Uninstalling ${app.appName}...")
            val res = com.leantv.app.data.SystemDebloater.uninstallOrDisable(
                ctx = ctx,
                pkg = app.packageName,
                label = app.appName,
                isSystemApp = app.isSystemApp,
                adbClient = adb,
                journal = journal
            )
            when (res) {
                is com.leantv.app.data.DebloatResult.Success -> {
                    com.leantv.app.ui.TvSound.playUninstall()
                    toast(res.message)
                    loadApps(silent = true)
                }
                is com.leantv.app.data.DebloatResult.NeedsPrivilege -> {
                    toast(res.message)
                }
                is com.leantv.app.data.DebloatResult.Failure -> {
                    toast(res.reason)
                }
            }
        }
    }

    // ---------- Hardware TV Input Switcher (Feature #3) ----------

    private fun startObservingTvInputs() {
        viewModelScope.launch {
            TvInputHelper.observeInputs(ctx).flowOn(Dispatchers.IO).collect { ports ->
                _state.update { it.copy(tvPorts = ports) }
            }
        }
    }

    fun openInputs() {
        // Show the dialog straight away with what we already know, then refresh off the main thread.
        _state.update { it.copy(showInputsDialog = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val ports = TvInputHelper.queryHardwareInputs(ctx)
            _state.update { it.copy(tvPorts = ports) }
        }
    }

    fun closeInputs() {
        _state.update { it.copy(showInputsDialog = false) }
    }

    fun closeTvPlayer() {
        _state.update { it.copy(showTvPlayer = false, activeInputId = null) }
    }

    /** Ports we can show inside our own player (real passthrough inputs: HDMI, AV, component). */
    private fun canPlayInApp(port: TvPort) =
        port.isPassthrough && port.kind != "TV" && !port.id.startsWith("default_")

    fun switchToPort(port: TvPort) {
        if (canPlayInApp(port)) {
            _state.update { it.copy(showInputsDialog = false, showTvPlayer = true, activeInputId = port.id) }
            return
        }
        closeTvPlayer()
        closeInputs()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) { TvInputHelper.switchToPort(ctx, port) }
            if (!ok && !pressRemoteKey(178)) { // 178 = KEYCODE_TV_INPUT
                toast("Unable to switch directly to ${port.label}")
            }
        }
    }

    /** The in-app player could not open this input: hand over to the system source picker. */
    fun tvPlayerFailed(port: TvPort?) {
        closeTvPlayer()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.Default) {
                if (port != null) TvInputHelper.switchToPort(ctx, port) else TvInputHelper.openNativeTvInputs(ctx)
            }
            if (!ok && !pressRemoteKey(178)) toast("Couldn't open this input.")
        }
    }

    // ---------- Google Account Profile (Feature #6) ----------

    fun loadProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            val profile = TvAccountHelper.getConnectedProfile(ctx)
            _state.update {
                it.copy(
                    tvProfile = profile,
                    currentAccountName = profile.displayName
                )
            }
        }
    }

    /** Corner account chip: go straight to the TV's own account screens (no separate sign-in in this app). */
    fun openAccountsNative() {
        val has = _state.value.tvProfile.email != null
        if (!TvAccountHelper.openAccountsNative(ctx, has)) toast("Couldn't open Accounts.")
    }

    private var lastAccountSyncMs = 0L

    /**
     * Lets apps like YouTube see the TV's Google account. Silent (Shizuku/root only) unless the user
     * asked for it, in which case it may also connect to on-device ADB.
     */
    fun syncAccountsToApps(userInitiated: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!userInitiated && now - lastAccountSyncMs < 120_000L) return
        lastAccountSyncMs = now
        viewModelScope.launch(Dispatchers.IO) {
            val n = TvAccountHelper.grantAccountAccessToApps(ctx, adb, allowAdbConnect = userInitiated)
            if (userInitiated) {
                val hasAccount = TvAccountHelper.getConnectedProfile(ctx).email != null
                toast(
                    when {
                        !hasAccount -> "No Google account on this TV yet. Tap the account chip to add one."
                        n > 0 -> "Account shared with $n app(s). Reopen YouTube / Play Store."
                        else -> "Apps already have access. If YouTube still asks to sign in, it can't use the system account."
                    }
                )
            }
        }
    }

    /** Fixes the on-screen keyboard (Gboard). Silent at start-up; user-triggered may use on-device ADB. */
    fun fixKeyboard(userInitiated: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!userInitiated && com.leantv.app.ui.KeyboardHelper.isReady(ctx)) return@launch
            val ok = com.leantv.app.ui.KeyboardHelper.repair(ctx, adb, allowAdbConnect = userInitiated)
            if (!userInitiated) return@launch
            if (ok) {
                toast("Keyboard is ready. Click any text box to type.")
            } else {
                toast("Please enable Gboard in the keyboard settings that open now.")
                withContext(Dispatchers.Main) { com.leantv.app.ui.KeyboardHelper.openKeyboardSettings(ctx) }
            }
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

    // ---------- TV File Manager Screen (Amaze TV Style) ----------

    fun openFileManager(initialDir: java.io.File? = null, highlightFile: String? = null) {
        _state.update {
            it.copy(
                showFileManager = true,
                fileManagerInitialDir = initialDir,
                fileManagerHighlightFile = highlightFile,
                showNativeQuickShare = false
            )
        }
    }

    fun closeFileManager() {
        _state.update {
            it.copy(
                showFileManager = false,
                fileManagerInitialDir = null,
                fileManagerHighlightFile = null
            )
        }
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
        // The ticker only wakes up every few seconds now, so apply the new format immediately.
        val (greeting, timeStr, dateStr) = clockFields()
        _state.update {
            it.copy(is24hClock = newFormat, greetingText = greeting, timeText = timeStr, dateText = dateStr)
        }
    }

    fun toggleGreeting() {
        val newGreeting = !journal.showGreeting
        journal.showGreeting = newGreeting
        _state.update { it.copy(showGreeting = newGreeting) }
    }

    // ---------- Quick Share Dual-Mode (Feature #8) ----------

    private var qsAutoOffJob: Job? = null
    private var qsTickerJob: Job? = null
    @Volatile private var qsDeadlineMs = 0L
    private var lastWebProgressAt = 0L
    private var lastNativeProgressAt = 0L

    /**
     * Shows the "turns off in mm:ss" countdown, but ONLY while the Quick Share panel is open.
     * It used to write a new value into the Home state every second for 15 minutes, which re-ran the whole
     * Home screen once per second, all the time. Now Home is left completely alone.
     */
    private fun startQsCountdownTicker() {
        qsTickerJob?.cancel()
        qsTickerJob = null
        if (!_state.value.isQuickShareActive || qsAutoOffJob?.isActive != true) {
            _state.update { if (it.quickShareAutoOffSeconds == null) it else it.copy(quickShareAutoOffSeconds = null) }
            return
        }
        qsTickerJob = viewModelScope.launch {
            while (isActive && _state.value.showQuickShare) {
                val sec = ((qsDeadlineMs - SystemClock.elapsedRealtime()) / 1000L).toInt().coerceAtLeast(0)
                _state.update { if (it.quickShareAutoOffSeconds == sec) it else it.copy(quickShareAutoOffSeconds = sec) }
                delay(1000)
            }
        }
    }

    private fun stopQsCountdownTicker() {
        qsTickerJob?.cancel()
        qsTickerJob = null
    }

    fun handleQuickShareClick() {
        openQuickShareControl()
    }

    fun openAndroidQuickShare() {
        openQuickShareControl()
    }

    fun openQuickShareControl() {
        _state.update { it.copy(showQuickShare = true) }
        startQsCountdownTicker()
    }

    fun closeQuickShareControl() {
        stopQsCountdownTicker()
        _state.update { it.copy(showQuickShare = false) }
    }

    fun closeQuickShareHub() {
        closeQuickShareControl()
    }

    fun toggleQuickShare(enable: Boolean) {
        if (enable) {
            turnOnQuickShare()
        } else {
            turnOffQuickShare()
        }
    }

    fun turnOnQuickShare() {
        if (!com.leantv.app.files.FileManagerHelper.hasStoragePermission(ctx)) {
            com.leantv.app.files.FileManagerHelper.requestStoragePermission(ctx)
        }
        startNativeQuickShare(openDialog = false, silent = true)
        startWebQuickShare()
        _state.update { it.copy(isQuickShareActive = true, quickShareAutoOffSeconds = null) }
        toast("Quick Share is ON")
    }

    fun turnOffQuickShare() {
        qsAutoOffJob?.cancel()
        qsAutoOffJob = null
        stopQsCountdownTicker()
        stopNativeQuickShare()
        stopWebQuickShare()
        _state.update { it.copy(isQuickShareActive = false, quickShareAutoOffSeconds = null) }
        toast("Quick Share turned OFF")
    }

    /** Quick Share now stays ON until explicitly turned off by the user. */
    fun resetQuickShareInactivityTimer() {
        // No auto-off timer: stays on until user turns it off
    }

    fun startNativeQuickShare(openDialog: Boolean = false, silent: Boolean = true) {
        val intent = android.content.Intent(ctx, com.leantv.app.share.NativeQuickShareReceiver::class.java).apply {
            action = com.leantv.app.share.NativeQuickShareReceiver.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("LauncherVM", "Failed to start NativeQuickShareReceiver", e)
        }

        nativeQsService?.startReceiving()

        if (nativeQsConnection == null) {
            val conn = object : android.content.ServiceConnection {
                override fun onServiceConnected(name: android.content.ComponentName?, binder: android.os.IBinder?) {
                    val svc = (binder as? com.leantv.app.share.NativeQuickShareReceiver.LocalBinder)?.service
                    nativeQsService = svc
                    if (svc != null) {
                        svc.startReceiving()
                        viewModelScope.launch {
                            svc.state.collect { qsState ->
                                val incoming = qsState.incomingTransfer
                                // Same transfer, same phase, only the progress number moved: at most ~7 updates/second.
                                val prevPhase = _state.value.nativeQuickShareState.incomingTransfer?.phase
                                val progressOnly = incoming != null && incoming.phase == prevPhase &&
                                    incoming.phase == com.leantv.app.share.TransferPhase.RECEIVING
                                val nowMs = SystemClock.elapsedRealtime()
                                if (progressOnly && nowMs - lastNativeProgressAt < 150L) return@collect
                                if (progressOnly) lastNativeProgressAt = nowMs
                                _state.update { current ->
                                    val currentWebIncoming = current.nativeQuickShareState.incomingTransfer?.takeIf { it.connection == null }
                                    val effectiveIncoming = incoming ?: currentWebIncoming
                                    current.copy(
                                        nativeQuickShareState = qsState.copy(incomingTransfer = effectiveIncoming)
                                    )
                                }

                                if (incoming != null) {
                                    resetQuickShareInactivityTimer()
                                }

                                if (incoming?.phase == com.leantv.app.share.TransferPhase.COMPLETED) {
                                    resetQuickShareInactivityTimer()
                                    viewModelScope.launch {
                                        delay(1200)
                                        val fileOnDisk = qsState.lastReceivedFiles.firstOrNull()?.let { java.io.File(it.path) }
                                        val qsDir = fileOnDisk?.parentFile ?: com.leantv.app.files.FileManagerHelper.ensureQuickShareDir()
                                        openFileManager(
                                            initialDir = qsDir,
                                            highlightFile = fileOnDisk?.name
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                override fun onServiceDisconnected(name: android.content.ComponentName?) {
                    nativeQsService = null
                    nativeQsConnection = null
                }
            }
            nativeQsConnection = conn
            try {
                ctx.bindService(intent, conn, android.content.Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                android.util.Log.e("LauncherVM", "Failed to bind NativeQuickShareReceiver", e)
            }
        }

        if (openDialog) {
            openQuickShareControl()
        }
    }

    fun acceptNativeTransfer() {
        nativeQsService?.submitConsent(true)
        _state.value.nativeQuickShareState.incomingTransfer?.onConsent?.invoke(true)
        resetQuickShareInactivityTimer()
    }

    fun rejectNativeTransfer() {
        nativeQsService?.submitConsent(false)
        _state.value.nativeQuickShareState.incomingTransfer?.onConsent?.invoke(false)
        resetQuickShareInactivityTimer()
        _state.update { current ->
            current.copy(
                nativeQuickShareState = current.nativeQuickShareState.copy(incomingTransfer = null)
            )
        }
    }

    fun dismissIncomingTransfer() {
        nativeQsService?.submitConsent(false)
        _state.value.nativeQuickShareState.incomingTransfer?.onConsent?.invoke(false)
        _state.update { current ->
            current.copy(
                nativeQuickShareState = current.nativeQuickShareState.copy(incomingTransfer = null)
            )
        }
    }

    fun closeNativeQuickShare() {
        closeQuickShareControl()
    }

    fun stopNativeQuickShare() {
        try {
            nativeQsService?.stopReceiving()
        } catch (_: Exception) {}
        try {
            nativeQsConnection?.let { ctx.unbindService(it) }
        } catch (_: Exception) {}
        nativeQsConnection = null
        nativeQsService = null
        try {
            val stopIntent = android.content.Intent(ctx, com.leantv.app.share.NativeQuickShareReceiver::class.java).apply {
                action = com.leantv.app.share.NativeQuickShareReceiver.ACTION_STOP
            }
            ctx.startService(stopIntent)
            ctx.stopService(stopIntent)
        } catch (_: Exception) {}
        _state.update { it.copy(nativeQuickShareState = com.leantv.app.share.NativeQuickShareState()) }
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
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                ctx.startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
                toast("Opening Google Quick Share...")
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun launchGoogleQuickShareStore() {
        closeQuickShareHub()
        try {
            val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.gms")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            }
            ctx.startActivity(storeIntent, android.app.ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
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
                intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_TASK_ON_HOME
                )
                intent.putExtra("leantv_launched", true)
                ctx.startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
                killBackgroundApps(exceptPackage = pkg)
            } catch (e: Exception) {
                toast("Couldn't open LocalSend: ${e.message}")
            }
        } else {
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                }
                ctx.startActivity(storeIntent, android.app.ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
            } catch (_: Exception) {
                toast("LocalSend not installed on TV. Opening Universal Wi-Fi AirShare...")
                openQuickShare()
            }
        }
    }

    fun openQuickShare() {
        openQuickShareControl()
    }

    fun closeQuickShare() {
        closeQuickShareControl()
    }

    fun startWebQuickShare() {
        if (shareServer != null) return

        val server = com.leantv.app.share.QuickShareServer(
            context = ctx,
            scope = viewModelScope,
            onRequestConsent = { sender, fileName, fileSize ->
                resetQuickShareInactivityTimer()
                val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
                val fileInfo = com.leantv.app.share.TransferFileInfo(
                    name = fileName,
                    sizeBytes = fileSize,
                    mimeType = null
                )
                val incoming = com.leantv.app.share.IncomingTransfer(
                    senderName = sender,
                    pin = "",
                    files = listOf(fileInfo),
                    phase = com.leantv.app.share.TransferPhase.WAITING_CONSENT,
                    onConsent = { approved ->
                        if (!deferred.isCompleted) deferred.complete(approved)
                    }
                )
                _state.update { s ->
                    s.copy(
                        nativeQuickShareState = s.nativeQuickShareState.copy(incomingTransfer = incoming)
                    )
                }

                // 45s safety timeout for consent dialog
                val timeoutJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(45_000)
                    if (!deferred.isCompleted) {
                        deferred.complete(false)
                        _state.update { s ->
                            s.copy(nativeQuickShareState = s.nativeQuickShareState.copy(incomingTransfer = null))
                        }
                    }
                }
                val result = try {
                    deferred.await()
                } catch (_: Exception) {
                    false
                }
                timeoutJob.cancel()
                result
            },
            onTransferProgress = { fraction, bytesPerSec ->
                resetQuickShareInactivityTimer()
                // At most ~7 UI updates per second: a fast transfer used to re-run the whole screen on every chunk.
                val nowMs = SystemClock.elapsedRealtime()
                if (fraction >= 1f || nowMs - lastWebProgressAt >= 150L) {
                    lastWebProgressAt = nowMs
                    _state.update { s ->
                        val cur = s.nativeQuickShareState.incomingTransfer ?: return@update s
                        s.copy(
                            nativeQuickShareState = s.nativeQuickShareState.copy(
                                incomingTransfer = cur.copy(
                                    phase = com.leantv.app.share.TransferPhase.RECEIVING,
                                    progressFraction = fraction,
                                    bytesPerSecond = bytesPerSec
                                )
                            )
                        )
                    }
                }
            },
            onTransferComplete = { file ->
                resetQuickShareInactivityTimer()
                val received = com.leantv.app.share.ReceivedFile(
                    name = file.name,
                    path = file.absolutePath,
                    sizeBytes = file.length(),
                    timestamp = System.currentTimeMillis()
                )
                _state.update { s ->
                    val cur = s.nativeQuickShareState.incomingTransfer
                    s.copy(
                        nativeQuickShareState = s.nativeQuickShareState.copy(
                            incomingTransfer = cur?.copy(
                                phase = com.leantv.app.share.TransferPhase.COMPLETED,
                                progressFraction = 1f
                            ),
                            lastReceivedFiles = listOf(received) + s.nativeQuickShareState.lastReceivedFiles
                        ),
                        toastMessage = "Received: ${file.name}"
                    )
                }
                viewModelScope.launch {
                    kotlinx.coroutines.delay(3500)
                    _state.update { s ->
                        if (s.nativeQuickShareState.incomingTransfer?.phase == com.leantv.app.share.TransferPhase.COMPLETED) {
                            s.copy(nativeQuickShareState = s.nativeQuickShareState.copy(incomingTransfer = null))
                        } else s
                    }
                }
            },
            onItemReceived = { item ->
                resetQuickShareInactivityTimer()
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
            }
        )

        val started = server.start()
        val ip = server.getLocalIpAddress()
        shareServer = server

        _state.update {
            it.copy(
                quickShareState = it.quickShareState.copy(
                    isRunning = started,
                    ipAddress = ip,
                    port = server.port,
                    pin = "",
                )
            )
        }
    }

    fun stopWebQuickShare() {
        try {
            shareServer?.stop()
        } catch (_: Exception) {}
        shareServer = null
        _state.update {
            it.copy(
                quickShareState = it.quickShareState.copy(
                    isRunning = false,
                    ipAddress = null,
                    pin = "",
                    transferStatus = null
                )
            )
        }
    }

    private fun stopQuickShare() {
        turnOffQuickShare()
    }

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            }
            ctx.startActivity(intent, android.app.ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
        } catch (e: Exception) {
            toast("Couldn't open URL: ${e.message}")
        }
    }

    // ---------- Native TV Settings (Feature #5) ----------

    fun openSystemSettings() {
        // Standard Settings first (works on every Android / Google TV), then known OEM entry points.
        val ok = launchFirstResolvable(
            listOf(
                Intent(Settings.ACTION_SETTINGS),
                Intent().setComponent(android.content.ComponentName("com.android.tv.settings", "com.android.tv.settings.MainSettings")).setAction("android.intent.action.MAIN"),
                Intent().setComponent(android.content.ComponentName("com.android.tv.settings", "com.android.tv.settings.SettingsActivity")),
                Intent("android.settings.SETTINGS")
            )
        )
        if (ok) return
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

    // ---------- ⚡ CleanYourTV Debloater Menu (Feature #9) ----------

    fun requestCleanTv() {
        _state.update { it.copy(showCleanTvScreen = true) }
    }

    fun openCleanTvScreen() {
        _state.update { it.copy(showCleanTvScreen = true) }
    }

    fun closeCleanTvScreen() {
        _state.update { it.copy(showCleanTvScreen = false) }
        loadApps(silent = true)
    }

    @Volatile private var screensaverShownAtMs = 0L

    fun isScreensaverShowing(): Boolean = _state.value.showScreensaver

    fun openScreensaver() {
        screensaverShownAtMs = System.currentTimeMillis()
        _state.update { it.copy(showScreensaver = true) }
    }

    fun closeScreensaver() {
        _state.update { it.copy(showScreensaver = false) }
        // Idle countdown restarts from the moment the screensaver goes away.
        resetIdleTimer()
    }

    /**
     * Called by the Activity for the first press of any remote key while the screensaver is showing.
     * Returns true if the screensaver was dismissed. The Activity swallows the key either way, so it never
     * reaches the Home screen underneath.
     */
    fun wakeScreensaver(): Boolean {
        if (!_state.value.showScreensaver) return false
        // Small guard so the click that started a "Preview" can't instantly dismiss it.
        if (System.currentTimeMillis() - screensaverShownAtMs < 500L) return false
        closeScreensaver()
        return true
    }

    fun openScreensaverSettings() {
        _state.update { it.copy(showScreensaverSettings = true) }
    }

    fun closeScreensaverSettings() {
        _state.update { it.copy(showScreensaverSettings = false) }
    }

    fun setScreensaverMode(mode: com.leantv.app.ui.ScreensaverSource) {
        journal.screensaverMode = mode.id
        _state.update { it.copy(screensaverMode = mode) }
    }

    fun setScreensaverCustomPath(path: String) {
        journal.screensaverCustomPath = path
        _state.update { it.copy(screensaverCustomPath = path) }
    }

    private val _lastInteractionMs = MutableStateFlow(System.currentTimeMillis())
    val lastInteractionMs: StateFlow<Long> = _lastInteractionMs.asStateFlow()

    fun resetIdleTimer() {
        _lastInteractionMs.value = System.currentTimeMillis()
    }

    fun setScreensaverIdleMinutes(mins: Int) {
        journal.screensaverIdleMinutes = mins
        _state.update { it.copy(screensaverIdleMinutes = mins) }
        resetIdleTimer()
    }

    fun setScreensaverAutoCache(enabled: Boolean) {
        journal.screensaverAutoCache = enabled
        _state.update { it.copy(screensaverAutoCache = enabled) }
    }

    fun setScreensaverSelectedUrl(url: String?) {
        journal.screensaverSelectedUrl = url
        _state.update { it.copy(screensaverSelectedUrl = url) }
    }

    fun startScreensaverNow() {
        closeScreensaverSettings()
        openScreensaver()
    }

    fun openNativeTvInputs() {
        com.leantv.app.inputs.TvInputHelper.openNativeTvInputs(ctx)
    }

    fun killBackgroundApps(exceptPackage: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Wait for the app/Settings transition to finish so this never competes with it.
                delay(1500)
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return@launch
                val pm = ctx.packageManager
                val myPkg = ctx.packageName
                val procs = am.runningAppProcesses ?: return@launch
                for (p in procs) {
                    val pkgs = p.pkgList ?: emptyArray()
                    for (pkg in pkgs) {
                        if (pkg == myPkg || pkg == exceptPackage) continue
                        // System apps (TV Settings, Live TV, input services, Play services...) are left alone:
                        // killing them is what made Settings / Wi-Fi cold-start and lag on every open.
                        val isSystem = try {
                            val f = pm.getApplicationInfo(pkg, 0).flags
                            (f and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
                        } catch (_: Exception) { true }
                        if (!isSystem) am.killBackgroundProcesses(pkg)
                    }
                }
            } catch (_: Exception) {}
        }
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
                            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME), android.app.ActivityOptions.makeCustomAnimation(ctx, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
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

    fun openDeveloperOptions() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {
            openSystemSettings()
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

    private suspend fun checkDefaultLauncherPrompt() {
        if (journal.defaultLauncherPromptDismissed) return
        val isDefault = withContext(Dispatchers.IO) { isCurrentLauncherDefault() }
        if (!isDefault) {
            // If on-device ADB is connected, fix it silently without annoying popups
            if (setDefaultViaAdb(allowConnect = false)) return
            _state.update { it.copy(showDefaultPrompt = true) }
        }
    }

    /**
     * Registers LeanTV as the system's default Home app using the TV's own ADB (same as running
     * `adb shell cmd package set-home-activity ...`). Unlike tapping "Always" in the chooser, this survives
     * reinstalls of the same build and never leaves Android without a default, so "Select a Home app" stops appearing.
     */
    private suspend fun setDefaultViaAdb(allowConnect: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!adb.isConnected) {
                if (!allowConnect) return@withContext false
                if (adb.connect() !is ConnResult.Ok) return@withContext false
            }
            val pkg = ctx.packageName
            adb.shell("pm disable-user com.duokan.factorytest")
            adb.shell("cmd package set-home-activity --user 0 $pkg/com.leantv.app.HomeActivity")
            adb.shell("cmd role add-role-holder --user 0 android.app.role.HOME $pkg")
            isCurrentLauncherDefault()
        } catch (_: Throwable) {
            false
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
        viewModelScope.launch {
            if (setDefaultViaAdb(allowConnect = true)) {
                toast("LeanTV is now your default Home app")
                return@launch
            }
            requestDefaultLauncherFromSystem()
        }
    }

    private fun requestDefaultLauncherFromSystem() {
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
                toast("Go to Apps -> Default Apps -> Home app to set LeanTV as default.")
            } catch (_: Exception) {
                toast("Please set LeanTV as default in TV Settings -> Home app.")
            }
        }
    }

    // ---------- Custom TV Volume HUD ----------

    private var volumeHideJob: Job? = null
    private val audioManager by lazy { ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    fun handleVolumeKey(keyCode: Int) {
        val am = audioManager ?: return
        try {
            val stream = AudioManager.STREAM_MUSIC
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, 0)
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, 0)
                }
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                }
            }
            com.leantv.app.ui.TvSound.playVolumeTick()
            val cur = am.getStreamVolume(stream)
            val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
            val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.isStreamMute(stream)
            } else {
                cur == 0
            }

            _state.update {
                it.copy(
                    showVolumeBar = true,
                    volumeLevel = cur,
                    volumeMax = max,
                    volumeIsMuted = isMuted
                )
            }

            volumeHideJob?.cancel()
            volumeHideJob = viewModelScope.launch {
                delay(2000)
                _state.update { it.copy(showVolumeBar = false) }
            }
        } catch (_: Exception) {}
    }

    fun clearToast() = _state.update { it.copy(toastMessage = null) }
    private fun toast(msg: String) = _state.update { it.copy(toastMessage = msg) }
}