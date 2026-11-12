package com.leantv.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.data.AppItem
import com.leantv.app.data.Filter
import com.leantv.app.data.RemovalMethod
import com.leantv.app.data.Tier
import com.leantv.app.data.UiState

@Composable
fun DebloatScreen(s: UiState, vm: MainViewModel) {
    val shown = remember(s.apps, s.filter) {
        when (s.filter) {
            Filter.RECOMMENDED -> s.apps.filter { it.tier == Tier.RECOMMENDED }
            Filter.TELEMETRY_ADS -> s.apps.filter {
                it.tier == Tier.RECOMMENDED ||
                it.pkg.contains("adservice", ignoreCase = true) ||
                it.pkg.contains("advertise", ignoreCase = true) ||
                it.pkg.contains("analytics", ignoreCase = true) ||
                it.pkg.contains("telemetry", ignoreCase = true) ||
                it.pkg.contains("tracking", ignoreCase = true) ||
                it.pkg.contains("statistic", ignoreCase = true) ||
                it.pkg.contains("recommendation", ignoreCase = true)
            }
            Filter.OEM_BLOAT -> s.apps.filter {
                it.pkg.startsWith("com.xiaomi") ||
                it.pkg.startsWith("com.mitv") ||
                it.pkg.startsWith("com.tcl.") ||
                it.pkg.startsWith("com.hisense.") ||
                it.pkg.startsWith("com.sony.dtv") ||
                it.pkg.startsWith("com.realme.") ||
                it.pkg.startsWith("com.oneplus.")
            }
            Filter.STREAMING -> s.apps.filter {
                it.pkg in listOf(
                    "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.jio.media.ondemand",
                    "com.hotstar.tv", "com.sonyliv.tv", "com.zee5.tv", "com.voot.tv", "com.altbalaji.tv",
                    "com.hungama.tv", "com.erosnow.tv", "com.spottv", "com.google.android.youtube.tvmusic"
                )
            }
            Filter.REVIEW -> s.apps.filter { it.tier == Tier.ADVANCED }
            Filter.ALL -> s.apps
        }
    }

    val fr = rememberAutoFocus()
    val n = s.selected.size

    Column(
        Modifier
            .fillMaxSize()
            .padding(ContentPad)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("CleanYourTV Debloater", style = Ty.Display.copy(fontSize = 36.sp))
                Spacer(Modifier.height(4.dp))
                Text(
                    "${s.apps.size} system apps found • ${s.protectedCount} critical core apps protected",
                    style = Ty.Dim.copy(fontSize = 19.sp)
                )
            }

            // Quick Auto-Clean preset button
            TvButton(
                text = "CleanYourTV Auto Preset",
                onClick = vm::applyCleanYourTvPreset,
                primary = true,
                modifier = Modifier.focusRequester(fr)
            )
        }

        Spacer(Modifier.height(14.dp))

        // Category Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(Filter.entries) { f ->
                val count = when (f) {
                    Filter.RECOMMENDED -> s.apps.count { it.tier == Tier.RECOMMENDED }
                    Filter.TELEMETRY_ADS -> s.apps.count {
                        it.tier == Tier.RECOMMENDED ||
                        it.pkg.contains("adservice", ignoreCase = true) ||
                        it.pkg.contains("telemetry", ignoreCase = true) ||
                        it.pkg.contains("analytics", ignoreCase = true) ||
                        it.pkg.contains("tracking", ignoreCase = true)
                    }
                    Filter.OEM_BLOAT -> s.apps.count {
                        it.pkg.startsWith("com.xiaomi") || it.pkg.startsWith("com.mitv") ||
                        it.pkg.startsWith("com.tcl.") || it.pkg.startsWith("com.hisense.") ||
                        it.pkg.startsWith("com.sony.dtv")
                    }
                    Filter.STREAMING -> s.apps.count {
                        it.pkg in listOf("com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.hotstar.tv", "com.jio.media.ondemand")
                    }
                    Filter.REVIEW -> s.apps.count { it.tier == Tier.ADVANCED }
                    Filter.ALL -> s.apps.size
                }

                Chip(
                    text = "${f.label} ($count)",
                    selected = s.filter == f,
                    onClick = { vm.setFilter(f) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Bulk Selection Actions & Removal Method
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvButton("Select Ads & Trackers", onClick = vm::selectTelemetryAndAds, primary = false)
            TvButton("Select OEM Bloat", onClick = vm::selectOemBloat, primary = false)
            if (n > 0) {
                TvButton("Clear ($n)", onClick = vm::clearSelection, primary = false)
            }

            Spacer(Modifier.weight(1f))

            RemovalMethod.entries.forEach { m ->
                Chip(m.label, s.method == m, { vm.setMethod(m) })
            }

            TvButton(
                text = if (n == 0) s.method.label else "${s.method.label} $n Selected",
                onClick = vm::requestRemove,
                enabled = n > 0,
                primary = true
            )
        }

        Spacer(Modifier.height(14.dp))

        // App List
        when {
            s.scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (s.filter == Filter.REVIEW) "Nothing here needs review." else "No packages match this filter. Your TV is clean!",
                    style = Ty.Dim
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 36.dp),
            ) {
                items(shown, key = { it.pkg }) { app ->
                    val sel = app.pkg in s.selected
                    DebloatAppRow(
                        app = app,
                        selected = sel,
                        onToggle = { vm.toggle(app.pkg) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DebloatAppRow(
    app: AppItem,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val cardBg by animateColorAsState(
        when {
            focused -> Pal.SurfaceHi
            selected -> Pal.Amber.copy(alpha = 0.12f)
            else -> Pal.Surface
        },
        tween(140), label = "rowBg"
    )
    val ringColor by animateColorAsState(
        if (focused) Pal.Amber else Color.Transparent,
        tween(140), label = "rowRing"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(if (focused) 2.dp else 1.dp, ringColor, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CheckMark(checked = selected)

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        app.label,
                        style = Ty.Heading.copy(
                            color = if (focused) Pal.Amber else Pal.Text,
                            fontSize = 20.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Pill(app.tier.label, tierColor(app.tier))

                    if (app.disabled) {
                        Pill("Disabled", Pal.Mint)
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    app.pkg,
                    style = Ty.Small.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Pal.Dim,
                        fontSize = 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    app.why,
                    style = Ty.Small.copy(color = Color(0xFFC7DCDE), fontSize = 17.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
