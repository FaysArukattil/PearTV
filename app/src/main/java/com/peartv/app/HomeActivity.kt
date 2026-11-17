package com.peartv.app

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
import com.peartv.app.launcher.HomeScreen
import com.peartv.app.launcher.LauncherViewModel
import com.peartv.app.ui.TvSound

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
        // Fast splash hand-off: rapid fade directly into Pear TV.
        splash.setOnExitAnimationListener { provider ->
            provider.iconView.animate()
                .scaleX(1.06f).scaleY(1.06f).alpha(0f)
                .setDuration(90L)
                .setInterpolator(AccelerateInterpolator())
                .start()
            provider.view.animate()
                .alpha(0f)
                .setDuration(110L)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction { provider.remove() }
                .start()
        }

        setContent { HomeScreen(viewModel) }

        // Non-essential start-up work happens after Home is on its way to the screen.
        lifecycleScope.launch(Dispatchers.Default) { TvSound.init(applicationContext) }
        // First run: the welcome screen asks for permissions itself, so no system pop-ups before it.
        val firstRunPending = !getSharedPreferences("leantv", MODE_PRIVATE).getBoolean("debloat_wizard_dismissed", false)
        if (!firstRunPending) {
            checkInitialStoragePermissions()
        }
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("get_started", false) ||
            intent.action == "com.peartv.app.action.GET_STARTED" ||
            intent.getBooleanExtra("wizard", false) ||
            intent.action == "com.peartv.app.action.WIZARD") {
            val stepExtra = intent.getIntExtra("wizard_step", 0)
            vm.setWizardStep(stepExtra)
            return
        }
        val screen = intent.getStringExtra("screen")
        if (screen != null && screen != "wizard") {
            vm.dismissCleanTvWizard()
            vm.goHome()
        }
        when (screen) {
            "home" -> Unit
            "quick_share" -> vm.openQuickShareControl()
            "storage" -> vm.openStorageManager()
            "storage_apps" -> vm.openCleanTvScreen()
            "screensaver" -> {
                val aerial = intent.getStringExtra("aerial")
                if (!aerial.isNullOrBlank()) {
                    val matching = com.peartv.app.ui.APPLE_AERIAL_PRESETS.firstOrNull {
                        it.location.contains(aerial, ignoreCase = true) ||
                        it.subtext.contains(aerial, ignoreCase = true) ||
                        it.url.contains(aerial, ignoreCase = true)
                    }
                    if (matching != null) {
                        vm.setScreensaverSelectedUrl(matching.url)
                    }
                } else if (intent.getBooleanExtra("aerial_reset", false)) {
                    vm.setScreensaverSelectedUrl(null)
                }
                vm.openScreensaver()
            }
            "screensaver_settings" -> vm.openScreensaverSettings()
            "system_monitor" -> vm.openSystemMonitor()
            "personalization" -> vm.openPersonalization()
            "file_manager" -> {
                val initialPath = intent.getStringExtra("path")
                val initialDir = if (initialPath != null) java.io.File(initialPath) else null
                val highlight = intent.getStringExtra("highlight")
                vm.openFileManager(initialDir = initialDir, highlightFile = highlight)
            }
        }
        if (intent.action == "com.peartv.app.action.FILE_MANAGER") {
            val initialPath = intent.getStringExtra("path")
            val initialDir = if (initialPath != null) java.io.File(initialPath) else null
            val highlight = intent.getStringExtra("highlight")
            vm.openFileManager(initialDir = initialDir, highlightFile = highlight)
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
            com.peartv.app.files.FileManagerHelper.autoGrantStoragePermission(applicationContext)
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
                if (!vm.handleBackPress()) {
                    vm.goHome()
                }
            }
            return true // ALWAYS consume Back key: Home is the root, never finish or pop to previous app!
        }
        if (event.keyCode == android.view.KeyEvent.KEYCODE_HOME) {
            if (event.action == android.view.KeyEvent.ACTION_UP) {
                vm.goHome()
            }
            return true
        }
        if (event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN ||
            event.keyCode == android.view.KeyEvent.KEYCODE_VOLUME_MUTE) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                vm.handleVolumeKey(event.keyCode)
            }
            return true // Suppress ugly system volume dialog and show our custom smooth Apple TV volume HUD!
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK || keyCode == android.view.KeyEvent.KEYCODE_HOME) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK || keyCode == android.view.KeyEvent.KEYCODE_HOME) {
            if (!vm.handleBackPress()) {
                vm.goHome()
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private companion object {
        const val SPLASH_MAX_MS = 200L
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The remote's Home key (or any HOME intent) always lands on plain Home: close every popup/page,
        // unless the initial Get Started wizard is active.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (!vm.state.value.showCleanTvWizard) {
                vm.goHome()
            }
        }
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        if (intent?.hasExtra("screen") == true || intent?.hasExtra("wizard") == true || intent?.hasExtra("get_started") == true || vm.state.value.showCleanTvWizard) {
            // Keep the requested screen/modal open
        } else {
            vm.goHome()
        }
    }

    override fun onPause() {
        // An app is coming to the front (input switch, screen off, etc.):
        // Always close the screensaver immediately — it must NEVER render over another HDMI input!
        vm.closeScreensaver()
        vm.onPause()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Safe: Never call super.onBackPressed() or fallback which kills the activity!
        if (!vm.handleBackPress()) {
            vm.goHome()
        }
    }
}