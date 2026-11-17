package com.peartv.app.share

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.peartv.app.R
import com.peartv.app.ui.Focusable
import com.peartv.app.ui.Pal
import com.peartv.app.ui.Ty
import com.peartv.app.ui.glassCard
import com.peartv.app.ui.modalTrap
import com.peartv.app.ui.rememberAutoFocus

/**
 * Apple TV / tvOS styled Quick Share control dialog.
 * Opens when the user clicks the Quick Share tile in the launcher top bar.
 */
@Composable
fun NativeQuickShareConsentDialog(
    state: NativeQuickShareState,
    isQuickShareActive: Boolean = false,
    autoOffSeconds: Int? = null,
    serverUrl: String? = null,
    pin: String? = null,
    deviceName: String = "Fadi Tv",
    onToggleActive: ((Boolean) -> Unit)? = null,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onDismiss: () -> Unit,
    onOpenFolder: () -> Unit = {},
) {
    val isIncoming = state.incomingTransfer != null
    BackHandler(enabled = !isIncoming) {
        onDismiss()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "qsPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Semi-transparent backdrop scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.76f))
            .focusProperties { canFocus = !isIncoming }
            .modalTrap(enabled = !isIncoming),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(680.dp)
                .wrapContentHeight()
                .glassCard(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // ─── Header: App Icon, Title & Pulsing Status ────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Official Quick Share gradient squircle
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF2870FD), Color(0xFF1456DA))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_quick_share),
                                contentDescription = "Quick Share",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Quick Share", style = Ty.Title)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isQuickShareActive)
                                                Pal.Mint.copy(alpha = pulseAlpha)
                                            else
                                                Pal.Dim.copy(alpha = 0.5f)
                                        )
                                )
                                Text(
                                    text = if (isQuickShareActive) "Active & Discoverable" else "Turned Off",
                                    style = Ty.Small.copy(
                                        color = if (isQuickShareActive) Pal.Mint else Pal.Dim,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    // Frosted Close Button
                    Focusable(
                        onClick = onDismiss,
                        shape = CircleShape,
                        idle = Color.White.copy(alpha = 0.08f)
                    ) {
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Pal.Dim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // ─── Clean Apple TV Device Info Bar ─────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Visible to nearby devices as",
                        style = Ty.Body.copy(color = Pal.Dim, fontSize = 16.sp)
                    )
                    Text(
                        text = deviceName.ifBlank { "Fadi Tv" },
                        style = Ty.Body.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }

                // ─── Main Switch: Big ON / OFF Toggle Card ───────────────────────────
                val autoFocus = rememberAutoFocus()
                Focusable(
                    onClick = { onToggleActive?.invoke(!isQuickShareActive) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(autoFocus),
                    shape = RoundedCornerShape(20.dp),
                    idle = if (isQuickShareActive)
                        Pal.Coral.copy(alpha = 0.16f)
                    else
                        Pal.Mint.copy(alpha = 0.22f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (isQuickShareActive) Pal.Coral else Pal.Mint,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (isQuickShareActive) "Turn OFF Quick Share" else "Turn ON Quick Share",
                            style = Ty.Button.copy(
                                color = if (isQuickShareActive) Pal.Coral else Pal.Mint,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        )
                    }
                }

                // ─── Bottom Navigation Buttons ───────────────────────────────────────
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = Pal.Text,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Open Received Files", style = Ty.Body.copy(color = Pal.Text, fontSize = 17.sp))
                        }
                    }

                    Focusable(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.65f),
                        shape = RoundedCornerShape(50),
                        idle = Pal.Surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 13.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Done", style = Ty.Body.copy(color = Pal.Dim, fontSize = 17.sp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Universal Quick Share Consent Popup:
 * Shows on top of EVERYTHING across the entire app (File Manager, Settings, Screensaver, Clean TV Wizard, etc.)
 * whenever an incoming file transfer request arrives.
 */
@Composable
fun NativeQuickShareConsentPopup(
    transfer: IncomingTransfer,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFolder: () -> Unit = {},
) {
    // Intercept Back button: Decline if waiting for consent, or dismiss if done/failed
    BackHandler(enabled = true) {
        if (transfer.phase == TransferPhase.WAITING_CONSENT) {
            onDecline()
        } else {
            onDismiss()
        }
    }

    val acceptFocus = remember { FocusRequester() }
    val declineFocus = remember { FocusRequester() }
    val terminalPrimaryFocus = remember { FocusRequester() }
    val terminalSecondaryFocus = remember { FocusRequester() }

    // Proactively request focus onto the actionable button with multiple staggered delays
    LaunchedEffect(transfer.phase) {
        for (delayMs in listOf(20L, 60L, 120L, 250L, 450L)) {
            delay(delayMs)
            when (transfer.phase) {
                TransferPhase.WAITING_CONSENT -> {
                    runCatching { acceptFocus.requestFocus() }
                }
                TransferPhase.COMPLETED -> {
                    runCatching { terminalPrimaryFocus.requestFocus() }
                }
                TransferPhase.FAILED,
                TransferPhase.REJECTED -> {
                    runCatching { terminalSecondaryFocus.requestFocus() }
                }
                else -> {}
            }
        }
    }

    // Full-screen scrim overlay with maximum z-index
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.84f))
            .clickable(enabled = false) {}
            .modalTrap(enabled = true, autoFocus = false)
            .zIndex(9999f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(660.dp)
                .wrapContentHeight()
                .glassCard(32.dp),
        ) {
            Column(
                modifier = Modifier.padding(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ─── Header Icon: Glowing AirDrop / Quick Share Squircle ──────────────
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2870FD), Color(0xFF1456DA))
                            )
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_quick_share),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }

                // ─── Sender Title ───────────────────────────────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (!transfer.senderName.isNullOrBlank())
                            "${transfer.senderName} wants to share"
                        else
                            "Incoming Quick Share",
                        style = Ty.Title,
                        textAlign = TextAlign.Center,
                    )
                    val totalBytes: Long = transfer.files.sumOf { it.sizeBytes }
                    val sizeSummary = if (totalBytes > 0L) " · ${formatBytes(totalBytes)}" else ""
                    Text(
                        text = "${transfer.files.size} file${if (transfer.files.size == 1) "" else "s"}$sizeSummary",
                        style = Ty.Small.copy(color = Pal.Dim)
                    )
                }

                // ─── PIN badge (if present) ──────────────────────────────────
                if (!transfer.pin.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "PIN: ${transfer.pin}",
                            style = Ty.Body.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                                color = Pal.Mint
                            )
                        )
                    }
                }

                // ─── File list ────────────────────────────────────────────────
                if (transfer.files.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp)),
                        contentPadding = PaddingValues(14.dp),
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
                    enter = fadeIn(tween(250)),
                    exit = fadeOut(tween(200)),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Receiving at full speed...",
                                style = Ty.Small.copy(color = Pal.Mint, fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = "${(transfer.progressFraction * 100).toInt()}%",
                                style = Ty.Small.copy(color = Color.White, fontWeight = FontWeight.Bold),
                            )
                        }

                        LinearProgressIndicator(
                            progress = { transfer.progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = Pal.Mint,
                            trackColor = Pal.Line,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Receiving...",
                                style = Ty.Small.copy(color = Pal.Dim, fontSize = 14.sp)
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
                    TransferPhase.COMPLETED -> StatusChip("✓  Transfer Complete", Pal.Mint)
                    TransferPhase.FAILED   -> StatusChip("✗  Transfer Failed", Pal.Coral)
                    TransferPhase.REJECTED -> StatusChip("Declined", Pal.Dim)
                    else -> {}
                }

                // ─── Buttons (only while awaiting consent) ───────────────────
                if (transfer.phase == TransferPhase.WAITING_CONSENT) {
                    ConsentButtons(
                        acceptFocus = acceptFocus,
                        declineFocus = declineFocus,
                        onAccept = onAccept,
                        onDecline = onDecline
                    )
                }

                // ─── Dismiss / Open in Files (after terminal state) ─────────
                if (transfer.phase in listOf(
                        TransferPhase.COMPLETED,
                        TransferPhase.FAILED,
                        TransferPhase.REJECTED,
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (transfer.phase == TransferPhase.COMPLETED) {
                            Focusable(
                                onClick = onOpenFolder,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(terminalPrimaryFocus)
                                    .focusProperties { right = terminalSecondaryFocus },
                                shape = RoundedCornerShape(50),
                                idle = Pal.Mint.copy(alpha = 0.22f),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = Pal.Mint,
                                        modifier = Modifier.size(19.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "View in Files",
                                        style = Ty.Body.copy(color = Pal.Mint, fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                        }
                        Focusable(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(if (transfer.phase == TransferPhase.COMPLETED) 0.7f else 1f)
                                .focusRequester(terminalSecondaryFocus)
                                .focusProperties {
                                    if (transfer.phase == TransferPhase.COMPLETED) {
                                        left = terminalPrimaryFocus
                                    }
                                },
                            shape = RoundedCornerShape(50),
                            idle = Pal.Surface,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Done",
                                    style = Ty.Body.copy(color = Pal.Dim),
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
            style = Ty.Body.copy(fontSize = 17.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = formatBytes(file.sizeBytes),
            style = Ty.Small.copy(color = Pal.Dim, fontSize = 15.sp),
        )
    }
}

@Composable
private fun ConsentButtons(
    acceptFocus: FocusRequester,
    declineFocus: FocusRequester,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Decline (Secondary)
        Focusable(
            onClick = onDecline,
            modifier = Modifier
                .weight(1f)
                .focusRequester(declineFocus)
                .focusProperties { right = acceptFocus },
            shape = RoundedCornerShape(50),
            idle = Pal.Surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = Pal.Coral, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Decline", style = Ty.Button.copy(color = Pal.Coral, fontWeight = FontWeight.SemiBold))
            }
        }

        // Accept (Primary, auto-focused)
        Focusable(
            onClick = onAccept,
            modifier = Modifier
                .weight(1.15f)
                .focusRequester(acceptFocus)
                .focusProperties { left = declineFocus },
            shape = RoundedCornerShape(50),
            idle = Color(0xFF007AFF).copy(alpha = 0.35f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Accept", style = Ty.Button.copy(color = Color.White, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(text, style = Ty.Body.copy(color = color, fontWeight = FontWeight.SemiBold))
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun formatBytes(bytes: Long): String = when {
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(bytes / 1_024f)
    bytes < 1_073_741_824 -> "%.1f MB".format(bytes / 1_048_576f)
    else -> "%.2f GB".format(bytes / 1_073_741_824f)
}
