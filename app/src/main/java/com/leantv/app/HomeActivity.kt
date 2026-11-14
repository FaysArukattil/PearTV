package com.leantv.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.leantv.app.launcher.HomeScreen
import com.leantv.app.launcher.LauncherViewModel
import com.leantv.app.ui.TvSound

class HomeActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()

        // Start loading preferences on a background thread right now; by the time the ViewModel needs
        // them they are already in memory instead of blocking the main thread.
        applicationContext.getSharedPreferences("leantv", MODE_PRIVATE)

        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Create the ViewModel now so app loading starts immediately, in parallel with window setup.
        val viewModel = vm

        // Keep the splash up until Home can draw its first REAL frame (apps + artwork ready),
        // but never longer than SPLASH_MAX_MS so a slow device can't get stuck on it.
        val startedAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            !viewModel.splashReady && SystemClock.uptimeMillis() - startedAt < SPLASH_MAX_MS
        }
        // Smooth hand-off: logo grows slightly while the splash fades into Home (no hard cut).
        splash.setOnExitAnimationListener { provider ->
            provider.iconView.animate()
                .scaleX(1.15f).scaleY(1.15f).alpha(0f)
                .setDuration(220L)
                .setInterpolator(AccelerateInterpolator())
                .start()
            provider.view.animate()
                .alpha(0f)
                .setDuration(240L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { provider.remove() }
                .start()
        }

        setContent { HomeScreen(viewModel) }

        // Non-essential start-up work happens after Home is on its way to the screen.
        lifecycleScope.launch(Dispatchers.Default) { TvSound.init(applicationContext) }
        checkInitialStoragePermissions()
    }

    private fun checkInitialStoragePermissions() {
        lifecycleScope.launch(Dispatchers.IO) {
            com.leantv.app.files.FileManagerHelper.autoGrantStoragePermission(applicationContext)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    101
                )
            }
        }
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

    private companion object {
        const val SPLASH_MAX_MS = 1500L
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
