package com.leantv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leantv.app.data.UiState

// ---------------------------------------------------------------- Tweaks

@Composable
fun TweaksScreen(s: UiState, vm: MainViewModel) {
    val fr = rememberAutoFocus()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ContentPad),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Speed tweaks", style = Ty.Title)
        Text(
            "Small system settings that make the TV feel quicker. Original values are saved and come back when you switch a tweak off.",
            style = Ty.Dim,
        )
        ToggleRow(
            title = "Faster animations",
            body = "Menus and app transitions run at half length. Helps most on slower processors.",
            on = s.tweaks.animations,
            onToggle = { vm.setAnimations(!s.tweaks.animations) },
            modifier = Modifier.focusRequester(fr),
        )
        ToggleRow(
            title = "Block ad and tracker domains",
            body = "Uses AdGuard's private DNS so known ad and tracking servers don't load. Switch it off if an app can't connect.",
            on = s.tweaks.dns,
            onToggle = { vm.setDns(!s.tweaks.dns) },
        )
        ActionRow(
            title = "Clear app caches",
            body = "Frees storage used by temporary files. Apps rebuild what they need.",
            action = "Clear now",
            onClick = vm::clearCaches,
        )
    }
}

@Composable
private fun ToggleRow(title: String, body: String, on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Focusable(onClick = onToggle, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Ty.Heading)
                Spacer(Modifier.height(4.dp))
                Text(body, style = Ty.Small)
            }
            Spacer(Modifier.width(24.dp))
            TvSwitch(on)
        }
    }
}

@Composable
private fun ActionRow(title: String, body: String, action: String, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Ty.Heading)
                Spacer(Modifier.height(4.dp))
                Text(body, style = Ty.Small)
            }
            Spacer(Modifier.width(24.dp))
            Pill(action, Pal.Amber)
        }
    }
}

// ---------------------------------------------------------------- Restore

@Composable
fun RestoreScreen(s: UiState, vm: MainViewModel) {
    val fr = rememberAutoFocus()
    Column(
        Modifier
            .fillMaxSize()
            .padding(ContentPad)
    ) {
        Text("Restore", style = Ty.Title)
        Spacer(Modifier.height(6.dp))
        Text(
            if (s.removed.isEmpty()) "Nothing has been removed. Your apps are as the manufacturer left them."
            else "${s.removed.size} app${if (s.removed.size == 1) "" else "s"} changed by Pear TV. Bring any back here.",
            style = Ty.Dim,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Restore all apps", onClick = vm::restoreAll, modifier = Modifier.focusRequester(fr))
            TvButton("Reset speed tweaks", onClick = vm::resetTweaks, primary = false)
        }
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(s.removed, key = { it.pkg }) { r ->
                Focusable(
                    onClick = { vm.restoreOne(r.pkg) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, style = Ty.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.pkg, style = Ty.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Pill(if (r.method.name == "DISABLE") "Disabled" else "Removed", Pal.Dim)
                        Spacer(Modifier.width(12.dp))
                        Pill("Restore", Pal.Amber)
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Pal.Surface)
                .padding(20.dp)
        ) {
            Text("Need to start completely fresh?", style = Ty.Heading)
            Spacer(Modifier.height(4.dp))
            Text(
                "Open the TV's own Settings, then Device Preferences, then Reset. A factory reset also erases your accounts and apps you installed.",
                style = Ty.Small,
            )
        }
    }
}
