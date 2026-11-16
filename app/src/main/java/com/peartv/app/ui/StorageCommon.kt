package com.peartv.app.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Back-key routing for everything inside Storage.
 *
 * HomeActivity swallows the Back key and calls LauncherViewModel.handleBackPress(), so Compose
 * BackHandlers never run. Instead every Storage screen registers here; the TOP-MOST screen's
 * handler runs first. Return true = "I went one step back", false = "nothing to go back to".
 */
object StorageBack {
    @Volatile
    var handler: (() -> Boolean)? = null
}

@Composable
fun StorageBackHandler(handler: () -> Boolean) {
    val latest by rememberUpdatedState(handler)
    DisposableEffect(Unit) {
        val previous = StorageBack.handler
        val mine: () -> Boolean = { latest() }
        StorageBack.handler = mine
        onDispose { if (StorageBack.handler === mine) StorageBack.handler = previous }
    }
}

/**
 * Simple remote-friendly confirm dialog. One OK press on a button = done (no press-and-hold).
 * Cancel is focused by default, LEFT/RIGHT switch buttons, BACK cancels.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelF = remember { FocusRequester() }
    val okF = remember { FocusRequester() }

    StorageBackHandler {
        onDismiss()
        true
    }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { cancelF.requestFocus() }
    }

    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1C1F27))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color(0xCCFFFFFF), fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.weight(1f).focusRequester(cancelF).onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false
                        else when (ev.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_RIGHT -> { runCatching { okF.requestFocus() }; true }
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
                            else -> false
                        }
                    }
                )
                TvButton(
                    text = confirmText,
                    onClick = onConfirm,
                    primary = true,
                    modifier = Modifier.weight(1f).focusRequester(okF).onPreviewKeyEvent { ev ->
                        if (ev.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false
                        else when (ev.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT -> { runCatching { cancelF.requestFocus() }; true }
                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> true
                            else -> false
                        }
                    }
                )
            }
        }
    }
}
