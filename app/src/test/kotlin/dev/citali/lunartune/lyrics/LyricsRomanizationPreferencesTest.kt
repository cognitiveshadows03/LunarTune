/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.lyrics

import dev.citali.lunartune.lyrics.LyricsUtils.shouldRomanizeLyricsLine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRomanizationPreferencesTest {
    @Test
    fun japaneseRomanizationUsesItsLanguagePreference() {
        val preferences =
            LyricsRomanizationPreferences(
                romanizeJapanese = true,
                romanizeKorean = false,
                romanizeChinese = false,
                romanizeHindi = false,
                romanizeOther = false,
            )

        assertTrue(shouldRomanizeLyricsLine("日本語の歌詞", preferences))
    }

    @Test
    fun disabledJapaneseRomanizationDoesNotScheduleLocalWork() {
        val preferences =
            LyricsRomanizationPreferences(
                romanizeJapanese = false,
                romanizeKorean = false,
                romanizeChinese = false,
                romanizeHindi = false,
                romanizeOther = false,
            )

        assertFalse(shouldRomanizeLyricsLine("日本語の歌詞", preferences))
    }
}
