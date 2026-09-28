/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.constants

import org.junit.Assert.assertEquals
import org.junit.Test

class CrossfadeCurveTest {
    @Test
    fun allCurves_spanZeroToOne_atEndpoints() {
        CrossfadeCurve.entries.forEach { curve ->
            assertEquals(1f, curve.fadeOut(0f), 0.0001f)
            assertEquals(0f, curve.fadeOut(1f), 0.0001f)
            assertEquals(0f, curve.fadeIn(0f), 0.0001f)
            assertEquals(1f, curve.fadeIn(1f), 0.0001f)
        }
    }

    @Test
    fun equalPower_preservesPower_atMidpoint() {
        val outgoing = CrossfadeCurve.EQUAL_POWER.fadeOut(0.5f)
        val incoming = CrossfadeCurve.EQUAL_POWER.fadeIn(0.5f)
        assertEquals(1f, outgoing * outgoing + incoming * incoming, 0.0001f)
    }

    @Test
    fun easeOutQuad_matchesQuadraticShape() {
        assertEquals(0.25f, CrossfadeCurve.EASE_OUT_QUAD.fadeOut(0.5f), 0.0001f)
        assertEquals(0f, CrossfadeCurve.EASE_OUT_QUAD.fadeOut(1f), 0.0001f)
    }

    @Test
    fun smoothstep_isSymmetricAroundMidpoint() {
        assertEquals(0.5f, CrossfadeCurve.SMOOTHSTEP.fadeOut(0.5f), 0.0001f)
        assertEquals(0.5f, CrossfadeCurve.SMOOTHSTEP.fadeIn(0.5f), 0.0001f)
    }
}
