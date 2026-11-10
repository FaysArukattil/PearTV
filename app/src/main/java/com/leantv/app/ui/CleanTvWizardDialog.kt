package com.leantv.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.data.MajorDebloatOption

/**
 * Interactive Decision Wizard for 1-Click Clean TV.
 * Lets users decide whether to keep or remove major apps (Miracast, Assistant, Netflix, Prime, etc.)
 * while ensuring core system framework is never bricked.
 */
@Composable
fun CleanTvWizardDialog(
    majorOptions: List<MajorDebloatOption>,
    onToggleOption: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD9050E14))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(680.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Pal.Surface)
                .border(1.dp, Pal.Line, RoundedCornerShape(26.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Text(
                "⚡ CleanYourTV Auto Debloater",
                style = Ty.Title.copy(color = Pal.Amber, fontSize = 28.sp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "All ads, telemetry, and background tracking will be disabled. Choose which major apps you wish to KEEP or REMOVE:",
                style = Ty.Dim.copy(fontSize = 14.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            // Options List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(majorOptions, key = { it.id }) { opt ->
                    MajorOptionRow(
                        option = opt,
                        onToggle = { onToggleOption(opt.id) }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Action Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.weight(1f)
                )

                TvButton(
                    text = "⚡ Clean TV Now",
                    onClick = onConfirm,
                    primary = true,
                    modifier = Modifier.weight(1.5f).focusRequester(autoFocus)
                )
            }
        }
    }
}

@Composable
private fun MajorOptionRow(
    option: MajorDebloatOption,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isRemoving = option.isSelectedForRemoval

    val cardBg by animateColorAsState(
        when {
            focused -> Pal.SurfaceHi
            isRemoving -> Pal.Coral.copy(alpha = 0.12f)
            else -> Pal.Mint.copy(alpha = 0.10f)
        },
        tween(140), label = "wizCardBg"
    )
    val ringColor by animateColorAsState(
        if (focused) Pal.Amber else Color.Transparent,
        tween(140), label = "wizCardRing"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(if (focused) 2.dp else 1.dp, ringColor, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    option.title,
                    style = Ty.Heading.copy(
                        color = if (focused) Pal.Amber else Pal.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    option.description,
                    style = Ty.Small.copy(fontSize = 13.sp, color = Pal.Dim)
                )
            }

            Spacer(Modifier.width(14.dp))

            // Action Status Badge: "KEEP" or "REMOVE"
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRemoving) Pal.Coral else Pal.Mint)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isRemoving) "REMOVE ✕" else "KEEP ✓",
                    style = Ty.Small.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

