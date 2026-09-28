/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.HttpDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * Download-only [DataSource] that fetches the requested byte range to a temp file at full
 * connection speed first, then serves it to ExoPlayer's cache pipeline from local disk.
 *
 * This is the 4nx3b/ArchiveTune speed trick (their PRDownloader bridge), re-implemented on
 * our own OkHttp stack instead of the 2019-era PRDownloader library. That keeps proxy
 * support, per-client request profiles, and the 403/410 URL-refresh invalidation working,
 * and it removes the failure modes behind their bulk-download mass failures:
 *
 * - Unique temp file per attempt (UUID): no two downloads ever share or delete each
 *   other's temp file, even for duplicate/retried songs.
 * - Real resume: when ExoPlayer re-opens with `position > 0` (retry/resume), only the
 *   missing tail is fetched via `Range`, instead of re-downloading the whole file.
 * - No aggressive stall killer. YouTube serves throttled connections in bursts with idle
 *   gaps, so a short "no progress" watchdog murders healthy bulk downloads. A slow
 *   connection is bounded by the read timeout plus an overall fetch deadline, and a
 *   killed fetch simply resumes its tail on retry.
 * - Every fetched byte count is verified against Content-Range/Content-Length, so a
 *   truncated response fails loudly and resumes instead of poisoning the cache.
 *
 * Trade-off, same as upstream: ExoPlayer only sees bytes during the local serve phase,
 * so per-song progress jumps from 0% to 100% instead of climbing smoothly.
 */
