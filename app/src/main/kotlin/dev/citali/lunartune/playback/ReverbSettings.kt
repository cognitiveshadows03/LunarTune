/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import android.media.audiofx.EnvironmentalReverb
import androidx.datastore.preferences.core.Preferences
import dev.citali.lunartune.constants.ReverbDampingKey
import dev.citali.lunartune.constants.ReverbEnabledKey
import dev.citali.lunartune.constants.ReverbMixKey
import dev.citali.lunartune.constants.ReverbPreDelayKey
import dev.citali.lunartune.constants.ReverbPresetKey
import dev.citali.lunartune.constants.ReverbRoomSizeKey

/** Reverb presets. The values are starting points for the sliders and can be changed afterwards. */
enum class ReverbPreset(
    val storageValue: String,
    val mix: Int,
    val roomSize: Int,
    val damping: Int,
    val preDelayMs: Int,
) {
    ROOM("room", mix = 50, roomSize = 55, damping = 45, preDelayMs = 12),
    STUDIO("studio", mix = 40, roomSize = 30, damping = 65, preDelayMs = 5),
    PLATE("plate", mix = 45, roomSize = 70, damping = 25, preDelayMs = 0),
    HALL("hall", mix = 55, roomSize = 85, damping = 40, preDelayMs = 30),
    ;

    companion object {
        fun fromStorage(value: String?): ReverbPreset = entries.firstOrNull { it.storageValue == value } ?: ROOM
    }
}

/**
 * Reverb state. Mix, room size and damping are 0–100 percentages; pre-delay is in milliseconds.
 * Reverb is off by default.
 */
data class ReverbSettings(
    val enabled: Boolean,
    val preset: ReverbPreset,
    val mix: Int,
    val roomSize: Int,
    val damping: Int,
    val preDelayMs: Int,
)

fun readReverbSettingsFromPrefs(prefs: Preferences): ReverbSettings {
    val preset = ReverbPreset.fromStorage(prefs[ReverbPresetKey])
    return ReverbSettings(
        enabled = prefs[ReverbEnabledKey] ?: false,
        preset = preset,
        mix = (prefs[ReverbMixKey] ?: preset.mix).coerceIn(0, 100),
        roomSize = (prefs[ReverbRoomSizeKey] ?: preset.roomSize).coerceIn(0, 100),
        damping = (prefs[ReverbDampingKey] ?: preset.damping).coerceIn(0, 100),
        preDelayMs = (prefs[ReverbPreDelayKey] ?: preset.preDelayMs).coerceIn(0, 100),
    )
}

/**
 * Maps the 0–100 slider values onto EnvironmentalReverb units and applies them.
 * Dry signal always passes at full level, so Mix only controls the reverb level.
 */
fun applyReverbSettings(
    reverb: EnvironmentalReverb,
    settings: ReverbSettings,
) {
    val roomLevelMb = lerpInt(from = -4000, to = 0, percent = settings.mix)
    val decayTimeMs = lerpInt(from = 300, to = 8000, percent = settings.roomSize)
    // High damping gives a darker tail, so the high-frequency decay ratio goes down.
    val decayHfRatio = (2000 - settings.damping * 18).coerceIn(200, 2000)
    val preDelay = settings.preDelayMs.coerceIn(0, 100)

    runCatching { reverb.setRoomLevel(roomLevelMb.toShort()) }
    runCatching { reverb.setDecayTime(decayTimeMs) }
    runCatching { reverb.setDecayHFRatio(decayHfRatio.toShort()) }
    runCatching { reverb.setReverbDelay(preDelay) }
    runCatching { reverb.enabled = settings.enabled }
}

private fun lerpInt(
    from: Int,
    to: Int,
    percent: Int,
): Int = from + (to - from) * percent.coerceIn(0, 100) / 100
