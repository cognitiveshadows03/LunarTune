/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TogetherLinkTest {
    @Test
    fun deepLinkRoundTripPreservesLanScheme() {
        val original =
            TogetherJoinInfo(
                host = "192.168.1.8",
                port = 42117,
                sessionId = "session-id",
                sessionKey = "secret-key",
            )

        val decoded = TogetherLink.decode(original.toDeepLink())

        assertEquals(original, decoded)
        assertEquals("ws://192.168.1.8:42117/together", decoded?.toWebSocketUrl())
    }

    @Test
    fun secureWebSocketLinkKeepsTlsScheme() {
        val decoded =
            TogetherLink.decode(
                "wss://music.example:443/together?sid=session-id&key=secret-key",
            )

        assertNotNull(decoded)
        assertEquals("wss", decoded?.scheme)
        assertEquals("wss://music.example:443/together", decoded?.toWebSocketUrl())
    }

    @Test
    fun legacyDeepLinkDefaultsToLanScheme() {
        val decoded =
            TogetherLink.decode(
                "lunartune://together?host=192.168.1.8&port=42117&sid=session-id&key=secret-key",
            )

        assertEquals("ws", decoded?.scheme)
        assertEquals("ws://192.168.1.8:42117/together", decoded?.toWebSocketUrl())
    }
}
