/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import dev.citali.lunartune.constants.AudioQuality
import dev.citali.lunartune.constants.AudioQualityKey
import dev.citali.lunartune.constants.PlayerStreamClient
import dev.citali.lunartune.constants.PlayerStreamClientKey
import dev.citali.lunartune.db.MusicDatabase
import dev.citali.lunartune.db.entities.FormatEntity
import dev.citali.lunartune.db.entities.SongEntity
import dev.citali.lunartune.di.DownloadCache
import dev.citali.lunartune.di.PlayerCache
import moe.rukamori.archivetune.innertube.YouTube
import dev.citali.lunartune.utils.AuthScopedCacheValue
import dev.citali.lunartune.utils.NewPipeStreamResolver
import dev.citali.lunartune.utils.StreamClientUtils
import dev.citali.lunartune.utils.YTPlayerUtils
import dev.citali.lunartune.utils.enumPreference
import dev.citali.lunartune.utils.ipVersionDns
import dev.citali.lunartune.utils.isLowDataModeActive
import dev.citali.lunartune.utils.retryWithoutPlaybackLoginContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.IOException
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
    @Inject
    constructor(
        @ApplicationContext context: Context,
        val database: MusicDatabase,
        val databaseProvider: DatabaseProvider,
        @DownloadCache val downloadCache: Cache,
        @PlayerCache val playerCache: Cache,
    ) {
        private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
        private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)
        private val preferredStreamClient by enumPreference(
            context,
            PlayerStreamClientKey,
            PlayerStreamClient.WEB_REMIX,
        )
        private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val songUrlCache = ConcurrentHashMap<String, AuthScopedCacheValue>()
        private val downloadAttempts = ConcurrentHashMap<String, Int>()
        private val autoRetriedSongIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        private val sessionFailedSongIds = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
        private val lastAutoRetryAtMs = AtomicLong(0L)
        private val downloadExecutor = Executors.newFixedThreadPool(MAX_PARALLEL_DOWNLOADS)

        private val mediaOkHttpClient: OkHttpClient by lazy {
            OkHttpClient
                .Builder()
                .proxy(YouTube.streamOkHttpProxy)
                .dns(ipVersionDns(context))
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .dispatcher(
                    okhttp3.Dispatcher().apply {
                        maxRequests = MAX_DOWNLOAD_HTTP_REQUESTS
                        maxRequestsPerHost = MAX_DOWNLOAD_HTTP_REQUESTS_PER_HOST
                    },
                ).connectionPool(
                    ConnectionPool(
                        MAX_IDLE_DOWNLOAD_CONNECTIONS,
                        DOWNLOAD_CONNECTION_KEEP_ALIVE_MINUTES,
                        TimeUnit.MINUTES,
                    ),
                ).addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    val isYouTubeMediaHost =
                        host.endsWith("googlevideo.com") ||
                            host.endsWith("googleusercontent.com") ||
                            host.endsWith("youtube.com") ||
                            host.endsWith("youtube-nocookie.com") ||
                            host.endsWith("ytimg.com")

                    if (!isYouTubeMediaHost) return@addInterceptor chain.proceed(request)

                    val requestProfile = StreamClientUtils.resolveRequestProfile(request.url)
                    val response =
                        chain.proceed(
                            StreamClientUtils
                                .applyRequestProfile(
                                    request.newBuilder(),
                                    requestProfile,
                                ).build(),
                        )
                    if (response.code in STREAM_REFRESH_RESPONSE_CODES) {
                        invalidateResolvedStreamUrl(request.url.toString())
                    }
                    response
                }.build()
        }

        val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())

        private val dataSourceFactory =
            ResolvingDataSource.Factory(
                // Eager whole-file prefetch (4nx3b pattern on our OkHttp stack) instead of
                // ExoPlayer's throttled read loop: full connection speed, then local serve.
                PrefetchDataSource.Factory(context, mediaOkHttpClient),
            ) { dataSpec ->
                val mediaId = dataSpec.key ?: error("No media id")
                val lowDataModeActive = context.isLowDataModeActive()
                val requestedAudioQuality = resolveDownloadAudioQuality(lowDataModeActive)
                val streamCacheKey = buildSongUrlCacheKey(mediaId, requestedAudioQuality)
                val authFingerprint = YouTube.currentPlaybackAuthState().fingerprint
                songUrlCache[streamCacheKey]
                    ?.takeIf {
                        it.isValidFor(
                            authFingerprint = authFingerprint,
                            minimumRemainingMs = YTPlayerUtils.STREAM_URL_EXPIRY_SAFETY_MS,
                        )
                    }?.let {
                        return@Factory dataSpec.withUri(it.url.toUri())
                    }
                val resolveStartMs = SystemClock.elapsedRealtime()
                val playbackData =
                    try {
                        runBlocking(Dispatchers.IO) {
                            withTimeout(DOWNLOAD_RESOLVE_TIMEOUT_MS) {
                                context.retryWithoutPlaybackLoginContext {
                                    YTPlayerUtils.playerResponseForDownload(
                                        mediaId,
                                        audioQuality = requestedAudioQuality,
                                        connectivityManager = connectivityManager,
                                        networkMetered = lowDataModeActive,
                                        preferredStreamClient = preferredStreamClient,
                                    )
                                }
                            }
                        }.getOrThrow()
                    } catch (timeout: TimeoutCancellationException) {
                        throw IOException(
                            "Download stream resolution timed out after " +
                                "${DOWNLOAD_RESOLVE_TIMEOUT_MS / 1000}s for $mediaId",
                            timeout,
                        )
                    }
                Timber.i(
                    "Resolved download stream for %s in %dms (quality=%s)",
                    mediaId,
                    SystemClock.elapsedRealtime() - resolveStartMs,
                    requestedAudioQuality.name,
                )
                persistPlaybackMetadata(mediaId, playbackData)

                val streamUrl = playbackData.streamUrl

                songUrlCache[streamCacheKey] =
                    AuthScopedCacheValue(
                        url = streamUrl,
                        expiresAtMs = System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L),
                        authFingerprint = playbackData.authFingerprint,
                    )
                dataSpec.withUri(streamUrl.toUri())
            }

        val downloadNotificationHelper =
            DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

        val downloadManager: DownloadManager =
            DownloadManager(
                context,
                DefaultDownloadIndex(databaseProvider),
                DefaultDownloaderFactory(
                    CacheDataSource
                        .Factory()
                        .setCache(downloadCache)
                        .setUpstreamDataSourceFactory(dataSourceFactory)
                        .setCacheWriteDataSinkFactory(
                            CacheDataSink.Factory()
                                .setCache(downloadCache)
                                .setBufferSize(DOWNLOAD_WRITE_BUFFER_SIZE)
                                .setFragmentSize(DOWNLOAD_FRAGMENT_SIZE),
                        ).setFlags(FLAG_IGNORE_CACHE_ON_ERROR),
                    downloadExecutor,
                ),
            ).apply {
                maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
                addListener(
                    object : DownloadManager.Listener {
                        override fun onInitialized(downloadManager: DownloadManager) {
                            refreshActiveDownloadSnapshots()
                        }

                        override fun onDownloadChanged(
                            downloadManager: DownloadManager,
                            download: Download,
                            finalException: Exception?,
                        ) {
                            if (finalException != null || download.state == Download.STATE_FAILED) {
                                sessionFailedSongIds.add(download.request.id)
                                Timber.w(
                                    finalException,
                                    "Download %s failed (state=%d failureReason=%d stopReason=%d)",
                                    download.request.id,
                                    download.state,
                                    download.failureReason,
                                    download.stopReason,
                                )
                                songUrlCache.keys.removeIf { it.startsWith("${download.request.id}:") }
                                YTPlayerUtils.invalidateCachedStreamUrls(download.request.id)
                            }
                            trackRetryState(download)
                            downloads.update { map ->
                                map.toMutableMap().apply {
                                    set(download.request.id, download.toProgressSnapshot())
                                }
                            }
                        }

                        override fun onDownloadRemoved(
                            downloadManager: DownloadManager,
                            download: Download,
                        ) {
                            downloadAttempts.remove(download.request.id)
                            autoRetriedSongIds.remove(download.request.id)
                            sessionFailedSongIds.remove(download.request.id)
                            downloads.update { map -> map - download.request.id }
                        }
                    },
                )
            }

        init {
            downloadScope.launch {
                val result = mutableMapOf<String, Download>()
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) {
                        result[cursor.download.request.id] = cursor.download.toProgressSnapshot()
                    }
                }
                downloads.update { current ->
                    result.apply { putAll(current) }
                }
            }
            downloadScope.launch {
                while (isActive) {
                    delay(DOWNLOAD_PROGRESS_REFRESH_INTERVAL_MS)
                    refreshActiveDownloadSnapshots()
                    retryFailedDownloadsWhenIdle()
                }
            }
            downloadScope.launch {
                var previousFingerprint: String? = null
                YouTube.authStateFlow
                    .map { it.fingerprint }
                    .distinctUntilChanged()
                    .collect { fingerprint ->
                        if (previousFingerprint != null && previousFingerprint != fingerprint) {
                            songUrlCache.clear()
                        }
                        previousFingerprint = fingerprint
                    }
            }
        }

        private fun trackRetryState(download: Download) {
            val songId = download.request.id
            when (download.state) {
                Download.STATE_COMPLETED -> {
                    downloadAttempts.remove(songId)
                    autoRetriedSongIds.remove(songId)
                    sessionFailedSongIds.remove(songId)
                }

                Download.STATE_DOWNLOADING -> {
                    // A download that got going again without our retry is a fresh
                    // attempt (manual resume), so give it its retries back.
                    if (!autoRetriedSongIds.remove(songId)) {
                        downloadAttempts.remove(songId)
                    }
                }
            }
        }

        /**
         * Re-queues downloads that failed on their own once nothing else is downloading,
         * so a bulk download finishes instead of leaving a pile of failed songs behind.
         *
         * Manually paused downloads are left alone, the cached stream url is dropped so
         * the retry resolves a fresh one, and every song only gets a few attempts. Only
         * songs that failed while the app was running are retried, so a failed download
         * from an older session is left for the user to resume instead of coming back on
         * every launch.
         */
        private fun retryFailedDownloadsWhenIdle() {
            val snapshot = downloads.value
            if (snapshot.values.any { download -> download.state.isActiveDownloadState() }) return

            val failed =
                snapshot.values.filter { download ->
                    download.state == Download.STATE_FAILED &&
                        download.stopReason == Download.STOP_REASON_NONE &&
                        download.request.id in sessionFailedSongIds
                }
            if (failed.isEmpty()) return

            val now = System.currentTimeMillis()
            if (now - lastAutoRetryAtMs.get() < AUTO_RETRY_COOLDOWN_MS) return
            lastAutoRetryAtMs.set(now)

            failed.forEach { download ->
                val songId = download.request.id
                val attempts = downloadAttempts[songId] ?: 0
                if (attempts >= MAX_AUTO_RETRY_ATTEMPTS) return@forEach
                downloadAttempts[songId] = attempts + 1
                autoRetriedSongIds.add(songId)
                songUrlCache.keys.removeIf { it.startsWith("$songId:") }
                YTPlayerUtils.invalidateCachedStreamUrls(songId)
                runCatching {
                    downloadManager.addDownload(download.request)
                }.onFailure { throwable ->
                    Timber.w(throwable, "Failed to re-queue download $songId")
                }
            }
        }

        private fun Int.isActiveDownloadState(): Boolean =
            this == Download.STATE_QUEUED ||
                this == Download.STATE_DOWNLOADING ||
                this == Download.STATE_RESTARTING

        private fun refreshActiveDownloadSnapshots() {
            val activeDownloads = downloadManager.currentDownloads
            if (activeDownloads.isEmpty()) return
            downloads.update { current ->
                current.toMutableMap().apply {
                    activeDownloads.forEach { download ->
                        set(download.request.id, download.toProgressSnapshot())
                    }
                }
            }
        }

        private fun invalidateResolvedStreamUrl(url: String) {
            songUrlCache.entries.forEach { (cacheKey, cached) ->
                if (cached.url == url && songUrlCache.remove(cacheKey, cached)) {
                    val songId = cacheKey.substringBeforeLast(':')
                    YTPlayerUtils.invalidateCachedStreamUrls(songId)
                    // The URL itself was rejected at fetch time (403/404/410/416), so a
                    // fresh resolve would mint the same shape again: serve this song from
                    // InnerTube for the rest of the session instead of 403-looping.
                    NewPipeStreamResolver.bypassForSession(songId)
                }
            }
        }

        private fun Download.toProgressSnapshot(): Download {
            // ExoPlayer only sees bytes during the local serve phase, so merge the live
            // prefetch counters in: without this every download sits at 0% + 0 B/s
            // until the whole file lands. Monotonic: never moves backwards.
            var mergedBytes = bytesDownloaded.coerceAtLeast(0L)
            var mergedPercent = percentDownloaded.takeIf { it >= 0f } ?: 0f
            if (state == Download.STATE_DOWNLOADING) {
                PrefetchDataSource.prefetchSample(request.id)?.let { sample ->
                    if (sample.bytesWrittenAbsolute > mergedBytes) {
                        mergedBytes = sample.bytesWrittenAbsolute
                    }
                    val total = sample.totalBytes
                    if (total != null && total > 0) {
                        mergedPercent =
                            maxOf(
                                mergedPercent,
                                (sample.bytesWrittenAbsolute * 100f / total).coerceIn(0f, 100f),
                            )
                    }
                }
            }
            val progressSnapshot =
                DownloadProgress().apply {
                    bytesDownloaded = mergedBytes
                    percentDownloaded = mergedPercent
                }
            return Download(
                request,
                state,
                startTimeMs,
                updateTimeMs,
                contentLength,
                stopReason,
                failureReason,
                progressSnapshot,
            )
        }

        fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

        private fun resolveDownloadAudioQuality(lowDataModeActive: Boolean): AudioQuality =
            if (lowDataModeActive) AudioQuality.LOW else audioQuality

        private fun buildSongUrlCacheKey(
            mediaId: String,
            requestedAudioQuality: AudioQuality,
        ): String = "$mediaId:${requestedAudioQuality.name}"

        private fun persistPlaybackMetadata(
            mediaId: String,
            playbackData: YTPlayerUtils.PlaybackData,
        ) {
            downloadScope.launch {
                runCatching {
                    val format = playbackData.format
                    val contentLength = format.contentLength ?: 0L
                    val resolvedCodecs =
                        format.mimeType
                            .substringAfter("codecs=", "")
                            .removeSurrounding("\"")
                            .substringBefore("\"")

                    database.query {
                        upsert(
                            FormatEntity(
                                id = mediaId,
                                itag = format.itag,
                                mimeType = format.mimeType.split(";")[0],
                                codecs = resolvedCodecs,
                                bitrate = format.bitrate,
                                sampleRate = format.audioSampleRate,
                                contentLength = contentLength,
                                loudnessDb = playbackData.audioConfig?.loudnessDb,
                                perceptualLoudnessDb = playbackData.audioConfig?.perceptualLoudnessDb,
                                playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                            ),
                        )

                        val now = LocalDateTime.now()
                        val existing = getSongByIdBlocking(mediaId)?.song
                        val resolvedThumbnailUrl =
                            playbackData.videoDetails
                                ?.thumbnail
                                ?.thumbnails
                                ?.lastOrNull()
                                ?.url
                                ?.takeIf { it.isNotBlank() }

                        val updatedSong =
                            if (existing != null) {
                                existing.copy(
                                    thumbnailUrl = existing.thumbnailUrl?.takeIf { it.isNotBlank() } ?: resolvedThumbnailUrl,
                                    dateDownload = existing.dateDownload ?: now,
                                )
                            } else {
                                SongEntity(
                                    id = mediaId,
                                    title = playbackData.videoDetails?.title ?: "Unknown",
                                    duration = playbackData.videoDetails?.lengthSeconds?.toIntOrNull() ?: 0,
                                    thumbnailUrl = resolvedThumbnailUrl,
                                    dateDownload = now,
                                )
                            }

                        upsert(updatedSong)
                    }
                }
            }
        }

        companion object {
            private const val MAX_PARALLEL_DOWNLOADS = 12
            private const val MAX_IDLE_DOWNLOAD_CONNECTIONS = 24
            private const val MAX_DOWNLOAD_HTTP_REQUESTS = 32
            private const val MAX_DOWNLOAD_HTTP_REQUESTS_PER_HOST = 16
            private const val MAX_AUTO_RETRY_ATTEMPTS = 3
            private const val AUTO_RETRY_COOLDOWN_MS = 3_000L
            private const val DOWNLOAD_RESOLVE_TIMEOUT_MS = 120_000L
            // Silence beyond this is a dead connection, not a slow tail: throttled
            // connections still deliver bursts well within it. Fail fast so the retry
            // resumes the missing tail instead of wedging a download slot for minutes.
            private const val DOWNLOAD_READ_TIMEOUT_SECONDS = 60L
            private const val DOWNLOAD_PROGRESS_REFRESH_INTERVAL_MS = 1_000L
            private const val DOWNLOAD_CONNECTION_KEEP_ALIVE_MINUTES = 10L
            // 4 MB batches saturate local storage writes; 16 MB x 12 parallel (4nx3b
            // verbatim) would transiently hold ~192 MB of heap on low-RAM devices.
            private const val DOWNLOAD_WRITE_BUFFER_SIZE = 4 * 1024 * 1024
            private const val DOWNLOAD_FRAGMENT_SIZE = 128L * 1024 * 1024
            private val STREAM_REFRESH_RESPONSE_CODES = setOf(403, 404, 410, 416)
        }
    }
