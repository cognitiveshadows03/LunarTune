/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.utils

import android.net.ConnectivityManager
import androidx.media3.common.PlaybackException
import dev.citali.lunartune.constants.AudioQuality
import dev.citali.lunartune.constants.PlayerStreamClient
import dev.citali.lunartune.utils.potoken.BotGuardTokenGenerator
import dev.citali.lunartune.utils.potoken.PoTokenResult
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_MUSIC
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.IOS
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.MWEB
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.TVHTML5
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.VISIONOS
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_PRIMARY
import moe.rukamori.archivetune.innertube.models.YouTubeClient.Companion.WEB_REMIX
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.morideobfuscator.CipherRuntimeStatus
import moe.rukamori.archivetune.morideobfuscator.MoriCipherRuntime
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val FAILED_CLIENT_BACKOFF_MS = 10 * 60 * 1000L
    private const val DEFAULT_STREAM_EXPIRE_SECONDS = 300
    private const val MAX_PLAYBACK_DATA_CACHE_ENTRIES = 128
    private const val PLAYBACK_DATA_RESOLUTION_MUTEX_COUNT = 32
    private const val SIGNATURE_TIMESTAMP_TTL_MS = 30 * 60 * 1000L
    private const val LOGGED_IN_POTOKEN_WAIT_MS = 12_000L
    private const val WARM_POTOKEN_WAIT_MS = 5_000L
    private const val FALLBACK_POTOKEN_WAIT_MS = 10_000L
    const val STREAM_URL_EXPIRY_SAFETY_MS = 60_000L
    private val RETRYABLE_STREAM_RESPONSE_CODES = setOf(403, 404, 410, 416)

    private val backgroundTokenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun extractExpireTimestampMsFromUrl(url: String): Long? {
        val expireTimestamp =
            url
                .toHttpUrlOrNull()
                ?.queryParameter("expire")
                ?.toLongOrNull()
                ?: return null
        return expireTimestamp * 1000L
    }

    private fun extractExpireSecondsFromUrl(url: String): Int? {
        val expiresAtMs = extractExpireTimestampMsFromUrl(url) ?: return null
        val remaining = (expiresAtMs - System.currentTimeMillis()) / 1000L
        return remaining.toInt().takeIf { it > 0 }
    }

    private fun resolveExpireSeconds(
        apiExpire: Int?,
        streamUrl: String?,
    ): Int {
        apiExpire?.let { return it }
        streamUrl?.let { url ->
            extractExpireSecondsFromUrl(url)?.let { fromUrl ->
                Timber.tag(logTag).w("Using expire time extracted from stream URL: ${fromUrl}s")
                return fromUrl
            }
        }
        Timber.tag(logTag).w("No expire time available from API or URL, using default: ${DEFAULT_STREAM_EXPIRE_SECONDS}s")
        return DEFAULT_STREAM_EXPIRE_SECONDS
    }

    class LoginRequiredForPlaybackException(
        val videoId: String,
        val targetUrl: String,
        reason: String?,
        cause: Throwable? = null,
    ) : IllegalStateException(reason, cause)

    class InvalidPlaybackLoginContextException(
        val videoId: String,
        val targetUrl: String,
        cause: Throwable,
    ) : IllegalStateException("Invalid YouTube Music playback login context", cause)

    class BotDetectionPlaybackException(
        val videoId: String,
        val clients: Set<String>,
        cause: Throwable? = null,
    ) : IllegalStateException("YouTube playback bot detection blocked all stream clients", cause)

    class BadStreamPlayerResponseException(
        val videoId: String,
        val failedClients: Set<String> = emptySet(),
        cause: Throwable? = null,
    ) : IllegalStateException("YouTube playback stream clients returned no playable response", cause)

    private data class PlaybackGateFailure(
        val clientName: String,
        val status: String,
        val reason: String?,
    )

    /**
     * The main client is used for metadata and initial streams.
     * Do not use other clients for this because it can result in inconsistent metadata.
     * For example other clients can have different normalization targets (loudnessDb).
     */
    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    private val ANONYMOUS_MWEB_CLIENT: YouTubeClient =
        MWEB.copy(
            supportsCookieAuthentication = false,
            friendlyName = "Mobile Web (Anonymous)",
        )

    /**
     * Clients used for fallback streams in case the streams of the main client do not work.
     */
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> =
        arrayOf(
            VISIONOS,
            ANDROID_VR_1_65_10,
            WEB_REMIX,
            WEB,
            MWEB,
            WEB_CREATOR,
        )

    private data class CachedStreamUrl(
        val url: String,
        val expiresAtMs: Long,
    )

    private data class PlaybackDataCacheKey(
        val videoId: String,
        val audioQuality: AudioQuality,
        val networkMetered: Boolean,
        val authFingerprint: String,
    )

    private data class CachedPlaybackData(
        val playbackData: PlaybackData,
        val expiresAtMs: Long,
    )

    private val streamUrlCache = ConcurrentHashMap<String, CachedStreamUrl>()
    private val playbackDataCache = ConcurrentHashMap<PlaybackDataCacheKey, CachedPlaybackData>()
    private val playbackDataResolutionMutexes = Array(PLAYBACK_DATA_RESOLUTION_MUTEX_COUNT) { Mutex() }
    private val youtubeiVisitorMutex = Mutex()
    private val unthrottledResolutionVideoIds = ConcurrentHashMap.newKeySet<String>()
    private val failedStreamClientsUntil = ConcurrentHashMap<String, Long>()

    @Volatile private var cachedSignatureTimestamp: Int? = null
    @Volatile private var cachedSignatureTimestampExpiresAtMs: Long = 0L
    @Volatile private var lastSuccessfulClientKey: String? = null

    fun currentStreamCacheFingerprint(authState: PlaybackAuthState = YouTube.currentPlaybackAuthState()): String =
        authState.streamCacheFingerprint()

    fun clearPlaybackAuthCaches() {
        streamUrlCache.clear()
        playbackDataCache.clear()
        cachedSignatureTimestamp = null
        cachedSignatureTimestampExpiresAtMs = 0L
        unthrottledResolutionVideoIds.clear()
        failedStreamClientsUntil.clear()
        lastSuccessfulClientKey = null
    }

    suspend fun preWarmWebPoTokens(authState: PlaybackAuthState = YouTube.currentPlaybackAuthState()) {
        val contentBinding = authState.youtubeiContentBinding() ?: return
        BotGuardTokenGenerator.preWarm(contentBinding)
    }

    suspend fun recoverFromBadStreamPlayerResponse(videoId: String) {
        BotGuardTokenGenerator.invalidateAll()
        val authState =
            YouTube
                .currentPlaybackAuthState()
                .copy(
                    poToken = null,
                    poTokenGvs = null,
                    poTokenGvsSession = null,
                    poTokenGvsVideoId = null,
                    poTokenPlayer = null,
                    poTokenPlayerVideoId = null,
                    poTokenSubs = null,
                    poTokenSubsVideoId = null,
                    webClientPoTokenEnabled = false,
                ).normalized()
        val refreshedAuthState =
            ensureVisitorDataReady(
                videoId = videoId,
                authState = authState,
                forceRefresh = true,
                reason = "all stream clients failed",
            )
        if (refreshedAuthState.fingerprint != authState.fingerprint) {
            YouTube.authState = refreshedAuthState
        }
        clearPlaybackAuthCaches()
    }

    private suspend fun ensureVisitorDataReady(
        videoId: String,
        authState: PlaybackAuthState,
        forceRefresh: Boolean = false,
        reason: String,
    ): PlaybackAuthState {
        if (!forceRefresh) {
            authState.visitorData
                ?.takeIf { it.isNotBlank() }
                ?.let { return authState }
        }

        return youtubeiVisitorMutex.withLock {
            if (!forceRefresh) {
                YouTube
                    .currentPlaybackAuthState()
                    .visitorData
                    ?.takeIf {
                        it.isNotBlank() &&
                            authState.cookie == YouTube.currentPlaybackAuthState().cookie &&
                            authState.dataSyncId == YouTube.currentPlaybackAuthState().dataSyncId
                    }?.let { existingVisitorData ->
                        return@withLock authState.copy(visitorData = existingVisitorData).normalized()
                    }
            }

            val action = if (forceRefresh) "Refreshing" else "Fetching"
            Timber.tag(logTag).i("%s visitorData for %s (%s)", action, videoId, reason)

            val refreshedVisitorData =
                YouTube
                    .visitorData()
                    .onFailure {
                        Timber.tag(logTag).e(it, "Failed to refresh visitorData for $videoId")
                        reportException(it)
                    }.getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

            if (refreshedVisitorData != null) {
                if (authState.hasSamePlaybackSession(YouTube.currentPlaybackAuthState())) {
                    YouTube.visitorData = refreshedVisitorData
                }
                return@withLock authState.copy(visitorData = refreshedVisitorData).normalized()
            }

            authState
        }
    }

    private suspend fun repairAuthStateAfterBotDetection(
        videoId: String,
        authState: PlaybackAuthState,
        reason: String,
    ): PlaybackAuthState {
        var repairedAuthState = authState

        if (!authState.youtubeiContentBinding().isNullOrBlank()) {
            BotGuardTokenGenerator.invalidateAll()
            repairedAuthState =
                repairedAuthState
                    .copy(
                        poToken = null,
                        poTokenGvs = null,
                        poTokenGvsSession = null,
                        poTokenGvsVideoId = null,
                        poTokenPlayer = null,
                        poTokenPlayerVideoId = null,
                        poTokenSubs = null,
                        poTokenSubsVideoId = null,
                    ).normalized()
        }

        if (authState.hasLoginCookie) {
            val activeChannel =
                YouTube
                    .accountChannels()
                    .onFailure {
                        Timber.tag(logTag).w(it, "Failed to refresh playback account channel for $videoId")
                        reportException(it)
                    }.getOrNull()
                    ?.let { channels ->
                        channels.firstOrNull { it.dataSyncId == authState.dataSyncId }
                            ?: channels.firstOrNull { it.isSelected }
                            ?: channels.firstOrNull()
                    }

            val refreshedDataSyncId = activeChannel?.dataSyncId?.takeIf { it.isNotBlank() }
            if (refreshedDataSyncId != null && refreshedDataSyncId != repairedAuthState.dataSyncId) {
                Timber.tag(logTag).i("Refreshed playback dataSyncId for %s after bot detection", videoId)
                repairedAuthState =
                    repairedAuthState
                        .copy(
                            dataSyncId = refreshedDataSyncId,
                            poToken = null,
                            poTokenGvs = null,
                            poTokenGvsSession = null,
                            poTokenGvsVideoId = null,
                            poTokenPlayer = null,
                            poTokenPlayerVideoId = null,
                            poTokenSubs = null,
                            poTokenSubsVideoId = null,
                        ).normalized()
            }
        }

        if (
            repairedAuthState.visitorData.isNullOrBlank() ||
            !hasWebGvsPoToken(repairedAuthState, videoId)
        ) {
            repairedAuthState =
                ensureVisitorDataReady(
                    videoId = videoId,
                    authState = repairedAuthState,
                    forceRefresh = true,
                    reason = reason,
                )
        }

        if (!hasWebGvsPoToken(repairedAuthState, videoId)) {
            repairedAuthState =
                mintWebPlaybackPoTokens(
                    videoId = videoId,
                    authState = repairedAuthState,
                    maximumWaitMillis = LOGGED_IN_POTOKEN_WAIT_MS,
                )
        }

        if (repairedAuthState.fingerprint != authState.fingerprint) {
            YouTube.authState = repairedAuthState
            clearPlaybackAuthCaches()
        }

        return repairedAuthState
    }

    private fun hasWebGvsPoToken(
        authState: PlaybackAuthState,
        videoId: String,
    ): Boolean =
        authState.webClientPoTokenEnabled &&
            !authState.resolveVideoScopedGvsPoToken(WEB_CREATOR, videoId).isNullOrBlank()

    private suspend fun mintWebPlaybackPoTokens(
        videoId: String,
        authState: PlaybackAuthState,
        maximumWaitMillis: Long? = null,
    ): PlaybackAuthState {
        if (hasWebGvsPoToken(authState, videoId) &&
            !authState.resolveVideoScopedPlayerPoToken(WEB_REMIX, videoId).isNullOrBlank()
        ) {
            return authState
        }
        val contentBinding = authState.youtubeiContentBinding() ?: return authState
        val tokenResult =
            if (maximumWaitMillis != null) {
                BotGuardTokenGenerator.mintToken(videoId, contentBinding, maximumWaitMillis)
            } else {
                BotGuardTokenGenerator.mintToken(videoId, contentBinding)
            } ?: return authState
        return authState.withGeneratedPoTokens(videoId, tokenResult)
    }

    private fun PlaybackAuthState.withGeneratedPoTokens(
        videoId: String,
        tokenResult: PoTokenResult,
    ): PlaybackAuthState {
        // Scope minted GVS & Player PoTokens strictly to the current videoId (per upstream core 133f7ba)
        // so stale or session-only tokens are never attached to another video's player/stream URL.
        val updatedAuthState =
            copy(
                poTokenGvs = tokenResult.playerToken,
                poTokenGvsSession = null,
                poTokenGvsVideoId = videoId,
                poTokenPlayer = tokenResult.playerToken,
                poTokenPlayerVideoId = videoId,
                poTokenSubs = tokenResult.playerToken,
                poTokenSubsVideoId = videoId,
                webClientPoTokenEnabled = true,
            ).normalized()
        val currentAuthState = YouTube.currentPlaybackAuthState()
        if (hasSamePlaybackSession(currentAuthState)) {
            YouTube.authState =
                currentAuthState
                    .copy(
                        poTokenGvs = tokenResult.playerToken,
                        poTokenGvsSession = null,
                        poTokenGvsVideoId = videoId,
                        poTokenPlayer = tokenResult.playerToken,
                        poTokenPlayerVideoId = videoId,
                        poTokenSubs = tokenResult.playerToken,
                        poTokenSubsVideoId = videoId,
                        webClientPoTokenEnabled = true,
                    ).normalized()
        }
        return updatedAuthState
    }

    private fun PlaybackAuthState.hasSamePlaybackSession(other: PlaybackAuthState): Boolean =
        cookie == other.cookie &&
            visitorData == other.visitorData &&
            dataSyncId == other.dataSyncId

    private fun PlaybackAuthState.youtubeiContentBinding(): String? {
        if (!hasLoginCookie) return visitorData?.trim()?.takeIf(String::isNotBlank)
        val normalizedDataSyncId = dataSyncId?.trim()?.takeIf(String::isNotBlank)
        if (normalizedDataSyncId != null) {
            return if ("||" in normalizedDataSyncId) {
                normalizedDataSyncId
            } else {
                "$normalizedDataSyncId||"
            }
        }
        return sessionId
    }

    private fun PlaybackAuthState.streamCacheFingerprint(): String {
        val raw =
            listOf(
                cookie.orEmpty(),
                visitorData.orEmpty(),
                dataSyncId.orEmpty(),
            ).joinToString(separator = "\u0000")
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append(Character.forDigit(value ushr 4, 16))
                append(Character.forDigit(value and 0x0F, 16))
            }
        }
    }

    private fun PlaybackAuthState.resolveVideoScopedPlayerPoToken(
        client: YouTubeClient,
        videoId: String,
    ): String? {
        if (!webClientPoTokenEnabled) return null
        if (!client.useWebPoTokens || !PlaybackAuthState.supportsGvsPoToken(client)) return null
        return poTokenPlayer?.takeIf { poTokenPlayerVideoId == videoId }
            ?: poToken
    }

    private fun PlaybackAuthState.resolveVideoScopedGvsPoToken(
        client: YouTubeClient?,
        videoId: String,
    ): String? {
        if (client != null && (!client.useWebPoTokens || !PlaybackAuthState.supportsGvsPoToken(client))) return null
        if (!webClientPoTokenEnabled) return null
        return poTokenGvs?.takeIf { poTokenGvsVideoId == videoId }
            ?: poToken
    }

    private fun PlaybackAuthState.withoutAccountBoundPlaybackState(): PlaybackAuthState =
        copy(
            dataSyncId = null,
            poToken = null,
            poTokenGvs = null,
            poTokenGvsSession = null,
            poTokenGvsVideoId = null,
            poTokenPlayer = null,
            poTokenPlayerVideoId = null,
            poTokenSubs = null,
            poTokenSubsVideoId = null,
        ).normalized()

    internal fun shouldSkipCipheredWebPlaybackCandidate(
        isWebClient: Boolean,
        isCiphered: Boolean,
        hasGvsPoToken: Boolean,
    ): Boolean =
        isWebClient &&
            isCiphered &&
            !hasGvsPoToken

    internal fun buildStreamCacheKey(
        videoId: String,
        itag: Int,
        client: YouTubeClient,
        authFingerprint: String,
    ): String = "$authFingerprint:$videoId:$itag:${StreamClientUtils.buildClientKey(client)}"

    fun invalidateCachedStreamUrls(videoId: String) {
        val marker = ":$videoId:"
        streamUrlCache.keys.removeIf { it.contains(marker) }
        playbackDataCache.keys.removeIf { it.videoId == videoId }
    }

    fun markStreamUrlSuccessful(url: String) {
        StreamClientUtils
            .resolveRequestProfile(url)
            .clientKey
            .takeIf(String::isNotEmpty)
            ?.let { lastSuccessfulClientKey = it }
    }

    fun isExpiredOrNearExpiredStreamUrl(
        url: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val expiresAtMs = extractExpireTimestampMsFromUrl(url) ?: return false
        return expiresAtMs <= nowMs + STREAM_URL_EXPIRY_SAFETY_MS
    }

    fun markStreamClientFailed(
        videoId: String,
        clientKey: String?,
        httpStatusCode: Int?,
        authFingerprint: String = YouTube.currentPlaybackAuthState().streamCacheFingerprint(),
    ) {
        if (httpStatusCode != null && httpStatusCode !in RETRYABLE_STREAM_RESPONSE_CODES) return
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return
        failedStreamClientsUntil[buildFailedClientKey(videoId, normalizedClientKey, authFingerprint)] =
            System.currentTimeMillis() + FAILED_CLIENT_BACKOFF_MS
        if (lastSuccessfulClientKey == normalizedClientKey) {
            lastSuccessfulClientKey = null
        }
    }

    fun markPreferredClientFailed(
        videoId: String,
        client: PlayerStreamClient,
        httpStatusCode: Int?,
        authFingerprint: String = YouTube.currentPlaybackAuthState().streamCacheFingerprint(),
    ) {
        markStreamClientFailed(videoId, client.name, httpStatusCode, authFingerprint)
    }

    private fun isStreamClientTemporarilyBlocked(
        videoId: String,
        clientKey: String?,
        authFingerprint: String,
    ): Boolean {
        val normalizedClientKey = normalizeStreamClientKey(clientKey)
        if (normalizedClientKey.isEmpty()) return false
        val key = buildFailedClientKey(videoId, normalizedClientKey, authFingerprint)
        val until = failedStreamClientsUntil[key] ?: return false
        if (until <= System.currentTimeMillis()) {
            failedStreamClientsUntil.remove(key)
            return false
        }
        return true
    }

    private fun normalizeStreamClientKey(clientKey: String?): String = StreamClientUtils.normalizeClientKey(clientKey)

    internal fun buildFailedClientKey(
        videoId: String,
        clientKey: String,
        authFingerprint: String,
    ): String = "$authFingerprint:$videoId:${normalizeStreamClientKey(clientKey)}"

    internal fun resolvePreferredPlaybackClient(preferredStreamClient: PlayerStreamClient): YouTubeClient =
        when (preferredStreamClient) {
            PlayerStreamClient.VISION_OS -> VISIONOS
            PlayerStreamClient.ANDROID_VR -> ANDROID_VR_1_65_10
            PlayerStreamClient.WEB_CREATOR -> WEB_CREATOR
            PlayerStreamClient.WEB_REMIX -> WEB_REMIX
            PlayerStreamClient.HI_RES_LOSSLESS -> WEB_REMIX
            PlayerStreamClient.IOS -> IOS
            PlayerStreamClient.TVHTML5 -> TVHTML5
            PlayerStreamClient.ANDROID_MUSIC -> ANDROID_MUSIC
        }

    internal fun buildStreamClientOrder(
        preferredStreamClient: PlayerStreamClient,
        authState: PlaybackAuthState,
        videoId: String? = null,
    ): List<YouTubeClient> {
        val preferredYouTubeClient = resolvePreferredPlaybackClient(preferredStreamClient)
        val lastSuccessfulClient =
            lastSuccessfulClientKey?.let { key ->
                STREAM_FALLBACK_CLIENTS.find { StreamClientUtils.buildClientKey(it) == key }
            }

        val orderedFallbackClients =
            buildList {
                STREAM_FALLBACK_CLIENTS.forEach { client ->
                    add(client)
                    if (authState.hasPlaybackLoginContext && client == MWEB) {
                        add(ANONYMOUS_MWEB_CLIENT)
                    }
                }
            }

        val preferDirectClientsBeforeColdWeb =
            !authState.hasPlaybackLoginContext &&
                preferredYouTubeClient == WEB_REMIX &&
                (videoId == null || !hasWebGvsPoToken(authState, videoId))

        return buildList {
            if (preferDirectClientsBeforeColdWeb) {
                lastSuccessfulClient
                    ?.takeIf { !PlaybackAuthState.supportsGvsPoToken(it) }
                    ?.let { add(it) }
                add(VISIONOS)
                add(ANDROID_VR_1_65_10)
            }
            add(preferredYouTubeClient)
            lastSuccessfulClient
                ?.takeIf { it != preferredYouTubeClient }
                ?.let { add(it) }
            addAll(orderedFallbackClients)
            if (preferredYouTubeClient != MAIN_CLIENT) add(MAIN_CLIENT)
        }.distinct()
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val authFingerprint: String,
    )

    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.WEB_REMIX,
        networkMetered: Boolean? = null,
    ): Result<PlaybackData> {
        val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
        val initialKey =
            buildPlaybackDataCacheKey(
                videoId = videoId,
                audioQuality = audioQuality,
                networkMetered = isMetered,
                authFingerprint = YouTube.currentPlaybackAuthState().streamCacheFingerprint(),
            )
        getCachedPlaybackData(initialKey)?.let { return Result.success(it) }
        val resolutionMutex =
            playbackDataResolutionMutexes[(initialKey.hashCode() and Int.MAX_VALUE) % playbackDataResolutionMutexes.size]
        return resolutionMutex.withLock {
            val currentKey =
                buildPlaybackDataCacheKey(
                    videoId = videoId,
                    audioQuality = audioQuality,
                    networkMetered = isMetered,
                    authFingerprint = YouTube.currentPlaybackAuthState().streamCacheFingerprint(),
                )
            getCachedPlaybackData(currentKey)?.let { return@withLock Result.success(it) }
            resolvePlaybackData(
                videoId = videoId,
                playlistId = playlistId,
                audioQuality = audioQuality,
                connectivityManager = connectivityManager,
                preferredStreamClient = preferredStreamClient,
                networkMetered = isMetered,
            ).onSuccess { playbackData ->
                cachePlaybackData(
                    key = currentKey.copy(authFingerprint = playbackData.authFingerprint),
                    playbackData = playbackData,
                )
            }.also { result ->
                val failure = result.exceptionOrNull()
                if (failure is CancellationException) throw failure
            }
        }
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean,
    ): Result<PlaybackData> =
        runCatching {
            if (videoId in unthrottledResolutionVideoIds) {
                NewPipeStreamResolver.resolve(videoId, audioQuality).onSuccess { playbackData ->
                    Timber.tag(logTag).w(
                        "Resolved %s through NewPipe after a buffering stall (unthrottled URL)",
                        videoId,
                    )
                    return@runCatching playbackData
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    Timber.tag(logTag).w(error, "NewPipe stall recovery failed for %s", videoId)
                }
            }

            val attempts =
                when (audioQuality) {
                    AudioQuality.HIGHEST -> listOf(AudioQuality.HIGHEST, AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.HIGH -> listOf(AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.AUTO -> listOf(AudioQuality.AUTO, AudioQuality.HIGH, AudioQuality.LOW)
                    AudioQuality.LOW -> listOf(AudioQuality.LOW, AudioQuality.HIGH, AudioQuality.AUTO)
                }.distinct()

            var lastError: Throwable? = null
            var didRefreshIpRotationAfterBotDetection = false
            for (attempt in attempts) {
                val attemptResult =
                    runCatching {
                        playerResponseForPlaybackOnce(
                            videoId = videoId,
                            playlistId = playlistId,
                            audioQuality = attempt,
                            connectivityManager = connectivityManager,
                            preferredStreamClient = preferredStreamClient,
                            networkMetered = networkMetered,
                        )
                    }
                if (attemptResult.isSuccess) return@runCatching attemptResult.getOrThrow()
                lastError = attemptResult.exceptionOrNull()
                if (lastError is CancellationException) throw lastError
                if (
                    !didRefreshIpRotationAfterBotDetection &&
                    lastError is BotDetectionPlaybackException &&
                    refreshIpRotationForBotDetection(videoId, lastError)
                ) {
                    didRefreshIpRotationAfterBotDetection = true
                    val rotatedAttemptResult =
                        runCatching {
                            playerResponseForPlaybackOnce(
                                videoId = videoId,
                                playlistId = playlistId,
                                audioQuality = attempt,
                                connectivityManager = connectivityManager,
                                preferredStreamClient = preferredStreamClient,
                                networkMetered = networkMetered,
                            )
                        }
                    if (rotatedAttemptResult.isSuccess) return@runCatching rotatedAttemptResult.getOrThrow()
                    lastError = rotatedAttemptResult.exceptionOrNull()
                    if (lastError is CancellationException) throw lastError
                }
            }
            throw lastError ?: IllegalStateException("Failed to resolve stream")
        }

    /**
     * Mark a video so the next resolution goes through [NewPipeStreamResolver] first.
     */
    fun requestUnthrottledResolution(videoId: String) {
        if (unthrottledResolutionVideoIds.add(videoId)) {
            Timber.tag(logTag).w("Next resolution for %s will use unthrottled URLs", videoId)
        }
    }

    private fun buildPlaybackDataCacheKey(
        videoId: String,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
        authFingerprint: String,
    ): PlaybackDataCacheKey =
        PlaybackDataCacheKey(
            videoId = videoId,
            audioQuality = audioQuality,
            networkMetered = networkMetered,
            authFingerprint = authFingerprint,
        )

    private fun getCachedPlaybackData(key: PlaybackDataCacheKey): PlaybackData? {
        val cached = playbackDataCache[key] ?: return null
        val now = System.currentTimeMillis()
        if (
            cached.expiresAtMs <= now + STREAM_URL_EXPIRY_SAFETY_MS ||
            isExpiredOrNearExpiredStreamUrl(cached.playbackData.streamUrl, now)
        ) {
            playbackDataCache.remove(key, cached)
            return null
        }
        return cached.playbackData
    }

    private fun cachePlaybackData(
        key: PlaybackDataCacheKey,
        playbackData: PlaybackData,
    ) {
        val now = System.currentTimeMillis()
        val expiresAtMs = now + playbackData.streamExpiresInSeconds.coerceAtLeast(1) * 1000L
        playbackDataCache[key] = CachedPlaybackData(playbackData, expiresAtMs)
        if (playbackDataCache.size <= MAX_PLAYBACK_DATA_CACHE_ENTRIES) return
        playbackDataCache.entries.removeIf { (_, cached) ->
            cached.expiresAtMs <= now + STREAM_URL_EXPIRY_SAFETY_MS
        }
        while (playbackDataCache.size > MAX_PLAYBACK_DATA_CACHE_ENTRIES) {
            val oldest = playbackDataCache.entries.minByOrNull { it.value.expiresAtMs } ?: break
            playbackDataCache.remove(oldest.key, oldest.value)
        }
    }

    suspend fun playerResponseForDownload(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        networkMetered: Boolean? = null,
        preferredStreamClient: PlayerStreamClient = PlayerStreamClient.WEB_REMIX,
    ): Result<PlaybackData> =
        runCatching {
            Timber.tag(logTag).i("Fetching download response for videoId: $videoId, playlistId: $playlistId")

            val newPipeResult = NewPipeStreamResolver.resolve(videoId, audioQuality)
            if (newPipeResult.isSuccess) return@runCatching newPipeResult.getOrThrow()
            Timber.tag(logTag).w(
                newPipeResult.exceptionOrNull(),
                "NewPipe primary resolution failed for %s, falling back to InnerTube clients",
                videoId,
            )

            var lastError: Throwable? = null

            val downloadClients =
                listOf(preferredStreamClient) +
                    downloadPreferredStreamClientAttempts.filterNot { it == preferredStreamClient }

            for (downloadClient in downloadClients) {
                val attemptResult =
                    playerResponseForPlayback(
                        videoId = videoId,
                        playlistId = playlistId,
                        audioQuality = audioQuality,
                        connectivityManager = connectivityManager,
                        preferredStreamClient = downloadClient,
                        networkMetered = networkMetered,
                    )
                if (attemptResult.isSuccess) return@runCatching attemptResult.getOrThrow()
                lastError = attemptResult.exceptionOrNull()
                if (lastError is CancellationException) throw lastError
            }

            throw lastError ?: IllegalStateException("Failed to fetch download response for $videoId")
        }

    private val downloadPreferredStreamClientAttempts: List<PlayerStreamClient> =
        listOf(
            PlayerStreamClient.VISION_OS,
            PlayerStreamClient.ANDROID_VR,
            PlayerStreamClient.WEB_REMIX,
            PlayerStreamClient.HI_RES_LOSSLESS,
            PlayerStreamClient.TVHTML5,
            PlayerStreamClient.ANDROID_MUSIC,
        )

    private suspend fun refreshIpRotationForBotDetection(
        videoId: String,
        failure: BotDetectionPlaybackException?,
    ): Boolean {
        if (failure == null) return false
        if (YouTube.ipRotationActiveCount.value <= 0) return false

        return runCatching {
            Timber.tag(logTag).w(
                failure,
                "Refreshing IP rotation after YouTube bot detection blocked playback for %s",
                videoId,
            )
            YouTube.refreshIpRotation()
            clearPlaybackAuthCaches()
        }.onFailure {
            Timber.tag(logTag).w(it, "Failed to refresh IP rotation after bot detection for %s", videoId)
            reportException(it)
        }.isSuccess
    }

    private suspend fun playerResponseForPlaybackOnce(
        videoId: String,
        playlistId: String?,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        preferredStreamClient: PlayerStreamClient,
        networkMetered: Boolean?,
    ): PlaybackData {
        Timber.tag(logTag).i("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).v("Signature timestamp: $signatureTimestamp")

        var authState = YouTube.currentPlaybackAuthState()
        val hasLoginCookie = authState.hasLoginCookie
        var canUseLoggedInPlayback = authState.hasPlaybackLoginContext
        if (!canUseLoggedInPlayback) {
            if (hasLoginCookie) {
                Timber.tag(logTag).w(
                    "Ignoring incomplete login context for %s because dataSyncId is missing; falling back to visitorData playback",
                    videoId,
                )
            }
            authState =
                ensureVisitorDataReady(
                    videoId = videoId,
                    authState = authState,
                    reason = if (hasLoginCookie) "cookie-only playback fallback" else "anonymous playback bootstrap",
                )
        }
        val contentBinding = authState.youtubeiContentBinding()
        if (!contentBinding.isNullOrBlank()) {
            if (canUseLoggedInPlayback || BotGuardTokenGenerator.isWarmForSession(contentBinding)) {
                try {
                    val waitMs = if (canUseLoggedInPlayback) LOGGED_IN_POTOKEN_WAIT_MS else WARM_POTOKEN_WAIT_MS
                    authState = mintWebPlaybackPoTokens(videoId, authState, maximumWaitMillis = waitMs)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    Timber.tag(logTag).w(e, "PoToken generation failed for playback request")
                }
            } else {
                // Cold signed-out start: warm WebView BotGuard in background without blocking
                // direct VISIONOS / ANDROID_VR stream resolution for the first song.
                backgroundTokenScope.launch {
                    BotGuardTokenGenerator.preWarm(contentBinding)
                }
            }
        }
        val authStatus =
            when {
                canUseLoggedInPlayback -> "Logged in"
                hasLoginCookie -> "Cookie-only"
                else -> "Not logged in"
            }
        Timber.tag(logTag).v("Session authentication status: $authStatus (binding=${contentBinding.orEmpty()})")

        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var streamClientUsed: YouTubeClient? = null
        var didRepairAuthAfterBotDetection = false
        var didAttemptGvsPoTokenRecovery = false
        val failedPlayerClients = linkedSetOf<String>()
        val playerRequestFailures = mutableListOf<Throwable>()

        var metadataClient =
            if (authState.hasPlaybackLoginContext && !hasWebGvsPoToken(authState, videoId)) {
                WEB_PRIMARY
            } else {
                MAIN_CLIENT
            }

        Timber.tag(logTag).i("Fetching metadata response using client: ${metadataClient.clientName}")

        var metadataPoToken = authState.resolveVideoScopedPlayerPoToken(metadataClient, videoId = videoId)

        var metadataResult =
            YouTube.player(
                videoId = videoId,
                playlistId = playlistId,
                client = metadataClient,
                signatureTimestamp = signatureTimestamp,
                poToken = metadataPoToken,
                setLogin = canUseLoggedInPlayback && metadataClient.supportsCookieAuthentication,
                authState = authState,
            )
        val metadataFailure = metadataResult.exceptionOrNull()
        if (metadataFailure != null && canUseLoggedInPlayback && metadataFailure.isInvalidPlaybackLoginContextFailure()) {
            Timber.tag(logTag).w(
                metadataFailure,
                "Logged-in playback context is stale for %s; retrying metadata with visitor playback",
                videoId,
            )
            authState =
                ensureVisitorDataReady(
                    videoId = videoId,
                    authState = authState.withoutAccountBoundPlaybackState(),
                    forceRefresh = true,
                    reason = "stale logged-in playback context",
                )
            canUseLoggedInPlayback = false
            clearPlaybackAuthCaches()

            val newBinding = authState.youtubeiContentBinding()
            if (newBinding != null && BotGuardTokenGenerator.isWarmForSession(newBinding)) {
                try {
                    authState = mintWebPlaybackPoTokens(videoId, authState, maximumWaitMillis = WARM_POTOKEN_WAIT_MS)
                    metadataPoToken = authState.resolveVideoScopedPlayerPoToken(metadataClient, videoId = videoId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    Timber.tag(logTag).w(e, "PoToken generation failed for metadata retry request")
                }
            }

            metadataResult =
                YouTube.player(
                    videoId = videoId,
                    playlistId = playlistId,
                    client = metadataClient,
                    signatureTimestamp = signatureTimestamp,
                    poToken = metadataPoToken,
                    setLogin = canUseLoggedInPlayback && metadataClient.supportsCookieAuthentication,
                    authState = authState,
                )
        }
        if (metadataResult.exceptionOrNull()?.isForbiddenPlayerRequest() == true) {
            for (fallbackClient in buildStreamClientOrder(preferredStreamClient, authState, videoId)) {
                if (fallbackClient == metadataClient) continue
                val useCookieAuthentication = canUseLoggedInPlayback && fallbackClient.supportsCookieAuthentication
                if (fallbackClient.loginRequired && !useCookieAuthentication) continue

                Timber.tag(logTag).i(
                    "Metadata request was forbidden for %s; trying %s",
                    describeClient(metadataClient),
                    describeClient(fallbackClient),
                )
                val fallbackResult =
                    YouTube.player(
                        videoId = videoId,
                        playlistId = playlistId,
                        client = fallbackClient,
                        signatureTimestamp = signatureTimestamp,
                        poToken = authState.resolveVideoScopedPlayerPoToken(fallbackClient, videoId = videoId),
                        setLogin = useCookieAuthentication,
                        authState = authState,
                    )
                val fallbackFailure = fallbackResult.exceptionOrNull()
                if (fallbackFailure != null) {
                    if (fallbackFailure is CancellationException) throw fallbackFailure
                    failedPlayerClients += describeClient(fallbackClient)
                    playerRequestFailures += fallbackFailure
                    Timber.tag(logTag).w(
                        fallbackFailure,
                        "Metadata fallback player request failed for %s via %s",
                        videoId,
                        describeClient(fallbackClient),
                    )
                }
                val fallbackResponse = fallbackResult.getOrNull() ?: continue
                if (fallbackResponse.playabilityStatus.status != "OK") continue

                metadataClient = fallbackClient
                metadataResult = fallbackResult
                break
            }
        }
        var metadataPlayerResponse = metadataResult.getPlaybackPlayerResponseOrThrow(videoId, authState)
        var expectedDurationMs =
            metadataPlayerResponse.videoDetails
                ?.lengthSeconds
                ?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.times(1000L)

        val streamClients =
            buildStreamClientOrder(preferredStreamClient, authState, videoId).filterNot { client ->
                val blocked =
                    isStreamClientTemporarilyBlocked(
                        videoId = videoId,
                        clientKey = StreamClientUtils.buildClientKey(client),
                        authFingerprint = authState.streamCacheFingerprint(),
                    )
                if (blocked) {
                    Timber.tag(logTag).w("Temporarily blocked stream client for $videoId: ${describeClient(client)}")
                }
                blocked
            }

        val botDetectedClients = mutableSetOf<String>()
        var gateFailure: PlaybackGateFailure? = null

        fun shouldUseCookieAuthentication(client: YouTubeClient): Boolean = canUseLoggedInPlayback && client.supportsCookieAuthentication

        fun authMode(usesCookieAuthentication: Boolean): String = if (usesCookieAuthentication) "logged-in" else "visitor"

        for ((index, candidateClient) in streamClients.withIndex()) {
            var client = candidateClient
            var requestUsesCookieAuthentication = shouldUseCookieAuthentication(client)
            format = null
            streamUrl = null
            streamClientUsed = null
            streamExpiresInSeconds = null
            streamPlayerResponse = null

            Timber.tag(logTag).v(
                "Trying ${if (client == MAIN_CLIENT) "MAIN_CLIENT" else "fallback client"} ${index + 1}/${streamClients.size}: ${describeClient(
                    client,
                )}",
            )

            if (client != MAIN_CLIENT && client.loginRequired && !requestUsesCookieAuthentication) {
                Timber.tag(logTag).i(
                    "Skipping client ${describeClient(client)} - requires compatible cookie authentication",
                )
                continue
            }

            val hadPlayerPoTokenBeforeMint =
                !authState.resolveVideoScopedPlayerPoToken(client, videoId).isNullOrBlank()
            if (
                !didAttemptGvsPoTokenRecovery &&
                    client != WEB_PRIMARY &&
                    !authState.youtubeiContentBinding().isNullOrBlank() &&
                    PlaybackAuthState.supportsGvsPoToken(client) &&
                    authState.resolveVideoScopedGvsPoToken(client, videoId).isNullOrBlank()
            ) {
                didAttemptGvsPoTokenRecovery = true
                try {
                    authState =
                        mintWebPlaybackPoTokens(
                            videoId = videoId,
                            authState = authState,
                            maximumWaitMillis = FALLBACK_POTOKEN_WAIT_MS,
                        )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    Timber.tag(logTag).w(e, "PoToken generation failed for stream client ${client.clientName}")
                }
            }
            val mintedFreshPlayerPoToken =
                !hadPlayerPoTokenBeforeMint &&
                    !authState.resolveVideoScopedPlayerPoToken(client, videoId).isNullOrBlank()

            streamPlayerResponse =
                if (client == metadataClient && !mintedFreshPlayerPoToken) {
                    metadataPlayerResponse
                } else {
                    Timber.tag(logTag).i("Fetching player response for stream client: ${describeClient(client)}")
                    YouTube
                        .player(
                            videoId = videoId,
                            playlistId = playlistId,
                            client = client,
                            signatureTimestamp = signatureTimestamp,
                            poToken = authState.resolveVideoScopedPlayerPoToken(client, videoId = videoId),
                            setLogin = requestUsesCookieAuthentication,
                            authState = authState,
                        ).also { result ->
                            result.exceptionOrNull()?.let { failure ->
                                if (failure is CancellationException) throw failure
                                failedPlayerClients += describeClient(client)
                                playerRequestFailures += failure
                                Timber.tag(logTag).w(
                                    failure,
                                    "Stream player request failed for %s via %s",
                                    videoId,
                                    describeClient(client),
                                )
                            }
                        }.getPlaybackPlayerResponseOrNull(videoId, authState)
                }

            if (streamPlayerResponse == null) continue

            var playabilityStatus = streamPlayerResponse.playabilityStatus
            if (playabilityStatus.status != "OK") {
                var reason = playabilityStatus.reason.orEmpty()
                var isLoginRecovery = isLoginRecoveryResponse(playabilityStatus.status, reason)
                var isBotDetection = isBotDetectionError(reason)

                if (isBotDetection && !didRepairAuthAfterBotDetection) {
                    val repairedAuthState =
                        repairAuthStateAfterBotDetection(
                            videoId = videoId,
                            authState = authState,
                            reason = "bot-detection recovery on ${client.clientName}",
                        )
                    val shouldUseWebRemix =
                        repairedAuthState.hasPlaybackLoginContext &&
                            hasWebGvsPoToken(repairedAuthState, videoId) &&
                            client != WEB_REMIX &&
                            WEB_REMIX in streamClients

                    if (repairedAuthState.fingerprint != authState.fingerprint || shouldUseWebRemix) {
                        authState = repairedAuthState
                        canUseLoggedInPlayback = authState.hasPlaybackLoginContext
                        client = if (shouldUseWebRemix) WEB_REMIX else client
                        requestUsesCookieAuthentication = shouldUseCookieAuthentication(client)
                        didRepairAuthAfterBotDetection = true
                        Timber.tag(logTag).i(
                            "Retrying %s for %s after repairing playback auth",
                            describeClient(client),
                            videoId,
                        )
                        streamPlayerResponse =
                            YouTube
                                .player(
                                    videoId = videoId,
                                    playlistId = playlistId,
                                    client = client,
                                    signatureTimestamp = signatureTimestamp,
                                    poToken = authState.resolveVideoScopedPlayerPoToken(client, videoId = videoId),
                                    setLogin = requestUsesCookieAuthentication,
                                    authState = authState,
                                ).getPlaybackPlayerResponseOrNull(videoId, authState)

                        if (streamPlayerResponse == null) continue

                        playabilityStatus = streamPlayerResponse.playabilityStatus
                        reason = playabilityStatus.reason.orEmpty()
                        isLoginRecovery = isLoginRecoveryResponse(playabilityStatus.status, reason)
                        isBotDetection = isBotDetectionError(reason)
                    }
                }

                if (playabilityStatus.status == "OK") {
                    if (client == metadataClient) {
                        metadataPlayerResponse = streamPlayerResponse
                        expectedDurationMs =
                            metadataPlayerResponse.videoDetails
                                ?.lengthSeconds
                                ?.toLongOrNull()
                                ?.takeIf { it > 0 }
                                ?.times(1000L)
                    }
                    Timber.tag(logTag).i(
                        "Recovered playback with %s after auth repair",
                        describeClient(client),
                    )
                } else {
                    if (isLoginRecovery && canUseLoggedInPlayback && !requestUsesCookieAuthentication) {
                        markStreamClientFailed(
                            videoId = videoId,
                            clientKey = StreamClientUtils.buildClientKey(client),
                            httpStatusCode = null,
                            authFingerprint = authState.streamCacheFingerprint(),
                        )
                        Timber.tag(logTag).v(
                            "Skipping visitor-only client %s because it rejected anonymous playback",
                            describeClient(client),
                        )
                        continue
                    }
                    val statusMessage =
                        "Player response status not OK for ${describeClient(client)} " +
                            "[auth=${authMode(requestUsesCookieAuthentication)}]: " +
                            "${playabilityStatus.status}, reason: $reason, loginRecovery: $isLoginRecovery, botDetection: $isBotDetection"
                    if (isLoginRecovery) {
                        Timber.tag(logTag).i(statusMessage)
                    } else {
                        Timber.tag(logTag).w(statusMessage)
                    }
                    if (isLoginRecovery) {
                        gateFailure =
                            PlaybackGateFailure(
                                clientName = describeClient(client),
                                status = playabilityStatus.status,
                                reason = playabilityStatus.reason,
                            )
                    } else if (isBotDetection) {
                        botDetectedClients.add(describeClient(client))
                    }
                    continue
                }
            }

            val isMetered = networkMetered ?: connectivityManager.isActiveNetworkMetered
            val candidates =
                selectAudioFormatCandidates(
                    streamPlayerResponse,
                    audioQuality,
                    isMetered,
                )

            if (candidates.isEmpty()) continue

            var selectedFormat: PlayerResponse.StreamingData.Format? = null
            var selectedUrl: String? = null
            val cacheFingerprint = authState.streamCacheFingerprint()

            for (candidate in candidates) {
                if (canUseLoggedInPlayback && expectedDurationMs != null && isLikelyPreview(candidate, expectedDurationMs)) continue
                if (shouldSkipCipheredWebCandidate(client, candidate, videoId, authState)) continue
                val cacheKey = buildStreamCacheKey(videoId, candidate.itag, client, cacheFingerprint)
                val cached = streamUrlCache[cacheKey]
                val candidateResult =
                    if (cached != null && cached.expiresAtMs > System.currentTimeMillis() + STREAM_URL_EXPIRY_SAFETY_MS) {
                        Result.success(cached.url)
                    } else {
                        findUrl(candidate, videoId, client, authState)
                    }
                val candidateFailure = candidateResult.exceptionOrNull()
                if (candidateFailure != null) {
                    if (candidateFailure is CancellationException) throw candidateFailure
                    if (candidateFailure.isJavaScriptPlayerExtractorFailure()) {
                        Timber.tag(logTag).w(
                            "Skipping remaining ciphered formats for %s because JavaScript decipher is unavailable: %s",
                            describeClient(client),
                            candidateFailure.message,
                        )
                        markStreamClientFailed(
                            videoId = videoId,
                            clientKey = StreamClientUtils.buildClientKey(client),
                            httpStatusCode = null,
                            authFingerprint = cacheFingerprint,
                        )
                        break
                    }
                    Timber.tag(logTag).e(candidateFailure, "Failed to get stream URL")
                    reportException(candidateFailure)
                    continue
                }
                val candidateUrl = candidateResult.getOrThrow()
                selectedFormat = candidate
                selectedUrl = candidateUrl
                break
            }

            if (selectedFormat == null || selectedUrl == null) {
                Timber.tag(logTag).w(
                    "No playable stream candidate resolved for %s at quality %s after checking %d formats",
                    describeClient(client),
                    audioQuality,
                    candidates.size,
                )
                continue
            }

            format = selectedFormat
            streamUrl = selectedUrl
            streamClientUsed = client
            streamExpiresInSeconds =
                resolveExpireSeconds(
                    apiExpire = streamPlayerResponse.streamingData?.expiresInSeconds,
                    streamUrl = selectedUrl,
                )

            Timber.tag(logTag).i("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")
            Timber.tag(logTag).v("Stream expires in: $streamExpiresInSeconds seconds")
            break
        }

        if (streamPlayerResponse == null) {
            gateFailure?.let { failure ->
                Timber.tag(logTag).w(
                    "Playback requires login recovery for $videoId via ${failure.clientName} (${failure.status}): ${failure.reason.orEmpty()}",
                )
                throw LoginRequiredForPlaybackException(
                    videoId = videoId,
                    targetUrl = "https://music.youtube.com/watch?v=$videoId",
                    reason = failure.reason,
                )
            }
            if (botDetectedClients.isNotEmpty()) {
                Timber.tag(logTag).e("Bot detection triggered on clients: $botDetectedClients - all clients failed")
                throw BotDetectionPlaybackException(
                    videoId = videoId,
                    clients = botDetectedClients.toSet(),
                )
            }
            Timber.tag(logTag).e(
                "Bad stream player response - all clients failed: $failedPlayerClients",
            )
            throw BadStreamPlayerResponseException(
                videoId = videoId,
                failedClients = failedPlayerClients,
                cause = playerRequestFailures.firstOrNull(),
            )
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            if (isLoginRecoveryResponse(streamPlayerResponse.playabilityStatus.status, errorReason.orEmpty())) {
                Timber.tag(logTag).w("Playback requires login recovery for $videoId: $errorReason")
                throw LoginRequiredForPlaybackException(
                    videoId = videoId,
                    targetUrl = "https://music.youtube.com/watch?v=$videoId",
                    reason = errorReason,
                )
            }
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR,
            )
        }

        if (streamExpiresInSeconds == null) {
            streamExpiresInSeconds =
                resolveExpireSeconds(
                    apiExpire = null,
                    streamUrl = streamUrl,
                )
        }

        if (format == null) {
            Timber
                .tag(
                    logTag,
                ).e(
                    "Could not find suitable format for quality: $audioQuality. Available formats from last client: ${streamPlayerResponse.streamingData?.adaptiveFormats?.filter {
                        it.isAudio
                    }?.map { "${it.mimeType} @ ${it.bitrate}bps (itag: ${it.itag})" }}",
                )
            throw Exception("Could not find format for quality: $audioQuality")
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url for format: ${format.mimeType}, itag: ${format.itag}")
            throw Exception("Could not find stream url")
        }

        Timber.tag(logTag).i("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")

        val resolvedStreamClient =
            requireNotNull(streamClientUsed) {
                "No resolved stream client for validated playback URL"
            }

        val cacheFingerprint = authState.streamCacheFingerprint()
        streamUrlCache[buildStreamCacheKey(videoId, format.itag, resolvedStreamClient, cacheFingerprint)] =
            CachedStreamUrl(
                url = streamUrl,
                expiresAtMs = System.currentTimeMillis() + (streamExpiresInSeconds * 1000L),
            )

        return PlaybackData(
            metadataPlayerResponse.playerConfig?.audioConfig,
            metadataPlayerResponse.videoDetails,
            metadataPlayerResponse.playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
            cacheFingerprint,
        )
    }

    /**
     * Simple player response intended to use for metadata only.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
        authState: PlaybackAuthState = YouTube.currentPlaybackAuthState(),
    ): Result<PlayerResponse> {
        Timber.tag(logTag).i("Fetching metadata player response for videoId: $videoId")

        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        var resolvedAuthState = authState
        val contentBinding = resolvedAuthState.youtubeiContentBinding()

        if (MAIN_CLIENT.useWebPoTokens && contentBinding != null && BotGuardTokenGenerator.isWarmForSession(contentBinding)) {
            try {
                resolvedAuthState = mintWebPlaybackPoTokens(videoId, resolvedAuthState, maximumWaitMillis = WARM_POTOKEN_WAIT_MS)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                Timber.tag(logTag).w(e, "PoToken generation failed for metadata request")
            }
        }

        return YouTube
            .player(
                videoId = videoId,
                playlistId = playlistId,
                client = MAIN_CLIENT,
                signatureTimestamp = signatureTimestamp,
                poToken = resolvedAuthState.resolveVideoScopedPlayerPoToken(MAIN_CLIENT, videoId = videoId),
                setLogin = true,
                authState = resolvedAuthState,
            ).onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e(it, "Failed to fetch metadata") }
    }

    private fun selectAudioFormatCandidates(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        networkMetered: Boolean,
    ): List<PlayerResponse.StreamingData.Format> {
        Timber.tag(logTag).i("Finding format with audioQuality: $audioQuality, network metered: $networkMetered")

        val audioFormats =
            playerResponse.streamingData
                ?.adaptiveFormats
                ?.asSequence()
                ?.filter { it.isAudio && it.bitrate > 0 }
                ?.filter { it.url != null || it.signatureCipher != null || it.cipher != null }
                ?.toList()
                .orEmpty()

        if (audioFormats.isEmpty()) return emptyList()

        val effectiveQuality =
            when (audioQuality) {
                AudioQuality.AUTO -> if (networkMetered) AudioQuality.HIGH else AudioQuality.HIGHEST
                else -> audioQuality
            }

        val targetBitrateBps =
            when (effectiveQuality) {
                AudioQuality.LOW -> 70_000
                AudioQuality.HIGH -> 160_000
                AudioQuality.HIGHEST -> 320_000
                AudioQuality.AUTO -> null
            }

        val preferHigher =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenByDescending { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val preferLowerAboveTarget =
            compareByDescending<PlayerResponse.StreamingData.Format> { it.url != null }
                .thenBy { it.bitrate }
                .thenByDescending { codecRank(extractCodec(it.mimeType)) }
                .thenByDescending { it.audioSampleRate ?: 0 }

        val candidates =
            when {
                targetBitrateBps == null || effectiveQuality == AudioQuality.HIGHEST -> {
                    audioFormats.sortedWith(preferHigher)
                }

                else -> {
                    val preferred =
                        audioFormats
                            .filter { it.bitrate <= targetBitrateBps }
                            .sortedWith(preferHigher)
                    val fallback =
                        audioFormats
                            .filter { it.bitrate > targetBitrateBps }
                            .sortedWith(preferLowerAboveTarget)

                    preferred + fallback
                }
            }

        Timber
            .tag(logTag)
            .v(
                "Available audio formats: ${
                    candidates.take(12).map {
                        val codec = extractCodec(it.mimeType)
                        val direct = if (it.url != null) "direct" else "cipher"
                        "${it.mimeType} ($direct, codec=${codec ?: "unknown"}) @ ${it.bitrate}bps"
                    }
                }",
            )

        return candidates
    }

    private fun extractCodec(mimeType: String): String? {
        val match = Regex("""codecs="([^"]+)"""").find(mimeType) ?: return null
        return match.groupValues
            .getOrNull(1)
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
    }

    private fun isCipheredFormat(format: PlayerResponse.StreamingData.Format): Boolean =
        format.url == null && (format.signatureCipher != null || format.cipher != null)

    private fun shouldSkipCipheredWebCandidate(
        client: YouTubeClient,
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        authState: PlaybackAuthState,
    ): Boolean {
        val isCiphered = isCipheredFormat(format)
        val isWebClient = PlaybackAuthState.supportsGvsPoToken(client)
        val hasGvsPoToken = !authState.resolveVideoScopedGvsPoToken(client, videoId).isNullOrBlank()
        if (
            !shouldSkipCipheredWebPlaybackCandidate(
                isWebClient = isWebClient,
                isCiphered = isCiphered,
                hasGvsPoToken = hasGvsPoToken,
            )
        ) {
            return false
        }

        Timber.tag(logTag).w(
            "Skipping ciphered %s stream candidate because no video-scoped GVS PoToken is available",
            client.clientName,
        )
        return true
    }

    private fun codecRank(codec: String?): Int =
        when {
            codec.isNullOrBlank() -> 0
            codec.contains("opus", ignoreCase = true) -> 3
            codec.contains("mp4a", ignoreCase = true) -> 2
            else -> 1
        }

    private fun isLikelyPreview(
        format: PlayerResponse.StreamingData.Format,
        expectedDurationMs: Long,
    ): Boolean {
        val approx = format.approxDurationMs?.toLongOrNull() ?: return false
        if (expectedDurationMs < 90_000L) return false
        return approx in 1L..(minOf(90_000L, (expectedDurationMs * 9L) / 10L))
    }

    private suspend fun getSignatureTimestampOrNull(videoId: String): Int? {
        val now = System.currentTimeMillis()
        cachedSignatureTimestamp
            ?.takeIf { now < cachedSignatureTimestampExpiresAtMs }
            ?.let { return it }

        Timber.tag(logTag).i("Getting signature timestamp for videoId: $videoId")
        NewPipeBootstrap.ensureInitialized()
        return withContext(Dispatchers.IO) {
            runCatching {
                withJavaScriptPlayerCacheRecovery {
                    YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
                }
            }.recoverCatching { primaryFailure ->
                if (MoriCipherRuntime.snapshot.value.status == CipherRuntimeStatus.READY) {
                    MoriCipherRuntime.signatureTimestamp(videoId).getOrNull() ?: throw primaryFailure
                } else {
                    throw primaryFailure
                }
            }
        }.onSuccess { timestamp ->
            Timber.tag(logTag).i("Signature timestamp obtained: $timestamp")
            cachedSignatureTimestamp = timestamp
            cachedSignatureTimestampExpiresAtMs = System.currentTimeMillis() + SIGNATURE_TIMESTAMP_TTL_MS
        }.onFailure {
            Timber.tag(logTag).e(it, "Failed to get signature timestamp")
            reportException(it)
        }.getOrNull()
    }

    /**
     * Resolves a playable stream URL using NewPipeExtractor v0.26.5's JavaScript player
     * manager (without blocking on Rhino recompilation or mutating signed `cver` params),
     * and attaches video-scoped GVS PoTokens for Web clients.
     */
    private suspend fun findUrl(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        client: YouTubeClient? = null,
        authState: PlaybackAuthState,
    ): Result<String> {
        Timber.tag(logTag).i("Finding stream URL for format: ${format.mimeType}, videoId: $videoId")
        NewPipeBootstrap.ensureInitialized()
        return withContext(Dispatchers.IO) {
            try {
                val directUrl = format.url
                if (directUrl != null) {
                    val resolvedDirectUrl =
                        if (directUrl.toHttpUrlOrNull()?.queryParameter("n")?.isNotBlank() == true) {
                            deobfuscateNParameter(videoId, directUrl)
                        } else {
                            directUrl
                        }
                    return@withContext Result.success(
                        applyGvsPoToken(
                            url = resolvedDirectUrl,
                            client = client,
                            videoId = videoId,
                            authState = authState,
                        ),
                    )
                }

                val cipherString =
                    format.signatureCipher ?: format.cipher
                        ?: return@withContext Result.failure(ParsingException("Could not find format url"))

                val params = parseQueryString(cipherString)
                val obfuscatedSignature = params["s"] ?: throw ParsingException("Could not parse cipher signature")
                val signatureParam = params["sp"]?.takeIf { it.isNotBlank() } ?: "signature"
                val urlString = params["url"] ?: throw ParsingException("Could not parse cipher url")

                val resolvedCipherUrl =
                    runCatching {
                        val urlBuilder = URLBuilder(urlString)
                        val deobfuscatedSig =
                            withJavaScriptPlayerCacheRecovery {
                                YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, obfuscatedSignature)
                            }
                        urlBuilder.parameters[signatureParam] = deobfuscatedSig
                        deobfuscateNParameter(videoId, urlBuilder.buildString())
                    }.recoverCatching { primaryFailure ->
                        if (MoriCipherRuntime.snapshot.value.status == CipherRuntimeStatus.READY) {
                            MoriCipherRuntime.resolveStreamUrl(videoId, cipherString).getOrThrow()
                        } else {
                            throw primaryFailure
                        }
                    }.getOrThrow()

                Result.success(
                    applyGvsPoToken(
                        url = resolvedCipherUrl,
                        client = client,
                        videoId = videoId,
                        authState = authState,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
        }.onSuccess { Timber.tag(logTag).i("Stream URL obtained successfully") }
    }

    private suspend fun deobfuscateNParameter(
        videoId: String,
        url: String,
    ): String =
        try {
            withJavaScriptPlayerCacheRecovery {
                YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (primaryFailure: Exception) {
            if (MoriCipherRuntime.snapshot.value.status == CipherRuntimeStatus.READY) {
                MoriCipherRuntime.transformNParameter(videoId, url).getOrElse { throw primaryFailure }
            } else {
                throw primaryFailure
            }
        }

    private inline fun <T> withJavaScriptPlayerCacheRecovery(block: () -> T): T {
        try {
            return block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            if (!error.isJavaScriptPlayerExtractorFailure()) {
                throw error
            }
            cachedSignatureTimestamp = null
            cachedSignatureTimestampExpiresAtMs = 0L
            runCatching { YoutubeJavaScriptPlayerManager.clearAllCaches() }
            try {
                return block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (retryFailure: Exception) {
                retryFailure.addSuppressed(error)
                throw retryFailure
            }
        }
    }

    private fun applyGvsPoToken(
        url: String,
        client: YouTubeClient?,
        videoId: String,
        authState: PlaybackAuthState,
    ): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (parsed.queryParameter("sabr") == "1") return url
        val supportsGvsPoToken = client != null && PlaybackAuthState.supportsGvsPoToken(client)
        if (!supportsGvsPoToken) {
            if (parsed.queryParameter("pot") == null) return url
            return parsed.newBuilder().removeAllQueryParameters("pot").build().toString()
        }
        val token = authState.resolveVideoScopedGvsPoToken(client, videoId) ?: return url
        return parsed.newBuilder().setQueryParameter("pot", token).build().toString()
    }

    private fun Throwable.isJavaScriptPlayerExtractorFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            val message = current.message.orEmpty()
            if (
                message.contains("deobfuscation", ignoreCase = true) ||
                message.contains("JavaScript player", ignoreCase = true) ||
                message.contains("base JavaScript player", ignoreCase = true) ||
                message.contains("throttling parameter", ignoreCase = true) ||
                message.contains("signature timestamp", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun Result<PlayerResponse>.getPlaybackPlayerResponseOrThrow(
        videoId: String,
        authState: PlaybackAuthState,
    ): PlayerResponse {
        val failure = exceptionOrNull()
        if (failure != null) {
            throwInvalidPlaybackLoginContextIfNeeded(videoId, authState, failure)
            throw failure
        }
        return getOrThrow()
    }

    private fun Result<PlayerResponse>.getPlaybackPlayerResponseOrNull(
        videoId: String,
        authState: PlaybackAuthState,
    ): PlayerResponse? {
        val failure = exceptionOrNull()
        if (failure != null) {
            throwInvalidPlaybackLoginContextIfNeeded(videoId, authState, failure)
            return null
        }
        return getOrNull()
    }

    private fun throwInvalidPlaybackLoginContextIfNeeded(
        videoId: String,
        authState: PlaybackAuthState,
        failure: Throwable,
    ) {
        if (!authState.hasPlaybackLoginContext) return
        if (!failure.isInvalidPlaybackLoginContextFailure()) return

        Timber.tag(logTag).w(
            failure,
            "Detected invalid logged-in playback context for %s; requiring login refresh",
            videoId,
        )
        throw InvalidPlaybackLoginContextException(
            videoId = videoId,
            targetUrl = "https://music.youtube.com/watch?v=$videoId",
            cause = failure,
        )
    }

    private fun Throwable.isInvalidPlaybackLoginContextFailure(): Boolean {
        val clientError = this as? ClientRequestException ?: return false
        if (clientError.response.status != HttpStatusCode.BadRequest) return false

        val message = clientError.message.orEmpty()
        if (!message.contains("/youtubei/v1/player", ignoreCase = true)) return false
        if (message.contains("Origin doesn't match Host", ignoreCase = true)) return false

        return message.contains("INVALID_ARGUMENT", ignoreCase = true) ||
            message.contains("invalid argument", ignoreCase = true)
    }

    private fun Throwable.isForbiddenPlayerRequest(): Boolean =
        (this as? ClientRequestException)?.response?.status == HttpStatusCode.Forbidden

    internal fun isBotDetectionError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "bot" in lower ||
            "unusual traffic" in lower ||
            "automated" in lower ||
            "confirm" in lower && "not a" in lower ||
            "not a robot" in lower ||
            "verify" in lower && "human" in lower
    }

    private fun isLoginRecoveryError(reason: String): Boolean {
        val lower = reason.lowercase(Locale.US)
        return "confirm your age" in lower ||
            "age-restricted" in lower ||
            "age restricted" in lower ||
            "inappropriate for some users" in lower ||
            "mature audiences" in lower ||
            "adult" in lower && "sign in" in lower ||
            "please sign in" in lower ||
            "sign in to confirm" in lower ||
            "allow" in lower && "youtube music" in lower
    }

    private fun isLoginRecoveryResponse(
        status: String,
        reason: String,
    ): Boolean = status.equals("LOGIN_REQUIRED", ignoreCase = true) || isLoginRecoveryError(reason)

    fun isBotDetectionException(error: PlaybackException): Boolean {
        val message = error.message.orEmpty()
        if (isBotDetectionError(message)) return true
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is BotDetectionPlaybackException) return true
            if (isBotDetectionError(cause.message.orEmpty())) return true
            cause = cause.cause
        }
        return false
    }

    fun isBadStreamPlayerResponseException(error: PlaybackException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is BadStreamPlayerResponseException) return true
            if (
                cause.message?.contains(
                    "YouTube playback stream clients returned no playable response",
                    ignoreCase = true,
                ) == true
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun describeClient(client: YouTubeClient): String = "${client.clientName}@${client.clientVersion}"
}
