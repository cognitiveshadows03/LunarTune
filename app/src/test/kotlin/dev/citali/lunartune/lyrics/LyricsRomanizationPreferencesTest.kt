/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import dev.citali.lunartune.lyrics.LyricsUtils.providedRomanizedTextForEntry
import dev.citali.lunartune.lyrics.LyricsUtils.shouldRomanizeLyricsLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRomanizationPreferencesTest {
    @Test
    fun providerRomanizationWorksWithoutLocalRomanizationEnabled() {
        val preferences =
            LyricsRomanizationPreferences(
                romanizeJapanese = false,
                romanizeKorean = false,
                romanizeChinese = false,
                romanizeHindi = false,
                romanizeOther = false,
                useProvidedRomanization = true,
            )
        val entry =
            LyricsEntry(
                time = 1_000L,
                text = "日本語の歌詞",
                providerRomanizedText = "nihongo no kashi",
                providerRomanizedLanguage = "ja",
            )

        assertEquals("nihongo no kashi", providedRomanizedTextForEntry(entry, preferences))
        assertTrue(preferences.isEnabled)
        assertFalse(shouldRomanizeLyricsLine(entry.text, preferences))
    }

    @Test
    fun disablingProvidedRomanizationDoesNotDisableLocalRomanization() {
        val preferences =
            LyricsRomanizationPreferences(
                romanizeJapanese = true,
                romanizeKorean = false,
                romanizeChinese = false,
                romanizeHindi = false,
                romanizeOther = false,
                useProvidedRomanization = false,
            )
        val entry =
            LyricsEntry(
                time = 1_000L,
                text = "日本語の歌詞",
                providerRomanizedText = "nihongo no kashi",
                providerRomanizedLanguage = "ja",
            )

        assertNull(providedRomanizedTextForEntry(entry, preferences))
        assertTrue(shouldRomanizeLyricsLine(entry.text, preferences))
    }
}
