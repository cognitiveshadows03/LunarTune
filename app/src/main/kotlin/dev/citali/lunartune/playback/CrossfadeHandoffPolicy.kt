/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import dev.citali.lunartune.constants.CrossfadeCurve
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Overlap loudness coupling constant (κ = 0.41) modeling perceptual loudness summation of two
 * simultaneous full-mix music tracks. At the midpoint of [CrossfadeCurve.EQUAL_POWER], this
 * yields the broadcast -4.5 dB constant-loudness crossfade law (`1 / sqrt(2 * 1.41) ≈ 0.5955`)
 * while preserving exact `(1.0, 0.0)` and `(0.0, 1.0)` unity endpoints.
 */
internal const val CROSSFADE_OVERLAP_COUPLING = 0.41f

internal data class CrossfadeGainPair(
    val outgoingGain: Float,
    val incomingGain: Float,
)

internal data class PlayerGainSplit(
    val playerVolume: Float,
    val postVolumeGain: Float,
)

internal fun calculateCrossfadeMixGains(
    curve: CrossfadeCurve,
    progress: Float,
): CrossfadeGainPair {
    val clampedProgress = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val rawOut = curve.fadeOut(clampedProgress).coerceIn(0f, 1f)
    val rawIn = curve.fadeIn(clampedProgress).coerceIn(0f, 1f)
    val combinedLoudnessPower =
        (rawOut * rawOut) +
            (rawIn * rawIn) +
            (2f * CROSSFADE_OVERLAP_COUPLING * rawOut * rawIn)
    val attenuation =
        if (combinedLoudnessPower > 1f) {
            (1.0 / sqrt(combinedLoudnessPower.toDouble())).toFloat()
        } else {
            1f
        }
    return CrossfadeGainPair(
        outgoingGain = (rawOut * attenuation).coerceIn(0f, 1f),
        incomingGain = (rawIn * attenuation).coerceIn(0f, 1f),
    )
}

/**
 * Equal-amplitude linear fade (`outgoing + incoming == 1.0`) for handing off between two
 * phase-aligned players rendering the exact same incoming track, avoiding the +3 dB spike that
 * equal-power curves produce on correlated signals.
 */
internal fun calculateCorrelatedHandoffGains(progress: Float): CrossfadeGainPair {
    val t = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return CrossfadeGainPair(
        outgoingGain = 1f - t,
        incomingGain = t,
    )
}

/**
 * Splits a target linear gain (`0f..maxSafeGainFactor`) across `ExoPlayer.setVolume` (`0f..1f`)
 * and [PlaybackGainAndSyncAudioProcessor.postVolumeGain] (`1f..maxSafeGainFactor`) so positive
 * loudness normalization boosts above `1.0f` are preserved instead of clamped by ExoPlayer.
 */
internal fun splitPlayerAndPostGain(
    effectiveLinearGain: Float,
    maxSafeGainFactor: Float,
): PlayerGainSplit {
    val safeMax = maxSafeGainFactor.takeIf { it.isFinite() && it >= 1f } ?: 1f
    val clampedGain = effectiveLinearGain.takeIf { it.isFinite() }?.coerceIn(0f, safeMax) ?: 0f
    return if (clampedGain <= 1f) {
        PlayerGainSplit(playerVolume = clampedGain, postVolumeGain = 1f)
    } else {
        PlayerGainSplit(playerVolume = 1f, postVolumeGain = clampedGain)
    }
}

internal fun needsCorrectiveCrossfadeSeek(
    primaryPositionMs: Long,
    secondaryPositionMs: Long,
    maximumDriftMs: Long,
): Boolean {
    require(maximumDriftMs >= 0L)
    return abs(primaryPositionMs - secondaryPositionMs) > maximumDriftMs
}

internal fun hasPlaybackPositionAdvanced(
    positionAfterSeekMs: Long,
    currentPositionMs: Long,
): Boolean = currentPositionMs > positionAfterSeekMs
