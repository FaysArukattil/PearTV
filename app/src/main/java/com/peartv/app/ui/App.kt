package com.peartv.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.peartv.app.data.Conn
import com.peartv.app.data.Screen
import com.peartv.app.data.UiState
import kotlinx.coroutines.delay

@Composable
fun PearTvApp(vm: MainViewModel) {
    TvCanvas { AppContent(vm) }
}

@Deprecated("Use PearTvApp instead", ReplaceWith("PearTvApp(vm)"))
@Composable
fun LeanTvApp(vm: MainViewModel) = PearTvApp(vm)

@Composable
private fun AppContent(vm: MainViewModel) {
    val s by vm.state.collectAsState()

    BackHandler(enabled = s.showCleanWizard || s.screen in listOf(Screen.InstalledApps, Screen.Debloat, Screen.Tweaks, Screen.Restore)) {
        if (s.showCleanWizard) {
            vm.dismissCleanTvWizard()
        } else {
            vm.go(Screen.Dashboard)
        }
    }

    Backdrop {
        // Swallow every remote key while a long operation is running.
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { s.busy != null }
        ) {
            when (s.screen) {
                Screen.Setup -> SetupScreen(s, vm)
                else -> Row(Modifier.fillMaxSize()) {
                    Rail(s, vm)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (s.screen) {
                            Screen.Dashboard -> DashboardScreen(s, vm)
                            Screen.InstalledApps -> InstalledAppsScreen(s, vm)
                            Screen.Debloat -> DebloatScreen(s, vm)
                            Screen.Tweaks -> TweaksScreen(s, vm)
                            Screen.Restore -> RestoreScreen(s, vm)
                            else -> Unit
                        }
                    }
                }
            }

            // ⚡ CleanYourTV 1-Click Wizard
            if (s.showCleanWizard) {
                CleanTvWizardDialog(
                    majorOptions = s.majorOptions,
                    onToggleOption = vm::toggleMajorOption,
                    onConfirm = vm::executeCleanTvWizard,
                    onDismiss = vm::dismissCleanTvWizard
                )
            }

            s.confirm?.let { c ->
                Scrim {
                    val cancelFocus = rememberAutoFocus(c)
                    Column(
                        Modifier
                            .width(580.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Pal.Surface)
                            .padding(36.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(c.title, style = Ty.Title)
                        Text(c.body, style = Ty.Dim)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TvButton("Cancel", onClick = vm::cancelConfirm, primary = false, modifier = Modifier.focusRequester(cancelFocus))
                            TvButton(c.action, onClick = vm::confirmRemove)
                        }
                    }
                }
            }

            s.busy?.let { label ->
                Scrim {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Spinner()
                        Text(label, style = Ty.Heading)
                        s.progress?.let { (done, total) -> Text("$done of $total", style = Ty.Dim) }
                    }
                }
            }

            s.toast?.let { msg ->
                LaunchedEffect(msg) {
                    delay(4200)
                    vm.clearToast()
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = Safe.V + 8.dp, start = Safe.H, end = Safe.H),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(
                        msg,
                        style = Ty.Body,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Pal.SurfaceHi)
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Rail(s: UiState, vm: MainViewModel) {
    Column(
        Modifier
            .width(232.dp)
            .fillMaxHeight()
            .background(Color(0x40000000))
            .padding(start = 28.dp, end = 16.dp, top = Safe.V, bottom = Safe.V),
    ) {
        Text(androidx.compose.ui.res.stringResource(com.peartv.app.R.string.app_name), style = Ty.Title.copy(color = Pal.Amber), modifier = Modifier.padding(start = 16.dp))
        Spacer(Modifier.height(20.dp))
        val context = androidx.compose.ui.platform.LocalContext.current
        RailItem("Home screen", false, {
            val intent = android.content.Intent(context, com.peartv.app.HomeActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        })
        Spacer(Modifier.height(8.dp))
        RailItem("Overview", s.screen == Screen.Dashboard, { vm.go(Screen.Dashboard) })
        Spacer(Modifier.height(4.dp))
        RailItem("Installed apps", s.screen == Screen.InstalledApps, { vm.go(Screen.InstalledApps) })
        Spacer(Modifier.height(4.dp))
        RailItem("Debloat", s.screen == Screen.Debloat, { vm.go(Screen.Debloat) })
        Spacer(Modifier.height(4.dp))
        RailItem("Speed tweaks", s.screen == Screen.Tweaks, { vm.go(Screen.Tweaks) })
        Spacer(Modifier.height(4.dp))
        RailItem("Restore", s.screen == Screen.Restore, { vm.go(Screen.Restore) })
        Spacer(Modifier.weight(1f))
        val connected = s.conn as? Conn.Connected
        Text(
            if (connected != null) "Connected" else "Not connected",
            style = Ty.Small.copy(color = if (connected != null) Pal.Mint else Pal.Coral),
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}
