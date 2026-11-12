package com.leantv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ThemePreset(
    val id: String,
    val name: String,
    val gradientColors: List<Color>,
    val description: String
)

val WALLPAPER_THEMES = listOf(
    ThemePreset(
        id = "aurora",
        name = "Graphite",
        gradientColors = listOf(Color(0xFF050507), Color(0xFF101014), Color(0xFF1A1A21)),
        description = "Calm neutral graphite, like a premium streaming box."
    ),
    ThemePreset(
        id = "pitch_black",
        name = "Pitch Black",
        gradientColors = listOf(Color(0xFF000000), Color(0xFF070707), Color(0xFF101010)),
        description = "OLED pure black with minimal specular vignette."
    ),
    ThemePreset(
        id = "deep_space",
        name = "Deep Space",
        gradientColors = listOf(Color(0xFF0B001A), Color(0xFF1F0D3D), Color(0xFF381460)),
        description = "Midnight violet and deep nebula sapphire."
    ),
    ThemePreset(
        id = "sunset",
        name = "Sunset Crimson",
        gradientColors = listOf(Color(0xFF1A0A0E), Color(0xFF3B151E), Color(0xFF5C1B2A)),
        description = "Warm burgundy, wine, and amber glow."
    ),
    ThemePreset(
        id = "glass",
        name = "Apple Dark Glass",
        gradientColors = listOf(Color(0xFF1C1C1E), Color(0xFF232326), Color(0xFF2C2C2E)),
        description = "Frosted smoky slate with dynamic hero color accent."
    )
)

@Composable
fun PersonalizationDialog(
    currentTheme: String,
    is24h: Boolean,
    showGreeting: Boolean,
    onSelectTheme: (String) -> Unit,
    onToggle24h: () -> Unit,
    onToggleGreeting: () -> Unit,
    onOpenFileManagerForWallpaper: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .modalTrap()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .glassCard()
                .clickable(enabled = false) {}
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PERSONALIZATION",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF64D2FF),
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Wallpaper & Display",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Ambient Themes",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xCCFFFFFF),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(WALLPAPER_THEMES, key = { it.id }) { theme ->
                    ThemeCard(
                        theme = theme,
                        isSelected = currentTheme == theme.id,
                        onClick = { onSelectTheme(theme.id) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Display Toggles Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingTogglePill(
                    label = if (is24h) "Clock: 24 Hours" else "Clock: 12 Hours (AM/PM)",
                    isActive = is24h,
                    onClick = onToggle24h,
                    modifier = Modifier.weight(1f)
                )

                SettingTogglePill(
                    label = if (showGreeting) "Greeting: Shown" else "Greeting: Hidden",
                    isActive = showGreeting,
                    onClick = onToggleGreeting,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Custom Wallpaper from File Manager
            SettingTogglePill(
                label = "Choose Custom Wallpaper from TV Storage...",
                isActive = false,
                onClick = onOpenFileManagerForWallpaper,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x22FFFFFF))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text("Close", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: ThemePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Column(
        modifier = Modifier
            .width(135.dp)
            .scale(if (isFocused) 1.06f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFocused) Color(0x38FFFFFF) else Color(0x14FFFFFF))
            .border(
                width = if (isFocused) 2.dp else if (isSelected) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) Color(0xFF64D2FF) else Color(0x22FFFFFF),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gradient Preview Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(theme.gradientColors))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = theme.name,
            color = if (isSelected) Color(0xFF64D2FF) else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun SettingTogglePill(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .scale(if (isFocused) 1.03f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) Color.White else if (isActive) Color(0x3364D2FF) else Color(0x16FFFFFF))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isActive) Color(0xFF64D2FF) else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