class PrefetchDataSource(
    private val httpClient: OkHttpClient,
    private val tempDir: File,
) : BaseDataSource(/* isNetwork = */ true) {
    class Factory(
        private val context: Context,
        private val httpClient: OkHttpClient,
    ) : DataSource.Factory {
        private val tempDir: File by lazy {
            File(context.cacheDir, TEMP_DIR_NAME).apply {
                mkdirs()
                // Temps are always deleted on close, so anything left here is orphaned
                // by a crashed process and safe to drop.
                listFiles()?.forEach { file ->
                    runCatching { file.delete() }
                }
            }
        }

        override fun createDataSource(): PrefetchDataSource = PrefetchDataSource(httpClient, tempDir)
    }

    /** Latest fetch progress per song id (dataSpec.key), so the UI can show live
     * progress during the prefetch phase instead of a stuck 0%. Absolute file bytes. */
    data class PrefetchSample(val bytesWrittenAbsolute: Long, val totalBytes: Long?)

    private val fileDataSource = FileDataSource()
    private var tempFile: File? = null
    private var fileUri: Uri? = null
    private var bytesRemaining: Long = 0
    private var openKey: String? = null

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        if (dataSpec.length == 0L) {
            bytesRemaining = 0
            transferStarted(dataSpec)
            return 0
        }
        val temp = File(tempDir, "$TEMP_FILE_PREFIX${UUID.randomUUID()}")
        var success = false
        try {
            val fetchedBytes = fetchRangeToFile(dataSpec, temp)
            tempFile = temp
            val uri = Uri.fromFile(temp)
            fileUri = uri
            openKey = dataSpec.key
            // The temp file holds exactly the requested range, so serve it from offset 0
            // even when the original request was a resumed range.
            val served = fileDataSource.open(DataSpec(uri, 0, fetchedBytes, dataSpec.key))
            bytesRemaining = served
            transferStarted(dataSpec)
            success = true
            return served
        } finally {
            if (!success) {
                runCatching { temp.delete() }
                dataSpec.key?.let(prefetchSamples::remove)
            }
        }
    }

    @Throws(IOException::class)
    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0 || bytesRemaining == 0L) {
            return if (length == 0) 0 else C.RESULT_END_OF_INPUT
        }
        val read = fileDataSource.read(buffer, offset, length)
        if (read > 0) {
            bytesRemaining -= read
            bytesTransferred(read)
        }
        return read
    }

    override fun getUri(): Uri? = fileUri

    @Throws(IOException::class)
    override fun close() {
        fileUri = null
        openKey?.let(prefetchSamples::remove)
        openKey = null
        runCatching { fileDataSource.close() }
        tempFile?.let { temp ->
            tempFile = null
            runCatching {
                if (!temp.delete()) temp.deleteOnExit()
            }
        }
    }

    /**
     * Streams `[position, position + length)` (or to end of file) into [temp].
     *
     * @return the number of range bytes written.
     */
    @Throws(IOException::class)
    private fun fetchRangeToFile(
        dataSpec: DataSpec,
        temp: File,
    ): Long {
        val position = dataSpec.position
        val requestBuilder =
            Request
                .Builder()
                .url(dataSpec.uri.toString())
                .get()
        dataSpec.httpRequestHeaders.forEach { (name, value) ->
            if (!name.equals("Range", ignoreCase = true) &&
                !name.equals("Accept-Encoding", ignoreCase = true)
            ) {
                requestBuilder.header(name, value)
            }
        }
        // Identity encoding keeps Content-Length exact so short reads are detectable.
        requestBuilder.header("Accept-Encoding", "identity")
        val rangeRequested = position > 0 || dataSpec.length != C.LENGTH_UNSET.toLong()
        if (rangeRequested) {
            val rangeEnd =
                if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                    ""
                } else {
                    (position + dataSpec.length - 1).toString()
                }
            requestBuilder.header("Range", "bytes=$position-$rangeEnd")
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw HttpDataSource.InvalidResponseCodeException(
                    response.code,
                    response.message.ifEmpty { null },
                    null,
                    response.headers.toMultimap(),
                    dataSpec,
                    ByteArray(0),
                )
            }
            val body = response.body ?: throw IOException("Empty response body for ${dataSpec.uri.host}")
            val rangeHonored = response.code == 206
            val expectedBytes =
                expectedRangeLength(
                    contentRange = response.header("Content-Range"),
                    contentLength = body.contentLength(),
                    rangeHonored = rangeHonored,
                    position = position,
                )
            val bodyLength = body.contentLength()
            val progressTotalBytes =
                response.header("Content-Range")?.let(::parseContentRangeTotal)
                    ?: bodyLength.takeIf { it >= 0 }
            val progressKey = dataSpec.key
            if (progressKey != null) {
                prefetchSamples[progressKey] = PrefetchSample(position, progressTotalBytes)
                Timber.i(
                    "Prefetch start key=%s position=%d total=%s",
                    progressKey,
                    position,
                    progressTotalBytes?.toString() ?: "unknown",
                )
            }
            // A server that ignores Range answers 200 with the full file: skip the bytes
            // that precede the requested range instead of serving the wrong window.
            var bytesToSkip = if (rangeRequested && !rangeHonored && position > 0) position else 0L
            var written = 0L
            val startMs = SystemClock.elapsedRealtime()
            body.byteStream().use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_SIZE)
                    while (true) {
                        if (SystemClock.elapsedRealtime() - startMs > FETCH_DEADLINE_MS) {
                            throw IOException(
                                "Prefetch of ${dataSpec.uri.host} exceeded the fetch deadline " +
                                    "after $written bytes; retry will resume the tail",
                            )
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        var chunkOffset = 0
                        var chunkLength = read
                        if (bytesToSkip > 0) {
                            val skipped = minOf(bytesToSkip, chunkLength.toLong()).toInt()
                            chunkOffset += skipped
                            chunkLength -= skipped
                            bytesToSkip -= skipped
                        }
                        if (chunkLength > 0) {
                            output.write(buffer, chunkOffset, chunkLength)
                            written += chunkLength
                            if (progressKey != null) {
                                prefetchSamples[progressKey] =
                                    PrefetchSample(position + written, progressTotalBytes)
                            }
                        }
                    }
                    output.flush()
                }
            }
            if (bytesToSkip > 0) {
                throw IOException("Server ignored Range request and sent fewer than $position bytes")
            }
            if (expectedBytes != C.LENGTH_UNSET.toLong() && written != expectedBytes) {
                throw IOException("Short prefetch: got $written of $expectedBytes bytes; retry will resume the tail")
            }
            val prefetchElapsedMs = SystemClock.elapsedRealtime() - startMs
            Timber.i(
                "Prefetched %d bytes (resume=%b) for %s in %dms",
                written,
                position > 0,
                dataSpec.key,
                prefetchElapsedMs,
            )
            return written
        }
    }

    private fun expectedRangeLength(
        contentRange: String?,
        contentLength: Long,
        rangeHonored: Boolean,
        position: Long,
    ): Long =
        contentRange
            ?.let(::parseContentRangeLength)
            ?: if (contentLength >= 0) {
                if (!rangeHonored && position > 0) {
                    (contentLength - position).coerceAtLeast(0)
                } else {
                    contentLength
                }
            } else {
                C.LENGTH_UNSET.toLong()
            }

    /** Parses `bytes <first>-<last>/<total|*>` into `<last> - <first> + 1`, or null. */
    private fun parseContentRangeLength(contentRange: String): Long? {
        val dash = contentRange.indexOf('-')
        val slash = contentRange.indexOf('/')
        val wellFormed = contentRange.startsWith("bytes ") && dash > 6 && slash > dash + 1
        val first = if (wellFormed) contentRange.substring(6, dash).toLongOrNull() else null
        val last = if (wellFormed) contentRange.substring(dash + 1, slash).toLongOrNull() else null
        return if (first != null && last != null && last >= first) last - first + 1 else null
    }

    /** Parses the `/total` of `bytes <first>-<last>/<total|*>`, or null when absent/starred. */
    private fun parseContentRangeTotal(contentRange: String): Long? {
        val slash = contentRange.indexOf('/')
        if (!contentRange.startsWith("bytes ") || slash < 0) return null
        return contentRange.substring(slash + 1).toLongOrNull()?.takeIf { it >= 0 }
    }

    companion object {
        private val prefetchSamples = ConcurrentHashMap<String, PrefetchSample>()

        fun prefetchSample(key: String?): PrefetchSample? = key?.let(prefetchSamples::get)

        private const val TEMP_DIR_NAME = "dl_prefetch"
        private const val TEMP_FILE_PREFIX = "fetch_"
        private const val COPY_BUFFER_SIZE = 256 * 1024
        private const val FETCH_DEADLINE_MS = 10 * 60 * 1000L
    }
}
