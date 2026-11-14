package com.leantv.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object InstalledAppsScanner {
    @Volatile
    private var cachedApps: List<InstalledAppInfo>? = null

    fun getCached(): List<InstalledAppInfo>? = cachedApps

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


