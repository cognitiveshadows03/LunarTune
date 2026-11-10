package dev.citali.lunartune.ui.component.karaoke

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsWidthTest {
    @Test fun transientWidthsDoNotProduceCharacterWideScenes() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f, 2f, 24f, 95f).forEach {
            assertEquals(320f, safeLyricsWidthPx(it), 0f)
        }
    }

    @Test fun settledWidthIsPreservedAndHugeWidthsAreBounded() {
        assertEquals(720f, safeLyricsWidthPx(720f), 0f)
        assertEquals(4096f, safeLyricsWidthPx(10000f), 0f)
    }
}
