package com.peartv.app.storage

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
import com.peartv.app.adb.AdbClient
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

data class StorageFileItem(
    val file: File,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val path: String
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
    val appDetails: List<AppStorageDetail>,
    val videoFiles: List<StorageFileItem> = emptyList(),
    val audioFiles: List<StorageFileItem> = emptyList(),
    val imageFiles: List<StorageFileItem> = emptyList(),
    val apkFiles: List<StorageFileItem> = emptyList()
)

object StorageHelper {
    private const val TAG = "StorageHelper"

    /**
     * Compute comprehensive storage breakdown similar to Samsung Device Care.
     */
    suspend fun getStorageOverview(ctx: Context, journal: com.peartv.app.data.Journal? = null): StorageOverview = withContext(Dispatchers.IO) {
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
            appDetails = appDetails,
            videoFiles = fileStats.videoFiles,
            audioFiles = fileStats.audioFiles,
            imageFiles = fileStats.imageFiles,
            apkFiles = fileStats.apkFiles
        )
    }

    private data class FileStats(
        val videosBytes: Long = 0L,
        val audioBytes: Long = 0L,
        val imagesBytes: Long = 0L,
        val apksBytes: Long = 0L,
        val videoFiles: List<StorageFileItem> = emptyList(),
        val audioFiles: List<StorageFileItem> = emptyList(),
        val imageFiles: List<StorageFileItem> = emptyList(),
        val apkFiles: List<StorageFileItem> = emptyList()
    )

    private fun scanExternalMedia(): FileStats {
        val videos = mutableListOf<StorageFileItem>()
        val audio = mutableListOf<StorageFileItem>()
        val images = mutableListOf<StorageFileItem>()
        val apks = mutableListOf<StorageFileItem>()

        var videosBytes = 0L
        var audioBytes = 0L
        var imagesBytes = 0L
        var apksBytes = 0L

        try {
            val root = Environment.getExternalStorageDirectory()
            if (root != null && root.exists()) {
                val queue = ArrayDeque<File>()
                queue.add(root)
                var scannedCount = 0

                while (queue.isNotEmpty() && scannedCount < 10000) {
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
                            val item = StorageFileItem(
                                file = file,
                                name = file.name,
                                size = len,
                                lastModified = file.lastModified(),
                                path = file.absolutePath
                            )
                            when {
                                name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
                                        name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".ts") ||
                                        name.endsWith(".3gp") || name.endsWith(".m4v") -> {
                                    videosBytes += len
                                    videos.add(item)
                                }
                                name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".wav") ||
                                        name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".ogg") ||
                                        name.endsWith(".opus") || name.endsWith(".wma") -> {
                                    audioBytes += len
                                    audio.add(item)
                                }
                                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                                        name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp") -> {
                                    imagesBytes += len
                                    images.add(item)
                                }
                                name.endsWith(".apk") || name.endsWith(".xapk") -> {
                                    apksBytes += len
                                    apks.add(item)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "File scan encountered error", e)
        }

        return FileStats(
            videosBytes = videosBytes,
            audioBytes = audioBytes,
            imagesBytes = imagesBytes,
            apksBytes = apksBytes,
            videoFiles = videos.sortedByDescending { it.size },
            audioFiles = audio.sortedByDescending { it.size },
            imageFiles = images.sortedByDescending { it.size },
            apkFiles = apks.sortedByDescending { it.size }
        )
    }

    private fun scanAppStorage(ctx: Context, journal: com.peartv.app.data.Journal? = null): List<AppStorageDetail> {
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

    /** Connects to on-device ADB if needed. True when a shell is available. */
    private suspend fun ensureAdb(adbClient: AdbClient?): Boolean {
        if (adbClient == null) return false
        try { if (!adbClient.isConnected) adbClient.connect() } catch (_: Exception) {}
        return adbClient.isConnected
    }

    /**
     * Wipes ALL data of [pkg] (`pm clear`), like Settings > Clear data. Tries on-device ADB (connecting
     * if needed), then Shizuku / root. Only if both are unavailable does it open App Info and return false.
     */
    suspend fun clearAppData(ctx: Context, adbClient: AdbClient?, pkg: String): Boolean = withContext(Dispatchers.IO) {
        val cmd = "pm clear $pkg"
        if (ensureAdb(adbClient)) {
            val res = adbClient!!.shell(cmd)
            if (res.code == 0 && !res.out.contains("Failed", ignoreCase = true)) return@withContext true
        }
        if (com.peartv.app.data.SystemDebloater.runPrivilegedCommand(cmd)) return@withContext true
        openAppDetails(ctx, pkg)
        false
    }

    /**
     * Clears cache of [pkg]. Android only lets the shell empty another app's external cache directly;
     * its internal cache is freed by `pm trim-caches`, which clears cache of all apps (cache only, never data).
     */
    suspend fun clearAppCache(ctx: Context, adbClient: AdbClient?, pkg: String): Boolean = withContext(Dispatchers.IO) {
        if (pkg == ctx.packageName) cleanAllAccessibleCaches(ctx)
        val cmd = "rm -rf /sdcard/Android/data/$pkg/cache/* /sdcard/Android/data/$pkg/code_cache/*; pm trim-caches 999999999999"
        if (ensureAdb(adbClient)) {
            if (adbClient!!.shell(cmd).code == 0) return@withContext true
        }
        if (com.peartv.app.data.SystemDebloater.runPrivilegedCommand(cmd)) return@withContext true
        openAppDetails(ctx, pkg)
        false
    }

    /** Clears every app's cache (`pm trim-caches`) plus LeanTV's own. Returns LeanTV's own freed bytes. */
    suspend fun clearAllAppCaches(ctx: Context, adbClient: AdbClient?): Long = withContext(Dispatchers.IO) {
        val own = cleanAllAccessibleCaches(ctx)
        val cmd = "pm trim-caches 999999999999"
        val ok = ensureAdb(adbClient) && adbClient!!.shell(cmd).code == 0
        if (!ok) com.peartv.app.data.SystemDebloater.runPrivilegedCommand(cmd)
        own
    }

    /** Clear all temporary app caches across accessible directories.
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

    suspend fun deleteFile(ctx: Context, file: File): Boolean = withContext(Dispatchers.IO) {
        com.peartv.app.files.FileManagerHelper.deleteFile(ctx, file)
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