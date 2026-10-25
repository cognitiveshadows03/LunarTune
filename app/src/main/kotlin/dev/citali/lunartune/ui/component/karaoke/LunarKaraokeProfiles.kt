/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.ui.component.karaoke

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.ui.preparation.MeasuredLyricsLine
import com.mocharealm.accompanist.lyrics.ui.preparation.MeasuredSyllable
import com.mocharealm.accompanist.lyrics.ui.profile.ArabicProfile
import com.mocharealm.accompanist.lyrics.ui.profile.CjkProfile
import com.mocharealm.accompanist.lyrics.ui.profile.CyrillicProfile
import com.mocharealm.accompanist.lyrics.ui.profile.DefaultLyricsProfile
import com.mocharealm.accompanist.lyrics.ui.profile.FallbackProfile
import com.mocharealm.accompanist.lyrics.ui.profile.GreekProfile
import com.mocharealm.accompanist.lyrics.ui.profile.HangulJamoProfile
import com.mocharealm.accompanist.lyrics.ui.profile.HebrewProfile
import com.mocharealm.accompanist.lyrics.ui.profile.IndicProfile
import com.mocharealm.accompanist.lyrics.ui.profile.LyricsProfile
import com.mocharealm.accompanist.lyrics.ui.profile.ProfileAnimationUnit
import com.mocharealm.accompanist.lyrics.ui.profile.ProfileGroupEffects
import com.mocharealm.accompanist.lyrics.ui.profile.ProfileTextUnit
import com.mocharealm.accompanist.lyrics.ui.profile.ProfileTiming
import com.mocharealm.accompanist.lyrics.ui.profile.SoutheastAsianProfile
import com.mocharealm.accompanist.lyrics.ui.profile.TibetanProfile
import java.text.BreakIterator

/**
 * High-throughput Latin lyrics profile that splits shaped syllables at grapheme & glyph-box
 * boundaries in pure arithmetic (eliminating hundreds of per-word ImageBitmap.readPixels passes
 * during scene preparation) and uses group-level glow (`glowAsGroup = true`) so held notes use a
 * single pre-baked atlas glow tile instead of per-character offscreen BlurEffect layers.
 */
object FastLatinProfile : DefaultLyricsProfile() {
    override fun prepare(
        line: MeasuredLyricsLine,
        group: List<KaraokeSyllable>,
        measurer: TextMeasurer,
        style: TextStyle,
    ): List<ProfileTextUnit> =
        bindUnitsToSyllableAnimations(line, super.prepare(line, group, measurer, style))

    override fun effects(
        units: List<ProfileTextUnit>,
        accompaniment: Boolean,
    ): ProfileGroupEffects {
        val duration =
            (units.maxOfOrNull { it.end } ?: 0).toLong() - (units.minOfOrNull { it.start } ?: 0)
        val timingCount = units.sumOf { it.timing.size }
        val enabled =
            !accompaniment &&
                timingCount > 1 &&
                duration >= HELD_WORD_MIN_DURATION_MS &&
                duration / timingCount > HELD_CHAR_MIN_AVG_MS
        return ProfileGroupEffects(scale = enabled, glow = enabled, glowAsGroup = true)
    }

