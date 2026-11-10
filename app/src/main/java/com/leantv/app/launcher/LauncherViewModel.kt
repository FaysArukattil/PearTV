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
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    // Phase 3: System Monitor
    val showMonitorPanel: Boolean = false,
    val ramSnapshot: RamSnapshot? = null,
    val cpuSnapshot: CpuSnapshot? = null,
    val runningApps: List<RunningAppItem> = emptyList(),
    val isCleaning: Boolean = false,
    // Phase 4: Quick Share
    val showQuickShare: Boolean = false,
    val quickShareState: com.leantv.app.share.QuickShareState = com.leantv.app.share.QuickShareState(),
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val journal = Journal(ctx)
    private val systemMonitor = SystemMonitor(ctx)
    private var monitorJob: Job? = null
    private var shareServer: com.leantv.app.share.QuickShareServer? = null

    private val _state = MutableStateFlow(LauncherState())
    val state: StateFlow<LauncherState> = _state.asStateFlow()

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = DateFormat.getDateInstance(DateFormat.FULL)

    init {
        startTimeTicker()
        loadApps()
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
