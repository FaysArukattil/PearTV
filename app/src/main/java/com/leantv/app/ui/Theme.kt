package com.leantv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Pal {
    val Bg = Color(0xFF0B1B26)
    val BgDeep = Color(0xFF07131B)
    val Surface = Color(0xFF17394A)
    val SurfaceHi = Color(0xFF24566C)
    val Line = Color(0x33FFFFFF)
    val Text = Color(0xFFF2F8F7)
    val Dim = Color(0xFFA9C6CA)
    val Amber = Color(0xFFFFC15E)
    val AmberDeep = Color(0xFFF0A02E)
    val OnAmber = Color(0xFF2B1A00)
    val Mint = Color(0xFF5FD6A4)
    val Sky = Color(0xFF7FB4FF)
    val Coral = Color(0xFFFF7A6B)
}

/** TV-safe margins (about 5% of a 1280 x 720 canvas) so nothing hides under overscan. */
object Safe {
    val H = 64.dp
    val V = 36.dp
}

/** Padding for the content area to the right of the rail. */
val ContentPad = PaddingValues(start = 40.dp, end = Safe.H, top = Safe.V, bottom = Safe.V)

object Ty {
    val Display = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
        fontSize = 48.sp, lineHeight = 54.sp, color = Pal.Text,
    )
    val Title = TextStyle(
        fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 40.sp, color = Pal.Text,
    )
    val Heading = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, color = Pal.Text)
    val Body = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, color = Pal.Text)
    val Dim = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, color = Pal.Dim)
    val Small = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, color = Pal.Dim)
    val Button = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
}

/**
 * Lays everything out on a fixed 1280 x 720 dp canvas, whatever resolution or density the
 * TV reports. This is what keeps text readable and nothing cut off on every TV.
 */
@Composable
fun TvCanvas(content: @Composable () -> Unit) {
    val cfg = LocalConfiguration.current
    val res = LocalContext.current.resources
    val d = remember(cfg) {
        val m = res.displayMetrics
        minOf(m.widthPixels / 1280f, m.heightPixels / 720f).coerceAtLeast(0.5f)
    }
    CompositionLocalProvider(LocalDensity provides Density(d, 1f)) { content() }
}

@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pal.Bg, Pal.BgDeep)))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Pal.Amber.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.9f, 0f),
                        radius = size.width * 0.55f,
                    )
                )
            },
        content = content,
    )
}
