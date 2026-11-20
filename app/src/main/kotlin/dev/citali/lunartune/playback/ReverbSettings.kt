package dev.citali.lunartune.playback

import android.media.audiofx.EnvironmentalReverb
import androidx.datastore.preferences.core.Preferences
import dev.citali.lunartune.constants.ReverbDampingKey
import dev.citali.lunartune.constants.ReverbDryKey
import dev.citali.lunartune.constants.ReverbEnabledKey
import dev.citali.lunartune.constants.ReverbLowCutKey
import dev.citali.lunartune.constants.ReverbMixKey
import dev.citali.lunartune.constants.ReverbPreDelayKey
import dev.citali.lunartune.constants.ReverbPresetKey
import dev.citali.lunartune.constants.ReverbRoomSizeKey
import dev.citali.lunartune.constants.ReverbWetKey
import dev.citali.lunartune.constants.ReverbWidthKey

/** Reverb presets. The values are starting points for the sliders and can be changed afterwards. */
enum class ReverbPreset(
    val storageValue: String,
    val mix: Int,
    val roomSize: Int,
    val damping: Int,
    val preDelayMs: Int,
    val wet: Int,
    val dry: Int,
    val width: Int,
    val lowCutHz: Int,
) {
    ROOM(
        storageValue = "room",
        mix = 50,
        roomSize = 55,
        damping = 45,
        preDelayMs = 12,
        wet = 60,
        dry = 100,
        width = 100,
        lowCutHz = 180,
    ),
    STUDIO(
        storageValue = "studio",
        mix = 40,
        roomSize = 30,
        damping = 65,
        preDelayMs = 5,
        wet = 45,
        dry = 100,
        width = 80,
        lowCutHz = 250,
    ),
    // Plate and Hall use Room's wet, dry, width and low cut until their own values are set.
    PLATE(
        storageValue = "plate",
        mix = 45,
        roomSize = 70,
        damping = 25,
        preDelayMs = 0,
        wet = 60,
        dry = 100,
        width = 100,
        lowCutHz = 180,
    ),
    HALL(
        storageValue = "hall",
        mix = 55,
        roomSize = 85,
        damping = 40,
        preDelayMs = 30,
        wet = 60,
        dry = 100,
        width = 100,
        lowCutHz = 180,
    ),
    ;

    companion object {
        fun fromStorage(value: String?): ReverbPreset = entries.firstOrNull { it.storageValue == value } ?: ROOM
    }
}

/**
 * Reverb state. Percentages are 0–100, pre-delay is in milliseconds and low cut is in Hz.
 * Reverb is off by default.
 */
data class ReverbSettings(
    val enabled: Boolean,
    val preset: ReverbPreset,
    val mix: Int,
    val roomSize: Int,
    val damping: Int,
    val preDelayMs: Int,
    val wet: Int,
    val dry: Int,
    val width: Int,
    val lowCutHz: Int,
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
        wet = (prefs[ReverbWetKey] ?: preset.wet).coerceIn(0, 100),
        dry = (prefs[ReverbDryKey] ?: preset.dry).coerceIn(0, 100),
        width = (prefs[ReverbWidthKey] ?: preset.width).coerceIn(0, 100),
        lowCutHz = (prefs[ReverbLowCutKey] ?: preset.lowCutHz).coerceIn(LOW_CUT_MIN_HZ, LOW_CUT_MAX_HZ),
    )
}

const val LOW_CUT_MIN_HZ = 20
const val LOW_CUT_MAX_HZ = 500

/**
 * Maps the slider values onto EnvironmentalReverb units and applies them.
 * Dry signal always passes at full level, so Mix only controls the room reverb level.
 * Wet controls the late reverb level.
 *
 * Dry, Width and Low cut are stored but have no matching EnvironmentalReverb parameter,
 * so they do not change the sound yet.
 */
fun applyReverbSettings(
    reverb: EnvironmentalReverb,
    settings: ReverbSettings,
) {
    val roomLevelMb = lerpInt(from = -4000, to = 0, percent = settings.mix)
    val lateLevelMb = lerpInt(from = -9000, to = 0, percent = settings.wet)
    val decayTimeMs = lerpInt(from = 300, to = 8000, percent = settings.roomSize)
    // High damping gives a darker tail, so the high-frequency decay ratio goes down.
    val decayHfRatio = (2000 - settings.damping * 18).coerceIn(200, 2000)
    val preDelay = settings.preDelayMs.coerceIn(0, 100)

    runCatching { reverb.setRoomLevel(roomLevelMb.toShort()) }
    runCatching { reverb.setReverbLevel(lateLevelMb.toShort()) }
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
