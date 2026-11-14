package com.leantv.app.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore
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

data class MultiClipboard(
    val files: List<File>,
    val isCut: Boolean
)

object FileManagerHelper {
    private const val TAG = "FileManagerHelper"

    fun ensureQuickShareDir(): File {
        // Target: /storage/emulated/0/Download/LeanTvQuickshare
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: File("/storage/emulated/0/Download")
        val dir = File(downloadsDir, "LeanTvQuickshare")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        // Try to chmod via privileged command
        try {
            com.leantv.app.data.SystemDebloater.runPrivilegedCommand("mkdir -p \"${dir.absolutePath}\" && chmod 777 \"${dir.absolutePath}\"")
        } catch (_: Exception) {}
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

        // 1. Internal Storage — always add even if not yet readable (permission may be granted later)
        val internalRoot = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
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
        if (!dir.exists()) {
            try { dir.mkdirs() } catch (_: Exception) {}
        }

        // Try direct file listing first
        val files = try {
            dir.listFiles()
        } catch (e: SecurityException) {
            Log.w(TAG, "listDirectory: SecurityException on ${dir.path}", e)
            null
        }

        if (files == null) {
            // If dir is /storage/emulated/0 and listFiles returned null (permission restricted),
            // provide the well-known public folders that are readable so the screen is never blank:
            if (dir.absolutePath == "/storage/emulated/0" || dir.absolutePath == "/sdcard") {
                val publicDirs = listOf(
                    File(dir, "Download"),
                    ensureQuickShareDir(),
                    File(dir, "Movies"),
                    File(dir, "Music"),
                    File(dir, "Pictures"),
                    File(dir, "DCIM"),
                    File(dir, "Documents")
                ).filter { it.exists() || it.mkdirs() }

                return@withContext publicDirs.map { d ->
                    FileItem(
                        file = d,
                        name = d.name,
                        isDirectory = true,
                        sizeBytes = 0L,
                        lastModified = d.lastModified(),
                        extension = "",
                        mimeType = "resource/folder",
                        childCount = d.listFiles()?.size ?: 0
                    )
                }
            }
            return@withContext emptyList()
        }

        files.filter { !it.name.startsWith(".") }.map { file ->
            val ext = file.extension.lowercase(Locale.ROOT)
            val mime = getMimeType(ext)
            val childCount = if (file.isDirectory) {
                try { file.listFiles()?.count { !it.name.startsWith(".") } ?: 0 } catch (_: Exception) { 0 }
            } else 0
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
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    private fun matchesCategory(ext: String, categoryKey: String): Boolean = when (categoryKey) {
        "videos" -> isVideo(ext)
        "audio" -> isAudio(ext)
        "images" -> isImage(ext)
        "apks" -> ext == "apk"
        "documents" -> isDocument(ext)
        else -> false
    }

    private fun toFileItem(f: File): FileItem {
        val ext = f.extension.lowercase(Locale.ROOT)
        return FileItem(
            file = f,
            name = f.name,
            isDirectory = f.isDirectory,
            sizeBytes = if (f.isDirectory) 0L else f.length(),
            lastModified = f.lastModified(),
            extension = ext,
            mimeType = getMimeType(ext),
            childCount = if (f.isDirectory) (f.listFiles()?.size ?: 0) else 0
        )
    }

    /**
     * Scan files for media categories (Downloads, APKs, Videos, Music, Pictures, Documents, Quickshare)
     * using MediaStore queries and targeted directory scans for maximum speed and reliability.
     */
    suspend fun scanCategoryFiles(ctx: Context, categoryKey: String): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()

        if (categoryKey == "quickshare") {
            val qs = ensureQuickShareDir()
            return@withContext listDirectory(qs)
        }

        if (categoryKey == "downloads") {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File("/storage/emulated/0/Download")
            val items = listDirectory(downloadsDir).toMutableList()
            val qsDir = ensureQuickShareDir()
            if (qsDir.exists() && qsDir.absolutePath != downloadsDir.absolutePath) {
                items.addAll(listDirectory(qsDir))
            }
            return@withContext items.distinctBy { it.file.absolutePath }
        }

        // Try MediaStore content resolver queries first (very fast on Android)
        try {
            val contentUri = when (categoryKey) {
                "videos" -> android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                "audio" -> android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                "images" -> android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                else -> null
            }

            if (contentUri != null) {
                val projection = arrayOf(
                    android.provider.MediaStore.MediaColumns.DATA,
                    android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                    android.provider.MediaStore.MediaColumns.SIZE,
                    android.provider.MediaStore.MediaColumns.DATE_MODIFIED
                )
                ctx.contentResolver.query(
                    contentUri,
                    projection,
                    null,
                    null,
                    "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    val nameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.SIZE)
                    val dateCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)

                    while (cursor.moveToNext() && results.size < 200) {
                        val path = if (dataCol != -1) cursor.getString(dataCol) else null
                        if (path == null) continue
                        val file = File(path)
                        if (!file.exists()) continue
                        val name = if (nameCol != -1) cursor.getString(nameCol) ?: file.name else file.name
                        val size = if (sizeCol != -1) cursor.getLong(sizeCol) else file.length()
                        val mod = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else file.lastModified()
                        val ext = file.extension.lowercase(Locale.ROOT)
                        results.add(
                            FileItem(
                                file = file,
                                name = name,
                                isDirectory = false,
                                sizeBytes = if (size > 0) size else file.length(),
                                lastModified = if (mod > 0) mod else file.lastModified(),
                                extension = ext,
                                mimeType = getMimeType(ext)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query failed for $categoryKey", e)
        }

        // Fast scan targeted folders for APKs, documents, and local media files
        val targetFolders = mutableListOf<File>()
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?: File("/storage/emulated/0/Download")
        if (downloads.exists()) targetFolders.add(downloads)
        targetFolders.add(ensureQuickShareDir())

        val root = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
        when (categoryKey) {
            "videos" -> {
                targetFolders.add(File(root, "Movies"))
                targetFolders.add(File(root, "DCIM"))
            }
            "audio" -> {
                targetFolders.add(File(root, "Music"))
                targetFolders.add(File(root, "Podcasts"))
            }
            "images" -> {
                targetFolders.add(File(root, "Pictures"))
                targetFolders.add(File(root, "DCIM"))
            }
            "documents" -> {
                targetFolders.add(File(root, "Documents"))
                targetFolders.add(File(root, "Download"))
            }
            "apks" -> {
                targetFolders.add(File(root, "Download"))
                targetFolders.add(File(root, "Download/LeanTvQuickshare"))
            }
        }

        for (folder in targetFolders) {
            if (!folder.exists() || !folder.isDirectory) continue
            val list = folder.listFiles() ?: continue
            for (f in list) {
                if (f.isDirectory) {
                    val sub = f.listFiles() ?: continue
                    for (sf in sub) {
                        if (!sf.isDirectory && matchesCategory(sf.extension, categoryKey)) {
                            if (results.none { it.file.absolutePath == sf.absolutePath }) {
                                results.add(toFileItem(sf))
                            }
                        }
                    }
                } else if (matchesCategory(f.extension, categoryKey)) {
                    if (results.none { it.file.absolutePath == f.absolutePath }) {
                        results.add(toFileItem(f))
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

    private fun purgeFromMediaStore(ctx: Context, file: File) {
        try {
            val uri = MediaStore.Files.getContentUri("external")
            ctx.contentResolver.delete(
                uri,
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(file.absolutePath)
            )
        } catch (_: Exception) {}
        try {
            MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), null, null)
        } catch (_: Exception) {}
    }

    suspend fun deleteFile(ctx: Context? = null, file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) {
                ctx?.let { purgeFromMediaStore(it, file) }
                return@withContext true
            }

            // 1. Standard Java delete
            val standardDeleted = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            if (standardDeleted && !file.exists()) {
                ctx?.let { purgeFromMediaStore(it, file) }
                return@withContext true
            }

            // 2. MediaStore ContentResolver delete
            if (ctx != null) {
                try {
                    val uri = MediaStore.Files.getContentUri("external")
                    val rows = ctx.contentResolver.delete(
                        uri,
                        "${MediaStore.MediaColumns.DATA} = ?",
                        arrayOf(file.absolutePath)
                    )
                    if (rows > 0 && !file.exists()) {
                        return@withContext true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ContentResolver delete failed for ${file.name}", e)
                }
            }

            // 3. Privileged command fallback (handles files owned by root, adb, or other UIDs)
            try {
                val escaped = file.absolutePath.replace("'", "'\\''")
                SystemDebloater.runPrivilegedCommand("rm -rf '$escaped'")
            } catch (e: Exception) {
                Log.w(TAG, "Privileged rm -rf failed for ${file.name}", e)
            }

            // 4. Purge from MediaStore
            ctx?.let { purgeFromMediaStore(it, file) }

            !file.exists()
        } catch (e: Exception) {
            Log.w(TAG, "Delete failed for ${file.name}", e)
            false
        }
    }

    suspend fun deleteFile(file: File): Boolean = deleteFile(null, file)

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

    suspend fun copyFile(ctx: Context? = null, source: File, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = File(targetDir, source.name)
            if (source.isDirectory) {
                source.copyRecursively(dest, overwrite = true)
            } else {
                source.copyTo(dest, overwrite = true)
            }
            ctx?.let {
                try { MediaScannerConnection.scanFile(it, arrayOf(dest.absolutePath), null, null) } catch (_: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed", e)
            false
        }
    }

    suspend fun copyFile(source: File, targetDir: File): Boolean = copyFile(null, source, targetDir)

    suspend fun moveFile(ctx: Context? = null, source: File, targetDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val dest = File(targetDir, source.name)
            val success = source.renameTo(dest)
            if (!success) {
                if (source.isDirectory) {
                    source.copyRecursively(dest, overwrite = true)
                    deleteFile(ctx, source)
                } else {
                    source.copyTo(dest, overwrite = true)
                    deleteFile(ctx, source)
                }
            }
            ctx?.let {
                try { MediaScannerConnection.scanFile(it, arrayOf(dest.absolutePath), null, null) } catch (_: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Move failed", e)
            false
        }
    }

    suspend fun moveFile(source: File, targetDir: File): Boolean = moveFile(null, source, targetDir)

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
    fun isText(ext: String) = ext in listOf("txt", "log", "json", "xml", "md", "sh", "py", "conf", "ini", "yaml", "yml", "html", "css", "js", "properties", "gradle", "kt", "java")
    fun isPdf(ext: String) = ext == "pdf"

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

    /**
     * Share one or more files using Android system chooser (Quick Share, Bluetooth, Drive, etc.).
     */
    fun shareFiles(ctx: Context, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        return try {
            val uris = ArrayList<Uri>()
            for (f in files) {
                uris.add(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
            }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    val mime = getMimeType(files[0].extension.lowercase(Locale.ROOT))
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooser = Intent.createChooser(intent, "Share via Quick Share / Bluetooth").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share files", e)
            false
        }
    }

    /**
     * Directly trigger Bluetooth sharing for selected files.
     */
    fun shareViaBluetooth(ctx: Context, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        return try {
            val uris = ArrayList<Uri>()
            for (f in files) {
                uris.add(FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", f))
            }
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    val mime = getMimeType(files[0].extension.lowercase(Locale.ROOT))
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            intent.setPackage("com.android.bluetooth")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            shareFiles(ctx, files)
        }
    }

    suspend fun batchDelete(ctx: Context? = null, files: List<File>): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (f in files) {
            if (deleteFile(ctx, f)) {
                count++
            }
        }
        count
    }

    suspend fun batchDelete(files: List<File>): Int = batchDelete(null, files)

    suspend fun batchCopy(ctx: Context? = null, sources: List<File>, targetDir: File): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (f in sources) {
            if (copyFile(ctx, f, targetDir)) {
                count++
            }
        }
        count
    }

    suspend fun batchCopy(sources: List<File>, targetDir: File): Int = batchCopy(null, sources, targetDir)

    suspend fun batchMove(ctx: Context? = null, sources: List<File>, targetDir: File): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (f in sources) {
            if (moveFile(ctx, f, targetDir)) {
                count++
            }
        }
        count
    }

    suspend fun batchMove(sources: List<File>, targetDir: File): Int = batchMove(null, sources, targetDir)

    fun readText(file: File, maxBytes: Int = 120_000): String {
        return try {
            val length = minOf(file.length(), maxBytes.toLong()).toInt()
            val buf = ByteArray(length)
            file.inputStream().use { it.read(buf, 0, length) }
            String(buf, Charsets.UTF_8)
        } catch (e: Exception) {
            "Could not read file: ${e.message}"
        }
    }

    fun decodeBitmap(file: File, maxDim: Int = 1920): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDim || options.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    fun renderPdfPage(file: File, pageIndex: Int, targetWidth: Int = 1080): Pair<Bitmap?, Int> {
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            val validIndex = pageIndex.coerceIn(0, pageCount - 1)
            val page = renderer.openPage(validIndex)

            val width = targetWidth
            val height = (targetWidth.toFloat() * page.height / page.width).toInt().coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            Pair(bitmap, pageCount)
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering PDF page", e)
            Pair(null, 0)
        }
    }
}
