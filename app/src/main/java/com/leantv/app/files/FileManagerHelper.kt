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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class FileItem(
    val file: File,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val mimeType: String
)

data class StorageDrive(
    val name: String,
    val path: File,
    val totalBytes: Long,
    val freeBytes: Long,
    val isRemovable: Boolean
)

data class FileCategorySummary(
    val key: String,
    val label: String,
    val count: Int,
    val totalBytes: Long
)

object FileManagerHelper {
    private const val TAG = "FileManagerHelper"

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
     * Auto-detect all accessible storage volumes (Internal Flash + USB Flash Drives / Hard Drives).
     */
    fun detectStorageDrives(ctx: Context): List<StorageDrive> {
        val drives = mutableListOf<StorageDrive>()

        // 1. Internal Storage
        val internalRoot = Environment.getExternalStorageDirectory()
        if (internalRoot != null && internalRoot.exists()) {
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
                    // Filter out internal emulated mounts and self
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
        val files = dir.listFiles() ?: return@withContext emptyList()
        val items = files.filter { !it.name.startsWith(".") }.map { file ->
            val ext = file.extension.lowercase(Locale.ROOT)
            val mime = getMimeType(ext)
            FileItem(
                file = file,
                name = file.name,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                extension = ext,
                mimeType = mime
            )
        }

        // Sort: directories first, then alphabetically
        items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    /**
     * Scan files for standard Samsung My Files categories.
     */
    suspend fun scanCategoryFiles(categoryKey: String): List<FileItem> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory() ?: return@withContext emptyList()
        val results = mutableListOf<FileItem>()

        if (categoryKey == "downloads") {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadDir != null && downloadDir.exists()) {
                val files = downloadDir.listFiles() ?: emptyArray()
                for (f in files) {
                    if (!f.isDirectory && !f.name.startsWith(".")) {
                        val ext = f.extension.lowercase(Locale.ROOT)
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
            return@withContext results.sortedByDescending { it.lastModified }
        }

        val queue = ArrayDeque<File>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 6000) {
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
     * Open media file (Video, Audio, Photo) in external TV media player or gallery.
     */
    fun openMediaFile(ctx: Context, file: File): Boolean {
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
            Log.w(TAG, "Failed to open media file ${file.name}", e)
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

    private fun isVideo(ext: String) = ext in listOf("mp4", "mkv", "avi", "mov", "webm", "ts", "3gp", "m4v")
    private fun isAudio(ext: String) = ext in listOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma")
    private fun isImage(ext: String) = ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
    private fun isDocument(ext: String) = ext in listOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "epub", "zip", "tar", "gz")

    private fun getMimeType(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "mkv" -> "video/x-matroska"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            else -> "*/*"
        }
    }
}
