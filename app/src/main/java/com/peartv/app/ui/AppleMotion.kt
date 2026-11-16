package com.peartv.app.ui

import androidx.compose.animation.core.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple TV / tvOS–grade motion constants.
 * All timings and physics match the values Apple uses in its own tvOS Focus Engine
 * and SwiftUI transition system (reverse-engineered from Apple's HIG and open-source references).
 */
object AppleMotion {

    // ── Spring presets ────────────────────────────────────────────────────────
    /** Snappy interactive spring – used for tile focus scale, pill focus. */
    val SpringSnappy = spring<Float>(dampingRatio = 0.72f, stiffness = 450f)
    /** Standard UI spring – used for modal entrance, card expand. */
    val SpringStandard = spring<Float>(dampingRatio = 0.78f, stiffness = 300f)
    /** Gentle/floaty spring – used for hero text, background parallax. */
    val SpringGentle = spring<Float>(dampingRatio = 0.82f, stiffness = 200f)
    /** Critically damped – smooth with zero bounce, for opacity/color transitions. */
    val SpringCritical = spring<Float>(dampingRatio = 1f, stiffness = 380f)

    // ── Easing ────────────────────────────────────────────────────────────────
    /** Apple "standard easing" – swift accel, long smooth deceleration. */
    val EaseOut = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    /** Apple app launch zoom easing – swift initial expansion with smooth continuous deceleration into full screen. */
    val LaunchZoomEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
    /** Apple tvOS app close / minimize easing – starts with smooth weight, decelerates slowly and gracefully into the tile. */
    val AppleCloseEasing = CubicBezierEasing(0.20f, 1.0f, 0.32f, 1.0f)
    /** Apple "ease in" – slow start, fast exit (for elements leaving screen). */
    val EaseIn  = CubicBezierEasing(0.0f, 0.0f, 0.6f, 1.0f)
    /** Apple decelerate – used for things entering the screen. */
    val Decelerate = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

    // ── Duration presets (ms) ─────────────────────────────────────────────────
    const val DurMicro    = 120  // button press feedback
    const val DurShort    = 200  // small context changes
    const val DurStandard = 300  // standard transitions
    const val DurEnter    = 380  // screen enter
    const val DurExit     = 220  // screen exit (faster feels snappier)
    const val DurAppClose = 560  // tvOS app return / minimize transition (smooth and cinematic)

    // ── Reusable AnimationSpecs ───────────────────────────────────────────────
    fun <T> enterTween() = tween<T>(DurEnter, easing = Decelerate)
    fun <T> exitTween()  = tween<T>(DurExit,  easing = EaseIn)
    fun <T> stdTween()   = tween<T>(DurStandard, easing = EaseOut)
    fun <T> microTween() = tween<T>(DurMicro, easing = EaseOut)
}
