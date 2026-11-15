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
        checkAccountPermission()
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getStringExtra("screen") == "file_manager" || intent.action == "com.leantv.app.action.FILE_MANAGER") {
            val initialPath = intent.getStringExtra("path")
            val initialDir = if (initialPath != null) java.io.File(initialPath) else null
            val highlight = intent.getStringExtra("highlight")
            vm.openFileManager(initialDir = initialDir, highlightFile = highlight)
        }
    }

    /** Lets Home see the TV's signed-in Google account (shown in the top-right chip). */
    private fun checkAccountPermission() {
        val storagePending = Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (!storagePending && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(android.Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.GET_ACCOUNTS), 102)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 102 || requestCode == 101) {
            vm.loadProfile()
            vm.syncAccountsToApps()
        }
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
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.GET_ACCOUNTS
                    ),
                    101
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Let standard window & Compose hierarchy process the key event first so dialogs,
        // focus managers, and child components receive their key callbacks normally.
        if (super.dispatchKeyEvent(event)) return true

        // If unhandled Back reaches the root activity level:
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            if (event.action == android.view.KeyEvent.ACTION_UP) {
                if (onBackPressedDispatcher.hasEnabledCallbacks()) {
                    onBackPressedDispatcher.onBackPressed()
                }
            }
            return true // Consume back key: Home is the root, never exit to previous app or crash!
        }
        return false
    }

    private companion object {
        const val SPLASH_MAX_MS = 1500L
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The remote's Home key (or any HOME intent) always lands on plain Home: close every popup/page.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            vm.goHome()
        }
        handleIntent(intent)
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