    override fun matches(syllable: KaraokeSyllable): Boolean {
        val text = syllable.content
        var hasLatin = false
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate()) {
                val cp = Character.toCodePoint(ch, text[index + 1])
                index += 2
                if (Character.isLetter(cp)) return false
                continue
            }
            index++
            when {
                isLatinChar(ch) -> hasLatin = true
                ch.isLetter() -> return false
            }
        }
        return hasLatin
    }

    override fun prepareWithLayout(
        group: List<KaraokeSyllable>,
        measurer: TextMeasurer,
        style: TextStyle,
        initialLayout: TextLayoutResult?,
    ): List<ProfileTextUnit> {
        val content = group.joinToString("") { it.content }
        val layout = initialLayout ?: measurer.measure(content, style, softWrap = false)
        val result = ArrayList<ProfileTextUnit>(content.length)
        var offset = 0
        for (syllable in group) {
            val len = syllable.content.length
            if (len == 0) continue
            val animation = ProfileAnimationUnit(syllable.start, syllable.end)
            for (index in 0 until len) {
                val bounds = layout.getBoundingBox(offset + index)
                val start =
                    syllable.start +
                        ((syllable.end.toLong() - syllable.start) * index / len).toInt()
                val end =
                    syllable.start +
                        ((syllable.end.toLong() - syllable.start) * (index + 1) / len).toInt()
                val range = TextRange(offset + index, offset + index + 1)
                result.add(
                    ProfileTextUnit(
                        layout = layout,
                        left = bounds.left,
                        right = bounds.right,
                        start = start,
                        end = end,
                        phonetic = if (index == 0) syllable.phonetic else null,
                        timing = listOf(ProfileTiming(start, end, 0f, bounds.width, range)),
                        sourceStart = syllable.start,
                        animation = animation,
                        sourceRange = range,
                    ),
                )
            }
            offset += len
        }
        return result
    }

    override fun protectUnits(units: List<ProfileTextUnit>): List<ProfileTextUnit> {
        if (units.size <= 1) return units
        val firstLayout = units.first().layout
        if (units.any { it.layout !== firstLayout || it.sourceRange == null }) return units

        val sourceText = firstLayout.layoutInput.text.text
        val graphemeSafe = computeGraphemeBoundaries(sourceText)
        val merged = ArrayList<ProfileTextUnit>(units.size)
        var current = units[0]
        for (index in 1 until units.size) {
            val next = units[index]
            val currRange = current.sourceRange!!
            val nextRange = next.sourceRange!!
            val isContiguous = currRange.max == nextRange.min
            val splitsGrapheme =
                isContiguous &&
                    currRange.max in 0..sourceText.length &&
                    !graphemeSafe[currRange.max]
            // Merge combining marks / surrogate pairs or overlapping kerning/ligature advance cells.
            val overlapsAdvance = current.right > next.left + OVERLAP_EPSILON_PX
            val degenerateWidth = current.width <= 0.01f || next.width <= 0.01f
            current =
                if (splitsGrapheme || (isContiguous && (overlapsAdvance || degenerateWidth))) {
                    mergeAdjacentUnits(current, next)
                } else {
                    merged.add(current)
                    next
                }
        }
        merged.add(current)
        return merged
    }

    private fun mergeAdjacentUnits(
        first: ProfileTextUnit,
        second: ProfileTextUnit,
    ): ProfileTextUnit {
        val left = minOf(first.left, second.left)
        val right = maxOf(first.right, second.right)
        val firstShift = first.left - left
        val secondShift = second.left - left
        val combinedTiming =
            ArrayList<ProfileTiming>(first.timing.size + second.timing.size).apply {
                for (t in first.timing) {
                    add(t.copy(left = t.left + firstShift, right = t.right + firstShift))
                }
                for (t in second.timing) {
                    add(t.copy(left = t.left + secondShift, right = t.right + secondShift))
                }
            }
        val firstRange = first.sourceRange
        val secondRange = second.sourceRange
        val mergedRange =
            if (firstRange != null && secondRange != null) {
                TextRange(minOf(firstRange.min, secondRange.min), maxOf(firstRange.max, secondRange.max))
            } else {
                null
            }
        val mergedPhonetic =
            when {
                first.phonetic.isNullOrEmpty() -> second.phonetic
                second.phonetic.isNullOrEmpty() -> first.phonetic
                else -> "${first.phonetic}${second.phonetic}"
            }
        return ProfileTextUnit(
            layout = first.layout,
            left = left,
            right = right,
            start = minOf(first.start, second.start),
            end = maxOf(first.end, second.end),
            phonetic = mergedPhonetic,
            timing = combinedTiming,
            sourceStart = minOf(first.sourceStart, second.sourceStart),
            breakBefore = first.breakBefore,
            animation =
                if (first.animation == second.animation) {
                    first.animation
                } else {
                    ProfileAnimationUnit(
                        minOf(first.animation.start, second.animation.start),
                        maxOf(first.animation.end, second.animation.end),
                    )
                },
            sourceRange = mergedRange,
        )
    }

    private fun computeGraphemeBoundaries(text: String): BooleanArray {
        val safe = BooleanArray(text.length + 1)
        safe[0] = true
        safe[text.length] = true
        val iterator = BreakIterator.getCharacterInstance()
        iterator.setText(text)
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            if (boundary in safe.indices) safe[boundary] = true
            boundary = iterator.next()
        }
        return safe
    }

    private fun bindUnitsToSyllableAnimations(
        line: MeasuredLyricsLine,
        units: List<ProfileTextUnit>,
    ): List<ProfileTextUnit> {
        val ranges = line.ranges
        if (ranges.isEmpty()) return units
        val ordered =
            ranges.zipWithNext().all { (a, b) ->
                a.source.start <= b.source.start && a.source.end <= b.source.end
            }
        return units.map { unit ->
            val matched = findSourceSyllable(ranges, ordered, unit.start, unit.end)
            if (matched == null) {
                unit
            } else {
                unit.copy(animation = matched.animation, sourceStart = matched.source.start)
            }
        }
    }

    private fun findSourceSyllable(
        ranges: List<MeasuredSyllable>,
        ordered: Boolean,
        start: Int,
        end: Int,
    ): MeasuredSyllable? {
        if (!ordered) {
            return ranges.firstOrNull { start >= it.source.start && end <= it.source.end }
        }
        var low = 0
        var high = ranges.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (ranges[mid].source.end < end) low = mid + 1 else high = mid
        }
        return ranges.getOrNull(low)?.takeIf { start >= it.source.start }
    }

    private fun isLatinChar(ch: Char): Boolean =
        ch in 'A'..'Z' ||
            ch in 'a'..'z' ||
            ch.code in 0x00C0..0x024F ||
            ch.code in 0x1E00..0x1EFF

    private const val HELD_WORD_MIN_DURATION_MS = 1000L
    private const val HELD_CHAR_MIN_AVG_MS = 200L
    private const val OVERLAP_EPSILON_PX = 0.5f
}

val LunarLyricsProfiles: List<LyricsProfile> =
    listOf(
        ArabicProfile,
        HebrewProfile,
        IndicProfile,
        SoutheastAsianProfile,
        TibetanProfile,
        HangulJamoProfile,
        CyrillicProfile,
        GreekProfile,
        CjkProfile,
        FastLatinProfile,
        FallbackProfile,
    )
