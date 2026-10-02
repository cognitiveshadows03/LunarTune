/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring

/**
 * Central motion specs for LunarTune.
 *
 * Springs replace the default stiff tweens on interactive motion (presses, slides, chevrons,
 * snap-backs) so movement settles organically instead of stopping dead. Curves intentionally use
 * literal stiffness/damping values rather than the [Spring] presets so the feel stays identical
 * across Compose versions.
 *
 * Deliberately NOT sprung: color morphs (channels overshoot into flashes), alpha fades, lyric
 * motion, ambient/AOD motion, and loading choreography — tweens remain correct there.
 *
 * Every spec below is tuned live by [MotionTuning] (Motion Lab presets + sliders). At stock
 * settings (100% speed, 100% bounce, reduced off) the values are exactly the documented ones.
 */
object LunarMotion {
    /** MD3 emphasized decelerate — elements entering the screen. Linear when reduced motion is on. */
    val EmphasizedDecelerate: Easing
        get() = if (MotionTuning.reducedMotion) LinearEasing else CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** MD3 emphasized accelerate — elements leaving the screen. Linear when reduced motion is on. */
    val EmphasizedAccelerate: Easing
        get() = if (MotionTuning.reducedMotion) LinearEasing else CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // Restored pre-LunarMotion feel: no overshoot, quick settles (performance).
    /**
     * Gentle bounce for entrances, icon rotations, sheet panels and swipe snap-backs.
     * Settles in ~350ms with one soft overshoot.
     */
    fun <T> bouncy() = spring<T>(dampingRatio = MotionTuning.dampingRatio(1f), stiffness = MotionTuning.stiffness(1500f))

    /**
     * Organic settle with zero overshoot, for progress/shape-driven motion (search morph,
     * crossfades with scale) where overshooting past the target would distort layout.
     */
    fun <T> smooth() = spring<T>(dampingRatio = MotionTuning.dampingRatio(1f), stiffness = MotionTuning.stiffness(1500f))

    /**
     * Fast retract for exits (FAB scrolling away, banners dismissing). Barely-there give,
     * gone in ~200ms.
     */
    fun <T> snappy() = spring<T>(dampingRatio = MotionTuning.dampingRatio(1f), stiffness = MotionTuning.stiffness(2500f))

    /**
     * Full-screen glide for nav transitions (tab blooms, drill slides). A whisper of
     * overshoot over ~520ms so page changes read clearly without seasickness.
     */
    fun <T> glide() = spring<T>(dampingRatio = MotionTuning.dampingRatio(1f), stiffness = MotionTuning.stiffness(400f))

    /** Tactile press bounce for [dev.citali.lunartune.ui.component.IconButton]. */
    fun press() = spring<Float>(dampingRatio = MotionTuning.dampingRatio(1f), stiffness = MotionTuning.stiffness(1500f))

    /** Scale target while pressed. */
    const val PressedScale = 0.85f
}
