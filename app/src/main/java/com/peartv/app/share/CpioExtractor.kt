package com.peartv.app.share

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object CpioExtractor {

    /**
     * Extracts files from a CPIO stream (SVR4 newc format "070701" or "070702")
     * into target directory [targetDir].
     */
    fun extract(
        input: InputStream,
        targetDir: File,
        onProgress: (fraction: Float, bytesPerSec: Long) -> Unit = { _, _ -> },
    ): List<File> {
        targetDir.mkdirs()
        val extractedFiles = mutableListOf<File>()
        val headerBuf = ByteArray(110)
        var totalBytesRead = 0L
        val startTime = System.currentTimeMillis()

        while (true) {
            val n = readFully(input, headerBuf, 0, 110)
            if (n < 110) break
            totalBytesRead += n

            val magic = String(headerBuf, 0, 6, Charsets.US_ASCII)
            if (magic != "070701" && magic != "070702") {
                // Not standard newc or reached stream end
                break
            }

            val fileSizeHex = String(headerBuf, 54, 8, Charsets.US_ASCII)
            val nameSizeHex = String(headerBuf, 94, 8, Charsets.US_ASCII)

            val fileSize = fileSizeHex.toLongOrNull(16) ?: 0L
            val nameSize = nameSizeHex.toIntOrNull(16) ?: 0

            if (nameSize <= 0) break

            val nameBytes = ByteArray(nameSize)
            readFully(input, nameBytes, 0, nameSize)
            totalBytesRead += nameSize

            // Pad name to 4-byte boundary
            val namePad = (4 - (110 + nameSize) % 4) % 4
            if (namePad > 0) {
                skipFully(input, namePad.toLong())
                totalBytesRead += namePad
            }

            val name = String(nameBytes, 0, nameSize - 1, Charsets.UTF_8)
            if (name == "TRAILER!!!" || name.endsWith("TRAILER!!!")) {
                break
            }

            val cleanName = File(name).name
            if (cleanName.isNotBlank() && cleanName != "." && cleanName != "..") {
                val outFile = File(targetDir, cleanName)
                FileOutputStream(outFile).use { fos ->
                    copyBytes(input, fos, fileSize)
                }
                totalBytesRead += fileSize
                extractedFiles.add(outFile)
            } else {
                skipFully(input, fileSize)
                totalBytesRead += fileSize
            }

            // Pad file content to 4-byte boundary
            val filePad = ((4 - (fileSize % 4)) % 4).toInt()
            if (filePad > 0) {
                skipFully(input, filePad.toLong())
                totalBytesRead += filePad
            }

            val elapsedSec = ((System.currentTimeMillis() - startTime) / 1000.0).coerceAtLeast(0.001)
            val bps = (totalBytesRead / elapsedSec).toLong()
            onProgress(0.9f, bps)
        }

        return extractedFiles
    }

    private fun readFully(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, offset + total, length - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        val discard = ByteArray(1024)
        while (remaining > 0) {
            val toRead = remaining.coerceAtMost(discard.size.toLong()).toInt()
            val read = input.read(discard, 0, toRead)
            if (read < 0) break
            remaining -= read
        }
    }

    private fun copyBytes(input: InputStream, output: OutputStream, count: Long) {
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
}
