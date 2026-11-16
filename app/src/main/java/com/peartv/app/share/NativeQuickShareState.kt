package com.peartv.app.share

import dev.bluehouse.bada.protocol.connection.InboundConnection
import dev.bluehouse.bada.protocol.connection.TransferItem

// ─── State model for Native Quick Share receiver ─────────────────────────────

data class NativeQuickShareState(
    /** Whether the mDNS + BLE advertisement is live. */
    val isAdvertising: Boolean = false,
    /** Non-null while there is an in-progress or waiting transfer. */
    val incomingTransfer: IncomingTransfer? = null,
    /** Completed transfers since the service started. */
    val lastReceivedFiles: List<ReceivedFile> = emptyList(),
)

data class IncomingTransfer(
    val senderName: String?,
    /** 4-digit confirmation PIN from UKEY2 auth-string. */
    val pin: String = "",
    val files: List<TransferFileInfo>,
    val progressFraction: Float = 0f,
    val bytesPerSecond: Long = 0L,
    val phase: TransferPhase = TransferPhase.WAITING_CONSENT,
    /**
     * Live reference to the connection so the UI can call
     * submitUserConsent without an extra IPC layer.
     * Not serialised / not part of equals — only used at runtime.
     */
    @Transient val connection: InboundConnection? = null,
    /** Optional callback for web/Apple transfers consent. */
    @Transient val onConsent: ((Boolean) -> Unit)? = null,
)

data class TransferFileInfo(
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
)

data class ReceivedFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val timestamp: Long,
)

enum class TransferPhase {
    WAITING_CONSENT,
    RECEIVING,
    COMPLETED,
    FAILED,
    REJECTED,
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

internal fun TransferItem.toFileInfo(): TransferFileInfo? =
    when (this) {
        is TransferItem.File -> TransferFileInfo(name, size, mimeType.takeIf { it.isNotEmpty() })
        is TransferItem.Text -> null // texts have no path/size in the file sense
    }

