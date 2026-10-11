package dev.citali.lunartune.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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
 * These values are fixed: the user-tunable Motion Lab (LunarMotion tuning) was removed; the
 * stock feel below is the only feel, and the mini player's bounce lives in
 * [dev.citali.lunartune.constants.BottomSheetAnimationSpec].
 */
object LunarMotion {
    /** MD3 emphasized decelerate — elements entering the screen. */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** MD3 emphasized accelerate — elements leaving the screen. */
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /**
     * Gentle bounce for entrances, icon rotations, sheet panels and swipe snap-backs.
     * Settles in ~350ms with one soft overshoot.
     */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.75f, stiffness = 500f)

    /**
     * Organic settle with zero overshoot, for progress/shape-driven motion (search morph,
     * crossfades with scale) where overshooting past the target would distort layout.
     */
    fun <T> smooth() = spring<T>(dampingRatio = 1f, stiffness = 700f)

    /**
     * Fast retract for exits (FAB scrolling away, banners dismissing). Barely-there give,
     * gone in ~200ms.
     */
    fun <T> snappy() = spring<T>(dampingRatio = 0.95f, stiffness = 1500f)

    /**
     * Full-screen glide for nav transitions (tab blooms, drill slides). A whisper of
     * overshoot over ~520ms so page changes read clearly without seasickness.
     */
    fun <T> glide() = spring<T>(dampingRatio = 0.88f, stiffness = 350f)

    /** Tactile press bounce for [dev.citali.lunartune.ui.component.IconButton]. */
    fun press() = spring<Float>(dampingRatio = 0.62f, stiffness = 900f)

    /** Scale target while pressed. */
    const val PressedScale = 0.85f
}
