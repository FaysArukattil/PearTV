package com.leantv.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import com.leantv.app.adb.AdbClient
import com.leantv.app.data.SystemDebloater

/**
 * Makes the on-screen keyboard (Gboard) open on Android TV.
 *
 * Typical reasons it stays closed on a TV: the installed keyboard isn't enabled / isn't the selected
 * one, or the remote counts as a hardware keyboard and "show on-screen keyboard with hardware
 * keyboard" is off. [repair] fixes both; [show] forces the keyboard up for a focused text field.
 */
object KeyboardHelper {
    const val GBOARD_PKG = "com.google.android.inputmethod.latin"
    private const val SHOW_WITH_HW_KEY = "show_ime_with_hard_keyboard"

    private fun imm(ctx: Context) = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    /** Gboard if installed, otherwise the first installed keyboard that isn't a remote/voice service. */
    fun preferredIme(ctx: Context): InputMethodInfo? {
        val all = try { imm(ctx).inputMethodList } catch (_: Exception) { emptyList() }
        return all.firstOrNull { it.packageName == GBOARD_PKG }
            ?: all.firstOrNull { !it.packageName.contains("remote", true) && !it.packageName.contains("voice", true) }
            ?: all.firstOrNull()
    }

    private fun defaultImeId(ctx: Context): String? =
        try { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) } catch (_: Exception) { null }

    private fun showWithHardwareKeyboard(ctx: Context): Boolean =
        try { Settings.Secure.getInt(ctx.contentResolver, SHOW_WITH_HW_KEY, 0) == 1 } catch (_: Exception) { false }

    fun isReady(ctx: Context): Boolean {
        val ime = preferredIme(ctx) ?: return false
        val enabled = try { imm(ctx).enabledInputMethodList.any { it.id == ime.id } } catch (_: Exception) { false }
        return enabled && defaultImeId(ctx) == ime.id && showWithHardwareKeyboard(ctx)
    }

    /**
     * Enables + selects the keyboard and turns on "show with hardware keyboard".
     * Tries Shizuku/root first, then on-device ADB. [allowAdbConnect] is only true when the user asked.
     */
    suspend fun repair(ctx: Context, adb: AdbClient, allowAdbConnect: Boolean): Boolean {
        val ime = preferredIme(ctx) ?: return false
        if (isReady(ctx)) return true
        val id = ime.id
        val cmd = "ime enable $id; ime set $id; settings put secure $SHOW_WITH_HW_KEY 1"
        var ok = SystemDebloater.runPrivilegedCommand(cmd)
        if (!ok) {
            try {
                if (!adb.isConnected && allowAdbConnect) adb.connect()
                if (adb.isConnected) ok = adb.shell(cmd).code == 0
            } catch (_: Exception) {}
        }
        return ok && isReady(ctx)
    }

    /** Last resort: the system keyboard settings (or the picker) so the user can enable it by hand. */
    fun openKeyboardSettings(ctx: Context): Boolean {
        val pm = ctx.packageManager
        val intents = listOf(
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            Intent("android.settings.INPUT_METHOD_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in intents) {
            try {
                if (pm.resolveActivity(i, 0) == null) continue
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                ctx.startActivity(i)
                return true
            } catch (_: Exception) {}
        }
        return try { imm(ctx).showInputMethodPicker(); true } catch (_: Exception) { false }
    }

    /** Forces the soft keyboard up for the focused view, even with a remote that looks like a keyboard. */
    @Suppress("DEPRECATION")
    fun show(view: View) {
        view.postDelayed({
            try {
                val m = imm(view.context)
                m.showSoftInput(view, InputMethodManager.SHOW_FORCED)
            } catch (_: Exception) {}
        }, 150)
    }
}

/**
 * Put on any text field: the keyboard opens when the field gets focus and whenever the user presses
 * OK / Enter on it (plain D-pad focus alone does not open it on a TV).
 */
@Composable
fun Modifier.tvKeyboard(): Modifier {
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    return this
        .onFocusChanged {
            if (it.isFocused) {
                keyboard?.show()
                KeyboardHelper.show(view)
            }
        }
        .onPreviewKeyEvent { ev ->
            if (ev.type == KeyEventType.KeyUp && (ev.key == Key.DirectionCenter || ev.key == Key.Enter || ev.key == Key.NumPadEnter)) {
                keyboard?.show()
                KeyboardHelper.show(view)
            }
            false
        }
}
