/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.net.Uri
import dev.citali.lunartune.constants.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import timber.log.Timber

/**
 * Primary download resolver backed by NewPipe Extractor.
 *
 * NewPipe Extractor ships an upstream-maintained Java decipher and returns descrambled,
 * unthrottled progressive URLs, which are mapped onto [YTPlayerUtils.PlaybackData] so the
 * regular ExoPlayer download stack can consume them unchanged. InnerTube remains the
 * fallback, covering content that needs login context.
 */
internal object NewPipeStreamResolver {
    private const val TAG = "NewPipeFallback"
    private const val DEFAULT_STREAM_EXPIRY_SECONDS = 21_000
    private const val EXPIRY_SAFETY_MARGIN_SECONDS = 120L

    suspend fun resolve(
        videoId: String,
        audioQuality: AudioQuality,
    ): Result<YTPlayerUtils.PlaybackData> =
        runCatching {
            if (!NewPipeBootstrap.ensureInitialized()) {
                throw IllegalStateException("NewPipe Extractor is unavailable")
            }
            withContext(Dispatchers.IO) {
                val extractor =
                    ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                extractor.fetchPage()
                val stream = selectAudioStream(extractor.audioStreams, audioQuality, videoId)
                val streamUrl =
                    stream.url?.takeIf { it.isNotBlank() }
                        ?: throw IllegalStateException("NewPipe returned an empty stream URL for $videoId")
                Timber.tag(TAG).i(
                    "NewPipe fallback resolved %s: itag=%s bitrate=%d mime=%s",
                    videoId,
                    stream.id,
                    stream.averageBitrate,
                    stream.format?.mimeType,
                )
                YTPlayerUtils.PlaybackData(
                    audioConfig = null,
                    videoDetails =
                        PlayerResponse.VideoDetails(
                            videoId = videoId,
                            title = runCatching { extractor.name }.getOrNull(),
                            author = runCatching { extractor.uploaderName }.getOrNull(),
                            lengthSeconds =
                                runCatching { extractor.length }
                                    .getOrNull()
                                    ?.takeIf { it > 0 }
                                    ?.toString(),
                        ),
                    playbackTracking = null,
                    format = stream.toInnerTubeFormat(streamUrl),
                    streamUrl = streamUrl,
                    streamExpiresInSeconds = streamExpirySeconds(streamUrl),
                    authFingerprint = YouTube.currentPlaybackAuthState().fingerprint,
                )
            }
        }

    private fun selectAudioStream(
        streams: List<AudioStream>,
        audioQuality: AudioQuality,
        videoId: String,
    ): AudioStream {
        val progressive =
            streams.filter {
                it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && !it.url.isNullOrBlank()
            }
        if (progressive.isEmpty()) {
            throw IllegalStateException("NewPipe returned no progressive audio streams for $videoId")
        }
        val pool = progressive.filter { it.averageBitrate > 0 }.ifEmpty { progressive }
        return when (audioQuality) {
            AudioQuality.LOW -> pool.minByOrNull { it.averageBitrate } ?: progressive.first()
            else -> pool.maxByOrNull { it.averageBitrate } ?: progressive.first()
        }
    }

    private fun AudioStream.toInnerTubeFormat(streamUrl: String): PlayerResponse.StreamingData.Format {
        val bitrate = averageBitrate.takeIf { it > 0 } ?: 0
        return PlayerResponse.StreamingData.Format(
            itag = id.toIntOrNull() ?: -1,
            url = streamUrl,
            mimeType = buildMimeType(),
            bitrate = bitrate,
            width = null,
            height = null,
            contentLength = null,
            quality = if (bitrate >= 128_000) "medium" else "tiny",
            fps = null,
            qualityLabel = null,
            averageBitrate = averageBitrate.takeIf { it > 0 },
            audioQuality =
                when {
                    bitrate >= 128_000 -> "AUDIO_QUALITY_HIGH"
                    bitrate >= 64_000 -> "AUDIO_QUALITY_MEDIUM"
                    bitrate > 0 -> "AUDIO_QUALITY_LOW"
                    else -> null
                },
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
        )
    }

    private fun AudioStream.buildMimeType(): String {
        val mediaFormat = runCatching { format }.getOrNull()
        val base = mediaFormat?.mimeType ?: "audio/mp4"
        val codec =
            when (mediaFormat?.name) {
                "M4A" -> "mp4a.40.2"
                "WEBMA", "WEBMA_OPUS", "OPUS" -> "opus"
                "MP3" -> "mp3"
                "OGG" -> "vorbis"
                else -> null
            }
        return if (codec != null) "$base; codecs=\"$codec\"" else base
    }

    private fun streamExpirySeconds(streamUrl: String): Int {
        val expireAt =
            runCatching { Uri.parse(streamUrl).getQueryParameter("expire")?.toLongOrNull() }.getOrNull()
        if (expireAt != null && expireAt > 0) {
            val remaining = expireAt - System.currentTimeMillis() / 1000L - EXPIRY_SAFETY_MARGIN_SECONDS
            if (remaining > 60) return remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        return DEFAULT_STREAM_EXPIRY_SECONDS
    }
}
