package com.leantv.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class AppVisuals(
    val bitmap: ImageBitmap,
    val isBanner: Boolean,
    val topColor: Color = Color(0xFF1E2832),
    val bottomColor: Color = Color(0xFF10161C),
    /** Vivid colour of the artwork, used for the ambient glow behind the home screen. */
    val glow: Color = Color(0xFF3A4A5A),
)

object IconCache {
    // Bounded by memory (32 MB of pixels), not by count: low-RAM TVs can't afford hundreds of bitmaps.
    private val visualsCache = object : LruCache<String, AppVisuals>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppVisuals): Int =
            value.bitmap.width * value.bitmap.height * 4
    }

    // At most 4 artwork decodes at once so fast scrolling can't starve the UI thread of CPU.
    private val decodeGate = Semaphore(4)

    fun get(packageName: String): AppVisuals? = visualsCache.get(packageName)

    fun getIcon(packageName: String): ImageBitmap? = visualsCache.get(packageName)?.bitmap

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        val visuals = loadVisuals(context, packageName)
        return visuals?.bitmap
    }

    suspend fun loadVisuals(context: Context, packageName: String): AppVisuals? {
        val cached = visualsCache.get(packageName)
        if (cached != null) return cached
        return decodeGate.withPermit { decodeVisuals(context, packageName) }
    }

    private suspend fun decodeVisuals(context: Context, packageName: String): AppVisuals? {
        val cached = visualsCache.get(packageName)
        if (cached != null) return cached

        return withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val leanbackIntent = pm.getLeanbackLaunchIntentForPackage(packageName)
                val resolveInfo = leanbackIntent?.let { pm.resolveActivity(it, 0) }

                // 1. Try Leanback activity banner
                var bannerDrawable: Drawable? = leanbackIntent?.let {
                    try { pm.getActivityBanner(it) } catch (_: Throwable) { null }
                }
                // 2. Try resolved activity loadBanner
                if (bannerDrawable == null && resolveInfo?.activityInfo != null) {
                    bannerDrawable = try { resolveInfo.activityInfo.loadBanner(pm) } catch (_: Throwable) { null }
                }
                // 3. Try standard launch intent banner
                if (bannerDrawable == null) {
                    val standardIntent = pm.getLaunchIntentForPackage(packageName)
                    if (standardIntent != null) {
                        bannerDrawable = try { pm.getActivityBanner(standardIntent) } catch (_: Throwable) { null }
                        if (bannerDrawable == null) {
                            val stdResolve = pm.resolveActivity(standardIntent, 0)
                            bannerDrawable = try { stdResolve?.activityInfo?.loadBanner(pm) } catch (_: Throwable) { null }
                        }
                    }
                }
                // 4. Try application banner
                if (bannerDrawable == null) {
                    bannerDrawable = try { pm.getApplicationBanner(packageName) } catch (_: Throwable) { null }
                }

                val isRealBanner = bannerDrawable != null &&
                    isBannerShaped(bannerDrawable.intrinsicWidth, bannerDrawable.intrinsicHeight)

                if (isRealBanner && bannerDrawable != null) {
                    val bannerBmp = drawableToImageBitmap(bannerDrawable, 384, 216)
                    if (bannerBmp != null) {
                        val visuals = AppVisuals(
                            bitmap = bannerBmp,
                            isBanner = true,
                            glow = vividColor(bannerDrawable),
                        )
                        visualsCache.put(packageName, visuals)
                        return@withContext visuals
                    }
                }

                // Fallback to high-res application icon with tinted plate
                val appDrawable = pm.getApplicationIcon(packageName)
                val (topCol, botCol) = extractPlateColors(appDrawable)
                val iconBmp = drawableToImageBitmap(appDrawable, 192, 192)

                if (iconBmp != null) {
                    val visuals = AppVisuals(
                        bitmap = iconBmp,
                        isBanner = false,
                        topColor = topCol,
                        bottomColor = botCol,
                        glow = vividColor(appDrawable),
                    )
                    visualsCache.put(packageName, visuals)
                    visuals
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Saturation-weighted average colour of the artwork, pushed to a vivid value. Grey if the art is neutral. */
    private fun vividColor(drawable: Drawable): Color = try {
        val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, 24, 24)
        drawable.draw(canvas)
        val hsv = FloatArray(3)
        var r = 0.0; var g = 0.0; var b = 0.0; var total = 0.0
        for (x in 0 until 24) for (y in 0 until 24) {
            val p = bmp.getPixel(x, y)
            if (((p ushr 24) and 0xFF) < 100) continue
            android.graphics.Color.colorToHSV(p, hsv)
            if (hsv[2] < 0.15f) continue
            val w = (hsv[1] * hsv[2]).toDouble().let { it * it } + 0.001
            r += ((p shr 16) and 0xFF) * w
            g += ((p shr 8) and 0xFF) * w
            b += (p and 0xFF) * w
            total += w
        }
        if (total <= 0.0) {
            Color(0xFF6E6E73)
        } else {
            val avg = android.graphics.Color.rgb((r / total).toInt(), (g / total).toInt(), (b / total).toInt())
            android.graphics.Color.colorToHSV(avg, hsv)
            if (hsv[1] < 0.12f) {
                Color(0xFF6E6E73)
            } else {
                hsv[1] = hsv[1].coerceIn(0.55f, 1f)
                hsv[2] = hsv[2].coerceIn(0.75f, 1f)
                Color(android.graphics.Color.HSVToColor(hsv))
            }
        }
    } catch (_: Throwable) {
        Color(0xFF6E6E73)
    }

    private fun isBannerShaped(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val ratio = width.toFloat() / height.toFloat()
        return ratio in 1.35f..2.5f
    }

    private fun drawableToImageBitmap(drawable: Drawable, targetWidth: Int, targetHeight: Int): ImageBitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val orig = drawable.bitmap
                if (orig.width == targetWidth && orig.height == targetHeight) {
                    orig.asImageBitmap()
                } else {
                    val scaled = Bitmap.createScaledBitmap(orig, targetWidth, targetHeight, true)
                    scaled.asImageBitmap()
                }
            } else {
                val bitmap = Bitmap.createBitmap(
                    targetWidth,
                    targetHeight,
                    Bitmap.Config.ARGB_8888,
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, targetWidth, targetHeight)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Extracts an elegant dark tinted plate [topColor, bottomColor] from the icon's dominant pixels.
     */
    private fun extractPlateColors(drawable: Drawable): Pair<Color, Color> {
        return try {
            val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, 32, 32)
            drawable.draw(canvas)

            var rSum = 0L
            var gSum = 0L
            var bSum = 0L
            var count = 0

            for (x in 6 until 26) {
                for (y in 6 until 26) {
                    val p = bmp.getPixel(x, y)
                    val alpha = (p ushr 24) and 0xFF
                    if (alpha > 80) {
                        rSum += (p ushr 16) and 0xFF
                        gSum += (p ushr 8) and 0xFF
                        bSum += p and 0xFF
                        count++
                    }
                }
            }
            bmp.recycle()

            if (count > 0) {
                val r = (rSum / count).toInt()
                val g = (gSum / count).toInt()
                val b = (bSum / count).toInt()

                // Darken so it stays elegant and doesn't overpower the icon
                val topR = (r * 0.45f).toInt().coerceIn(18, 120)
                val topG = (g * 0.45f).toInt().coerceIn(18, 120)
                val topB = (b * 0.45f).toInt().coerceIn(24, 120)

                val botR = (topR * 0.6f).toInt()
                val botG = (topG * 0.6f).toInt()
                val botB = (topB * 0.6f).toInt()

                Pair(
                    Color(topR, topG, topB),
                    Color(botR, botG, botB)
                )
            } else {
                Pair(Color(0xFF1E2832), Color(0xFF10161C))
            }
        } catch (_: Throwable) {
            Pair(Color(0xFF1E2832), Color(0xFF10161C))
        }
    }
}
