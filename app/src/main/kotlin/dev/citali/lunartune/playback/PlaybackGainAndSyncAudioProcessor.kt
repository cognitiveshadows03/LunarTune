/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Custom [BaseAudioProcessor] attached to primary and secondary crossfade players to provide:
 *
 * 1. **Post-volume linear gain (> 1.0x)** with smooth soft-knee peak limiting so positive
 *    ReplayGain / YouTube loudness normalization boosts (up to +3 dB / 1.414x) actually take
 *    effect instead of being silently clamped to `1.0f` by `ExoPlayer.setVolume(float)`.
 * 2. **Sample-accurate muted frame skipping** during crossfade handoff so `localPlayer` catches
 *    up to `secondaryCrossfadePlayer` in the PCM pipeline while muted (`volume == 0f`) without
 *    a second `AudioTrack` flush/re-seek or duplicated incoming frame.
 *
 * Skipped frames are reported through [PlaybackAudioProcessorChain.getSkippedOutputFrameCount],
 * which `DefaultAudioSink.applySkipping` adds directly to `ExoPlayer.getCurrentPosition()` so
 * playback position and synchronized lyrics stay exact.
 */
@UnstableApi
internal class PlaybackGainAndSyncAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var postVolumeGain: Float = 1f

    private val configuredSampleRateHz = AtomicInteger(0)
    private val configuredBytesPerFrame = AtomicInteger(0)
    private val configuredPcmEncoding = AtomicInteger(C.Format.NO_VALUE)

    private val forwardedFramesSinceFlush = AtomicLong(0L)
    private val skippedFramesSinceFlush = AtomicLong(0L)
    private val pendingSkipFrames = AtomicLong(0L)
    private val spliceOffsetAfterFlushMs = AtomicLong(UNSET_OFFSET_MS)

    val skippedFrames: Long
        get() = skippedFramesSinceFlush.get()

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = inputAudioFormat.encoding
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        configuredSampleRateHz.set(inputAudioFormat.sampleRate)
        configuredBytesPerFrame.set(inputAudioFormat.bytesPerFrame)
        configuredPcmEncoding.set(encoding)
        return inputAudioFormat
    }

    override fun isActive(): Boolean =
        configuredSampleRateHz.get() > 0 && configuredBytesPerFrame.get() > 0

    /**
     * Schedules [lagMs] milliseconds of decoded PCM frames to be dropped at the processor input
     * while the player output is muted (`volume == 0f`). Returns `true` if a positive frame skip
     * was scheduled on an active audio stream.
     */
    fun scheduleSkipMs(lagMs: Long): Boolean {
        if (lagMs <= 0L) return false
        val sampleRate = configuredSampleRateHz.get()
        if (sampleRate <= 0 || configuredBytesPerFrame.get() <= 0) return false
        val framesToSkip = ((lagMs * sampleRate) + 500L) / 1_000L
        if (framesToSkip <= 0L) return false
        spliceOffsetAfterFlushMs.set(UNSET_OFFSET_MS)
        pendingSkipFrames.set(framesToSkip)
        return true
    }

    /**
     * Cancels any unconsumed frame skip scheduled by [scheduleSkipMs].
     */
    fun clearPendingSkip() {
        pendingSkipFrames.set(0L)
        spliceOffsetAfterFlushMs.set(UNSET_OFFSET_MS)
    }

    /**
     * Once a scheduled skip has been fully consumed at the processor write-head, returns the
     * media position (relative to [seekOriginMs]) at which all pre-splice PCM frames buffered
     * downstream in `AudioTrack` have finished playing through the speaker.
     */
    fun spliceMediaPositionMs(seekOriginMs: Long): Long? {
        if (pendingSkipFrames.get() > 0L) return null
        val offsetMs = spliceOffsetAfterFlushMs.get()
        if (offsetMs < 0L) return null
        return seekOriginMs + offsetMs
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frameSize = configuredBytesPerFrame.get()
        if (frameSize <= 0 || !inputBuffer.hasRemaining()) return

        var remainingSkip = pendingSkipFrames.get()
        if (remainingSkip > 0L) {
            val availableFrames = (inputBuffer.remaining() / frameSize).toLong()
            val framesToDrop = minOf(availableFrames, remainingSkip)
            if (framesToDrop > 0L) {
                val bytesToDrop = (framesToDrop * frameSize).toInt()
                inputBuffer.position(inputBuffer.position() + bytesToDrop)
                skippedFramesSinceFlush.addAndGet(framesToDrop)
                remainingSkip = pendingSkipFrames.addAndGet(-framesToDrop)
                if (remainingSkip <= 0L) {
                    pendingSkipFrames.set(0L)
                    val sampleRate = configuredSampleRateHz.get()
                    if (sampleRate > 0) {
                        val mediaFramesAtSplice =
                            forwardedFramesSinceFlush.get() + skippedFramesSinceFlush.get()
                        spliceOffsetAfterFlushMs.set((mediaFramesAtSplice * 1_000L) / sampleRate)
                    }
                }
            }
        }

        val remainingBytes = inputBuffer.remaining()
        if (remainingBytes <= 0) return

        val outputFrames = (remainingBytes / frameSize).toLong()
        val gain = postVolumeGain
        val encoding = configuredPcmEncoding.get()
        val outputBuffer = replaceOutputBuffer(remainingBytes)

        if (abs(gain - 1f) <= UNITY_GAIN_EPSILON) {
            outputBuffer.put(inputBuffer)
        } else if (encoding == C.ENCODING_PCM_16BIT) {
            processPcm16WithSoftClip(inputBuffer, outputBuffer, gain)
        } else if (encoding == C.ENCODING_PCM_FLOAT) {
            processPcmFloatWithSoftClip(inputBuffer, outputBuffer, gain)
        } else {
            outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
        forwardedFramesSinceFlush.addAndGet(outputFrames)
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        forwardedFramesSinceFlush.set(0L)
        skippedFramesSinceFlush.set(0L)
        pendingSkipFrames.set(0L)
        spliceOffsetAfterFlushMs.set(UNSET_OFFSET_MS)
    }

    override fun onReset() {
        configuredSampleRateHz.set(0)
        configuredBytesPerFrame.set(0)
        configuredPcmEncoding.set(C.Format.NO_VALUE)
        postVolumeGain = 1f
        forwardedFramesSinceFlush.set(0L)
        skippedFramesSinceFlush.set(0L)
        pendingSkipFrames.set(0L)
        spliceOffsetAfterFlushMs.set(UNSET_OFFSET_MS)
    }

    private fun processPcm16WithSoftClip(
        input: ByteBuffer,
        output: ByteBuffer,
        gain: Float,
    ) {
        input.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        while (input.remaining() >= 2) {
            val sample = input.short.toFloat() * gain
            val limited = softLimitPcm16(sample)
            output.putShort(limited)
        }
        while (input.hasRemaining()) {
            output.put(input.get())
        }
    }

    private fun processPcmFloatWithSoftClip(
        input: ByteBuffer,
        output: ByteBuffer,
        gain: Float,
    ) {
        input.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        while (input.remaining() >= 4) {
            val sample = input.float * gain
            output.putFloat(softLimitNormalizedFloat(sample))
        }
        while (input.hasRemaining()) {
            output.put(input.get())
        }
    }

    companion object {
        private const val UNSET_OFFSET_MS = -1L
        private const val UNITY_GAIN_EPSILON = 0.0005f
        private const val PCM16_KNEE_START = 28_000f
        private const val PCM16_KNEE_HEADROOM = 4_767f
        private const val FLOAT_KNEE_START = 0.8545f
        private const val FLOAT_KNEE_HEADROOM = 0.1455f

        internal fun softLimitPcm16(amplifiedSample: Float): Short {
            if (!amplifiedSample.isFinite()) return 0
            val magnitude = abs(amplifiedSample)
            if (magnitude <= PCM16_KNEE_START) {
                return amplifiedSample.toInt().toShort()
            }
            val excess = magnitude - PCM16_KNEE_START
            val compressed =
                PCM16_KNEE_START + (PCM16_KNEE_HEADROOM * excess) / (PCM16_KNEE_HEADROOM + excess)
            val signed = if (amplifiedSample < 0f) -compressed else compressed
            return signed.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }

        internal fun softLimitNormalizedFloat(amplifiedSample: Float): Float {
            if (!amplifiedSample.isFinite()) return 0f
            val magnitude = abs(amplifiedSample)
            if (magnitude <= FLOAT_KNEE_START) return amplifiedSample
            val excess = magnitude - FLOAT_KNEE_START
            val compressed =
                FLOAT_KNEE_START + (FLOAT_KNEE_HEADROOM * excess) / (FLOAT_KNEE_HEADROOM + excess)
            val signed = if (amplifiedSample < 0f) -compressed else compressed
            return signed.coerceIn(-1f, 1f)
        }
    }
}

