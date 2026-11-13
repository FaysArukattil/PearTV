package com.leantv.app.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.Settings
import android.util.Log
import com.leantv.app.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class AppStorageDetail(
    val packageName: String,
    val appLabel: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val totalBytes: Long,
    val isSystemApp: Boolean
)

data class StorageOverview(
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val appsBytes: Long,
    val videosBytes: Long,
    val audioBytes: Long,
    val imagesBytes: Long,
    val apksBytes: Long,
    val systemBytes: Long,
    val appDetails: List<AppStorageDetail>
)

object StorageHelper {
    private const val TAG = "StorageHelper"

    /**
     * Compute comprehensive storage breakdown similar to Samsung Device Care.
     */
    suspend fun getStorageOverview(ctx: Context, journal: com.leantv.app.data.Journal? = null): StorageOverview = withContext(Dispatchers.IO) {
        val stat = StatFs(Environment.getDataDirectory().path)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)

        // 1. Per-App storage details (excluding removed/disabled bloatware)
        val appDetails = scanAppStorage(ctx, journal)
        val totalAppsBytes = appDetails.sumOf { it.totalBytes }

        // 2. Scan external storage files by category
        val fileStats = scanExternalMedia()

        // 3. Compute System OS overhead
        val measuredBytes = totalAppsBytes + fileStats.videosBytes + fileStats.audioBytes +
                fileStats.imagesBytes + fileStats.apksBytes
        val systemBytes = (usedBytes - measuredBytes).coerceAtLeast(0L)

        StorageOverview(
            totalBytes = totalBytes,
            freeBytes = freeBytes,
            usedBytes = usedBytes,
            appsBytes = totalAppsBytes,
            videosBytes = fileStats.videosBytes,
            audioBytes = fileStats.audioBytes,
            imagesBytes = fileStats.imagesBytes,
            apksBytes = fileStats.apksBytes,
            systemBytes = systemBytes,
            appDetails = appDetails
        )
    }

    private data class FileStats(
        val videosBytes: Long = 0L,
        val audioBytes: Long = 0L,
        val imagesBytes: Long = 0L,
        val apksBytes: Long = 0L
    )

    private fun scanExternalMedia(): FileStats {
        var videos = 0L
        var audio = 0L
        var images = 0L
        var apks = 0L

        try {
            val root = Environment.getExternalStorageDirectory()
            if (root != null && root.exists()) {
                val queue = ArrayDeque<File>()
                queue.add(root)
                var scannedCount = 0

                while (queue.isNotEmpty() && scannedCount < 8000) {
                    val dir = queue.removeFirst()
                    val files = dir.listFiles() ?: continue
                    for (file in files) {
                        scannedCount++
                        if (file.isDirectory) {
                            val name = file.name
                            // Skip deep hidden/android private dirs for speed
                            if (!name.startsWith(".") && name != "Android") {
                                queue.add(file)
                            }
                        } else {
                            val len = file.length()
                            val name = file.name.lowercase(Locale.ROOT)
                            when {
                                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
                                        name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".ts") -> {
                                    videos += len
                                }
                                name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") ||
                                        name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ogg") -> {
                                    audio += len
                                }
                                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                                        name.endsWith(".webp") || name.endsWith(".gif") -> {
                                    images += len
                                }
                                name.endsWith(".apk") -> {
                                    apks += len
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "File scan encountered error", e)
        }

        return FileStats(videos, audio, images, apks)
    }

    private fun scanAppStorage(ctx: Context, journal: com.leantv.app.data.Journal? = null): List<AppStorageDetail> {
        val pm = ctx.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
        val storageStatsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else null

        val removedPkgs = journal?.removed()?.map { it.pkg }?.toSet() ?: emptySet()
        val result = mutableListOf<AppStorageDetail>()

        for (pkg in packages) {
            val appInfo = pkg.applicationInfo ?: continue
            val pkgName = pkg.packageName

            // Skip removed, disabled, or uninstalled bloatware packages
            if (!appInfo.enabled || pkgName in removedPkgs) continue
            val label = appInfo.loadLabel(pm)?.toString() ?: pkgName
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            var appBytes = 0L
            var dataBytes = 0L
            var cacheBytes = 0L

            if (storageStatsManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val stats = storageStatsManager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        pkgName,
                        Process.myUserHandle()
                    )
                    appBytes = stats.appBytes
                    dataBytes = stats.dataBytes
                    cacheBytes = stats.cacheBytes
                } catch (_: Exception) {
                    // Fallback to apk file length if stats permission restricted
                    val apkFile = File(appInfo.sourceDir ?: "")
                    if (apkFile.exists()) appBytes = apkFile.length()
                }
            } else {
                val apkFile = File(appInfo.sourceDir ?: "")
                if (apkFile.exists()) appBytes = apkFile.length()
            }

            val total = appBytes + dataBytes + cacheBytes
            result.add(
                AppStorageDetail(
                    packageName = pkgName,
                    appLabel = label,
                    appBytes = appBytes,
                    dataBytes = dataBytes,
                    cacheBytes = cacheBytes,
                    totalBytes = total,
                    isSystemApp = isSystem
                )
            )
        }

        return result.sortedByDescending { it.totalBytes }
    }

    /**
     * Format byte values to human readable GB / MB / KB strings.
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
        val mb = bytes.toDouble() / (1024 * 1024)
        if (mb >= 1.0) return String.format(Locale.US, "%.0f MB", mb)
        val kb = bytes.toDouble() / 1024
        return String.format(Locale.US, "%.0f KB", kb)
    }

    /**
     * Clear application cache using local ADB or system intent.
     */
    suspend fun clearAppData(ctx: Context, adbClient: AdbClient?, pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (adbClient != null && adbClient.isConnected) {
            val res = adbClient.shell("pm clear $pkg")
            return@withContext res.code == 0
        }
        // Fallback: Open App Settings
        openAppDetails(ctx, pkg)
        return@withContext false
    }

    /**
     * Clear all temporary app caches across accessible directories.
     */
    suspend fun cleanAllAccessibleCaches(ctx: Context): Long = withContext(Dispatchers.IO) {
        var reclaimed = 0L
        try {
            val cacheDir = ctx.cacheDir
            reclaimed += deleteDirContents(cacheDir)
            val externalCache = ctx.externalCacheDir
            if (externalCache != null) {
                reclaimed += deleteDirContents(externalCache)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache cleanup error", e)
        }
        reclaimed
    }

    private fun deleteDirContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var deletedBytes = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            if (child.isDirectory) {
                deletedBytes += deleteDirContents(child)
                child.delete()
            } else {
                deletedBytes += child.length()
                child.delete()
            }
        }
        return deletedBytes
    }

    fun openAppDetails(ctx: Context, pkg: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", pkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun requestUninstall(ctx: Context, pkg: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.fromParts("package", pkg, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (_: Exception) {}
    }
}
