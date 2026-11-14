package com.leantv.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object InstalledAppsScanner {
    @Volatile
    private var cachedApps: List<InstalledAppInfo>? = null

    /** App labels resolved once per process; resolving a label loads app resources and is the slow part. */
    private val labelCache = ConcurrentHashMap<String, String>()

    fun getCached(): List<InstalledAppInfo>? = cachedApps

    /**
     * Fast scan for the launcher home screen: only apps that can actually be launched.
     *
     * The full [scan] loads PackageInfo + a label for EVERY installed package (hundreds on a TV) even though
     * Home only shows the launchable ones. This asks the package manager for the launchable activities
     * (2 IPC calls) and resolves labels only for those, in parallel, with a per-process label cache.
     * It never uses a stale list, so installs / uninstalls show up on the next refresh.
     */
    suspend fun scanLaunchable(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val found = LinkedHashMap<String, ApplicationInfo>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
                @Suppress("DEPRECATION")
                val list = pm.queryIntentActivities(intent, 0)
                for (ri in list) {
                    val ai = ri.activityInfo ?: continue
                    val app = ai.applicationInfo ?: continue
                    if (!app.enabled) continue
                    if (!found.containsKey(ai.packageName)) found[ai.packageName] = app
                }
            } catch (_: Exception) {}
        }

        coroutineScope {
            found.entries.map { entry ->
                async {
                    val pkg = entry.key
                    val app = entry.value
                    val cachedLabel = labelCache[pkg]
                    val label = if (cachedLabel != null) {
                        cachedLabel
                    } else {
                        val resolved = try {
                            pm.getApplicationLabel(app).toString().trim().ifEmpty { pkg }
                        } catch (_: Exception) {
                            pkg
                        }
                        labelCache[pkg] = resolved
                        resolved
                    }
                    InstalledAppInfo(
                        packageName = pkg,
                        appName = label,
                        versionName = "",
                        versionCode = 0L,
                        isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        isUpdatedSystemApp = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                        installTimeMillis = 0L,
                        lastUpdateTimeMillis = 0L,
                        sizeBytes = 0L,
                        launchIntentAvailable = true,
                    )
                }
            }.awaitAll()
        }
    }

    fun invalidate() {
        cachedApps = null
    }

    suspend fun scan(context: Context, forceRefresh: Boolean = false): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedApps != null) {
            return@withContext cachedApps!!
        }

        val pm = context.packageManager

        // Batch query all launchable activities in 2 IPC calls instead of hundreds
        val launchablePackages = mutableSetOf<String>()
        try {
            val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            @Suppress("DEPRECATION")
            val leanbackList = pm.queryIntentActivities(leanbackIntent, 0)
            for (ri in leanbackList) {
                ri.activityInfo?.packageName?.let { launchablePackages.add(it) }
            }
        } catch (_: Exception) {}

        try {
            val mainLauncherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val launcherList = pm.queryIntentActivities(mainLauncherIntent, 0)
            for (ri in launcherList) {
                ri.activityInfo?.packageName?.let { launchablePackages.add(it) }
            }
        } catch (_: Exception) {}

        val packages: List<PackageInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
        } catch (_: Exception) {
            emptyList()
        }

        val result = ArrayList<InstalledAppInfo>(packages.size)

        for (pi in packages) {
            val ai = pi.applicationInfo ?: continue
            if (!ai.enabled) continue
            val pkg = pi.packageName

            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val label = try {
                pm.getApplicationLabel(ai).toString().trim().ifEmpty { pkg }
            } catch (_: Exception) {
                pkg
            }

            val versionName = pi.versionName?.trim()?.ifEmpty { "v${PackageInfoCompat.getLongVersionCode(pi)}" }
                ?: "v${PackageInfoCompat.getLongVersionCode(pi)}"
            val versionCode = PackageInfoCompat.getLongVersionCode(pi)

            val sizeBytes = 0L

            val hasLaunchIntent = pkg in launchablePackages

            result.add(
                InstalledAppInfo(
                    packageName = pkg,
                    appName = label,
                    versionName = versionName,
                    versionCode = versionCode,
                    isSystemApp = isSystem,
                    isUpdatedSystemApp = isUpdatedSystem,
                    installTimeMillis = pi.firstInstallTime,
                    lastUpdateTimeMillis = pi.lastUpdateTime,
                    sizeBytes = sizeBytes,
                    launchIntentAvailable = hasLaunchIntent,
                )
            )
        }

        cachedApps = result
        result
    }
}


