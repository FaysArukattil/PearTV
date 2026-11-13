package com.leantv.app.share

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    val transfer = state.incomingTransfer ?: return

    // Semi-transparent scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .modalTrap(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(640.dp)
                .wrapContentHeight()
                .glassCard(32.dp),
        ) {
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

                // ─── PIN confirmation ─────────────────────────────────────────
                PinDisplay(pin = transfer.pin)

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
                        LinearProgressIndicator(
                            progress = { transfer.progressFraction },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = Pal.Mint,
                            trackColor = Pal.Line,
                        )
                        if (transfer.bytesPerSecond > 0) {
                            Text(
                                text = "${formatBytes(transfer.bytesPerSecond)}/s",
                                style = Ty.Small,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }

                // ─── Status messages (terminal states) ───────────────────────
                when (transfer.phase) {
                    TransferPhase.COMPLETED -> StatusChip("✓  Transfer complete", Pal.Mint)
                    TransferPhase.FAILED    -> StatusChip("✗  Transfer failed", Pal.Coral)
                    TransferPhase.REJECTED  -> StatusChip("Declined", Pal.Dim)
                    else -> {}
                }

                // ─── Buttons (only while awaiting consent) ───────────────────
                if (transfer.phase == TransferPhase.WAITING_CONSENT) {
                    ConsentButtons(onAccept = onAccept, onDecline = onDecline)
                }

                // ─── Dismiss link (after terminal state) ─────────────────────
                if (transfer.phase in listOf(
                        TransferPhase.COMPLETED,
                        TransferPhase.FAILED,
                        TransferPhase.REJECTED,
                    )
                ) {
                    val autoFocus = rememberAutoFocus()
                    Focusable(
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(autoFocus),
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

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun PinDisplay(pin: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Confirm PIN", style = Ty.Small)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pin.forEach { digit ->
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Pal.SurfaceHi),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = digit.toString(),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Pal.Text,
                    )
                }
            }
        }
    }
}

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

