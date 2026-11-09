/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 */
package dev.citali.lunartune.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** App-owned feedback: deliberately independent of system touch-feedback preferences. */
@Suppress("DEPRECATION")
fun performAppHaptic(context: Context, strength: Int, texture: Boolean = false) {
    val level = strength.coerceIn(0, 100)
    if (level == 0) return
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    val duration = if (texture) 5L + level / 20 else 8L + level / 10
    runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            val amplitude = if (vibrator.hasAmplitudeControl()) {
                ((if (texture) 1.4f else 2.55f) * level).toInt().coerceIn(1, 255)
            } else VibrationEffect.DEFAULT_AMPLITUDE
            vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else vibrator.vibrate(duration)
    }
}
