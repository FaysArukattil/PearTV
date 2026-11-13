package com.leantv.app.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.leantv.app.data.SystemDebloater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String,
    val childCount: Int = 0
)

data class StorageDrive(
    val name: String,
    val path: File,
    val totalBytes: Long,
    val freeBytes: Long,
    val isRemovable: Boolean
)

data class ClipboardItem(
    val file: File,
    val isCut: Boolean // true for Move, false for Copy
)

object FileManagerHelper {
    private const val TAG = "FileManagerHelper"

    fun ensureQuickShareDir(): File {
        val root = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
        val dir = File(root, "Quickshare")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Checks if full external storage permission is granted.
     */
    fun hasStoragePermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Attempt to auto-grant MANAGE_EXTERNAL_STORAGE via Shizuku / on-device ADB.
     */
    suspend fun autoGrantStoragePermission(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) return@withContext true
            try {
                SystemDebloater.runPrivilegedCommand("appops set ${ctx.packageName} MANAGE_EXTERNAL_STORAGE allow")
                SystemDebloater.runPrivilegedCommand("pm grant ${ctx.packageName} android.permission.MANAGE_EXTERNAL_STORAGE")
            } catch (e: Exception) {
                Log.w(TAG, "Failed auto-grant storage permission", e)
            }
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Open system settings screen to grant All Files Access on TV.
     */
    fun requestStoragePermission(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(fallback)
            }
        }
    }

    /**
     * Auto-detect accessible storage drives (Internal Storage, USB Drives, OTG).
     */
    fun detectStorageDrives(ctx: Context): List<StorageDrive> {
        val drives = mutableListOf<StorageDrive>()

        // 1. Internal Storage
        val internalRoot = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
        if (internalRoot.exists() || internalRoot.canRead()) {
            val (total, free) = getDriveStats(internalRoot)
            drives.add(
                StorageDrive(
                    name = "Internal Storage",
                    path = internalRoot,
                    totalBytes = total,
                    freeBytes = free,
                    isRemovable = false
                )
            )
        }

        // 2. Scan /storage for USB drives and external SD cards
        try {
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                val subDirs = storageDir.listFiles() ?: emptyArray()
                for (dir in subDirs) {
                    val name = dir.name
                    if (name != "emulated" && name != "self" && dir.canRead() && dir.isDirectory) {
                        val (total, free) = getDriveStats(dir)
                        if (total > 0L) {
                            drives.add(
                                StorageDrive(
                                    name = "USB Drive ($name)",
                                    path = dir,
                                    totalBytes = total,
                                    freeBytes = free,
                                    isRemovable = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning /storage for USB drives", e)
        }

        // 3. Fallback: ContextCompat external dirs
        try {
            val externalDirs = ContextCompat.getExternalFilesDirs(ctx, null)
            for (f in externalDirs) {
                if (f != null && Environment.isExternalStorageRemovable(f)) {
                    var root = f
                    while (root.parentFile != null && root.parentFile?.path != "/storage") {
                        root = root.parentFile ?: break
                    }
                    if (drives.none { it.path.path == root.path }) {
                        val (total, free) = getDriveStats(root)
                        drives.add(
                            StorageDrive(
                                name = "Removable Drive (${root.name})",
                                path = root,
                                totalBytes = total,
                                freeBytes = free,
                                isRemovable = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback drive if nothing was found
        if (drives.isEmpty()) {
            drives.add(
                StorageDrive(
                    name = "Internal Storage",
                    path = File("/storage/emulated/0"),
                    totalBytes = 0L,
                    freeBytes = 0L,
                    isRemovable = false
                )
            )
        }

        return drives
    }

    private fun getDriveStats(dir: File): Pair<Long, Long> {
        return try {
            val stat = StatFs(dir.path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            Pair(total, free)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    /**
     * List directory contents: folders first, then files.
     */
    suspend fun listDirectory(dir: File): List<FileItem> = withContext(Dispatchers.IO) {
        if (!dir.exists()) dir.mkdirs()
        val files = dir.listFiles() ?: return@withContext emptyList()
        val items = files.filter { !it.name.startsWith(".") }.map { file ->
            val ext = file.extension.lowercase(Locale.ROOT)
            val mime = getMimeType(ext)
            val childCount = if (file.isDirectory) (file.listFiles()?.count { !it.name.startsWith(".") } ?: 0) else 0
            FileItem(
                file = file,
                name = file.name,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                extension = ext,
                mimeType = mime,
                childCount = childCount
            )
        }

        // Sort: directories first, then alphabetically
        items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    /**
     * Scan files for media categories (Downloads, APKs, Videos, Music, Pictures, Documents, Quickshare).
     */
    suspend fun scanCategoryFiles(categoryKey: String): List<FileItem> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
        val results = mutableListOf<FileItem>()

        if (categoryKey == "quickshare") {
            val qs = ensureQuickShareDir()
            return@withContext listDirectory(qs)
        }

        if (categoryKey == "downloads") {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File(root, "Download")
            return@withContext listDirectory(downloadDir)
        }

        val queue = ArrayDeque<File>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 8000) {
            val dir = queue.removeFirst()
            val files = dir.listFiles() ?: continue
            for (f in files) {
                count++
                if (f.isDirectory) {
                    if (!f.name.startsWith(".") && f.name != "Android") queue.add(f)
                } else {
                    val ext = f.extension.lowercase(Locale.ROOT)
                    val matches = when (categoryKey) {
                        "videos" -> isVideo(ext)
                        "audio" -> isAudio(ext)
                        "images" -> isImage(ext)
                        "apks" -> ext == "apk"
                        "documents" -> isDocument(ext)
                        else -> false
                    }
                    if (matches) {
                        results.add(
                            FileItem(
                                file = f,
                                name = f.name,
                                isDirectory = false,
                                sizeBytes = f.length(),
                                lastModified = f.lastModified(),
                                extension = ext,
                                mimeType = getMimeType(ext)
                            )
                        )
                    }
                }
            }
        }

        results.sortedByDescending { it.lastModified }
    }

    /**
     * Install an APK directly from TV File Manager.
     */
    fun installApk(ctx: Context, file: File): Boolean {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch package installer for ${file.name}", e)
            return false
        }
    }

    /**
     * Open file with suitable TV viewer, media player or package installer.
     */
    fun openFile(ctx: Context, file: File): Boolean {
        if (file.extension.equals("apk", ignoreCase = true)) {
            return installApk(ctx, file)
        }
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val ext = file.extension.lowercase(Locale.ROOT)
            val mime = getMimeType(ext)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open file ${file.name}", e)
            return false
        }
    }

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Delete failed", e)
            false
        }
    }

    suspend fun renameFile(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parent = file.parentFile ?: return@withContext false
            val target = File(parent, newName)
            if (target.exists()) return@withContext false
            file.renameTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "Rename failed", e)
            false
        }
    }

    suspend fun copyFile(source: File, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = File(targetDir, source.name)
            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = true)
            } else {
                source.copyTo(dest, overwrite = true)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed", e)
            false
        }
    }

    suspend fun moveFile(source: File, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = File(targetDir, source.name)
            val success = source.renameTo(dest)
            if (!success) {
                if (source.isDirectory) {
                    source.copyRecursively(dest, overwrite = true)
                    source.deleteRecursively()
                } else {
                    source.copyTo(dest, overwrite = true)
                    source.delete()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Move failed", e)
            false
        }
    }

    suspend fun createFolder(parentDir: File, name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val newDir = File(parentDir, name)
            if (!newDir.exists()) newDir.mkdirs() else true
        } catch (e: Exception) {
            Log.e(TAG, "Create folder failed", e)
            false
        }
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes <= 0L -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
        else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f))
    }

    fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getFileDetails(file: File): Map<String, String> {
        return linkedMapOf(
            "Name" to file.name,
            "Path" to file.absolutePath,
            "Type" to (if (file.isDirectory) "Folder" else file.extension.uppercase().ifEmpty { "File" }),
            "Size" to (if (file.isDirectory) "${file.listFiles()?.size ?: 0} items" else formatFileSize(file.length())),
            "Modified" to formatDate(file.lastModified()),
            "Readable" to (if (file.canRead()) "Yes" else "No"),
            "Writable" to (if (file.canWrite()) "Yes" else "No"),
        )
    }

    fun isVideo(ext: String) = ext in listOf("mp4", "mkv", "avi", "mov", "webm", "ts", "3gp", "m4v")
    fun isAudio(ext: String) = ext in listOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma")
    fun isImage(ext: String) = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    fun isDocument(ext: String) = ext in listOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "epub", "zip", "tar", "gz", "rar", "7z")

    fun getMimeType(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "mkv" -> "video/x-matroska"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            else -> "*/*"
        }
    }
}
