package com.peartv.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.peartv.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Outfit (SIL OFL), the same variable font the reference launcher uses. */
@OptIn(ExperimentalTextApi::class)
val Outfit = FontFamily(
    Font(R.font.outfit, FontWeight.ExtraLight, variationSettings = FontVariation.Settings(FontVariation.weight(200))),
    Font(R.font.outfit, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.outfit, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.outfit, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.outfit, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.outfit, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

object Pal {
    val Bg = Color(0xFF000000)
    val BgDeep = Color(0xFF0A0A0D)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHi = Color(0xFF3A3A3C)
    val Line = Color(0x1FFFFFFF)
    val Text = Color(0xFFFFFFFF)
    val Dim = Color(0xFF9A9AA2)
    // "Amber" names kept so every screen picks up the new accent (Apple blue) automatically.
    val Amber = Color(0xFF0A84FF)
    val AmberDeep = Color(0xFF0A84FF)
    val OnAmber = Color(0xFFFFFFFF)
    val Mint = Color(0xFF30D158)
    val Sky = Color(0xFF64D2FF)
    val Coral = Color(0xFFFF453A)
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
        fontFamily = Outfit, fontWeight = FontWeight.Light, letterSpacing = (-1).sp,
        fontSize = 52.sp, lineHeight = 58.sp, color = Pal.Text,
    )
    val Title = TextStyle(
        fontFamily = Outfit, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp,
        fontSize = 34.sp, lineHeight = 40.sp, color = Pal.Text,
    )
    val Heading = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, color = Pal.Text)
    val Body = TextStyle(fontFamily = Outfit, fontSize = 20.sp, lineHeight = 28.sp, color = Pal.Text)
    val Dim = TextStyle(fontFamily = Outfit, fontSize = 20.sp, lineHeight = 28.sp, color = Pal.Dim)
    val Small = TextStyle(fontFamily = Outfit, fontSize = 17.sp, lineHeight = 23.sp, color = Pal.Dim)
    val Button = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 20.sp)
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
    CompositionLocalProvider(
        LocalDensity provides Density(d, 1f),
        LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = Outfit)),
    ) { content() }
}

@Composable
fun Backdrop(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Pal.Bg, Pal.BgDeep)))
            .drawWithCache {
                val radialBrush = Brush.radialGradient(
                    colors = listOf(Pal.Amber.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.9f, 0f),
                    radius = size.width * 0.55f,
                )
                onDrawBehind {
                    drawRect(radialBrush)
                }
            },
        content = content,
    )
}
