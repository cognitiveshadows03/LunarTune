/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.utils

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.navigation.NavBackStackEntry

/**
 * Centralized animation scale controller — mirrors Mochy Music's AppMotion.
 *
 * Every animation in the app reads [durationScale] each frame via the
 * window recomposer. This means:
 * - When [enabled] is false, every animation jumps to its end instantly.
 * - When the system animator duration scale changes (Developer Options),
 *   [systemScale] follows it so users who turn animations down to 0.5×
 *   get 0.5× animations in LunarTune too.
 * - [speed] can multiply the system scale for a custom animation speed.
 */
object AppMotion {
    var enabled by mutableStateOf(true)

    var speed by mutableFloatStateOf(1f)

    private var systemScale by mutableFloatStateOf(1f)

    val durationScale: MotionDurationScale = object : MotionDurationScale {
        override val scaleFactor: Float
            get() = if (!enabled) 0f else systemScale / speed.coerceAtLeast(0.1f)
    }

    fun followSystemScale(context: Context): ContentObserver {
        val resolver = context.contentResolver
        fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        systemScale = read()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemScale = read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        return observer
    }
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.motionEnter(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?,
): EnterTransition? = if (AppMotion.enabled) transition() else EnterTransition.None

fun AnimatedContentTransitionScope<NavBackStackEntry>.motionExit(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?,
): ExitTransition? = if (AppMotion.enabled) transition() else ExitTransition.None

fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultMotionEnter(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition,
): EnterTransition = if (AppMotion.enabled) transition() else EnterTransition.None

fun AnimatedContentTransitionScope<NavBackStackEntry>.defaultMotionExit(
    transition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition,
): ExitTransition = if (AppMotion.enabled) transition() else ExitTransition.None
