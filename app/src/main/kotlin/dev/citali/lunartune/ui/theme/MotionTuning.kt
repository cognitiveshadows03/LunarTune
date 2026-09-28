/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Animation tempo as a percentage of the stock feel. 50 = half speed, 200 = double. */
val MotionSpeedKey = intPreferencesKey("motionSpeed")

/** Spring playfulness as a percentage of the stock feel. 0 = no overshoot anywhere. */
val MotionBounceKey = intPreferencesKey("motionBounce")

/** Reduced motion: near-instant linear settles, no overshoot, for accessibility. */
val MotionReducedKey = booleanPreferencesKey("motionReduced")

enum class MotionPreset {
    SNAPPY,
    BALANCED,
    BUTTERY,
    REDUCED,
    CUSTOM,
}

/**
 * User-tunable motion config backing [LunarMotion].
 *
 * DataStore stays the source of truth; [startSync] mirrors the three keys into volatile fields
 * at app start and on every change. [LunarMotion]'s spring factories read the snapshot each time
 * an animation spec is created, so tuning applies to subsequently started animations with zero
 * changes at call sites (plain reads never trigger recomposition).
 *
 * Presets are derived, not stored: a (speed, bounce) pair either matches a preset definition
 * exactly or reads as [MotionPreset.CUSTOM].
 */
object MotionTuning {
    const val SPEED_MIN = 50
    const val SPEED_MAX = 200
    const val SPEED_DEFAULT = 100

    const val BOUNCE_MIN = 0
    const val BOUNCE_MAX = 150
    const val BOUNCE_DEFAULT = 100

    private const val REDUCED_STIFFNESS_BOOST = 16f
    private const val REDUCED_MIN_STIFFNESS = 8000f
    private const val MIN_STIFFNESS = 50f
    private const val MAX_STIFFNESS = 20000f
    private const val MIN_DAMPING = 0.15f

    @Volatile
    var speedPercent: Int = SPEED_DEFAULT
        private set

    @Volatile
    var bouncePercent: Int = BOUNCE_DEFAULT
        private set

    @Volatile
    var reducedMotion: Boolean = false
        private set

    fun startSync(
        scope: CoroutineScope,
        dataStore: DataStore<Preferences>,
    ) {
        scope.launch(Dispatchers.IO) {
            dataStore.data
                .map { prefs ->
                    Triple(
                        prefs[MotionSpeedKey] ?: SPEED_DEFAULT,
                        prefs[MotionBounceKey] ?: BOUNCE_DEFAULT,
                        prefs[MotionReducedKey] ?: false,
                    )
                }.distinctUntilChanged()
                .collect { (speed, bounce, reduced) ->
                    speedPercent = speed.coerceIn(SPEED_MIN, SPEED_MAX)
                    bouncePercent = bounce.coerceIn(BOUNCE_MIN, BOUNCE_MAX)
                    reducedMotion = reduced
                }
        }
    }

    /** (speed, bounce) written when a preset is picked. REDUCED keeps the tune, CUSTOM writes nothing. */
    fun presetDefinition(preset: MotionPreset): Pair<Int, Int>? =
        when (preset) {
            MotionPreset.SNAPPY -> 140 to 35
            MotionPreset.BALANCED -> SPEED_DEFAULT to BOUNCE_DEFAULT
            MotionPreset.BUTTERY -> 80 to 125
            MotionPreset.REDUCED,
            MotionPreset.CUSTOM,
            -> null
        }

    fun derivePreset(
        speed: Int,
        bounce: Int,
        reduced: Boolean,
    ): MotionPreset {
        if (reduced) return MotionPreset.REDUCED
        return MotionPreset.entries.firstOrNull { presetDefinition(it) == (speed to bounce) }
            ?: MotionPreset.CUSTOM
    }

    suspend fun applyPreset(
        dataStore: DataStore<Preferences>,
        preset: MotionPreset,
    ) {
        if (preset == MotionPreset.CUSTOM) return
        dataStore.edit { prefs ->
            if (preset == MotionPreset.REDUCED) {
                prefs[MotionReducedKey] = true
            } else {
                val (speed, bounce) = presetDefinition(preset) ?: return@edit
                prefs[MotionSpeedKey] = speed
                prefs[MotionBounceKey] = bounce
                prefs[MotionReducedKey] = false
            }
        }
    }

    /**
     * Tempo mapping: stiffness scales with the square of the speed factor so 50% feels half as
     * fast and 200% twice as fast. Reduced motion pins everything near-instant.
     */
    fun stiffness(base: Float): Float {
        if (reducedMotion) {
            return (base * REDUCED_STIFFNESS_BOOST).coerceAtLeast(REDUCED_MIN_STIFFNESS)
        }
        val scale = (speedPercent / 100f).coerceIn(0.5f, 2f)
        return (base * scale * scale).coerceIn(MIN_STIFFNESS, MAX_STIFFNESS)
    }

    /**
     * Playfulness mapping: scales each spec's stock bounciness (1 - dampingRatio), so 0%
     * critically damps every spring and 100% keeps the designed feel. Overdamped bases are
     * left untouched.
     */
    fun dampingRatio(base: Float): Float {
        if (reducedMotion) return 1f
        if (base >= 1f) return base
        val bounce = (bouncePercent / 100f).coerceIn(0f, 1.5f)
        return (1f - (1f - base) * bounce).coerceIn(MIN_DAMPING, 1f)
    }
}
