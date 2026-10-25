/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.EOFException
import java.util.Locale

internal class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkLength: Long,
) : DataSource {
    private var request: DataSpec? = null
    private var effectiveLength = C.LENGTH_UNSET.toLong()
    private var bytesRead = 0L
    private var chunkBytesRemaining = C.LENGTH_UNSET.toLong()
    private var upstreamOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(request == null)
        request = dataSpec
        effectiveLength = dataSpec.length
        bytesRead = 0L
        val resolvedLength = openNextChunk(dataSpec)
        return if (effectiveLength != C.LENGTH_UNSET.toLong()) effectiveLength else resolvedLength
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) return 0
        val dataSpec = checkNotNull(request)
        if (effectiveLength != C.LENGTH_UNSET.toLong() && bytesRead >= effectiveLength) {
            return C.RESULT_END_OF_INPUT
        }
        if (chunkBytesRemaining == 0L) {
            closeUpstream()
            val nextSpec =
                if (effectiveLength != C.LENGTH_UNSET.toLong()) {
                    val remaining = effectiveLength - bytesRead
                    if (remaining <= 0L) return C.RESULT_END_OF_INPUT
                    dataSpec.subrange(bytesRead, remaining)
                } else {
                    dataSpec.subrange(bytesRead)
                }
            openNextChunk(nextSpec)
        }
        val readLength =
            if (chunkBytesRemaining == C.LENGTH_UNSET.toLong()) {
                length
            } else {
                minOf(length.toLong(), chunkBytesRemaining).toInt()
            }
        val count = upstream.read(buffer, offset, readLength)
        if (count == C.RESULT_END_OF_INPUT) {
            if (chunkBytesRemaining > 0L) {
                throw EOFException("Audio stream ended before the requested range was complete")
            }
            return count
        }
        bytesRead += count
        if (chunkBytesRemaining != C.LENGTH_UNSET.toLong()) {
            chunkBytesRemaining -= count
        }
        return count
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        request = null
        effectiveLength = C.LENGTH_UNSET.toLong()
        bytesRead = 0L
        chunkBytesRemaining = C.LENGTH_UNSET.toLong()
        closeUpstream()
    }

    private fun openNextChunk(dataSpec: DataSpec): Long {
        val requestedChunkLength =
            when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> {
                    resolveStreamChunkLength(
                        requestedLength = dataSpec.length,
                        position = dataSpec.position,
                        knownContentLength = null,
                        chunkLength = chunkLength,
                    )
                }

                chunkLength > 0L && dataSpec.uri.isYouTubeStreamHost() -> {
                    chunkLength
                }

                else -> {
                    null
                }
            }
        val chunkSpec = requestedChunkLength?.let { dataSpec.subrange(0L, it) } ?: dataSpec
        chunkBytesRemaining = chunkSpec.length
        upstreamOpen = true
        val openedLength = upstream.open(chunkSpec)

        if (effectiveLength == C.LENGTH_UNSET.toLong()) {
            val contentRangeTotal = upstream.responseHeaders.firstHeaderIgnoreCase("Content-Range")?.let(::parseContentRangeTotal)
            val basePosition = request?.position ?: dataSpec.position
            if (contentRangeTotal != null && contentRangeTotal > basePosition) {
                effectiveLength = contentRangeTotal - basePosition
            } else if (requestedChunkLength != null && upstream.responseHeaders.firstHeaderIgnoreCase("Content-Range") == null) {
                // Server ignored Range and returned a plain 200 entity; read it without range slicing.
                chunkBytesRemaining = openedLength
                if (openedLength != C.LENGTH_UNSET.toLong()) {
                    effectiveLength = bytesRead + openedLength
                }
                return openedLength
            }
        }

        if (openedLength != C.LENGTH_UNSET.toLong()) {
            if (chunkBytesRemaining != C.LENGTH_UNSET.toLong()) {
                chunkBytesRemaining = minOf(chunkBytesRemaining, openedLength)
            }
            if (chunkSpec.length != C.LENGTH_UNSET.toLong() && openedLength < chunkSpec.length) {
                effectiveLength = bytesRead + openedLength
            }
        }
        return openedLength
    }

    private fun closeUpstream() {
        if (!upstreamOpen) return
        upstreamOpen = false
        upstream.close()
    }

    private fun Uri.isYouTubeStreamHost(): Boolean {
        val host = host?.lowercase(Locale.US) ?: return false
        return host == "googlevideo.com" ||
            host.endsWith(".googlevideo.com") ||
            host == "googleusercontent.com" ||
            host.endsWith(".googleusercontent.com")
    }

    private fun Map<String, List<String>>.firstHeaderIgnoreCase(name: String): String? =
        entries
            .firstOrNull { (key, values) -> key.equals(name, ignoreCase = true) && values.isNotEmpty() }
            ?.value
            ?.firstOrNull()

    private fun parseContentRangeTotal(contentRange: String): Long? {
        val slash = contentRange.indexOf('/')
        if (!contentRange.startsWith("bytes ", ignoreCase = true) || slash < 0) return null
        return contentRange.substring(slash + 1).trim().toLongOrNull()?.takeIf { it > 0L }
    }
}
