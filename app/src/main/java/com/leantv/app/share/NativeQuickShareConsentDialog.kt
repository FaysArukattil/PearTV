package com.leantv.app.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.ui.Focusable
import com.leantv.app.ui.Pal
import com.leantv.app.ui.Ty
import com.leantv.app.ui.glassCard
import com.leantv.app.ui.modalTrap
import com.leantv.app.ui.rememberAutoFocus
import java.util.Locale

/**
 * TV D-Pad–navigable consent dialog for the native Quick Share receiver.
 *
 * Shows:
 * - Sender name + 4-digit PIN
 * - File list with sizes
 * - Accept / Decline buttons (or progress bar when transferring)
 * - Success / failure message on completion
 */
@Composable
fun NativeQuickShareConsentDialog(
    state: NativeQuickShareState,
    isQuickShareActive: Boolean = false,
    autoOffSeconds: Int? = null,
    serverUrl: String? = null,
    pin: String? = null,
    deviceName: String = android.os.Build.MODEL,
    onToggleActive: ((Boolean) -> Unit)? = null,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFolder: () -> Unit = {},
) {
    val transfer = state.incomingTransfer

    // Semi-transparent scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .modalTrap(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(660.dp)
                .wrapContentHeight()
                .glassCard(32.dp),
        ) {
            if (transfer == null) {
                // ─── Quick Share Control & Settings Screen ───────────────────────────────
                Column(
                    modifier = Modifier.padding(34.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF007AFF).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    androidx.compose.ui.res.painterResource(com.leantv.app.R.drawable.ic_quick_share),
                                    contentDescription = "Quick Share",
                                    tint = Color(0xFF0A84FF),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Column {
                                Text("Quick Share", style = Ty.Title)
                                Text("Receive photos, videos, and files wirelessly", style = Ty.Small.copy(color = Pal.Dim))
                            }
                        }

                        // Close button (X)
                        Focusable(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(50),
                            idle = Color.White.copy(alpha = 0.08f)
                        ) {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Pal.Dim, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Status & Inactivity Timer Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isQuickShareActive) Pal.Mint.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
                            .border(1.dp, if (isQuickShareActive) Pal.Mint.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (isQuickShareActive) Pal.Mint else Color.Gray)
                                    )
                                    Text(
                                        text = if (isQuickShareActive) "Active & Discoverable" else "Turned Off",
                                        style = Ty.Body.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isQuickShareActive) Pal.Mint else Pal.Dim
                                        )
                                    )
                                }

                                if (isQuickShareActive && autoOffSeconds != null) {
                                    val m = autoOffSeconds / 60
                                    val s = autoOffSeconds % 60
                                    Text(
                                        text = "⏱️ Auto-off in %d:%02d".format(m, s),
                                        style = Ty.Small.copy(color = Pal.Amber, fontWeight = FontWeight.SemiBold)
                                    )
                                }
                            }

                            if (isQuickShareActive) {
                                Text(
                                    text = "This TV is visible as \"$deviceName\". When someone sends a file, an Accept / Decline popup will appear anywhere on screen.",
                                    style = Ty.Small.copy(color = Pal.Text)
                                )
                                Text(
                                    text = "Note: Quick Share turns off automatically after 15 minutes of inactivity to save TV resources.",
                                    style = Ty.Small.copy(color = Pal.Dim, fontSize = 13.sp)
                                )
                            } else {
                                Text(
                                    text = "Quick Share is turned off. Turn it on when you want to receive files from your phone or PC.",
                                    style = Ty.Small.copy(color = Pal.Dim)
                                )
                                Text(
                                    text = "Note: When turned on, it automatically turns off after 15 minutes if not used.",
                                    style = Ty.Small.copy(color = Pal.Dim, fontSize = 13.sp)
                                )
                            }
                        }
                    }

                    // Main Action: Big ON / OFF Toggle Button
                    val autoFocus = rememberAutoFocus()
                    Focusable(
                        onClick = { onToggleActive?.invoke(!isQuickShareActive) },
                        modifier = Modifier.fillMaxWidth().focusRequester(autoFocus),
                        shape = RoundedCornerShape(16.dp),
                        idle = if (isQuickShareActive) Pal.Coral.copy(alpha = 0.22f) else Pal.Mint.copy(alpha = 0.25f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isQuickShareActive) "Turn OFF Quick Share" else "Turn ON Quick Share",
                                style = Ty.Button.copy(
                                    color = if (isQuickShareActive) Pal.Coral else Pal.Mint,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Instructions (when Active)
                    if (isQuickShareActive) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("📱 From Android / Windows Phone:", style = Ty.Small.copy(color = Pal.Amber, fontWeight = FontWeight.Bold))
                            Text("1. Open any photo, video, or file on phone -> tap Share -> Quick Share", style = Ty.Small.copy(color = Pal.Text))
                            Text("2. Select \"$deviceName\" from Nearby Devices", style = Ty.Small.copy(color = Pal.Text))
                            Text("3. Accept prompt on TV (pops up automatically anywhere in the app)", style = Ty.Small.copy(color = Pal.Text))

                            if (!serverUrl.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("🌐 From iPhone, Mac, or PC Browser:", style = Ty.Small.copy(color = Pal.Amber, fontWeight = FontWeight.Bold))
                                Text("Open browser: $serverUrl${if (!pin.isNullOrBlank()) " (PIN: $pin)" else ""}", style = Ty.Small.copy(color = Pal.Mint, fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }

                    // Bottom Navigation Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Focusable(
                            onClick = onOpenFolder,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            idle = Pal.SurfaceHi
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Open Received Files", style = Ty.Body.copy(color = Pal.Text))
                            }
                        }

                        Focusable(
                            onClick = onDismiss,
                            modifier = Modifier.weight(0.8f),
                            shape = RoundedCornerShape(50),
                            idle = Pal.Surface
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isQuickShareActive) "Keep in Background" else "Close", style = Ty.Body.copy(color = Pal.Dim))
                            }
                        }
                    }
                }
            } else {
                // ─── Active Transfer / Incoming Consent State ─────────────────────
                Column(
                    modifier = Modifier.padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // ─── Header icon ─────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Pal.Amber.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.FileDownload,
                            contentDescription = null,
                            tint = Pal.Amber,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    // ─── Title ───────────────────────────────────────────────────
                    Text(
                        text = if (transfer.senderName != null)
                            "${transfer.senderName} wants to share"
                        else
                            "Incoming Quick Share",
                        style = Ty.Title,
                        textAlign = TextAlign.Center,
                    )

                    // ─── File list ────────────────────────────────────────────────
                    if (transfer.files.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.05f)),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(transfer.files) { file ->
                                FileRow(file)
                            }
                        }
                    }

                    // ─── Progress bar (when receiving) ───────────────────────────
                    AnimatedVisibility(
                        visible = transfer.phase == TransferPhase.RECEIVING,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(200)),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Receiving file...",
                                    style = Ty.Body.copy(color = Pal.Amber, fontWeight = FontWeight.SemiBold)
                                )
                                val pct = (transfer.progressFraction * 100).toInt()
                                Text(
                                    text = if (pct > 0) "$pct%" else "Connecting...",
                                    style = Ty.Body.copy(fontWeight = FontWeight.Bold, color = Pal.Mint)
                                )
                            }

                            if (transfer.progressFraction > 0f) {
                                LinearProgressIndicator(
                                    progress = { transfer.progressFraction },
                                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                                    color = Pal.Mint,
                                    trackColor = Pal.Line,
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                                    color = Pal.Mint,
                                    trackColor = Pal.Line,
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📁 Internal Storage / Quickshare",
                                    style = Ty.Small.copy(color = Pal.Dim)
                                )
                                if (transfer.bytesPerSecond > 0) {
                                    Text(
                                        text = "${formatBytes(transfer.bytesPerSecond)}/s",
                                        style = Ty.Small.copy(color = Pal.Mint, fontWeight = FontWeight.SemiBold),
                                    )
                                }
                            }
                        }
                    }

                    // ─── Status messages (terminal states) ───────────────────────
                    when (transfer.phase) {
                        TransferPhase.COMPLETED -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusChip("✓  Transfer Complete!", Pal.Mint)
                                Text(
                                    text = "Saved in /storage/emulated/0/Quickshare",
                                    style = Ty.Small.copy(color = Pal.Dim),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        TransferPhase.FAILED    -> StatusChip("✗  Transfer Failed or Cancelled", Pal.Coral)
                        TransferPhase.REJECTED  -> StatusChip("Declined", Pal.Dim)
                        else -> {}
                    }

                    // ─── Buttons (only while awaiting consent) ───────────────────
                    if (transfer.phase == TransferPhase.WAITING_CONSENT) {
                        ConsentButtons(onAccept = onAccept, onDecline = onDecline)
                    }

                    // ─── Dismiss / Open in Files (after terminal state) ─────────
                    if (transfer.phase in listOf(
                            TransferPhase.COMPLETED,
                            TransferPhase.FAILED,
                            TransferPhase.REJECTED,
                        )
                    ) {
                        val autoFocus = rememberAutoFocus()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (transfer.phase == TransferPhase.COMPLETED) {
                                Focusable(
                                    onClick = onOpenFolder,
                                    modifier = Modifier.weight(1f).focusRequester(autoFocus),
                                    shape = RoundedCornerShape(50),
                                    idle = Pal.Mint.copy(alpha = 0.2f),
                                ) {
                                    Text(
                                        "View in Files",
                                        style = Ty.Body.copy(color = Pal.Mint, fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                    )
                                }
                            }
                            Focusable(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f).then(if (transfer.phase != TransferPhase.COMPLETED) Modifier.focusRequester(autoFocus) else Modifier),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    "Dismiss",
                                    style = Ty.Body.copy(color = Pal.Dim),
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun FileRow(file: TransferFileInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = file.name,
            style = Ty.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatBytes(file.sizeBytes),
            style = Ty.Small,
            color = Pal.Dim,
        )
    }
}

@Composable
private fun ConsentButtons(onAccept: () -> Unit, onDecline: () -> Unit) {
    val acceptFocus = rememberAutoFocus()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Decline
        Focusable(
            onClick = onDecline,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(50),
            idle = Pal.Surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Pal.Coral, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Decline", style = Ty.Button.copy(color = Pal.Coral))
            }
        }

        // Accept (auto-focused)
        Focusable(
            onClick = onAccept,
            modifier = Modifier
                .weight(1f)
                .focusRequester(acceptFocus),
            shape = RoundedCornerShape(50),
            idle = Pal.Amber.copy(alpha = 0.18f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Pal.Amber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Accept", style = Ty.Button.copy(color = Pal.Amber))
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(text, style = Ty.Body.copy(color = color))
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(bytes / 1_024f)
    bytes < 1_073_741_824 -> "%.1f MB".format(bytes / 1_048_576f)
    else -> "%.2f GB".format(bytes / 1_073_741_824f)
}

@Suppress("UNUSED")
private val Pal.Coral: Color get() = com.leantv.app.ui.Pal.Coral

