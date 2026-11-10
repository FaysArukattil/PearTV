package com.leantv.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object InstalledAppsScanner {

    suspend fun scan(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
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

        val result = mutableListOf<InstalledAppInfo>()

        for (pi in packages) {
            val ai = pi.applicationInfo ?: continue
            val pkg = pi.packageName

            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            val label = try {
                pm.getApplicationLabel(ai).toString().trim().ifEmpty { pkg }
            } catch (_: Exception) {
                pkg
            }

            val versionName = pi.versionName?.trim()?.ifEmpty { "v${PackageInfoCompat.getLongVersionCode(pi)}" } ?: "v${PackageInfoCompat.getLongVersionCode(pi)}"
            val versionCode = PackageInfoCompat.getLongVersionCode(pi)

            var sizeBytes = 0L
            try {
                if (ai.sourceDir != null) {
                    val baseApk = File(ai.sourceDir)
                    if (baseApk.exists()) sizeBytes += baseApk.length()
                }
                ai.splitSourceDirs?.forEach { splitDir ->
                    val splitFile = File(splitDir)
                    if (splitFile.exists()) sizeBytes += splitFile.length()
                }
            } catch (_: Exception) {
                // Ignore file access limitations
            }

            val hasLaunchIntent = try {
                pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null
            } catch (_: Exception) {
                false
            }

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

        result
    }
}

