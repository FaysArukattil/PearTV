package com.leantv.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object IconCache {
    // Cache up to 150 icons in memory
    private val cache = LruCache<String, ImageBitmap>(150)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun put(packageName: String, bitmap: ImageBitmap) {
        cache.put(packageName, bitmap)
    }

    suspend fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        val cached = cache.get(packageName)
        if (cached != null) return cached

        return withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val drawable = pm.getApplicationIcon(packageName)
                val imageBitmap = drawableToImageBitmap(drawable)
                if (imageBitmap != null) {
                    cache.put(packageName, imageBitmap)
                }
                imageBitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable, targetSize: Int = 96): ImageBitmap? {
        return try {
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                val orig = drawable.bitmap
                if (orig.width <= targetSize && orig.height <= targetSize) {
                    orig.asImageBitmap()
                } else {
                    val scaled = Bitmap.createScaledBitmap(orig, targetSize, targetSize, true)
                    scaled.asImageBitmap()
                }
            } else {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else targetSize
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else targetSize
                val bitmap = Bitmap.createBitmap(
                    minOf(width, targetSize),
                    minOf(height, targetSize),
                    Bitmap.Config.ARGB_8888,
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            }
        } catch (_: Throwable) {
            null
        }
    }
}

