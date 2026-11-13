package com.leantv.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.leantv.app.launcher.HomeScreen
import com.leantv.app.launcher.LauncherViewModel
import com.leantv.app.ui.TvSound

class HomeActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvSound.init(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window.setFormat(android.graphics.PixelFormat.RGBA_8888)
        setContent { HomeScreen(vm) }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.action == android.view.KeyEvent.ACTION_UP) {
                if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
            return true // Consume back key: Home is the root, never exit or pop to previous app!
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Compose BackHandler in HomeScreen handles any active dialogs/overlays.
        // If on the root home screen, consume the back press so the launcher never exits.
        if (onBackPressedDispatcher.hasEnabledCallbacks()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
