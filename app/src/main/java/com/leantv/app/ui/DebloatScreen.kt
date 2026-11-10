package com.leantv.app.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.leantv.app.data.AppItem
import com.leantv.app.data.Filter
import com.leantv.app.data.RemovalMethod
import com.leantv.app.data.Tier
import com.leantv.app.data.UiState

@Composable
fun DebloatScreen(s: UiState, vm: MainViewModel) {
    val shown = remember(s.apps, s.filter) {
        when (s.filter) {
            Filter.RECOMMENDED -> s.apps.filter { it.tier != Tier.ADVANCED }
            Filter.REVIEW -> s.apps.filter { it.tier == Tier.ADVANCED }
            Filter.ALL -> s.apps
        }
    }
    val fr = rememberAutoFocus()
    val easy = s.apps.count { it.tier != Tier.ADVANCED }
    val review = s.apps.count { it.tier == Tier.ADVANCED }
    val n = s.selected.size

    Column(
        Modifier
            .fillMaxSize()
            .padding(ContentPad)
    ) {
        // Header: title left, short summary right (one line, saves height for the list)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Debloat", style = Ty.Title)
            Spacer(Modifier.weight(1f))
            Text("${s.protectedCount} core apps hidden for safety", style = Ty.Small)
        }
        Spacer(Modifier.height(14.dp))

        // Filters + method, one row that fits the content width
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip("Suggested $easy", s.filter == Filter.RECOMMENDED, { vm.setFilter(Filter.RECOMMENDED) }, Modifier.focusRequester(fr))
            Chip("Review $review", s.filter == Filter.REVIEW, { vm.setFilter(Filter.REVIEW) })
            Chip("All ${s.apps.size}", s.filter == Filter.ALL, { vm.setFilter(Filter.ALL) })
            Spacer(Modifier.weight(1f))
            RemovalMethod.entries.forEach { m ->
                Chip(m.label, s.method == m, { vm.setMethod(m) })
            }
        }
        Spacer(Modifier.height(12.dp))

        // Actions + method hint
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvButton("Select suggested", onClick = vm::selectRecommended, primary = false)
            TvButton(
                if (n == 0) s.method.label else "${s.method.label} $n app${if (n == 1) "" else "s"}",
                onClick = vm::requestRemove,
                enabled = n > 0,
            )
            Text(
                s.method.hint,
                style = Ty.Small,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(14.dp))

        when {
            s.scanning -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner() }
            shown.isEmpty() -> Text(
                if (s.filter == Filter.REVIEW) "Nothing here needs review." else "Nothing left to suggest. Your TV is already lean.",
                style = Ty.Dim,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(shown, key = { it.pkg }) { app ->
                    AppRow(app, app.pkg in s.selected) { vm.toggle(app.pkg) }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppItem, checked: Boolean, onClick: () -> Unit) {
    Focusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        idle = Color.Transparent,
        enabled = !app.disabled,
        scaleOnFocus = 1.01f,
        idleOutline = false,
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckMark(checked)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = Ty.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.why, style = Ty.Small, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (app.tier == Tier.ADVANCED) {
                    Text(
                        app.pkg,
                        style = Ty.Small.copy(color = Pal.Dim.copy(alpha = 0.7f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            if (app.disabled) Pill("Disabled", Pal.Dim) else Pill(app.tier.label, tierColor(app.tier))
        }
    }
}