/**
 * [AudioProcessorChain] combining [PlaybackGainAndSyncAudioProcessor],
 * [SilenceSkippingAudioProcessor], and [SonicAudioProcessor], while folding frames skipped
 * during muted crossfade handoff into [getSkippedOutputFrameCount].
 */
@UnstableApi
internal class PlaybackAudioProcessorChain(
    private val gainAndSyncProcessor: PlaybackGainAndSyncAudioProcessor,
    silenceSkippingAudioProcessor: SilenceSkippingAudioProcessor,
    sonicAudioProcessor: SonicAudioProcessor,
) : AudioProcessorChain {
    private val delegate =
        DefaultAudioSink.DefaultAudioProcessorChain(
            arrayOf(gainAndSyncProcessor),
            silenceSkippingAudioProcessor,
            sonicAudioProcessor,
        )

    override fun getAudioProcessors(): Array<AudioProcessor> = delegate.audioProcessors

    override fun applyPlaybackParameters(playbackParameters: PlaybackParameters): PlaybackParameters =
        delegate.applyPlaybackParameters(playbackParameters)

    override fun applySkipSilenceEnabled(skipSilenceEnabled: Boolean): Boolean =
        delegate.applySkipSilenceEnabled(skipSilenceEnabled)

    override fun getMediaDuration(playoutDuration: Long): Long =
        delegate.getMediaDuration(playoutDuration)

    override fun getSkippedOutputFrameCount(): Long =
        delegate.skippedOutputFrameCount + gainAndSyncProcessor.skippedFrames
}
