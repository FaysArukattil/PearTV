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
 * Generates warm, organic acoustic tones with soft attack-decay cosine windowing.
 * Extremely pleasant, non-fatiguing, and fun.
 */
object TvSound {
    private var soundPool: SoundPool? = null
    private var moveSoundId: Int = 0
    private var clickSoundId: Int = 0
    private var openSoundId: Int = 0
    private var closeSoundId: Int = 0
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
                .setMaxStreams(6)
                .setAudioAttributes(attrs)
                .build()

            soundPool = pool

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val cache = context.cacheDir

                    // 1. Gentle, warm Apple TV navigation pop (moving around apps)
                    val tickWav = generateToneWav(
                        durationMs = 22,
                        startFreq = 320.0,
                        endFreq = 200.0,
                        attackMs = 4,
                        decayFactor = 11.0,
                        harmonicRatio = 1.5,
                        harmonicMix = 0.12,
                        gain = 0.45
                    )
                    val tickFile = File.createTempFile("tv_tick", ".wav", cache).apply { deleteOnExit(); writeBytes(tickWav) }
                    moveSoundId = pool.load(tickFile.absolutePath, 1)

                    // 2. Soft, warm confirmation tap (selecting a button or setting)
                    val clickWav = generateToneWav(
                        durationMs = 28,
                        startFreq = 260.0,
                        endFreq = 150.0,
                        attackMs = 5,
                        decayFactor = 8.5,
                        harmonicRatio = 2.0,
                        harmonicMix = 0.15,
                        gain = 0.48
                    )
                    val clickFile = File.createTempFile("tv_click", ".wav", cache).apply { deleteOnExit(); writeBytes(clickWav) }
                    clickSoundId = pool.load(clickFile.absolutePath, 1)

                    // 3. Pleasant, cheerful ascending chime for opening an app
                    val openWav = generateToneWav(
                        durationMs = 130,
                        startFreq = 280.0,
                        endFreq = 580.0,
                        attackMs = 18,
                        decayFactor = 5.5,
                        harmonicRatio = 2.0,
                        harmonicMix = 0.25,
                        gain = 0.52
                    )
                    val openFile = File.createTempFile("tv_open", ".wav", cache).apply { deleteOnExit(); writeBytes(openWav) }
                    openSoundId = pool.load(openFile.absolutePath, 1)

                    // 4. Soothing, gentle descending settle droplet tone for closing an app / returning home
                    val closeWav = generateToneWav(
                        durationMs = 140,
                        startFreq = 540.0,
                        endFreq = 270.0,
                        attackMs = 16,
                        decayFactor = 6.0,
                        harmonicRatio = 0.5,
                        harmonicMix = 0.20,
                        gain = 0.50
                    )
                    val closeFile = File.createTempFile("tv_close", ".wav", cache).apply { deleteOnExit(); writeBytes(closeWav) }
                    closeSoundId = pool.load(closeFile.absolutePath, 1)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * Play gentle acoustic feedback when cursor moves between TV grid items or apps.
     */
    fun playMove(view: View? = null) {
        val pool = soundPool ?: return
        if (moveSoundId != 0) {
            pool.play(moveSoundId, 0.22f, 0.22f, 1, 0, 1.0f)
        }
    }

    /**
     * Play soft confirmation tap when selecting / clicking a button or setting.
     */
    fun playClick(view: View? = null) {
        val pool = soundPool ?: return
        if (clickSoundId != 0) {
            pool.play(clickSoundId, 0.25f, 0.25f, 1, 0, 1.0f)
        }
    }

    /**
     * Play pleasant, inspiring ascending chime when an app is launched.
     */
    fun playAppOpen(view: View? = null) {
        val pool = soundPool ?: return
        if (openSoundId != 0) {
            pool.play(openSoundId, 0.35f, 0.35f, 2, 0, 1.0f)
        }
    }

    /**
     * Play soothing, gentle descending droplet tone when returning from an app back to Home.
     */
    fun playAppClose(view: View? = null) {
        val pool = soundPool ?: return
        if (closeSoundId != 0) {
            pool.play(closeSoundId, 0.32f, 0.32f, 2, 0, 1.0f)
        }
    }

    private fun generateToneWav(
        sampleRate: Int = 44100,
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        attackMs: Int = 15,
        decayFactor: Double = 6.0,
        harmonicRatio: Double = 2.0,
        harmonicMix: Double = 0.20,
        gain: Double = 0.50
    ): ByteArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(10)
        val attackSamples = (sampleRate * (attackMs / 1000.0)).toInt().coerceAtLeast(1)
        val pcm = ShortArray(numSamples)
        var phase1 = 0.0
        var phase2 = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val curFreq = startFreq + (endFreq - startFreq) * t
            phase1 += 2.0 * PI * curFreq / sampleRate
            phase2 += 2.0 * PI * (curFreq * harmonicRatio) / sampleRate

            val attack = if (i < attackSamples) {
                0.5 * (1.0 - cos(PI * i / attackSamples))
            } else 1.0

            val decay = exp(-t * decayFactor)
            val envelope = attack * decay

            val signal = (sin(phase1) + sin(phase2) * harmonicMix) / (1.0 + harmonicMix)
            val sample = (signal * envelope * gain * Short.MAX_VALUE).toInt()
            pcm[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        return encodePcmToWav(pcm, sampleRate)
    }

    private fun encodePcmToWav(pcm: ShortArray, sampleRate: Int): ByteArray {
        val byteBuf = ByteArrayOutputStream()
        val data = DataOutputStream(byteBuf)
        val dataSize = pcm.size * 2
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
