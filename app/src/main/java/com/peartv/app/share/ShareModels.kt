package com.peartv.app.share

import java.text.DateFormat
import java.util.Date
import java.util.Locale

sealed class SharedItem {
    abstract val timestamp: Long

    val formattedTime: String
        get() = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

    data class SharedFile(
        val name: String,
        val path: String,
        val sizeBytes: Long,
        override val timestamp: Long,
    ) : SharedItem() {
        val formattedSize: String
            get() = when {
                sizeBytes <= 0 -> "0 B"
                sizeBytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", sizeBytes / 1024f)
                sizeBytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", sizeBytes / (1024f * 1024f))
                else -> String.format(Locale.US, "%.2f GB", sizeBytes / (1024f * 1024f * 1024f))
            }
    }

    data class SharedText(
        val text: String,
        val isUrl: Boolean,
        override val timestamp: Long,
    ) : SharedItem()
}

data class QuickShareState(
    val isRunning: Boolean = false,
    val ipAddress: String? = null,
    val port: Int = 8765,
    val pin: String = "",
    val receivedItems: List<SharedItem> = emptyList(),
    val transferStatus: String? = null,
) {
    val serverUrl: String?
        get() = if (ipAddress != null) "http://$ipAddress:$port" else null
}

