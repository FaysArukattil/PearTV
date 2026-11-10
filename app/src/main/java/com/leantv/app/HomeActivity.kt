package com.leantv.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.leantv.app.launcher.HomeScreen
import com.leantv.app.launcher.LauncherViewModel

class HomeActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { HomeScreen(vm) }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}

