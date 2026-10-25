/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import dev.citali.lunartune.constants.CrossfadeCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeHandoffPolicyTest {
    @Test
    fun correctiveSeek_isSkipped_whenDriftIsWithinTolerance() {
        assertFalse(
            needsCorrectiveCrossfadeSeek(
                primaryPositionMs = 10_040L,
                secondaryPositionMs = 10_000L,
                maximumDriftMs = 75L,
            ),
        )
        assertFalse(needsCorrectiveCrossfadeSeek(10_075L, 10_000L, maximumDriftMs = 75L))
    }

    @Test
    fun correctiveSeek_isRequired_whenDriftExceedsTolerance_inEitherDirection() {
        assertTrue(needsCorrectiveCrossfadeSeek(9_900L, 10_000L, maximumDriftMs = 75L))
        assertTrue(needsCorrectiveCrossfadeSeek(10_100L, 10_000L, maximumDriftMs = 75L))
    }

    @Test
    fun playbackPositionMustMoveForward_afterSeek() {
        assertFalse(
            hasPlaybackPositionAdvanced(positionAfterSeekMs = 10_000L, currentPositionMs = 10_000L),
        )
        assertFalse(
            hasPlaybackPositionAdvanced(positionAfterSeekMs = 10_000L, currentPositionMs = 9_999L),
        )
        assertTrue(
            hasPlaybackPositionAdvanced(positionAfterSeekMs = 10_000L, currentPositionMs = 10_001L),
        )
    }

    @Test
    fun crossfadeMixGains_preserveExactUnityEndpoints_andEliminateOverlapSwell() {
        CrossfadeCurve.entries.forEach { curve ->
            val start = calculateCrossfadeMixGains(curve, 0f)
            assertEquals("fadeOut(0) for $curve", 1f, start.outgoingGain, 1e-5f)
            assertEquals("fadeIn(0) for $curve", 0f, start.incomingGain, 1e-5f)

            val end = calculateCrossfadeMixGains(curve, 1f)
            assertEquals("fadeOut(1) for $curve", 0f, end.outgoingGain, 1e-5f)
            assertEquals("fadeIn(1) for $curve", 1f, end.incomingGain, 1e-5f)

            for (step in 0..20) {
                val progress = step / 20f
                val gains = calculateCrossfadeMixGains(curve, progress)
                val perceivedPower =
                    (gains.outgoingGain * gains.outgoingGain) +
                        (gains.incomingGain * gains.incomingGain) +
                        (2f * CROSSFADE_OVERLAP_COUPLING * gains.outgoingGain * gains.incomingGain)
                assertTrue(
                    "Perceived power $perceivedPower exceeded unity at t=$progress for $curve",
                    perceivedPower <= 1.0005f,
                )
            }
        }
    }

    @Test
    fun equalPowerMixGains_matchConstantLoudnessMidpointLaw() {
        val mid = calculateCrossfadeMixGains(CrossfadeCurve.EQUAL_POWER, 0.5f)
        assertEquals(0.5955f, mid.outgoingGain, 0.002f)
        assertEquals(0.5955f, mid.incomingGain, 0.002f)
    }

    @Test
    fun easeOutCubicMixGains_keepOutgoingAboveIncomingAtMidpoint_withoutClippingUnityLoudness() {
        val mid = calculateCrossfadeMixGains(CrossfadeCurve.EASE_OUT_CUBIC, 0.5f)
        assertTrue(mid.outgoingGain > mid.incomingGain)
        assertTrue(mid.outgoingGain < 0.875f)
        assertTrue(mid.incomingGain < 0.707f)
    }

    @Test
    fun correlatedHandoffGains_maintainConstantAmplitudeAcrossSameTrackSplice() {
        for (step in 0..10) {
            val progress = step / 10f
            val handoff = calculateCorrelatedHandoffGains(progress)
            assertEquals(1f, handoff.outgoingGain + handoff.incomingGain, 1e-5f)
        }
    }

    @Test
    fun splitPlayerAndPostGain_routesPositiveNormalizationBoostToProcessor() {
        val attenuated = splitPlayerAndPostGain(effectiveLinearGain = 0.48f, maxSafeGainFactor = 1.414f)
        assertEquals(0.48f, attenuated.playerVolume, 1e-5f)
        assertEquals(1f, attenuated.postVolumeGain, 1e-5f)

        val boosted = splitPlayerAndPostGain(effectiveLinearGain = 1.32f, maxSafeGainFactor = 1.414f)
        assertEquals(1f, boosted.playerVolume, 1e-5f)
        assertEquals(1.32f, boosted.postVolumeGain, 1e-5f)

        val clamped = splitPlayerAndPostGain(effectiveLinearGain = 2.0f, maxSafeGainFactor = 1.414f)
        assertEquals(1f, clamped.playerVolume, 1e-5f)
        assertEquals(1.414f, clamped.postVolumeGain, 1e-5f)
    }
}
