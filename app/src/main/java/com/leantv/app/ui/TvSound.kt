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
    private var topOptionSoundId: Int = 0
    private var clickSoundId: Int = 0
    private var openSoundId: Int = 0
    private var closeSoundId: Int = 0
    private var uninstallSoundId: Int = 0
    private var volumeSoundId: Int = 0
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(attrs)
                .build()

            soundPool = pool

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val cache = context.cacheDir

                    // 1. Gentle, warm Apple TV navigation pop (moving around apps)
                    val tickWav = generateToneWav(
                        durationMs = 26,
                        startFreq = 340.0,
                        endFreq = 210.0,
                        attackMs = 3,
                        decayFactor = 10.0,
                        harmonicRatio = 1.5,
                        harmonicMix = 0.12,
                        gain = 0.88
                    )
                    val tickFile = File.createTempFile("tv_tick", ".wav", cache).apply { deleteOnExit(); writeBytes(tickWav) }
                    moveSoundId = pool.load(tickFile.absolutePath, 1)

                    // 2. Light, crisp, crystalline glass tick (navigating top bar options)
                    val topWav = generateToneWav(
                        durationMs = 24,
                        startFreq = 540.0,
                        endFreq = 380.0,
                        attackMs = 2,
                        decayFactor = 11.0,
                        harmonicRatio = 2.0,
                        harmonicMix = 0.22,
                        gain = 0.88
                    )
                    val topFile = File.createTempFile("tv_top", ".wav", cache).apply { deleteOnExit(); writeBytes(topWav) }
                    topOptionSoundId = pool.load(topFile.absolutePath, 1)

                    // 3. Soft, warm confirmation tap (selecting a button or setting)
                    val clickWav = generateToneWav(
                        durationMs = 32,
                        startFreq = 300.0,
                        endFreq = 160.0,
                        attackMs = 4,
                        decayFactor = 7.5,
                        harmonicRatio = 2.0,
                        harmonicMix = 0.18,
                        gain = 0.90
                    )
                    val clickFile = File.createTempFile("tv_click", ".wav", cache).apply { deleteOnExit(); writeBytes(clickWav) }
                    clickSoundId = pool.load(clickFile.absolutePath, 1)

                    // 4. Pleasant, cheerful ascending chime for opening an app
                    val openWav = generateToneWav(
                        durationMs = 150,
                        startFreq = 300.0,
                        endFreq = 650.0,
                        attackMs = 14,
                        decayFactor = 4.8,
                        harmonicRatio = 2.0,
                        harmonicMix = 0.28,
                        gain = 0.92
                    )
                    val openFile = File.createTempFile("tv_open", ".wav", cache).apply { deleteOnExit(); writeBytes(openWav) }
                    openSoundId = pool.load(openFile.absolutePath, 1)

                    // 5. Soothing, gentle descending settle droplet tone for closing an app / returning home
                    val closeWav = generateToneWav(
                        durationMs = 150,
                        startFreq = 560.0,
                        endFreq = 260.0,
                        attackMs = 12,
                        decayFactor = 5.2,
                        harmonicRatio = 0.5,
                        harmonicMix = 0.22,
                        gain = 0.90
                    )
                    val closeFile = File.createTempFile("tv_close", ".wav", cache).apply { deleteOnExit(); writeBytes(closeWav) }
                    closeSoundId = pool.load(closeFile.absolutePath, 1)

                    // 6. Distinct dissolution swoosh-pop for uninstalling an app
                    val uninstallWav = generateToneWav(
                        durationMs = 210,
                        startFreq = 450.0,
                        endFreq = 110.0,
                        attackMs = 8,
                        decayFactor = 4.2,
                        harmonicRatio = 0.75,
                        harmonicMix = 0.32,
                        gain = 0.95
                    )
                    val uninstallFile = File.createTempFile("tv_uninstall", ".wav", cache).apply { deleteOnExit(); writeBytes(uninstallWav) }
                    uninstallSoundId = pool.load(uninstallFile.absolutePath, 1)

                    // 7. Subtle, satisfying micro-click for volume + / - remote controls
                    val volWav = generateToneWav(
                        durationMs = 20,
                        startFreq = 620.0,
                        endFreq = 440.0,
                        attackMs = 2,
                        decayFactor = 12.0,
                        harmonicRatio = 1.5,
                        harmonicMix = 0.10,
                        gain = 0.85
                    )
                    val volFile = File.createTempFile("tv_vol", ".wav", cache).apply { deleteOnExit(); writeBytes(volWav) }
                    volumeSoundId = pool.load(volFile.absolutePath, 1)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * Play gentle acoustic feedback when cursor moves between TV grid items or apps.
     */
    fun playMove(view: View? = null) {
        val pool = soundPool
        if (pool != null && moveSoundId != 0) {
            pool.play(moveSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
        } else {
            view?.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_RIGHT)
        }
    }

    /**
     * Play lighter, crystalline glass tick when navigating top bar options.
     */
    fun playTopOptionMove(view: View? = null) {
        val pool = soundPool
        if (pool != null && topOptionSoundId != 0) {
            pool.play(topOptionSoundId, 0.88f, 0.88f, 1, 0, 1.0f)
        } else {
            view?.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_RIGHT)
        }
    }

    /**
     * Play soft confirmation tap when selecting / clicking a button or setting.
     */
    fun playClick(view: View? = null) {
        val pool = soundPool
        if (pool != null && clickSoundId != 0) {
            pool.play(clickSoundId, 0.90f, 0.90f, 1, 0, 1.0f)
        } else {
            view?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        }
    }

    /**
     * Play pleasant, inspiring ascending chime when an app is launched.
     */
    fun playAppOpen(view: View? = null) {
        val pool = soundPool
        if (pool != null && openSoundId != 0) {
            pool.play(openSoundId, 0.95f, 0.95f, 2, 0, 1.0f)
        } else {
            view?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        }
    }

    /**
     * Play soothing, gentle descending droplet tone when returning from an app back to Home.
     */
    fun playAppClose(view: View? = null) {
        val pool = soundPool
        if (pool != null && closeSoundId != 0) {
            pool.play(closeSoundId, 0.90f, 0.90f, 2, 0, 1.0f)
        }
    }

    /**
     * Play distinct dissolution swoosh-pop when an app is uninstalled.
     */
    fun playUninstall(view: View? = null) {
        val pool = soundPool
        if (pool != null && uninstallSoundId != 0) {
            pool.play(uninstallSoundId, 0.95f, 0.95f, 2, 0, 1.0f)
        }
    }

    /**
     * Play subtle tactile micro-click on volume remote adjustment.
     */
    fun playVolumeTick(view: View? = null) {
        val pool = soundPool
        if (pool != null && volumeSoundId != 0) {
            pool.play(volumeSoundId, 0.85f, 0.85f, 1, 0, 1.0f)
        } else {
            view?.playSoundEffect(android.view.SoundEffectConstants.CLICK)
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
