/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.floatPreferencesKey
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

/** Default dim: scrim over the live blur (0-90% adjustable in settings). */
const val FrostedDimDefault = 0.6f

/** Default blur radius in dp (0-[FrostedBlurMax] adjustable in settings). */
const val FrostedBlurDefault = 10f

/** Strongest blur the slider can reach, in dp. */
const val FrostedBlurMax = 20f

/** Default background-item glow (0-100% adjustable, off by default). */
const val FrostedBgGlowDefault = 0f

/** Extra blur radius in dp added at full background glow. */
const val FrostedBgGlowExtraBlur = 10f

/** White tint alpha over the blurred background at full background glow. */
const val FrostedBgGlowTint = 0.35f

val FrostedDimKey = floatPreferencesKey("frostedDim")
val FrostedBlurKey = floatPreferencesKey("frostedBlur")
val FrostedBgGlowKey = floatPreferencesKey("frostedBgGlow")

/** Opaque-ish scrim used only if blurring is unavailable on the device. */
const val FrostedFallbackAlpha = 0.92f

/**
 * Whether live frosted blur can run on this device at all.
 *
 * Haze blurs a captured frame, and how it does that depends on the platform. Android 12+
 * has [android.graphics.RenderEffect], which blurs on the GPU as part of drawing. Below 12
 * there is no RenderEffect and Haze falls back to RenderScript, which re-captures and
 * re-blurs the region on every frame and allocates a fresh set of full-frame buffers each
 * pass — measured on a Snapdragon 636 at 60 Hz as 49 % dropped frames for the whole of an
 * animation, alongside a 250 ms GC.
 *
 * That measurement is why this was once a hard API 31 cut-off, and it is worth keeping in
 * mind: the frost was asked for on every version the app supports, so the gate is gone, but
 * on the RenderScript path the frame cost above is real and shows up on old mid-range
 * hardware during animation. The blur radius stays small ([FrostedBlurDefault], and the
 * slider caps at [FrostedBlurMax]) and every call site asks Haze for its cheapest profile,
 * which keeps the work proportional. If the frame timer ever shows the RenderScript path
 * costing frames again, restoring the cut-off is a one-line change back to
 * `Build.VERSION_CODES.S`.
 *
 * Devices on 12+ are unaffected and get exactly the blur they got before.
 */
val HazeLiveBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

/**
 * AniDash-style frosted blur for the navigation bar: a small live-blur radius
 * over a 50% surface scrim. The radius stays tiny on purpose — a small kernel
 * over a small pill is what keeps this at full fps on weak GPUs.
 *
 * [HazeLiveBlurSupported] gates the live blur. Below Android 12 Haze draws the blur through
 * its RenderScript path instead of RenderEffect; the scrim, glow and tint are the same.
 *
 * @param bgGlow 0-1 background-item glow: adds extra blur plus a brightening
 * tint over the content behind the bar, so background items glow as it rises.
 */
fun frostedNavBarStyle(
    scrim: Color,
    fallbackScrim: Color,
    blurRadiusDp: Float = FrostedBlurDefault,
    bgGlow: Float = FrostedBgGlowDefault,
): HazeBlurStyle {
    val glow = bgGlow.coerceIn(0f, 1f)
    return HazeBlurStyle {
        blurEnabled(HazeLiveBlurSupported)
        blurRadius(
            (blurRadiusDp.coerceIn(0f, FrostedBlurMax) + glow * FrostedBgGlowExtraBlur).dp,
        )
        backgroundColor(scrim)
        colorEffects(
            if (glow > 0f) {
                listOf(HazeColorEffect.tint(Color.White.copy(alpha = glow * FrostedBgGlowTint)))
            } else {
                emptyList()
            },
        )
        fallbackColorEffect(HazeColorEffect.tint(fallbackScrim))
        noiseFactor(0f)
    }
}

/**
 * Frosted-glow mini player: live blur under the artwork glow. The radius
 * runs a little larger than the nav pill since the mini bar is wider.
 */
fun frostedMiniPlayerStyle(
    scrim: Color,
    fallbackScrim: Color,
): HazeBlurStyle =
    HazeBlurStyle {
        blurEnabled(HazeLiveBlurSupported)
        blurRadius(14.dp)
        backgroundColor(scrim)
        colorEffects(emptyList())
        fallbackColorEffect(HazeColorEffect.tint(fallbackScrim))
        noiseFactor(0f)
    }

/**
 * Frost for the top-bar dropdown menu. Painted over the menu's own opaque
 * container, so when Haze has no source to sample (frost disabled, or the
 * popup window can't be tracked on this device) the blur simply paints
 * nothing and the solid M3 surface remains.
 */
fun frostedTopMenuStyle(
    scrim: Color,
    fallbackScrim: Color,
): HazeBlurStyle =
    HazeBlurStyle {
        blurEnabled(HazeLiveBlurSupported)
        blurRadius(18.dp)
        backgroundColor(scrim)
        colorEffects(emptyList())
        fallbackColorEffect(HazeColorEffect.tint(fallbackScrim))
        noiseFactor(0f)
    }
