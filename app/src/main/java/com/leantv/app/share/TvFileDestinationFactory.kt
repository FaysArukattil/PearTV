package com.leantv.app.share

import android.content.Context
import android.os.Environment
import com.google.location.nearby.connections.proto.OfflineWireFormatsProto.PayloadTransferFrame.PayloadHeader
import dev.bluehouse.bada.protocol.payload.FileDestinationFactory
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.SeekableByteChannel
import java.util.concurrent.ConcurrentHashMap

/**
 * TV FileDestinationFactory for receiving files via native Quick Share.
 * Saves incoming payloads into the TV's system Downloads folder.
 */
class TvFileDestinationFactory(
    private val context: Context,
    private val onFileCommitted: (File, Long) -> Unit = { _, _ -> },
) : FileDestinationFactory {

    private val activeFiles = ConcurrentHashMap<Long, File>()
    private val activeChannels = ConcurrentHashMap<Long, SeekableByteChannel>()

    private fun getDownloadDir(): File {
        val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (publicDir != null && (publicDir.exists() || publicDir.mkdirs())) {
            return publicDir
        }
        val extFiles = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (extFiles != null && (extFiles.exists() || extFiles.mkdirs())) {
            return extFiles
        }
        val fallback = File(context.filesDir, "downloads")
        fallback.mkdirs()
        return fallback
    }

    override fun open(header: PayloadHeader): SeekableByteChannel {
        val dir = getDownloadDir()
        val rawName = header.fileName.ifEmpty { "received_file_${header.id}" }
        val safeName = sanitizeFileName(rawName)
        val targetFile = resolveUniqueFile(dir, safeName)

        val raf = RandomAccessFile(targetFile, "rw")
        val channel = raf.channel
        activeFiles[header.id] = targetFile
        activeChannels[header.id] = channel
        return channel
    }

    override fun commit(payloadId: Long): Boolean {
        val file = activeFiles.remove(payloadId)
        activeChannels.remove(payloadId)
        if (file != null && file.exists()) {
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

