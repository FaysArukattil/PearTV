package com.leantv.app.share

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.leantv.app.ui.Focusable
import com.leantv.app.ui.Pal
import com.leantv.app.ui.TvButton
import com.leantv.app.ui.Ty
import com.leantv.app.ui.rememberAutoFocus
import java.io.File

@Composable
fun QuickSharePanel(
    state: QuickShareState,
    onClose: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val closeFocus = rememberAutoFocus()

    val qrBitmap = remember(state.serverUrl) {
        state.serverUrl?.let { QrGenerator.generate(it, 340) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14)),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            Modifier
                .width(640.dp)
                .fillMaxHeight()
                .background(Pal.Surface)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Quick Share", style = Ty.Title)
                    Text("Wireless file & link transfer", style = Ty.Dim)
                }

                TvButton("Close", onClick = onClose, primary = false, modifier = Modifier.focusRequester(closeFocus))
            }

            // Connection Card
            if (state.serverUrl != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Pal.SurfaceHi.copy(alpha = 0.5f))
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // QR Code
                    if (qrBitmap != null) {
                        Box(
                            Modifier
                                .size(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = "Scan to open LeanTV Quick Share",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // Instructions, URL & PIN
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Pal.Mint)
                            )
                            Text("Online on local network", style = Ty.Small.copy(color = Pal.Mint, fontWeight = FontWeight.SemiBold))
                        }

                        Text(
                            state.serverUrl!!,
                            style = Ty.Body.copy(color = Pal.Amber, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        )

                        Spacer(Modifier.height(2.dp))

                        // Large PIN display
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Security PIN:", style = Ty.Small)
                            Text(
                                state.pin,
                                style = Ty.Heading.copy(
                                    color = Pal.Text,
                                    letterSpacing = 4.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Pal.Surface)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text("Scan QR or enter URL on your phone", style = Ty.Small.copy(fontSize = 12.sp))
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Pal.Coral.copy(alpha = 0.15f))
                        .padding(18.dp)
                ) {
                    Text(
                        "Not connected to Wi-Fi. Please connect your TV to Wi-Fi or Ethernet in system settings to use Quick Share.",
                        style = Ty.Body.copy(color = Pal.Coral)
                    )
                }
            }

            // Transfer progress banner
            state.transferStatus?.let { status ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Pal.Amber.copy(alpha = 0.18f))
                        .padding(12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(status, style = Ty.Body.copy(color = Pal.Amber, fontWeight = FontWeight.SemiBold))
                }
            }

            // Received Items Section
            Text(
                "Received Content (${state.receivedItems.size})",
                style = Ty.Heading.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            )

            if (state.receivedItems.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No files or links received yet.", style = Ty.Dim)
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.receivedItems) { item ->
                        when (item) {
                            is SharedItem.SharedFile -> {
                                SharedFileRow(
                                    item = item,
                                    onOpenFile = {
                                        openFile(context, item.path)
                                    }
                                )
                            }
                            is SharedItem.SharedText -> {
                                SharedTextRow(
                                    item = item,
                                    onOpenUrl = {
                                        onOpenUrl(item.text)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedFileRow(
    item: SharedItem.SharedFile,
    onOpenFile: () -> Unit,
) {
    Focusable(
        onClick = onOpenFile,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        idle = Pal.SurfaceHi.copy(alpha = 0.45f),
        scaleOnFocus = 1.02f
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = Ty.Body.copy(
                        color = if (focused) Pal.Amber else Pal.Text,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
                Text(
                    "${item.formattedSize} • Saved to Downloads/LeanTV/ • ${item.formattedTime}",
                    style = Ty.Small.copy(fontSize = 12.sp)
                )
            }

            TvButton("Open", onClick = onOpenFile, primary = true)
        }
    }
}

@Composable
private fun SharedTextRow(
    item: SharedItem.SharedText,
    onOpenUrl: () -> Unit,
) {
    Focusable(
        onClick = { if (item.isUrl) onOpenUrl() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        idle = Pal.SurfaceHi.copy(alpha = 0.45f),
        scaleOnFocus = 1.02f
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (item.isUrl) "Web Link" else "Note from Phone",
                    style = Ty.Small.copy(color = if (item.isUrl) Pal.Sky else Pal.Mint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.text,
                    style = Ty.Body.copy(
                        color = if (focused) Pal.Amber else Pal.Text,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2
                )
                Text(
                    "Received at ${item.formattedTime}",
                    style = Ty.Small.copy(fontSize = 12.sp)
                )
            }

            if (item.isUrl) {
                TvButton("Open Link", onClick = onOpenUrl, primary = true)
            }
        }
    }
}

private fun openFile(context: android.content.Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val mimeType = when (file.extension.lowercase()) {
            "apk" -> "application/vnd.android.package-archive"
            "mp4", "mkv", "avi" -> "video/*"
            "mp3", "flac", "wav" -> "audio/*"
            "jpg", "jpeg", "png", "webp" -> "image/*"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            else -> "*/*"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback: general view
        try {
            val file = File(path)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
