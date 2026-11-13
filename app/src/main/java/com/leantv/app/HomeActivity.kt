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
        setContent { HomeScreen(vm) }
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
