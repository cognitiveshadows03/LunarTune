/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

@OptIn(UnstableApi::class)
class PlaybackGainAndSyncAudioProcessorTest {
    @Test
    fun scheduleSkipMs_dropsExactLagFrames_andReportsSpliceMediaPosition() {
        val processor = PlaybackGainAndSyncAudioProcessor()
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

        // Feed 100 ms (4,800 stereo frames) prior to scheduling handoff skip.
        val initialChunk = buildStereoSequenceBuffer(frameCount = 4_800, startValue = 0)
        processor.queueInput(initialChunk)
        val initialOut = processor.output
        assertEquals(4_800 * 4, initialOut.remaining())
        initialOut.position(initialOut.limit())

        // Schedule a 50 ms (2,400-frame) catch-up skip while muted.
        assertTrue(processor.scheduleSkipMs(50L))
        assertNull(processor.spliceMediaPositionMs(seekOriginMs = 5_000L))

        // Feed 80 ms (3,840 stereo frames): first 2,400 dropped, remaining 1,440 forwarded.
        val secondChunk = buildStereoSequenceBuffer(frameCount = 3_840, startValue = 4_800)
        processor.queueInput(secondChunk)
        val secondOut = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1_440 * 4, secondOut.remaining())
        // First forwarded sample after skip must be frame index 4,800 + 2,400 = 7,200.
        assertEquals(7_200.toShort(), secondOut.short)
        assertEquals(2_400L, processor.skippedFrames)
        // 100 ms forwarded before skip + 50 ms skipped = 150 ms past seekOriginMs (5,000 + 150 = 5,150).
        assertEquals(5_150L, processor.spliceMediaPositionMs(seekOriginMs = 5_000L))

        // Flushing on a subsequent seek resets skipped frame accounting cleanly.
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertEquals(0L, processor.skippedFrames)
        assertNull(processor.spliceMediaPositionMs(seekOriginMs = 5_000L))
    }

    @Test
    fun postVolumeGain_amplifiesQuietSamples_andSoftLimitsPeaksWithoutClipping() {
        val processor = PlaybackGainAndSyncAudioProcessor()
        val format = AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.postVolumeGain = 1.4f

        val input =
            ByteBuffer
                .allocateDirect(8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putShort(10_000)
                    putShort((-10_000).toShort())
                    putShort(30_000)
                    putShort((-30_000).toShort())
                    flip()
                }

        processor.queueInput(input)
        val out = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(14_000, out.short.toInt())
        assertEquals(-14_000, out.short.toInt())

        val positivePeak = out.short.toInt()
        val negativePeak = out.short.toInt()
        assertTrue(positivePeak in 28_001..Short.MAX_VALUE.toInt())
        assertTrue(negativePeak in Short.MIN_VALUE.toInt()..-28_001)
    }

    private fun buildStereoSequenceBuffer(
        frameCount: Int,
        startValue: Int,
    ): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until frameCount) {
            val sample = ((startValue + i) % 16_000).toShort()
            buffer.putShort(sample)
            buffer.putShort(sample)
        }
        buffer.flip()
        return buffer
    }
}
