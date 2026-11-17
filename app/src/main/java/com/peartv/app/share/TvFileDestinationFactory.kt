package com.peartv.app.share

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame.PayloadHeader
import dev.bluehouse.bada.protocol.payload.FileDestinationFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * TV FileDestinationFactory for receiving files via native Quick Share.
 * Saves incoming payloads into the TV's system Downloads folder (LeanTv quick share).
 */
class TvFileDestinationFactory(
    private val context: Context,
    private val onFileCommitted: (File, Long) -> Unit = { _, _ -> },
) : FileDestinationFactory {

    private val activeFiles = ConcurrentHashMap<Long, File>()
    private val activeChannels = ConcurrentHashMap<Long, SeekableByteChannel>()

    companion object {
        private const val TAG = "TvFileDestFactory"
        const val FOLDER_NAME = "PearTvQuickshare"

        fun getDownloadDir(ctx: Context): File {
            val primary = com.peartv.app.files.FileManagerHelper.ensureQuickShareDir()
            if (primary.exists() && (primary.canWrite() || primary.setWritable(true))) {
                return primary
            }

            val candidates = mutableListOf<File>()
            candidates.add(primary)

            // 1. Primary: /storage/emulated/0/Download/LeanTvQuickshare
            val baseDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?: File("/storage/emulated/0/Download")
            candidates.add(File(baseDownloads, FOLDER_NAME))

            // 2. /storage/emulated/0/LeanTvQuickshare
            val extRoot = Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
            candidates.add(File(extRoot, FOLDER_NAME))
            candidates.add(File("/storage/emulated/0", FOLDER_NAME))
            candidates.add(File("/sdcard", FOLDER_NAME))

            // 3. Fallback: App external files dir
            val extFiles = ctx.getExternalFilesDir(null)
            if (extFiles != null) {
                candidates.add(File(extFiles, FOLDER_NAME))
            }

            // 4. App internal files
            candidates.add(File(ctx.filesDir, FOLDER_NAME))

            for (dir in candidates) {
                try {
                    if (!dir.exists()) {
                        dir.mkdirs()
                    }
                    if (dir.exists() && (dir.canWrite() || dir.setWritable(true))) {
                        // Validate writeability with test probe file
                        val probe = File(dir, ".probe_${System.currentTimeMillis()}")
                        if (probe.createNewFile()) {
                            probe.delete()
                            Log.i(TAG, "Selected Quick Share destination directory: ${dir.absolutePath}")
                            return dir
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Directory ${dir.path} not writable: ${e.message}")
                }
            }

            val fallback = File(Environment.getExternalStorageDirectory() ?: ctx.filesDir, FOLDER_NAME)
            fallback.mkdirs()
            return fallback
        }
    }

    override fun open(header: PayloadHeader): SeekableByteChannel {
        val rawName = header.fileName.ifEmpty { "received_file_${header.id}" }
        val safeName = sanitizeFileName(rawName)

        val candidateDirs = listOf(
            getDownloadDir(context),
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, FOLDER_NAME),
            File(context.filesDir, FOLDER_NAME)
        )

        var lastEx: Exception? = null
        for (dir in candidateDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val targetFile = resolveUniqueFile(dir, safeName)
                val raf = RandomAccessFile(targetFile, "rw")
                val channel = raf.channel
                activeFiles[header.id] = targetFile
                activeChannels[header.id] = channel
                Log.w(TAG, "Opened destination channel: ${targetFile.absolutePath}")
                return channel
            } catch (e: Exception) {
                lastEx = e
                Log.w(TAG, "Failed opening in ${dir.path}, trying next fallback: ${e.message}")
            }
        }

        throw lastEx ?: IllegalStateException("Could not create destination file for payload ${header.id}")
    }

    override fun commit(payloadId: Long): Boolean {
        var file = activeFiles.remove(payloadId)
        activeChannels.remove(payloadId)
        if (file != null && file.exists()) {
            val targetDir = com.peartv.app.files.FileManagerHelper.ensureQuickShareDir()
            if (file.parentFile?.absolutePath != targetDir.absolutePath && (targetDir.exists() || targetDir.mkdirs()) && targetDir.canWrite()) {
                try {
                    val publicDest = resolveUniqueFile(targetDir, file.name)
                    if (file.renameTo(publicDest)) {
                        file = publicDest
                        Log.w(TAG, "Promoted received file to public LeanTvQuickshare: ${file.absolutePath}")
                    } else {
                        file.copyTo(publicDest, overwrite = true)
                        file.delete()
                        file = publicDest
                        Log.w(TAG, "Copied received file to public LeanTvQuickshare: ${file.absolutePath}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not promote file to public QuickShare dir: ${e.message}")
                }
            }

            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null,
                    null
                )
            } catch (_: Exception) {}
            Log.i(TAG, "Committed received file: ${file.absolutePath} (${file.length()} bytes)")
            onFileCommitted(file, file.length())
            return true
        }
        return false
    }

    override fun abort(payloadId: Long): Boolean {
        activeChannels.remove(payloadId)?.runCatching { close() }
        val file = activeFiles.remove(payloadId)
        return file?.delete() == true
    }

    override fun abortAll(): Int {
        var count = 0
        activeChannels.values.forEach { runCatching { it.close() } }
        activeChannels.clear()
        activeFiles.values.forEach {
            if (it.delete()) count++
        }
        activeFiles.clear()
        return count
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .trim()
            .ifEmpty { "file" }
    }

    private fun resolveUniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file

        val dot = name.lastIndexOf('.')
        val base = if (dot != -1) name.substring(0, dot) else name
        val ext = if (dot != -1) name.substring(dot) else ""

        var index = 1
        while (file.exists()) {
            file = File(dir, "$base ($index)$ext")
            index++
        }
        return file
    }
}
