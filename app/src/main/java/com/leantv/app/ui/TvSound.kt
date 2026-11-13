package com.leantv.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Ultra-smooth, gentle Apple TV tactile sound engine for TV D-Pad navigation.
 * Generates warm, low-frequency acoustic pops with soft attack-decay cosine windowing.
 * Extremely pleasant, non-fatiguing, and subtle.
 */
object TvSound {
    private var soundPool: SoundPool? = null
    private var moveSoundId: Int = 0
    private var clickSoundId: Int = 0
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build()

            soundPool = pool

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Gentle, warm Apple TV navigation pop (low 320Hz down to 200Hz, soft cosine attack/decay)
                    val tickWav = generateWarmPopWav(
                        sampleRate = 44100,
                        durationMs = 20,
                        startFreq = 340.0,
                        endFreq = 210.0,
                        decayFactor = 12.0
                    )
                    val tickFile = File.createTempFile("tv_soft_tick", ".wav", context.cacheDir)
                    tickFile.deleteOnExit()
                    tickFile.writeBytes(tickWav)
                    moveSoundId = pool.load(tickFile.absolutePath, 1)

                    // Soft, warm confirmation tap (240Hz down to 130Hz)
                    val clickWav = generateWarmPopWav(
                        sampleRate = 44100,
                        durationMs = 26,
                        startFreq = 250.0,
                        endFreq = 140.0,
                        decayFactor = 9.0
                    )
                    val clickFile = File.createTempFile("tv_soft_click", ".wav", context.cacheDir)
                    clickFile.deleteOnExit()
                    clickFile.writeBytes(clickWav)
                    clickSoundId = pool.load(clickFile.absolutePath, 1)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * Play gentle, subtle acoustic feedback when cursor moves between TV grid items.
     * Low volume (0.16f) ensures it feels like a subtle hardware remote feedback.
     */
    fun playMove(view: View? = null) {
        val pool = soundPool ?: return
        if (moveSoundId != 0) {
            pool.play(moveSoundId, 0.16f, 0.16f, 1, 0, 1.0f)
        }
    }

    /**
     * Play soft confirmation tap when selecting / clicking an app or button.
     */
    fun playClick(view: View? = null) {
        val pool = soundPool ?: return
        if (clickSoundId != 0) {
            pool.play(clickSoundId, 0.22f, 0.22f, 1, 0, 1.0f)
        }
    }

    private fun generateWarmPopWav(
        sampleRate: Int,
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        decayFactor: Double
    ): ByteArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val pcm = ShortArray(numSamples)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            // Gentle frequency glide
            val currentFreq = startFreq + (endFreq - startFreq) * t
            phase += 2.0 * PI * currentFreq / sampleRate

            // Cosine ramp-up for the first 12% to completely eliminate any click / pop harshness
            val attack = if (t < 0.12) {
                0.5 * (1.0 - cos(PI * t / 0.12))
            } else 1.0

            // Exponential decay
            val decay = exp(-t * decayFactor)
            val envelope = attack * decay

            // Warm sine wave
            val sample = (sin(phase) * envelope * 0.55 * Short.MAX_VALUE).toInt()
            pcm[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        val byteBuf = ByteArrayOutputStream()
        val data = DataOutputStream(byteBuf)
        val dataSize = numSamples * 2
        val totalChunkSize = 36 + dataSize

        data.writeBytes("RIFF")
        data.writeInt(Integer.reverseBytes(totalChunkSize))
        data.writeBytes("WAVE")
        data.writeBytes("fmt ")
        data.writeInt(Integer.reverseBytes(16))
        data.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        data.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
        data.writeInt(Integer.reverseBytes(sampleRate))
        data.writeInt(Integer.reverseBytes(sampleRate * 2))
        data.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
        data.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())
        data.writeBytes("data")
        data.writeInt(Integer.reverseBytes(dataSize))
        for (s in pcm) {
            data.writeShort(java.lang.Short.reverseBytes(s).toInt())
        }
        data.flush()
        return byteBuf.toByteArray()
    }
}
