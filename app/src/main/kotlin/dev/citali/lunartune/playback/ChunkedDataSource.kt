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
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Locale

internal class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkLength: Long,
) : DataSource {
    private var request: DataSpec? = null
    private var effectiveLength = C.LENGTH_UNSET.toLong()
    private var bytesRead = 0L
    private var chunkBytesRemaining = C.LENGTH_UNSET.toLong()
    private var inChunkReconnects = 0
    private var upstreamOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(request == null)
        request = dataSpec
        effectiveLength = dataSpec.length
        bytesRead = 0L
        inChunkReconnects = 0
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
        while (true) {
            if (effectiveLength != C.LENGTH_UNSET.toLong() && bytesRead >= effectiveLength) {
                return C.RESULT_END_OF_INPUT
            }
            if (!upstreamOpen || chunkBytesRemaining == 0L) {
                closeUpstream()
                val nextSpec = buildRemainingSpec(dataSpec) ?: return C.RESULT_END_OF_INPUT
                val opened = openSubsequentChunkOrEnd(nextSpec)
                if (!opened) return C.RESULT_END_OF_INPUT
            }
            val readLength =
                if (chunkBytesRemaining == C.LENGTH_UNSET.toLong()) {
                    length
                } else {
                    minOf(length.toLong(), chunkBytesRemaining).toInt()
                }
            val count =
                try {
                    upstream.read(buffer, offset, readLength)
                } catch (error: IOException) {
                    if (!canReconnectMidStream(error)) throw error
                    inChunkReconnects += 1
                    closeUpstream()
                    chunkBytesRemaining = 0L
                    continue
                }
            if (count == C.RESULT_END_OF_INPUT) {
                if (chunkBytesRemaining > 0L) {
                    if (inChunkReconnects < MAX_IN_CHUNK_RECONNECTS) {
                        inChunkReconnects += 1
                        closeUpstream()
                        chunkBytesRemaining = 0L
                        continue
                    }
                    throw EOFException("Audio stream ended before the requested range was complete")
                }
                return C.RESULT_END_OF_INPUT
            }
            inChunkReconnects = 0
            bytesRead += count
            if (chunkBytesRemaining != C.LENGTH_UNSET.toLong()) {
                chunkBytesRemaining -= count
            }
            return count
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        request = null
        effectiveLength = C.LENGTH_UNSET.toLong()
        bytesRead = 0L
        chunkBytesRemaining = C.LENGTH_UNSET.toLong()
        inChunkReconnects = 0
        closeUpstream()
    }

    private fun buildRemainingSpec(baseSpec: DataSpec): DataSpec? =
        if (effectiveLength != C.LENGTH_UNSET.toLong()) {
            val remaining = effectiveLength - bytesRead
            if (remaining <= 0L) null else baseSpec.subrange(bytesRead, remaining)
        } else {
            baseSpec.subrange(bytesRead)
        }

    private fun openSubsequentChunkOrEnd(nextSpec: DataSpec): Boolean =
        try {
            openNextChunk(nextSpec)
            true
        } catch (error: HttpDataSource.InvalidResponseCodeException) {
            if (error.responseCode == 416 && bytesRead > 0L) {
                closeUpstream()
                effectiveLength = bytesRead
                chunkBytesRemaining = 0L
                false
            } else {
                throw error
            }
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
        val openedLength =
            try {
                upstream.open(chunkSpec)
            } catch (error: Throwable) {
                upstreamOpen = false
                throw error
            }

        val contentRangeHeader = upstream.responseHeaders.firstHeaderIgnoreCase("Content-Range")
        val contentRangeTotal = contentRangeHeader?.let(::parseContentRangeTotal)
        val basePosition = request?.position ?: dataSpec.position
        if (contentRangeTotal != null && contentRangeTotal > basePosition) {
            val serverRemainingFromBase = contentRangeTotal - basePosition
            effectiveLength =
                if (effectiveLength == C.LENGTH_UNSET.toLong()) {
                    serverRemainingFromBase
                } else {
                    minOf(effectiveLength, serverRemainingFromBase)
                }
        } else if (effectiveLength == C.LENGTH_UNSET.toLong() && requestedChunkLength != null && contentRangeHeader == null) {
            // Server ignored Range and returned a plain 200 entity; read it without range slicing.
            chunkBytesRemaining = openedLength
            if (openedLength != C.LENGTH_UNSET.toLong()) {
                effectiveLength = bytesRead + openedLength
            }
            return openedLength
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

    private fun canReconnectMidStream(error: IOException): Boolean {
        if (inChunkReconnects >= MAX_IN_CHUNK_RECONNECTS) return false
        if (Thread.currentThread().isInterrupted) return false
        if (error is InterruptedIOException && error !is SocketTimeoutException) return false
        var current: Throwable? = error
        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException) return false
            current = current.cause
        }
        return true
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

    private companion object {
        const val MAX_IN_CHUNK_RECONNECTS = 2
    }
}
