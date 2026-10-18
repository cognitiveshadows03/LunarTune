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
 * Momentum rule: a drag that is released must never animate from rest. Pass the release
 * velocity as `initialVelocity` to [settle] (snap-back) or [flingOut] (dismissal) so the
 * element continues at the speed the finger left at. [clampVelocity] trims flicks that are
 * too fast for a spring to settle cleanly.
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

    /**
     * Snap-back after a drag is released. Intended to be paired with the finger's release
     * velocity as `initialVelocity`, so the element leaves at the speed it was travelling
     * rather than restarting from rest. Damping is a touch above [bouncy] because a released
     * fling already carries energy; adding overshoot on top reads as sloppy, not lively.
     */
    fun <T> settle() = spring<T>(dampingRatio = 0.82f, stiffness = 520f)

    /**
     * Dismissal that continues the flick instead of replaying a fixed exit curve. Critically
     * damped: the target is off-screen, so any overshoot is invisible work, and bouncing
     * back off the screen edge reads as a bug.
     */
    fun <T> flingOut() = spring<T>(dampingRatio = 1f, stiffness = 700f)

    /** Scale target while pressed. */
    const val PressedScale = 0.85f

    /**
     * Largest stretch, as a fraction of size, applied along the drag axis while a surface is
     * being pulled. Deliberately tiny — the deformation should be felt during the gesture and
     * be gone before the eye can name it.
     */
    const val MaxStretch = 0.04f

    /**
     * Ceiling on momentum carried out of a fling, in px/s. A violent swipe can report
     * 15 000 px/s; fed straight into a spring that overshoots far enough to look like a
     * glitch. 6 000 px/s still reads as "thrown" while staying settleable.
     */
    const val MaxCarriedVelocity = 6_000f

    /** Clamp a raw release velocity into a range a spring can settle from cleanly. */
    fun clampVelocity(velocity: Float): Float =
        velocity.coerceIn(-MaxCarriedVelocity, MaxCarriedVelocity)
}
