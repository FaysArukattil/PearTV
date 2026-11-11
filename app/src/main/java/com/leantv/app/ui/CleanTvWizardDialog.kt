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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leantv.app.data.MajorDebloatOption

/**
 * CleanYourTV On-Device Remote-First Debloat Decision Wizard.
 * Recognizes TV make/model, displays detected telemetry and bloatware,
 * guides USB/Wireless debugging pairing with the remote,
 * and allows granular KEEP/REMOVE choices for major streaming apps and tools.
 */
@Composable
fun CleanTvWizardDialog(
    deviceLabel: String = "Android TV",
    majorOptions: List<MajorDebloatOption>,
    onToggleOption: (String) -> Unit,
    onOpenDeveloperOptions: () -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val autoFocus = rememberAutoFocus()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xD905080E))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(720.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF13171F))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp))
                .clickable(enabled = false) {}
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header with Device Recognition
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x3364B5F6))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "📺 RECOGNIZED: $deviceLabel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF90CAF9),
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "⚡ CleanYourTV System Debloater",
                style = Ty.Title.copy(color = Color.White, fontSize = 28.sp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Telemetry daemons, manufacturer ads, and background trackers will be safely removed. Choose which features to KEEP or REMOVE:",
                style = Ty.Dim.copy(fontSize = 13.sp),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            // Options List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(majorOptions, key = { it.id }) { opt ->
                    MajorOptionRow(
                        option = opt,
                        onToggle = { onToggleOption(opt.id) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Remote Pairing Tip Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x18FFFFFF))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "One-Time Remote Authorization:",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Ensure 'USB debugging' is ON in Developer Options, then accept prompt.",
                            color = Color(0x99FFFFFF),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable(onClick = onOpenDeveloperOptions)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Open Developer Options", color = Color(0xFF64B5F6), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
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
                    text = "Skip for Now",
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.weight(1f)
                )

                TvButton(
                    text = "⚡ Clean TV Now",
                    onClick = onConfirm,
                    primary = true,
                    modifier = Modifier.weight(1.6f).focusRequester(autoFocus)
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
            focused -> Color(0x38FFFFFF)
            isRemoving -> Color(0x24FF5252)
            else -> Color(0x184CAF50)
        },
        tween(140), label = "wizCardBg"
    )
    val ringColor by animateColorAsState(
        if (focused) Color.White else Color(0x22FFFFFF),
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
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    option.description,
                    style = Ty.Small.copy(fontSize = 12.sp, color = Color(0xAAFFFFFF))
                )
            }

            Spacer(Modifier.width(14.dp))

            // Action Status Badge: "KEEP" or "REMOVE"
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isRemoving) Color(0xFFFF5252) else Color(0xFF4CAF50))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isRemoving) "REMOVE ✕" else "KEEP ✓",
                    style = Ty.Small.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}
